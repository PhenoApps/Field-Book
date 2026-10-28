package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The background interval sync, scheduled by [BrapiSyncScheduler].
 *
 * With [KEY_STUDY_IDS] it instead syncs just those studies, for the reconnect notification's Sync
 * action, and raises the notification again if anything is still left unsynced.
 */
@HiltWorker
class BrapiSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val autoSyncManager: BrapiAutoSyncManager,
    private val notifier: BrapiReconnectNotifier
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_STUDY_IDS = "study_ids"
    }

    override suspend fun doWork(): Result {

        val studyIds = inputData.getIntArray(KEY_STUDY_IDS)

        if (studyIds != null) {

            autoSyncManager.syncStudies(studyIds.toList())

            // Conflicts and failures stay unsynced, so the user is told again where to look.
            notifier.notifyIfUnsynced(force = true)

        } else {

            autoSyncManager.syncAllBrapiFields()
        }

        // Failures are per field and already logged, the next interval simply tries again.
        return Result.success()
    }
}
