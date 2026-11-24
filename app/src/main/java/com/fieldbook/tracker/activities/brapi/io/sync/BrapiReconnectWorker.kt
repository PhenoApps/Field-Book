package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs once the device is connected again after losing its network, see [BrapiReconnectNotifier].
 */
@HiltWorker
class BrapiReconnectWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: BrapiReconnectNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {

        // Started while the network was still going down, try again once it is really back.
        if (!notifier.isOnline()) return Result.retry()

        notifier.notifyIfUnsynced()

        return Result.success()
    }
}
