package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fieldbook.tracker.preferences.PreferenceKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the background sync of every BrAPI field. The interval preference is handed to
 * WorkManager, which runs [BrapiSyncWorker] whether or not Field Book is open and waits for a
 * network connection.
 */
@Singleton
class BrapiSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: SharedPreferences
) : SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        const val INTERVAL_WORK_NAME = "brapi_interval_sync"

        private val SCHEDULE_KEYS = setOf(
            PreferenceKeys.BRAPI_ENABLED,
            PreferenceKeys.BRAPI_SYNC_INTERVAL
        )
    }

    fun start() {
        preferences.registerOnSharedPreferenceChangeListener(this)
        schedule()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key in SCHEDULE_KEYS) schedule()
    }

    private fun intervalMinutes(): Long =
        preferences.getString(PreferenceKeys.BRAPI_SYNC_INTERVAL, "0")?.toLongOrNull() ?: 0L

    private fun schedule() {

        val workManager = WorkManager.getInstance(context)
        val interval = intervalMinutes()

        if (preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false) && interval > 0) {

            val request = PeriodicWorkRequestBuilder<BrapiSyncWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                INTERVAL_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

        } else {

            workManager.cancelUniqueWork(INTERVAL_WORK_NAME)

        }
    }
}
