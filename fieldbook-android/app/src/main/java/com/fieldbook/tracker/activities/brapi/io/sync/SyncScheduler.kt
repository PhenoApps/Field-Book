package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fieldbook.tracker.preferences.PreferenceKeys
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val WORK_NAME = "fieldbook_periodic_sync"

    fun schedule(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = prefs.getBoolean(PreferenceKeys.BRAPI_SYNC_ENABLED, true)
        if (!enabled) {
            cancel(context)
            return
        }

        val intervalMinutes = prefs.getString(
            PreferenceKeys.BRAPI_SYNC_INTERVAL_MINUTES, "1"
        )?.toIntOrNull() ?: 1

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun isScheduled(context: Context): Boolean {
        val workInfo = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WORK_NAME).get()
        return workInfo.any { !it.state.isFinished }
    }
}
