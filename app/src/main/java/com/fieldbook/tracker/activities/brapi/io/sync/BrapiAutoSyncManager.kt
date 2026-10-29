package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiSyncEngine.AutoSyncResult
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.ImportFormat
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.Utils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs [BrapiSyncEngine.autoSync] outside of any screen, for syncing on field open, and publishes
 * each study's progress so field details can show it.
 */
@Singleton
class BrapiAutoSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: SharedPreferences,
    private val database: DataHelper,
    private val engine: BrapiSyncEngine
) {

    companion object {
        private const val TAG = "BrapiAutoSyncManager"
    }

    sealed class AutoSyncStatus {
        object Running : AutoSyncStatus()
        data class Done(val result: AutoSyncResult) : AutoSyncStatus()
    }

    // Requests started from a screen must outlive it, the sync keeps going if the user navigates away.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<Map<Int, AutoSyncStatus>>(emptyMap())
    val status: StateFlow<Map<Int, AutoSyncStatus>> = _status.asStateFlow()

    /**
     * Whether an automatic sync can reach the server at all right now.
     */
    fun canSync(): Boolean = preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)
            && BrAPIService.hasValidBaseUrl(context)
            && Utils.isConnected(context)

    /**
     * Fields that belong to the configured BrAPI server and are still in use.
     */
    fun eligibleFields(): List<FieldObject> = database.getAllFieldObjects().filter { field ->
        field.dataSourceFormat == ImportFormat.BRAPI
                && !field.archived
                && BrAPIService.checkMatchBrapiUrl(context, field.dataSource)
    }

    /**
     * Starts a sync of one study unless one is already running for it.
     */
    fun requestSync(studyId: Int) {

        if (!markRunning(studyId)) return

        scope.launch { sync(studyId) }
    }

    /**
     * Forgets the last automatic result for a study, once a manual sync has superseded it.
     */
    fun clearStatus(studyId: Int) {
        _status.update { current ->
            if (current[studyId] is AutoSyncStatus.Done) current - studyId else current
        }
    }

    /**
     * Claims the study for a sync, false if one is already running. Check and set happen in one
     * update so two quick requests cannot both start.
     */
    private fun markRunning(studyId: Int): Boolean {

        var claimed = false

        _status.update { current ->
            claimed = current[studyId] !is AutoSyncStatus.Running
            if (claimed) current + (studyId to AutoSyncStatus.Running) else current
        }

        return claimed
    }

    private suspend fun sync(studyId: Int): AutoSyncResult {

        val result = engine.autoSync(studyId)

        Log.d(TAG, "Automatic sync of study $studyId finished: $result")

        _status.update { it + (studyId to AutoSyncStatus.Done(result)) }

        return result
    }
}
