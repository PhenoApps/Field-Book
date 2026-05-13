package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.dao.StudyDao
import com.fieldbook.tracker.preferences.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        if (!prefs.getBoolean(PreferenceKeys.BRAPI_SYNC_ENABLED, false)) {
            Log.d(TAG, "Sync disabled, skipping")
            return@withContext Result.success()
        }

        val baseUrl = prefs.getString(PreferenceKeys.BRAPI_BASE_URL, null)
        if (baseUrl.isNullOrBlank()) {
            Log.d(TAG, "No BrAPI base URL, skipping")
            return@withContext Result.success()
        }

        try {
            val dataHelper = DataHelper(applicationContext)
            val brAPIService = BrAPIServiceFactory.getBrAPIService(applicationContext)
            val hostUrl = BrAPIService.getHostUrl(applicationContext) ?: "unknown"
            val fields = StudyDao.getAllFieldObjects("study_name")

            if (fields.isEmpty()) {
                Log.d(TAG, "No fields to sync")
                return@withContext Result.success()
            }

            var totalUploaded = 0
            var totalDownloaded = 0

            for (field in fields) {
                val fieldId = field.studyId
                val exportData = dataHelper.getBrAPIExportData(fieldId, hostUrl)

                // ── Upload new observations ──
                val newObs = exportData["newObservations"] ?: emptyList()
                if (newObs.isNotEmpty()) {
                    val uploaded = mutableListOf<Int>()
                    brAPIService.awaitCreateObservations(
                        applicationContext, newObs,
                        onChunkCompleted = { chunk -> synchronized(uploaded) { uploaded.add(chunk.size) } },
                        onChunkFailed = { code, _ -> Log.e(TAG, "Upload failed: $code") },
                    )
                    totalUploaded += uploaded.sum()
                }

                // ── Upload edited observations ──
                val editedObs = exportData["editedObservations"] ?: emptyList()
                if (editedObs.isNotEmpty()) {
                    val uploaded = mutableListOf<Int>()
                    brAPIService.awaitUpdateObservations(
                        applicationContext, editedObs,
                        onChunkCompleted = { chunk -> synchronized(uploaded) { uploaded.add(chunk.size) } },
                        onChunkFailed = { code, _ -> Log.e(TAG, "Update failed: $code") },
                    )
                    totalUploaded += uploaded.sum()
                }

                // ── Download: get latest observations for this study ──
                try {
                    val paginationManager = com.fieldbook.tracker.brapi.service.BrapiPaginationManager(0, 100)
                    val page = brAPIService.awaitGetSingleObservationPage(
                        fieldId.toString(), emptyList(), paginationManager
                    )
                    totalDownloaded += page.size
                    for (obs in page) {
                        if (obs.dbId != null) {
                            try {
                                dataHelper.insertObservation(
                                    obs.unitDbId ?: "",
                                    obs.variableDbId ?: "",
                                    obs.value ?: "",
                                    obs.collector ?: "",
                                    "", "", // location, notes
                                    fieldId.toString(),
                                    obs.dbId,
                                    obs.timestamp,
                                    obs.lastSyncedTime,
                                    obs.rep ?: "0",
                                )
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed for field $fieldId", e)
                }
            }

            // Update last sync time
            val now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            prefs.edit().putString(PreferenceKeys.BRAPI_LAST_SYNC_TIME, now).apply()

            if (totalUploaded > 0 || totalDownloaded > 0) {
                SyncNotifications.showSyncComplete(
                    applicationContext, totalUploaded, totalDownloaded, 0
                )
            }

            Log.d(TAG, "Sync done: $totalUploaded up, $totalDownloaded down")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
