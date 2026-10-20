package com.pos.transferapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.IOException

class DatabaseBackupBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                DatabaseBackup.scheduleDaily(appContext)
                DatabaseBackup.backupIfDue(appContext)
            } catch (error: IOException) {
                Log.e(TAG, "Daily database backup could not run after $action", error)
            } catch (error: SQLiteException) {
                Log.e(TAG, "Daily database backup failed after $action", error)
            } catch (error: SecurityException) {
                Log.e(TAG, "Daily database backup was denied after $action", error)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "DatabaseBackupBoot"
    }
}
