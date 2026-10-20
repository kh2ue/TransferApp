package com.pos.transferapp

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseBackup {
    private const val TAG = "DatabaseBackup"
    private const val JOB_ID = 24001
    private const val BACKUP_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
    private const val BACKUP_FLEX_MILLIS = 60L * 60L * 1000L
    private const val PREFS_NAME = "database_backup"
    private const val KEY_LAST_SUCCESS_DAY = "last_success_day"
    const val BACKUP_FILE_NAME = "TransferApp_Backup.db"
    private const val DATABASE_NAME = "TransferApp.db"
    private const val BACKUP_DIR_NAME = "backups"

    fun hasLiveDatabase(context: Context): Boolean {
        return context.getDatabasePath(DATABASE_NAME).isFile
    }

    @Synchronized
    fun getBackupFile(context: Context): File? {
        val backupDirectory = backupDirectoryOrNull(context) ?: return null
        migrateLegacyBackup(context, backupDirectory)
        val backupFile = File(backupDirectory, BACKUP_FILE_NAME)
        return if (backupFile.isFile && backupFile.length() > 0L) backupFile else null
    }

    @Throws(IOException::class)
    @Synchronized
    fun createBackup(context: Context, openDatabase: SQLiteDatabase? = null): File {
        val appContext = context.applicationContext
        val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
        if (!databaseFile.isFile) throw IOException("Database file does not exist")
        val backupDirectory = ensureBackupDirectory(appContext)
        migrateLegacyBackup(appContext, backupDirectory)

        val backupFile = File(backupDirectory, BACKUP_FILE_NAME)
        val temporaryFile = File(backupDirectory, "$BACKUP_FILE_NAME.tmp")
        val previousBackupFile = File(backupDirectory, "$BACKUP_FILE_NAME.previous")
        try {
            writeConsistentSnapshot(databaseFile, temporaryFile, openDatabase)
            validateBackupDatabase(temporaryFile.absolutePath)
            if (previousBackupFile.exists() && !previousBackupFile.delete()) {
                throw IOException("Could not remove stale previous backup")
            }
            val hadPreviousBackup = backupFile.exists()
            if (hadPreviousBackup && !backupFile.renameTo(previousBackupFile)) {
                throw IOException("Could not preserve previous backup")
            }
            if (!temporaryFile.renameTo(backupFile)) {
                if (hadPreviousBackup && !previousBackupFile.renameTo(backupFile)) {
                    Log.e(TAG, "Could not restore previous backup after replacement failed")
                }
                throw IOException("Could not finalize database backup")
            }
            if (previousBackupFile.exists() && !previousBackupFile.delete()) {
                Log.w(TAG, "Could not remove previous completed backup")
            }
            deleteLegacyExternalBackup(appContext)
            markBackupSavedToday(appContext)
            return backupFile
        } finally {
            if (temporaryFile.exists() && !temporaryFile.delete()) {
                Log.w(TAG, "Could not remove incomplete temporary backup")
            }
        }
    }

    @Synchronized
    fun backupIfDue(context: Context, openDatabase: SQLiteDatabase? = null) {
        val appContext = context.applicationContext
        if (!hasLiveDatabase(appContext)) return
        val today = todayKey()
        val alreadySavedToday = backupPreferences(appContext)
            .getString(KEY_LAST_SUCCESS_DAY, null) == today && getBackupFile(appContext) != null
        if (alreadySavedToday) return
        createBackup(appContext, openDatabase)
    }

    @Throws(IOException::class)
    @Synchronized
    fun restoreFromFile(context: Context, backupFile: File) {
        if (!backupFile.isFile) throw IOException("Backup file does not exist")
        validateBackupDatabase(backupFile.absolutePath)
        replaceLiveDatabase(context) { output ->
            FileInputStream(backupFile).use { input ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    @Throws(IOException::class)
    @Synchronized
    fun restoreFromUri(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        val stagingFile = File(context.cacheDir, "restore_import_${System.currentTimeMillis()}.db")
        try {
            val inputStream = resolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected backup file")
            inputStream.use { input ->
                FileOutputStream(stagingFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            validateBackupDatabase(stagingFile.absolutePath)
            replaceLiveDatabase(context) { output ->
                FileInputStream(stagingFile).use { input ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
        } finally {
            if (stagingFile.exists() && !stagingFile.delete()) {
                Log.w(TAG, "Could not remove temporary restore staging file")
            }
        }
    }

    fun scheduleDaily(context: Context) {
        val appContext = context.applicationContext
        val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val existing = scheduler.getPendingJob(JOB_ID)
        if (
            existing != null &&
            existing.isPersisted &&
            existing.intervalMillis == BACKUP_INTERVAL_MILLIS &&
            existing.flexMillis == BACKUP_FLEX_MILLIS &&
            existing.networkType == JobInfo.NETWORK_TYPE_NONE
        ) {
            return
        }
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(appContext, DatabaseBackupJobService::class.java)
        )
            .setPersisted(true)
            .setPeriodic(BACKUP_INTERVAL_MILLIS, BACKUP_FLEX_MILLIS)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(false)
            .build()
        if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            Log.e(TAG, "Could not schedule daily database backup")
        }
    }

    @Throws(IOException::class)
    private fun writeConsistentSnapshot(
        databaseFile: File,
        destination: File,
        openDatabase: SQLiteDatabase?
    ) {
        if (destination.exists() && !destination.delete()) {
            throw IOException("Could not clear temporary backup")
        }
        var vacuumDone = false
        val database = openDatabase ?: try {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
        } catch (error: SQLiteException) {
            throw IOException("Could not open the database for backup", error)
        }
        try {
            database.rawQuery("PRAGMA busy_timeout=5000", null).use { }
            val escapedPath = destination.absolutePath.replace("'", "''")
            try {
                database.execSQL("VACUUM INTO '$escapedPath'")
                vacuumDone = destination.isFile && destination.length() > 0L
            } catch (error: SQLException) {
                Log.i(TAG, "VACUUM INTO is unavailable; copying the database file")
                if (destination.exists() && !destination.delete()) {
                    throw IOException("Could not remove incomplete snapshot", error)
                }
            }
            if (!vacuumDone) {
                try {
                    val checkpoint = database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)
                    checkpoint.use {
                        if (it.moveToFirst() && it.getInt(0) != 0) {
                            throw IOException("Database checkpoint is busy")
                        }
                    }
                } catch (error: SQLiteException) {
                    throw IOException("Could not checkpoint the database before backup", error)
                }
            }
        } finally {
            if (openDatabase == null) database.close()
        }
        if (!vacuumDone) {
            copyFile(databaseFile, destination)
        }
    }

    @Throws(IOException::class)
    private fun validateBackupDatabase(path: String) {
        val database = try {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (error: SQLiteException) {
            throw IOException("Selected file is not a valid SQLite database", error)
        }
        try {
            val integrity = database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else "failed"
            }
            if (!integrity.equals("ok", ignoreCase = true)) {
                throw IOException("Backup database failed integrity check")
            }
            val hasCustomers = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='Customers'",
                null
            ).use { it.moveToFirst() }
            val hasTransactions = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='Transactions'",
                null
            ).use { it.moveToFirst() }
            if (!hasCustomers || !hasTransactions) {
                throw IOException("Backup file is missing required TransferApp tables")
            }
        } catch (error: SQLiteException) {
            throw IOException("Could not validate the backup database", error)
        } finally {
            database.close()
        }
    }

    @Throws(IOException::class)
    private fun replaceLiveDatabase(context: Context, writer: (FileOutputStream) -> Unit) {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        val parent = databaseFile.parentFile
            ?: throw IOException("Database directory is unavailable")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Could not create database directory")
        }

        val temporaryFile = File(parent, "$DATABASE_NAME.restore.tmp")
        val previousFile = File(parent, "$DATABASE_NAME.restore.previous")
        try {
            FileOutputStream(temporaryFile).use { output -> writer(output) }
            validateBackupDatabase(temporaryFile.absolutePath)

            if (previousFile.exists() && !previousFile.delete()) {
                throw IOException("Could not clear previous restore snapshot")
            }
            val hadLiveDatabase = databaseFile.exists()
            if (hadLiveDatabase && !databaseFile.renameTo(previousFile)) {
                throw IOException("Could not preserve the current database before restore")
            }
            if (!temporaryFile.renameTo(databaseFile)) {
                if (hadLiveDatabase && previousFile.exists() && !previousFile.renameTo(databaseFile)) {
                    Log.e(TAG, "Could not roll back live database after restore failure")
                }
                throw IOException("Could not finalize database restore")
            }

            deleteSidecarFiles(databaseFile)
            if (previousFile.exists() && !previousFile.delete()) {
                Log.w(TAG, "Could not remove previous database snapshot after restore")
            }
        } finally {
            if (temporaryFile.exists() && !temporaryFile.delete()) {
                Log.w(TAG, "Could not remove incomplete restore temporary file")
            }
        }
    }

    private fun deleteSidecarFiles(databaseFile: File) {
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            val sidecar = File(databaseFile.path + suffix)
            if (sidecar.exists() && !sidecar.delete()) {
                Log.w(TAG, "Could not delete database sidecar: ${sidecar.name}")
            }
        }
    }

    @Throws(IOException::class)
    private fun ensureBackupDirectory(context: Context): File {
        return backupDirectoryOrNull(context)
            ?: throw IOException("Could not create backup directory")
    }

    private fun backupDirectoryOrNull(context: Context): File? {
        val directory = File(context.filesDir, BACKUP_DIR_NAME)
        if (!directory.exists() && !directory.mkdirs()) return null
        return directory
    }

    private fun migrateLegacyBackup(context: Context, backupDirectory: File) {
        val legacyDirectory = context.getExternalFilesDir(null) ?: return
        if (legacyDirectory.absolutePath == backupDirectory.absolutePath) return
        val legacy = File(legacyDirectory, BACKUP_FILE_NAME)
        val internal = File(backupDirectory, BACKUP_FILE_NAME)
        try {
            if (!internal.isFile && legacy.isFile) {
                val staged = File(backupDirectory, "$BACKUP_FILE_NAME.migrate")
                copyFile(legacy, staged)
                if (!staged.renameTo(internal)) {
                    staged.delete()
                    Log.w(TAG, "Could not move the existing backup into internal storage")
                    return
                }
            }
            if (internal.isFile && legacy.isFile && !legacy.delete()) {
                Log.w(TAG, "Could not remove the old external backup after moving it")
            }
            File(legacyDirectory, "$BACKUP_FILE_NAME.tmp").delete()
            File(legacyDirectory, "$BACKUP_FILE_NAME.previous").delete()
        } catch (error: IOException) {
            Log.w(TAG, "Could not migrate the existing backup into internal storage", error)
        }
    }

    private fun deleteLegacyExternalBackup(context: Context) {
        val legacyDirectory = context.getExternalFilesDir(null) ?: return
        listOf(BACKUP_FILE_NAME, "$BACKUP_FILE_NAME.tmp", "$BACKUP_FILE_NAME.previous").forEach { name ->
            val legacy = File(legacyDirectory, name)
            if (legacy.exists() && !legacy.delete()) {
                Log.w(TAG, "Could not remove external backup file: $name")
            }
        }
    }

    private fun markBackupSavedToday(context: Context) {
        backupPreferences(context).edit().putString(KEY_LAST_SUCCESS_DAY, todayKey()).commit()
    }

    private fun backupPreferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    @Throws(IOException::class)
    private fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }
}
