package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the Dismiss button on a field's reconnect notification posted by
 * [BrapiReconnectNotifier]. Its Sync button opens the field's sync screen directly.
 */
class BrapiNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS = "com.fieldbook.tracker.brapi.sync.ACTION_DISMISS"
        const val EXTRA_STUDY_ID = "com.fieldbook.tracker.brapi.sync.EXTRA_STUDY_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != ACTION_DISMISS) return

        val studyId = intent.getIntExtra(EXTRA_STUDY_ID, -1)
        if (studyId != -1) BrapiReconnectNotifier.cancel(context, studyId)
    }
}
