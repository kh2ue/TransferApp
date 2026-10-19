package com.pos.transferapp

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object DatabaseBackup {
    private const val TAG = "DatabaseBackup"
    private const val JOB_ID = 24001
    private const val BACKUP_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L

    @Throws(IOException::class)
    @Synchronized
    fun createBackup(context: Context): File {
        val databaseFile = context.getDatabasePath("TransferApp.db")
        if (!databaseFile.isFile) throw IOException("Database file does not exist")
        val backupDirectory = context.getExternalFilesDir(null)
            ?: throw IOException("External files directory is unavailable")
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            throw IOException("Could not create backup directory")
        }

        val database = try {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
        } catch (error: SQLiteException) {
            throw IOException("Could not open the database for backup", error)
        }
        try {
            val checkpoint = database.rawQuery("PRAGMA wal_checkpoint(FULL)", null)
            checkpoint.use {
                if (it.moveToFirst() && it.getInt(0) != 0) {
                    throw IOException("Database checkpoint is busy")
                }
            }
        } catch (error: SQLiteException) {
            throw IOException("Could not checkpoint the database before backup", error)
        } finally {
            database.close()
        }

        val backupFile = File(backupDirectory, "TransferApp_Backup.db")
        val temporaryFile = File(backupDirectory, "TransferApp_Backup.db.tmp")
        val previousBackupFile = File(backupDirectory, "TransferApp_Backup.db.previous")
        try {
            FileInputStream(databaseFile).use { input ->
                FileOutputStream(temporaryFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
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
            return backupFile
        } finally {
            if (temporaryFile.exists() && !temporaryFile.delete()) {
                Log.w(TAG, "Could not remove incomplete temporary backup")
            }
        }
    }

    fun scheduleDaily(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, DatabaseBackupJobService::class.java)
        )
            .setPersisted(true)
            .setPeriodic(BACKUP_INTERVAL_MILLIS)
            .build()
        if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            Log.e(TAG, "Could not schedule daily database backup")
        }
    }
}
