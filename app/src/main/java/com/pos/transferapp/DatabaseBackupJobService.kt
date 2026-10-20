package com.pos.transferapp

import android.app.job.JobParameters
import android.app.job.JobService
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.IOException

class DatabaseBackupJobService : JobService() {
    @Volatile
    private var jobStopped = false

    override fun onStartJob(params: JobParameters): Boolean {
        jobStopped = false
        Thread {
            val needsRetry = try {
                if (DatabaseBackup.hasLiveDatabase(applicationContext)) {
                    DatabaseBackup.createBackup(applicationContext)
                }
                false
            } catch (error: IOException) {
                Log.e(TAG, "Scheduled database backup failed", error)
                true
            } catch (error: SecurityException) {
                Log.e(TAG, "Scheduled database backup was denied", error)
                true
            } catch (error: SQLiteException) {
                Log.e(TAG, "Scheduled database backup failed due to a database error", error)
                true
            }
            if (!jobStopped) jobFinished(params, needsRetry)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobStopped = true
        return true
    }

    private companion object {
        const val TAG = "DatabaseBackupJob"
    }
}
