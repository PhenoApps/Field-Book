package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Handles the Sync and Dismiss buttons on the reconnect notification posted by
 * [BrapiReconnectNotifier].
 */
class BrapiNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SYNC = "com.fieldbook.tracker.brapi.sync.ACTION_SYNC"
        const val ACTION_DISMISS = "com.fieldbook.tracker.brapi.sync.ACTION_DISMISS"
        const val EXTRA_STUDY_IDS = "com.fieldbook.tracker.brapi.sync.EXTRA_STUDY_IDS"

        private const val SYNC_WORK_NAME = "brapi_notification_sync"
    }

    override fun onReceive(context: Context, intent: Intent) {

        NotificationManagerCompat.from(context).cancel(BrapiReconnectNotifier.NOTIFICATION_ID)

        if (intent.action != ACTION_SYNC) return

        val studyIds = intent.getIntArrayExtra(EXTRA_STUDY_IDS) ?: return

        // Handed to WorkManager rather than run here, a receiver only lives for a few seconds and
        // a sync with images can take much longer.
        val request = OneTimeWorkRequestBuilder<BrapiSyncWorker>()
            .setInputData(workDataOf(BrapiSyncWorker.KEY_STUDY_IDS to studyIds))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
