package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.model.FieldBookImage
import com.fieldbook.tracker.brapi.model.Observation
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.preferences.PreferenceKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import androidx.core.content.edit
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.CategoricalTraitLayout
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.coders.DateJsonCoder
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.DateJsonUtil
import com.fieldbook.tracker.utilities.JsonUtil
import com.fieldbook.tracker.utilities.export.ValueProcessorFormatAdapter
import kotlinx.coroutines.withContext

@HiltViewModel
class BrapiSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataHelper: DataHelper,
    private val preferences: SharedPreferences,
    private val traitRepo: TraitRepository,
    private val valueProcessorFormatAdapter: ValueProcessorFormatAdapter
) : ViewModel() {

    private var brAPIService = BrAPIServiceFactory.getBrAPIService(context)

    private val _uiState = MutableStateFlow(BrapiExportUiState())
    val uiState = _uiState.asStateFlow()

    private var newObservations: List<Observation> = emptyList()
    private var syncedObservations: List<Observation> = emptyList()
    private var syncedImageObservations: List<Observation> = emptyList()
    private var editedObservations: List<Observation> = emptyList()
    private var newImageObservations: List<Observation> = emptyList()
    private var editedImageObservations: List<Observation> = emptyList()
    private var incompleteImageObservations: List<Observation> = emptyList()

    // Conflicts detected during last download that need user resolution
    private var pendingConflicts: List<Pair<Observation, Observation?>> = emptyList()
    // Pending pre-upload re-sync choices where local observations have no BrAPI id.
    private var pendingResyncChoices: List<PendingResyncChoice> = emptyList()
    private val resyncUploadAsNewKeys = mutableSetOf<String>()
    private val resyncAdoptServerKeys = mutableSetOf<String>()

    private var activeJob: Job? = null

    companion object {
        private const val TAG = "BrapiExportViewModel"
    }

    private val calendar: Calendar by lazy { Calendar.getInstance() }
    private val timestamp by lazy {
        SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSSZZZZZ",
            Locale.getDefault()
        )
    }

    data class Conflict(val serverObservation: Observation, val localObservation: Observation)
    private data class PreUploadResyncResult(
        val observationsToUpload: List<Observation>,
        val requiresUserChoice: Boolean
    )
    private data class PendingResyncChoice(
        val key: String,
        val localObservation: Observation,
        val serverObservation: Observation,
        val preservedLocalValue: String
    )

    enum class UploadError {
        NONE,
        MISSING_OBSERVATION_IN_RESPONSE,
        MULTIPLE_OBSERVATIONS_PER_VARIABLE,
        WRONG_NUM_OBSERVATIONS_RETURNED,
        API_CALLBACK_ERROR,
        API_UNAUTHORIZED_ERROR,
        API_NOT_SUPPORTED_ERROR,
        API_PERMISSION_ERROR
    }

    fun setServerDisplayName(name: String) {
        _uiState.update {
            it.copy(
                brapiServerDisplayName = name
            )
        }
    }

    fun resetClient() {
        brAPIService = BrAPIServiceFactory.getBrAPIService(context)
    }

    fun setMergeStrategy(newStrategy: MergeStrategy) {
        _uiState.update {
            it.copy(
                downloadMergeStrategy = newStrategy
            )
        }
    }

    // Load persisted last-checked values from SharedPreferences into the UI state
    private fun loadLastCheckedFromPrefs() {
        val lastDownload = preferences.getString(PreferenceKeys.BRAPI_LAST_CHECKED_DOWNLOAD, null)
        _uiState.update {
            it.copy(
                lastCheckedDownloadText = lastDownload
            )
        }
    }

    // Persist the given last-checked download text
    fun persistLastCheckedDownload(text: String?) {
        preferences.edit { putString(PreferenceKeys.BRAPI_LAST_CHECKED_DOWNLOAD, text) }
        _uiState.update { it.copy(lastCheckedDownloadText = text) }
    }

    fun refreshLocalStatus() {

        val studyId = uiState.value.study?.studyId ?: return

        val hostURL = BrAPIService.getHostUrl(context)
        val exportData = dataHelper.getBrAPIExportData(studyId, hostURL)

        newObservations = exportData["newObservations"] ?: emptyList()
        syncedObservations = exportData["syncedObservations"] ?: emptyList()
        syncedImageObservations = exportData["syncedImageObservations"] ?: emptyList()
        editedObservations = exportData["editedObservations"] ?: emptyList()
        newImageObservations = exportData["newImageObservations"] ?: emptyList()
        editedImageObservations = exportData["editedImageObservations"] ?: emptyList()
        incompleteImageObservations = exportData["incompleteImageObservations"] ?: emptyList()

        _uiState.update {
            it.copy(
                newObservationCount = newObservations.size,
                newImageCount = newImageObservations.size,
                editedObservationCount = editedObservations.size,
                editedImageCount = editedImageObservations.size,
                incompleteImageCount = incompleteImageObservations.size,
                syncedObservationCount = (exportData["syncedObservations"] ?: emptyList()).size,
                syncedImageCount = (exportData["syncedImageObservations"] ?: emptyList()).size,
            )
        }
    }

    /**
     * Loads initial data for the screen. Safe to call multiple times.
     */
    fun initialize(studyId: Int) {

        viewModelScope.launch(Dispatchers.IO) {

            Log.d(TAG, "Initializing BrapiExportViewModel")

            val fieldObject = dataHelper.getFieldObject(studyId)

            _uiState.update {
                it.copy(
                    isInitialized = true,
                    study = fieldObject,
                    viewMode = ViewMode.IDLE,
                    isDownloadFinished = false,
                    isUploadFinished = false,
                )
            }

            refreshLocalStatus()
            // load persisted last-checked timestamps into state
            loadLastCheckedFromPrefs()
            clearPendingResyncChoices()
            resyncUploadAsNewKeys.clear()
            resyncAdoptServerKeys.clear()

        }
    }

    fun startUpload() {

        if (activeJob?.isActive == true) return

        val study = _uiState.value.study
        if (study == null || study.studyDbId.isNullOrEmpty()) {
            _uiState.update {
                it.copy(
                    isUploadFinished = false,
                    uploadError = context.getString(R.string.study_info_missing)
                )
            }
            return
        }

        if (newObservations.isEmpty() && editedObservations.isEmpty() && newImageObservations.isEmpty() && editedImageObservations.isEmpty()) {
            _uiState.update {
                it.copy(
                    isUploadFinished = false,
                    uploadError = context.getString(R.string.nothing_to_upload)
                )
            }
            return
        }

        if (uiState.value.pendingResyncChoicesCount > 0) {
            _uiState.update {
                it.copy(
                    isUploadFinished = false,
                    uploadError = context.getString(R.string.brapi_resync_choice_required)
                )
            }
            return
        }

        activeJob = viewModelScope.launch(Dispatchers.IO) {

            try {

                if (newObservations.isNotEmpty()) {

                    _uiState.update {
                        it.copy(
                            viewMode = ViewMode.EXPORTING,
                            isUploadFinished = false,
                            progress = Progress(
                                message = context.getString(R.string.brapi_export_uploading_new_observations),
                                total = newObservations.size
                            )
                        )
                    }

                    processObservations(newObservations, ::processNewObservations)
                    if (uiState.value.pendingResyncChoicesCount > 0) return@launch
                }

                if (editedObservations.isNotEmpty()) {

                    _uiState.update {
                        it.copy(
                            viewMode = ViewMode.EXPORTING,
                            isUploadFinished = false,
                            progress = Progress(
                                message = context.getString(R.string.brapi_export_uploading_edited_observations),
                                total = editedObservations.size
                            )
                        )
                    }

                    processObservations(editedObservations, ::processEditedObservations)
                    if (uiState.value.pendingResyncChoicesCount > 0) return@launch
                }

                if (newImageObservations.isNotEmpty() && uiState.value.uploadImages) {

                    _uiState.update {
                        it.copy(
                            viewMode = ViewMode.EXPORTING,
                            isUploadFinished = false,
                            progress = Progress(
                                message = context.getString(R.string.brapi_export_uploading_new_images),
                                total = newImageObservations.size,
                                current = 0,
                            )
                        )
                    }

                    Log.d(TAG, "Uploading new images to BrAPI")

                    processImages(dataHelper.getImageDetails(context, newImageObservations))

                }

                val totalImageEdits =
                    editedImageObservations.size + incompleteImageObservations.size

                if (totalImageEdits > 0 && uiState.value.uploadImages) {

                    _uiState.update {
                        it.copy(
                            viewMode = ViewMode.EXPORTING,
                            isUploadFinished = false,
                            progress = Progress(
                                message = context.getString(R.string.brapi_export_uploading_new_images),
                                total = totalImageEdits
                            )
                        )
                    }

                    Log.d(TAG, "Uploading updates to BrAPI images")

                    processImages(
                        dataHelper.getImageDetails(context, editedImageObservations)
                                + dataHelper.getImageDetails(context, incompleteImageObservations),
                        isNew = false
                    )
                }

                _uiState.update {
                    it.copy(
                        viewMode = ViewMode.IDLE,
                        isUploadFinished = true,
                    )
                }

            } catch (e: CancellationException) {

                Log.w(TAG, "Download job was cancelled externally.", e)
                _uiState.update {
                    it.copy(
                        viewMode = ViewMode.IDLE,
                        isDownloadFinished = true,
                        downloadError = context.getString(R.string.download_cancelled)
                    )
                }

            } finally {

                refreshLocalStatus()
            }
        }
    }

    fun startDownload() {

        if (activeJob?.isActive == true) return

        val study = _uiState.value.study
        if (study == null || study.studyDbId.isNullOrEmpty()) {
            _uiState.update {
                it.copy(
                    isDownloadFinished = true,
                    downloadError = context.getString(R.string.study_info_missing),
                )
            }
            return
        }

        activeJob = viewModelScope.launch(Dispatchers.IO) {

            try {

                _uiState.update {
                    it.copy(
                        viewMode = ViewMode.DOWNLOADING,
                        isDownloadFinished = false,
                        progress = Progress(
                            message = context.getString(R.string.brapi_downloading_observations),
                            total = 1
                        )
                    )
                }

                getObservations()
                    .onCompletion { cause ->

                        Log.d(TAG, "Download flow completed with cause: $cause")

                        // record last checked time for download
                        val checkedDownloadText = timestamp.format(calendar.getTime())

                        _uiState.update {
                            it.copy(
                                viewMode = ViewMode.IDLE,
                                isDownloadFinished = true,
                                downloadError = if (cause == null) "" else cause.message,
                                lastCheckedDownloadText = checkedDownloadText

                            )
                        }
                        Log.d(TAG, "Download flow completed. Cause: ${cause?.message}")
                    }
                    .catch { cause ->

                        Log.e(TAG, "Error caught during download flow", cause)

                        _uiState.update {
                            it.copy(
                                viewMode = ViewMode.IDLE,
                                downloadError = cause.message ?: context.getString(R.string.download_failed)
                            )
                        }
                    }
                    .collect { update ->

                        when (update) {
                            is DownloadProgressUpdate.InDownloadProgress -> {
                                _uiState.update {
                                    it.copy(
                                        progress = it.progress.copy(
                                            current = update.pageCount,
                                            total = update.totalPages,
                                            message = context.getString(
                                                R.string.brapi_downloading_page,
                                                update.pageCount,
                                                update.totalPages
                                            )
                                        )
                                    )
                                }
                            }

                            is DownloadProgressUpdate.Completed -> {

                                Log.d(TAG, "Data size: ${update.data.size}")

                                val (inserts, updates, conflicts) = withContext(Dispatchers.IO) {

                                    val traitsById = traitRepo.getTraits().associateBy { it.id }
                                    val resolved = resolveObservationStatus(update.data, traitsById)

                                    Log.d(TAG, "Saving ${resolved.first.size} new observations")

                                    uiState.value.study?.let { fieldObject ->

                                        resolved.first.forEach { obs ->

                                            Log.d(TAG, "Saving observation: ${obs.dbId}")

                                            obs.internalVariableDbId = obs.variableDbId

                                            // Store categorical BrAPI values using Field Book's internal JSON format.
                                            obs.value = normalizeDownloadedObservationValue(obs, traitsById)

                                            val rep = dataHelper.getNextRep(fieldObject.studyId.toString(), obs.unitDbId, obs.internalVariableDbId).toInt()

                                            dataHelper.insertObservation(
                                                obs.unitDbId,
                                                obs.internalVariableDbId,
                                                obs.value ?: "",
                                                obs.collector ?: "",
                                                "",
                                                "",
                                                fieldObject.studyId.toString(),
                                                obs.dbId,
                                                obs.timestamp,
                                                obs.lastSyncedTime,
                                                rep.toString()
                                            )
                                        }
                                    }

                                    Log.d(TAG, "Updating local database with ${resolved.second.size} updates")

                                    resolved.second.forEach { obs ->
                                        if (obs.internalVariableDbId.isNullOrBlank()) {
                                            obs.internalVariableDbId = obs.variableDbId
                                        }
                                        obs.value = normalizeDownloadedObservationValue(obs, traitsById)
                                    }

                                    dataHelper.updateObservationsByBrapiId(resolved.second)

                                    resolved
                                }

                                if (conflicts.isNotEmpty()) {
                                    // store pending conflicts and notify UI to prompt user
                                    setPendingConflicts(conflicts)
                                    _uiState.update {
                                        it.copy(
                                            viewMode = ViewMode.IDLE,
                                            isDownloadFinished = true,
                                            downloadedInserts = inserts.size,
                                            downloadedUpdates = updates.size,
                                            downloadError = "",
                                        )
                                    }
                                } else {
                                    _uiState.update {
                                        it.copy(
                                            viewMode = ViewMode.IDLE,
                                            isDownloadFinished = true,
                                            downloadedInserts = inserts.size,
                                            downloadedUpdates = updates.size,
                                            downloadError = if (inserts.isEmpty() && updates.isEmpty()) {
                                                context.getString(R.string.no_new_or_updated_observations_found)
                                            } else {
                                                ""
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
            } catch (e: CancellationException) {

                Log.w(TAG, "Download job was cancelled externally.", e)

                _uiState.update {
                    it.copy(
                        viewMode = ViewMode.IDLE,
                        isDownloadFinished = true,
                        downloadError = context.getString(R.string.download_cancelled)
                    )
                }

            } finally {

                refreshLocalStatus()

            }
        }
    }

    fun cancelActiveJob() {
        activeJob?.cancel(CancellationException(context.getString(R.string.user_cancelled_operation)))
    }

    fun toggleImageUpload() {
        _uiState.update {
            it.copy(
                uploadImages = !it.uploadImages
            )
        }
    }

    /**
     * Reduces a stored or BrAPI observation value to a plain, comparable string so that
     * local (Field Book JSON) and server (BrAPI token) representations of the same value
     * are treated as equal.
     *
     * - Categorical: both local JSON and BrAPI tokens are reduced to "val1:val2" format.
     * - Date: local DateJson is reduced to its formattedDate (ISO "YYYY-MM-DD"); BrAPI ISO strings pass through unchanged.
     * - Other types: returned unchanged.
     */
    private fun normalizeValueForComparison(value: String, trait: TraitObject?): String {
        if (value.isBlank() || value == "NA") return value
        if (trait == null) return value
        return when {
            trait.format in CategoricalTraitLayout.POSSIBLE_VALUES ->
                CategoryJsonUtil.encodeBrapiCategoricalValue(value, trait.categories)
            trait.format == Formats.DATE.getDatabaseName() -> {
                val decoded = DateJsonUtil.decode(value)
                if (decoded is DateJsonCoder.DateJson) decoded.formattedDate else value
            }
            else -> value
        }
    }

    /**
     * @param: observations downloaded from brapi
     * Compare brapi observations with edited and synced observations to determine if we need to push
     * updates or update the local database.
     *
     * Default conflict strategy of using the newest observation
     *
     *
     * @return A Triple containing:
     *         - first: a list of obs from BrAPI that are new
     *         - second: a list of obs from brapi that can be updated in fieldbook
     *         - third: a list of obs that have conflicting edits (local is newer), and should be pushed
     */
    private fun resolveObservationStatus(brapiObservations: List<Observation>, traitsById: Map<String, TraitObject>): Triple<List<Observation>, List<Observation>, List<Conflict>> {

        val insertObservations = mutableListOf<Observation>()
        val updateObservations = mutableListOf<Observation>()
        val conflicts = mutableListOf<Conflict>()

        brapiObservations.forEach { brapiObs ->

            //check if this observation corresponds to one we have edited locally
            //either it has the same brapi id or its unit/variable ids match
            //edited observations have previously been synced so they must have a brapi id
            val localEdited = editedObservations.firstOrNull {
                it.dbId == brapiObs.dbId
            }

            if (localEdited != null) {
                val trait = traitsById[localEdited.internalVariableDbId]
                val localNorm = normalizeValueForComparison(localEdited.value ?: "", trait)
                val serverNorm = normalizeValueForComparison(brapiObs.value ?: "", trait)
                if (localNorm != serverNorm) {
                    // Conflict between local edited and server version: record for user resolution
                    val conflict = Conflict(serverObservation = brapiObs, localObservation = localEdited)
                    conflicts.add(conflict)
                }
                return@forEach
            }

            val localNew = (syncedImageObservations + syncedObservations).firstOrNull {
                it.dbId == brapiObs.dbId
            }

            if (localNew == null) {
                //purely new download
                insertObservations.add(brapiObs)

            } else { //already exists but check if there has been an update

                val trait = traitsById[localNew.internalVariableDbId]
                val localNorm = normalizeValueForComparison(localNew.value ?: "", trait)
                val serverNorm = normalizeValueForComparison(brapiObs.value ?: "", trait)
                if (localNorm != serverNorm) {
                    conflicts.add(Conflict(serverObservation = brapiObs, localObservation = localNew))
                }
            }
        }

        return Triple(insertObservations, updateObservations, conflicts)
    }

    /**
     * Decodes a raw stored observation value into a human-readable display string.
     *  - Categorical: decodes internal JSON (or raw BrAPI token list) to show category labels.
     *  - Date: decodes JSON to show the formatted date or day-of-year per trait settings.
     *  - All other types: returns the value unchanged.
     */
    private fun decodeObservationValueForDisplay(value: String, trait: TraitObject?): String {
        if (value.isBlank() || value == "NA") return value
        if (trait == null) return value
        return when {
            trait.format in CategoricalTraitLayout.POSSIBLE_VALUES -> {
                // Local values are stored as internal JSON; server values may be raw BrAPI tokens.
                // Normalize to internal JSON first, then extract the human-readable labels.
                val encoded = if (value.trim().startsWith("[") && JsonUtil.isJsonValid(value)) {
                    value
                } else {
                    CategoryJsonUtil.encodeDownloadedBrapiCategoricalValue(value, trait.categories)
                }
                try {
                    CategoryJsonUtil.decode(encoded).joinToString(":") { it.label }
                } catch (_: Exception) {
                    value
                }
            }
            trait.format == Formats.DATE.getDatabaseName() -> {
                val decoded = DateJsonUtil.decode(value)
                if (decoded is DateJsonCoder.DateJson) {
                    if (trait.useDayOfYear) decoded.dayOfYear else decoded.formattedDate
                } else value
            }
            else -> value
        }
    }

    // Store pending conflicts for UI to resolve
    private suspend fun setPendingConflicts(conflicts: List<Conflict>) {
        pendingConflicts = conflicts.map { Pair(it.serverObservation, it.localObservation) }

        val traitsById = traitRepo.getTraits().associateBy { it.id }

        val uiConflicts = conflicts.map { c ->
            val trait = traitsById[c.localObservation.internalVariableDbId]
                ?: traitsById[c.serverObservation.internalVariableDbId]
            PendingConflictUi(
                brapiId = c.serverObservation.dbId ?: c.localObservation.dbId ?: "",
                localValue = decodeObservationValueForDisplay(c.localObservation.value ?: "", trait),
                serverValue = decodeObservationValueForDisplay(c.serverObservation.value ?: "", trait),
                localDbId = c.localObservation.dbId,
                serverDbId = c.serverObservation.dbId
            )
        }
        _uiState.update { it.copy(pendingConflictsCount = uiConflicts.size, pendingConflicts = uiConflicts) }
    }

    /**
     * Apply a user-selected merge strategy to pending conflicts and persist results.
     * Supports Manual strategy where a map of brapiId->choice is provided (true=server, false=local).
     */
    fun applyConflictResolution(strategy: MergeStrategy, manualChoices: Map<String, Boolean>? = null) {
        if (pendingConflicts.isEmpty()) return

        val toUpdateFromServer = mutableListOf<Observation>()
        val toKeepLocal = mutableListOf<Observation>()

        when (strategy) {
            is MergeStrategy.Local -> {
                pendingConflicts.forEach { (_, local) -> local?.let { toKeepLocal.add(it) } }
            }

            is MergeStrategy.Server -> {
                pendingConflicts.forEach { (server, _) -> toUpdateFromServer.add(server) }
            }

            is MergeStrategy.MostRecent -> {
                pendingConflicts.forEach { (server, local) ->
                    if (local != null && local.timestamp > server.timestamp) toKeepLocal.add(local) else toUpdateFromServer.add(server)
                }
            }

            is MergeStrategy.Manual -> {
                // manualChoices: map from brapiId to true (choose server) or false (choose local)
                if (manualChoices == null) return
                Log.d(TAG, "Applying manual conflict resolution with choices: ${manualChoices.keys.size} entries")
                pendingConflicts.forEach { (server, local) ->
                    val id = server.dbId ?: local?.dbId
                    if (id == null) return@forEach
                    if (manualChoices[id] == true) {
                        toUpdateFromServer.add(server)
                    } else {
                        local?.let { toKeepLocal.add(it) }
                    }
                }
            }
        }

        // clear and update ui state
        pendingConflicts = emptyList()

        _uiState.update { it.copy(pendingConflictsCount = 0, pendingConflicts = emptyList()) }

        viewModelScope.launch(Dispatchers.IO) {
            if (toUpdateFromServer.isNotEmpty()) {
                // Re-encode raw BrAPI server values to Field Book's internal format before persisting.
                val traitsById = traitRepo.getTraits().associateBy { it.id }
                toUpdateFromServer.forEach { obs ->
                    if (obs.internalVariableDbId.isNullOrBlank()) {
                        obs.internalVariableDbId = obs.variableDbId
                    }
                    obs.value = normalizeDownloadedObservationValue(obs, traitsById)
                }
                dataHelper.updateObservationsByBrapiId(toUpdateFromServer)
            }

            refreshLocalStatus()

        }
    }

    private suspend fun setPendingResyncChoices(choices: List<PendingResyncChoice>) {
        pendingResyncChoices = choices

        val traitsById = traitRepo.getTraits().associateBy { it.id }

        val uiChoices = choices.map { choice ->
            val trait = traitsById[choice.localObservation.internalVariableDbId]
                ?: traitsById[choice.serverObservation.internalVariableDbId]
            PendingResyncChoiceUi(
                key = choice.key,
                localValue = decodeObservationValueForDisplay(choice.localObservation.value ?: "", trait),
                serverValue = decodeObservationValueForDisplay(choice.serverObservation.value ?: "", trait),
                localFieldBookId = choice.localObservation.fieldBookDbId,
                serverDbId = choice.serverObservation.dbId
            )
        }
        _uiState.update {
            it.copy(
                pendingResyncChoicesCount = uiChoices.size,
                pendingResyncChoices = uiChoices
            )
        }
    }

    private fun clearPendingResyncChoices() {
        pendingResyncChoices = emptyList()
        _uiState.update {
            it.copy(
                pendingResyncChoicesCount = 0,
                pendingResyncChoices = emptyList()
            )
        }
    }

    fun applyPendingResyncChoices(applyAsUpdate: Boolean) {
        if (pendingResyncChoices.isEmpty()) return

        val syncTime = timestamp.format(calendar.time)
        val updatesToPersist = mutableListOf<Observation>()

        pendingResyncChoices.forEach { choice ->
            val key = choice.key
            if (applyAsUpdate) {
                resyncAdoptServerKeys.add(key)
                resyncUploadAsNewKeys.remove(key)

                val localObservation = choice.localObservation
                localObservation.value = choice.preservedLocalValue
                localObservation.dbId = choice.serverObservation.dbId
                val serverSyncTime = choice.serverObservation.timestamp
                if (serverSyncTime != null) {
                    localObservation.setLastSyncedTime(serverSyncTime)
                } else {
                    localObservation.setLastSyncedTime(syncTime)
                }
                updatesToPersist.add(localObservation)
            } else {
                resyncUploadAsNewKeys.add(key)
                resyncAdoptServerKeys.remove(key)
            }
        }

        if (updatesToPersist.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                dataHelper.updateObservationsByFieldBookId(updatesToPersist)
                refreshLocalStatus()
            }
        }

        clearPendingResyncChoices()

        _uiState.update {
            it.copy(
                uploadError = ""
            )
        }
    }


    /**
     * New Observations are saved in chunks, when returning from BrAPI they are updated in the local FB
     * database to save the observationDbId and synced time.
     */
    private fun processNewObservations(
        newObs: List<Observation>,
        localValuesByFieldBookId: Map<String, String>
    ): Flow<UploadProgressUpdate> =
        channelFlow {

            val incompatible = AtomicInteger(0)
            val new = AtomicInteger(0)
            val edited = AtomicInteger(0)

            brAPIService.awaitCreateObservations(
                context,
                newObs,
                { chunk ->

                    val (incompats, news, edits) = processChunk(
                        chunk,
                        newObs,
                        localValuesByFieldBookId = localValuesByFieldBookId
                    )

                    incompatible.addAndGet(incompats)
                    new.addAndGet(news)
                    edited.addAndGet(edits)

                    trySend(UploadProgressUpdate.InUploadProgress(chunk.size, newObs.size))

                }) { errorCode, failedChunk ->

                //errors coming from brapi api
                trySend(UploadProgressUpdate.InUploadFailedChunk(errorCode, failedChunk))
            }

            send(UploadProgressUpdate.Completed(incompatible.get(), new.get(), edited.get()))

        }.flowOn(Dispatchers.IO)

    private fun processChunk(
        chunk: List<Observation>,
        referenceObservations: List<Observation>,
        isNew: Boolean = true,
        localValuesByFieldBookId: Map<String, String> = emptyMap()
    ): Triple<Int, Int, Int> {

        var incompatible = 0
        var new = 0
        var edited = 0

        //chunk returned from brapi, each observation is updated individually
        chunk.forEach { observation ->

            var retVal = UploadError.NONE

            val syncTime = timestamp.format(calendar.getTime())

            // find observation with matching keys and update observationDbId
            val firstIndex =
                referenceObservations.indexOfFirst { it.fieldBookDbId == observation.fieldBookDbId }
            val lastIndex =
                referenceObservations.indexOfLast { it.fieldBookDbId == observation.fieldBookDbId }

            if (firstIndex == -1) {

                retVal = UploadError.MISSING_OBSERVATION_IN_RESPONSE

            } else if (firstIndex != lastIndex) {

                retVal = UploadError.MULTIPLE_OBSERVATIONS_PER_VARIABLE

            } else if (firstIndex in referenceObservations.indices) {
                val update = referenceObservations[firstIndex]
                update.fieldBookDbId?.let { fieldBookId ->
                    localValuesByFieldBookId[fieldBookId]?.let { preservedValue ->
                        // Keep local DB observation value in Field Book's internal representation.
                        update.value = preservedValue
                    }
                }
                update.dbId = observation.dbId
                update.setLastSyncedTime(syncTime)
                dataHelper.updateObservationsByFieldBookId(listOf(update))

            }

            //process any potential upload errors here and display
            if (retVal != UploadError.NONE) {

                Log.e(TAG, "Error processing observation: $retVal")

                incompatible++

            } else if (isNew) {

                new++

            } else {

                edited++

            }
        }

        return Triple(incompatible, new, edited)
    }

    /**
     * When observation timestamp is greater that synced time, they must be pushed and updated
     * on the brapi endpoint.
     */
    private fun processEditedObservations(
        editedObs: List<Observation>,
        localValuesByFieldBookId: Map<String, String>
    ): Flow<UploadProgressUpdate> =
        channelFlow {

            val incompatible = AtomicInteger(0)
            val new = AtomicInteger(0)
            val edited = AtomicInteger(0)

            brAPIService.awaitUpdateObservations(
                context,
                editedObs, { chunk ->

                    val (incompats, news, edits) = processChunk(
                        chunk,
                        editedObs,
                        isNew = false,
                        localValuesByFieldBookId = localValuesByFieldBookId
                    )

                    incompatible.addAndGet(incompats)
                    new.addAndGet(news)
                    edited.addAndGet(edits)

                    trySend(UploadProgressUpdate.InUploadProgress(chunk.size, editedObs.size))

                }) { errorCode, failedChunk ->

                trySend(UploadProgressUpdate.InUploadFailedChunk(errorCode, failedChunk))
            }

            send(UploadProgressUpdate.Completed(incompatible.get(), new.get(), edited.get()))

        }.flowOn(Dispatchers.IO)

    private suspend fun processObservations(
        observations: List<Observation>,
        processor: suspend (List<Observation>, Map<String, String>) -> Flow<UploadProgressUpdate>
    ) {
        Log.d(TAG, "Starting upload observations")

        val localValuesByFieldBookId = observations.mapNotNull { obs ->
            obs.fieldBookDbId?.let { id -> id to (obs.value ?: "") }
        }.toMap()

        val traits = traitRepo.getTraits().associateBy { it.id }
        observations.forEach { obs ->
            traits[obs.internalVariableDbId]?.let { trait ->
                obs.value = valueProcessorFormatAdapter.processValue(obs.value, trait, forBrapi = true)
            }
        }

        val preUploadResyncResult = performResyncCheckBeforeUpload(
            observations,
            localValuesByFieldBookId
        )
        if (preUploadResyncResult.requiresUserChoice) {
            _uiState.update {
                it.copy(
                    viewMode = ViewMode.IDLE,
                    isUploadFinished = false,
                    uploadError = context.getString(R.string.brapi_resync_choice_required)
                )
            }
            return
        }

        val observationsToUpload = preUploadResyncResult.observationsToUpload
        if (observationsToUpload.isEmpty()) {
            Log.d(TAG, "Re-sync check found all observations on the server, skipping upload")
            return
        }

        processor(observationsToUpload, localValuesByFieldBookId)
            .onCompletion { cause ->

                _uiState.update {
                    it.copy(
                        viewMode = ViewMode.IDLE,
                        isUploadFinished = true,
                    )
                }

                Log.d(TAG, "Upload flow completed. Cause: ${cause?.message}")
            }
            .catch { cause ->

                Log.e(TAG, "Error caught during upload flow", cause)

                _uiState.update {
                    it.copy(
                        viewMode = ViewMode.IDLE,
                        uploadError = cause.message ?: context.getString(R.string.upload_failed)
                    )
                }
            }
            .collect { upload ->

                Log.d(TAG, "Upload progress update: $upload")

                when (upload) {
                    is UploadProgressUpdate.InUploadProgress -> {
                        _uiState.update {
                            it.copy(
                                progress = it.progress.copy(
                                    current = upload.items,
                                    total = upload.totalItems
                                )
                            )
                        }
                    }

                    is UploadProgressUpdate.InUploadFailedChunk -> {
                        _uiState.update {
                            it.copy(
                                uploadError = upload.errorCode.toString()
                            )
                        }
                    }

                    is UploadProgressUpdate.Completed -> {
                        _uiState.update {
                            it.copy(
                                uploadInserts = it.uploadInserts + upload.new,
                                uploadEdits = it.uploadEdits + upload.edited,
                                uploadFails = it.uploadFails + upload.incompatible
                            )
                        }
                    }
                }
            }

        Log.d(TAG, "Observation processing complete.")
    }

    /**
     * Re-sync check for issue #550. Before upload, verify if observations were already stored
     * by a prior request (for example, app crash after server write but before local sync update).
     */
    private suspend fun performResyncCheckBeforeUpload(
        observations: List<Observation>,
        localValuesByFieldBookId: Map<String, String>
    ): PreUploadResyncResult {

        if (observations.isEmpty()) return PreUploadResyncResult(observations, false)

        val studyDbId = uiState.value.study?.studyDbId
        if (studyDbId.isNullOrEmpty()) return PreUploadResyncResult(observations, false)

        val variableDbIds = observations.mapNotNull { it.variableDbId }.distinct()
        if (variableDbIds.isEmpty()) return PreUploadResyncResult(observations, false)

        return try {
            val pageSize = preferences.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50")?.toInt() ?: 50
            val paginationManager = BrapiPaginationManager(0, pageSize)

            val firstPage = brAPIService.awaitGetSingleObservationPage(
                studyDbId,
                variableDbIds,
                paginationManager
            )

            val allCandidateObservations = if ((paginationManager.totalPages ?: 1) <= 1) {
                firstPage
            } else {
                brAPIService.awaitGetObservations(
                    brapiStudyId = studyDbId,
                    variableDbIds = variableDbIds,
                    paginationManager = paginationManager,
                    initialPages = firstPage,
                    concurrencyLimit = preferences.getString(
                        PreferenceKeys.BRAPI_MAX_CONCURRENT_OBSERVATION_TRANSFER,
                        "5"
                    )?.toInt() ?: 5
                ) { _, _ ->
                    // Re-sync check runs silently without changing upload progress UI.
                }
            }

            val pendingUpload = mutableListOf<Observation>()
            val pendingUserChoices = mutableListOf<PendingResyncChoice>()
            val remainingCandidates = allCandidateObservations.toMutableList()
            var recoveredCount = 0
            val localDeviceId = getOrCreateBrapiDeviceId()

            observations.forEach { localObservation ->
                val matchIndex = remainingCandidates.indexOfFirst { serverObservation ->
                    matchesResyncCheck(localObservation, serverObservation, localDeviceId)
                }

                if (matchIndex >= 0) {
                    val matchedServerObservation = remainingCandidates.removeAt(matchIndex)
                    val choiceKey = buildResyncChoiceKey(localObservation, matchedServerObservation)

                    if (localObservation.dbId.isNullOrEmpty() && !resyncAdoptServerKeys.contains(choiceKey) && !resyncUploadAsNewKeys.contains(choiceKey)) {
                        val preservedValue = localObservation.fieldBookDbId
                            ?.let { localValuesByFieldBookId[it] }
                            ?: localObservation.value.orEmpty()
                        pendingUserChoices.add(
                            PendingResyncChoice(
                                key = choiceKey,
                                localObservation = localObservation,
                                serverObservation = matchedServerObservation,
                                preservedLocalValue = preservedValue
                            )
                        )
                        return@forEach
                    }

                    if (localObservation.dbId.isNullOrEmpty() && resyncUploadAsNewKeys.contains(choiceKey)) {
                        pendingUpload.add(localObservation)
                        return@forEach
                    }

                    localObservation.fieldBookDbId?.let { fieldBookId ->
                        localValuesByFieldBookId[fieldBookId]?.let { preservedValue ->
                            localObservation.value = preservedValue
                        }
                    }

                    localObservation.dbId = matchedServerObservation.dbId
                    val serverSyncTime = matchedServerObservation.timestamp
                    if (serverSyncTime != null) {
                        localObservation.setLastSyncedTime(serverSyncTime)
                    } else {
                        localObservation.setLastSyncedTime(timestamp.format(calendar.time))
                    }
                    dataHelper.updateObservationsByFieldBookId(listOf(localObservation))
                    resyncAdoptServerKeys.remove(choiceKey)
                    resyncUploadAsNewKeys.remove(choiceKey)
                    recoveredCount++
                } else {
                    pendingUpload.add(localObservation)
                }
            }

            if (pendingUserChoices.isNotEmpty()) {
                setPendingResyncChoices(pendingUserChoices)
                return PreUploadResyncResult(emptyList(), true)
            }

            clearPendingResyncChoices()

            if (recoveredCount > 0) {
                Log.i(
                    TAG,
                    "Pre-upload re-sync check recovered $recoveredCount/${observations.size} observations"
                )
            }

            PreUploadResyncResult(pendingUpload, false)
        } catch (e: Exception) {
            Log.w(TAG, "Pre-upload re-sync check failed; continuing with normal upload", e)
            PreUploadResyncResult(observations, false)
        }
    }

    private fun matchesResyncCheck(
        localObservation: Observation,
        serverObservation: Observation,
        localDeviceId: String
    ): Boolean {
        val localValue = localObservation.value?.trim().orEmpty()
        val serverValue = serverObservation.value?.trim().orEmpty()

        if (localValue != serverValue) return false

        val localDbId = localObservation.dbId
        if (!localDbId.isNullOrEmpty()) {
            return localDbId == serverObservation.dbId
        }

        val localFieldBookDbId = localObservation.fieldBookDbId
        val serverFieldBookDbId = serverObservation.fieldBookDbId
        val serverDeviceId = serverObservation.originDeviceId

        return !localFieldBookDbId.isNullOrEmpty()
                && localFieldBookDbId == serverFieldBookDbId
                && !serverDeviceId.isNullOrEmpty()
                && serverDeviceId == localDeviceId
    }

    private fun buildResyncChoiceKey(localObservation: Observation, serverObservation: Observation): String {
        val localKey = localObservation.fieldBookDbId ?: ""
        val serverKey = serverObservation.dbId ?: ""
        return "$localKey|$serverKey|${localObservation.variableDbId ?: ""}|${localObservation.unitDbId ?: ""}"
    }

    private fun getOrCreateBrapiDeviceId(): String {
        val existing = preferences.getString(PreferenceKeys.BRAPI_DEVICE_ID, null)
        if (!existing.isNullOrEmpty()) return existing

        val generated = UUID.randomUUID().toString()
        preferences.edit { putString(PreferenceKeys.BRAPI_DEVICE_ID, generated) }
        return generated
    }

    private suspend fun processImages(images: List<FieldBookImage>, isNew: Boolean = true) = coroutineScope {

        if (images.isEmpty()) {
            Log.d(TAG, "No images to process.")
            return@coroutineScope
        }

        val concurrencyLimit = (preferences.getString(
            PreferenceKeys.BRAPI_MAX_CONCURRENT_IMAGE_CONTENT, "3"
        ) ?: "3").toInt()

        Log.d(TAG, "Processing ${images.size} images with a concurrency of $concurrencyLimit.")

        val semaphore = Semaphore(concurrencyLimit)

        images.map { image ->
            async(Dispatchers.IO) {
                semaphore.withPermit {

                    if (!isActive) return@withPermit
                    val message = if (isNew) processNewImage(image)else updateImages(image)

                    _uiState.update {
                        it.copy(
                            progress = it.progress.copy(
                                current = it.progress.current + 1,
                                message = message
                            )
                        )
                    }
                }
            }
        }.awaitAll()

        Log.d(TAG, "All image processing jobs have completed.")
    }

    /**
     * For each image:
     * 1. upload metadata
     * 2. load image into memory
     * 3. upload image content
     * 4. clear memory
     */
    private suspend fun processNewImage(image: FieldBookImage): String {

        var completedMessage = context.getString(R.string.completed_brapi_upload, image.fileName)

        try {

            _uiState.update {
                it.copy(
                    progress = it.progress.copy(
                        message = context.getString(
                            R.string.uploading_metadata,
                            image.fileName
                        )
                    )
                )
            }

            val imageWithDbId = brAPIService.awaitPostImageMetaData(image)

            _uiState.update {
                it.copy(
                    progress = it.progress.copy(
                        message = context.getString(
                            R.string.loading_brapi_images,
                            image.fileName
                        )
                    )
                )
            }

            imageWithDbId.loadImage(context)

            _uiState.update {
                it.copy(
                    progress = it.progress.copy(
                        message = context.getString(
                            R.string.uploading_content,
                            image.fileName
                        )
                    )
                )
            }

            val finalImage = brAPIService.awaitPutImageContent(imageWithDbId)

            val retVal = processImageResponse(finalImage, finalImage.fieldBookDbId)

            if (retVal != UploadError.NONE) {

                Log.e(TAG, "Error processing image: ${image.fileName}")

                throw Exception()
            }

            _uiState.update {
                it.copy(
                    uploadImageInserts = it.uploadImageInserts + 1
                )
            }

        } catch (e: Exception) {

            Log.e(TAG, "Failed to process image pair for ${image.fileName}", e)

            _uiState.update {
                it.copy(
                    uploadImageFails = it.uploadImageFails + 1
                )
            }

            completedMessage = context.getString(R.string.failed_brapi_image_upload, image.fileName)

        } finally {

            image.setBytes(null)

        }

        return completedMessage
    }

    /**
     * For editing or finishing completed images
     * 1. load image into memory
     * 2. put image (update metadata)
     * 3. put image content
     * 4. clear memory
     */
    private suspend fun updateImages(image: FieldBookImage): String {

        var completedMessage = context.getString(R.string.completed_brapi_upload, image.fileName)

        try {

            _uiState.update {
                it.copy(
                    progress = it.progress.copy(
                        message = context.getString(R.string.loading_brapi_images, image.fileName),
                    )
                )
            }

            image.loadImage(context)

            _uiState.update {
                it.copy(
                    progress = it.progress.copy(
                        message = context.getString(
                            R.string.updating_metadata_brapi_image,
                            image.fileName
                        ),
                    )
                )
            }

            val responseImage = brAPIService.awaitPutImage(image)

            _uiState.update {
                it.copy(
                    progress = it.progress.copy(
                        message = context.getString(
                            R.string.updating_content_brapi_image,
                            image.fileName
                        ),
                    )
                )
            }

            brAPIService.awaitPutImageContent(responseImage)

            _uiState.update {
                it.copy(
                    uploadImageEdits = it.uploadImageEdits + 1
                )
            }

        } catch (e: Exception) {

            Log.e(TAG, "Failed to update image ${image.fileName}", e)

            completedMessage = context.getString(R.string.failed_brapi_image_upload, image.fileName)

            _uiState.update {
                it.copy(
                    uploadImageFails = it.uploadImageFails + 1
                )
            }

        } finally {

            image.setBytes(null)

        }

        return completedMessage
    }

    private fun getObservations(): Flow<DownloadProgressUpdate> = channelFlow {

        val pageCount = AtomicInteger(1)

        val study = _uiState.value.study ?: throw IllegalStateException(context.getString(R.string.study_not_initialized))
        val brapiStudyId =
            study.studyDbId ?: throw IllegalStateException(context.getString(R.string.brapi_study_db_id_is_missing))

        val variables = traitRepo.getTraits().filter {
            it.traitDataSource.isNotEmpty() && it.traitDataSource == study.dataSource
        }

        val variableDbIds = variables.mapNotNull { it.externalDbId }

        val pageSize = preferences.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50")?.toInt() ?: 50
        val paginationManager = BrapiPaginationManager(0, pageSize)

        Log.d(
            TAG,
            "Starting to pull observations from brapi server with pagesize: $pageSize and ${variableDbIds.size} variables for $brapiStudyId"
        )

        //gets the first page of data and updates the pagination manager with total page size
        val firstPageResult = brAPIService.awaitGetSingleObservationPage(
            brapiStudyId,
            variableDbIds,
            paginationManager
        )

        Log.d(
            TAG,
            "First page returned with ${firstPageResult.size} observations, total pages: ${paginationManager.totalPages}"
        )

        val totalPages = paginationManager.totalPages ?: 0

        //if there's only one page, we're done.
        if (totalPages <= 1) {

            send(DownloadProgressUpdate.Completed(firstPageResult))

        } else {

            trySend(
                DownloadProgressUpdate.InDownloadProgress(
                    pageCount.get(),
                    totalPages
                )
            )

            val concurrencyLimit = (preferences.getString(
                PreferenceKeys.BRAPI_MAX_CONCURRENT_OBSERVATION_TRANSFER, "5"))?.toInt() ?: 5

            val allObservations = brAPIService.awaitGetObservations(
                brapiStudyId = brapiStudyId,
                variableDbIds = variableDbIds,
                paginationManager = paginationManager,
                initialPages = firstPageResult,
                concurrencyLimit = concurrencyLimit
            ) { _, observations ->

                trySend(
                    DownloadProgressUpdate.InDownloadProgress(
                        pageCount.incrementAndGet(),
                        totalPages
                    )
                )

                Log.d(TAG, "Downloaded: ${observations.size} observations")
            }

            Log.d(TAG, "All observations returned with ${allObservations.size} observations")

            send(DownloadProgressUpdate.Completed(allObservations))
        }

    }.flowOn(Dispatchers.IO)

    private fun normalizeDownloadedObservationValue(
        observation: Observation,
        traitsById: Map<String, TraitObject>
    ): String {
        val rawValue = observation.value ?: return ""

        if (rawValue.isBlank() || rawValue == "NA") return rawValue

        val traitId = observation.internalVariableDbId
            ?.takeIf { it.isNotBlank() }
            ?: observation.variableDbId?.takeIf { it.isNotBlank() }
            ?: return rawValue
        val trait = traitsById[traitId] ?: return rawValue

        return when {
            trait.format in CategoricalTraitLayout.POSSIBLE_VALUES ->
                CategoryJsonUtil.encodeDownloadedBrapiCategoricalValue(rawValue, trait.categories)

            trait.format == Formats.DATE.getDatabaseName() -> {
                // If the value is already internal DateJson, keep it as-is.
                if (JsonUtil.isJsonValid(rawValue)) return rawValue
                // Otherwise it's a plain BrAPI ISO-8601 date "YYYY-MM-DD". Convert to DateJson.
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val parsed = sdf.parse(rawValue) ?: return rawValue
                    val cal = Calendar.getInstance()
                    cal.time = parsed
                    DateJsonUtil.encode(
                        DateJsonCoder.DateJson(
                            formattedDate = rawValue,
                            dayOfYear = cal.get(Calendar.DAY_OF_YEAR).toString()
                        )
                    )
                } catch (_: Exception) {
                    rawValue
                }
            }

            else -> rawValue
        }
    }

    private fun processImageResponse(
        converted: FieldBookImage,
        fieldBookId: String?,
    ): UploadError {

        var retVal = UploadError.NONE

        val syncTime = timestamp.format(calendar.getTime())

        converted.setLastSyncedTime(syncTime)
        converted.fieldbookDbId = fieldBookId

        if (converted.dbId != null && converted.fieldBookDbId != null) {

            dataHelper.updateImage(converted)

        } else {

            retVal = UploadError.MISSING_OBSERVATION_IN_RESPONSE

        }

        return retVal
    }
}
