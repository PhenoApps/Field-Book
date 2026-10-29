package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiSyncEngine.ImageUploadEvent
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiSyncEngine.PendingResyncChoice
import com.fieldbook.tracker.brapi.model.FieldBookImage
import com.fieldbook.tracker.brapi.model.Observation
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.traits.CategoricalTraitLayout
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.coders.DateJsonCoder
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.DateJsonUtil
import com.fieldbook.tracker.utilities.JsonUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Drives the interactive sync screen. The transfer work itself lives in [BrapiSyncEngine], shared
 * with the automatic syncs, this class turns its results into [BrapiExportUiState] and holds the
 * conflict and re-sync choices the user has to make.
 */
@HiltViewModel
class BrapiSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataHelper: DataHelper,
    private val preferences: SharedPreferences,
    private val traitRepo: TraitRepository,
    private val engine: BrapiSyncEngine
) : ViewModel() {

    private var brAPIService = engine.newService()

    private val _uiState = MutableStateFlow(BrapiExportUiState())
    val uiState = _uiState.asStateFlow()

    private var localStatus = BrapiSyncEngine.LocalStatus()

    // Conflicts detected during last download that need user resolution
    private var pendingConflicts: List<Pair<Observation, Observation?>> = emptyList()

    // Pending pre-upload re-sync choices where local observations have no BrAPI id.
    private var pendingResyncChoices: List<PendingResyncChoice> = emptyList()
    private val resyncUploadAsNewKeys = mutableSetOf<String>()
    private val resyncAdoptServerKeys = mutableSetOf<String>()

    // Re-sync choices raised mid-upload resume that upload once answered, ones found when the
    // screen opened leave the upload to the user.
    private var resyncFromUpload = false

    // Conflicts the user resolved by keeping the local value. The server copy is still newer than
    // the last sync until the edit is uploaded, so without this they would be raised again.
    private val keepLocalDbIds = mutableSetOf<String>()

    private var activeJob: Job? = null

    companion object {
        private const val TAG = "BrapiExportViewModel"
    }

    fun setServerDisplayName(name: String) {
        _uiState.update {
            it.copy(
                brapiServerDisplayName = name
            )
        }
    }

    fun resetClient() {
        brAPIService = engine.newService()
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

    /**
     * Records that the study exchanged data with the server, this is what the field detail
     * screen reads for its last sync date.
     */
    private fun markStudySynced() {

        val studyId = uiState.value.study?.studyId ?: return

        engine.markStudySynced(studyId)
    }

    fun refreshLocalStatus() {

        val studyId = uiState.value.study?.studyId ?: return

        localStatus = engine.loadLocalStatus(studyId)

        _uiState.update {
            it.copy(
                newObservationCount = localStatus.newObservations.size,
                newImageCount = localStatus.newImageObservations.size,
                editedObservationCount = localStatus.editedObservations.size,
                editedImageCount = localStatus.editedImageObservations.size,
                incompleteImageCount = localStatus.incompleteImageObservations.size,
                syncedObservationCount = localStatus.syncedObservations.size,
                syncedImageCount = localStatus.syncedImageObservations.size,
                unsyncableObservationCount = localStatus.unsyncableObservationCount,
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
            keepLocalDbIds.clear()

            loadUploadSupport()
            checkForReview()
        }
    }

    /**
     * Asks the server which uploads it accepts, so the screen can grey out the rest. Runs beside
     * the review check rather than as the active job, it never blocks the buttons.
     */
    private fun loadUploadSupport() {
        viewModelScope.launch(Dispatchers.IO) {
            val support = engine.uploadSupport(brAPIService)
            _uiState.update { it.copy(uploadSupport = support) }
        }
    }

    /**
     * Looks for observations that need the user before anything is transferred: changes on both
     * this device and the server, and new observations that already appear on the server. They
     * are listed on the screen, nothing is changed.
     */
    private fun checkForReview() {

        if (activeJob?.isActive == true) return

        val study = _uiState.value.study
        if (study == null || study.studyDbId.isNullOrEmpty()) return

        activeJob = viewModelScope.launch(Dispatchers.IO) {

            val lock = engine.lockFor(study.studyId)

            // Held means an automatic sync of this field is running, say so rather than appear
            // to be checking the server for however long that sync takes.
            _uiState.update {
                it.copy(viewMode = ViewMode.CHECKING, waitingForAutoSync = lock.isLocked)
            }

            try {

                val review = lock.withLock {

                    _uiState.update { it.copy(waitingForAutoSync = false) }

                    // The counts shown were read when the screen opened, an automatic sync that
                    // finished since may have uploaded or downloaded some of them.
                    refreshLocalStatus()

                    // Only shown when the server is really asked, not for a reused result.
                    engine.checkForReview(brAPIService, study) {
                        _uiState.update { it.copy(checkingServer = true) }
                    }
                }

                setPendingConflicts(review.conflicts)

                resyncFromUpload = false
                if (review.resyncChoices.isNotEmpty()) {
                    setPendingResyncChoices(review.resyncChoices)
                } else {
                    clearPendingResyncChoices()
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Upload repeats the conflict check, so a failure here only delays the listing.
                Log.w(TAG, "Review check failed", e)
            } finally {
                _uiState.update {
                    it.copy(viewMode = ViewMode.IDLE, waitingForAutoSync = false, checkingServer = false)
                }
            }
        }
    }

    private fun requestReview() {
        _uiState.update { it.copy(reviewRequest = it.reviewRequest + 1) }
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

        // Only what the server accepts counts, the rest is skipped and explained on the screen.
        val support = _uiState.value.uploadSupport
        val uploadImages = _uiState.value.uploadImages && support.images

        val hasNew = support.newObservations && localStatus.newObservations.isNotEmpty()
        val hasEdited = support.editedObservations && localStatus.editedObservations.isNotEmpty()
        val hasImages = uploadImages && (localStatus.newImageObservations.isNotEmpty()
                || localStatus.editedImageObservations.isNotEmpty()
                || localStatus.incompleteImageObservations.isNotEmpty())

        if (!hasNew && !hasEdited && !hasImages) {
            _uiState.update {
                it.copy(
                    isUploadFinished = false,
                    uploadError = context.getString(R.string.nothing_to_upload)
                )
            }
            return
        }

        if (uiState.value.pendingConflictsCount > 0 || uiState.value.pendingResyncChoicesCount > 0) {
            requestReview()
            return
        }

        activeJob = viewModelScope.launch(Dispatchers.IO) {

            try {

                // An automatic sync of this study may be running, wait for it and then re-read
                // what is left to upload, it may already have sent some of these observations.
                engine.lockFor(study.studyId).withLock {

                    refreshLocalStatus()

                    // when these observations were read, recorded as their sync time on upload
                    val syncedAt = localStatus.loadedAt

                    // Edits are only sent once the server copy is known not to have changed too,
                    // otherwise an upload would silently overwrite an edit made elsewhere.
                    if (support.editedObservations && localStatus.editedObservations.isNotEmpty()) {

                        _uiState.update {
                            it.copy(
                                viewMode = ViewMode.EXPORTING,
                                isUploadFinished = false,
                                progress = Progress(
                                    message = context.getString(R.string.brapi_review_checking, it.brapiServerDisplayName)
                                )
                            )
                        }

                        val conflicts = try {
                            val traitsById = traitRepo.getTraits().associateBy { it.id }
                            engine.resolveObservationStatus(
                                engine.downloadServerObservations(brAPIService, study),
                                localStatus,
                                traitsById
                            ).conflicts
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Pre-upload conflict check failed", e)
                            _uiState.update {
                                it.copy(
                                    viewMode = ViewMode.IDLE,
                                    // the code itself, so a 401 shows the authentication message
                                    uploadError = (e as? BrapiException)?.code?.toString()
                                        ?: e.message
                                        ?: context.getString(R.string.upload_failed)
                                )
                            }
                            return@launch
                        }

                        setPendingConflicts(conflicts)

                        if (uiState.value.pendingConflictsCount > 0) {
                            _uiState.update { it.copy(viewMode = ViewMode.IDLE) }
                            requestReview()
                            return@launch
                        }
                    }

                    val newObservations = localStatus.newObservations
                    val editedObservations = localStatus.editedObservations
                    val newImageObservations = localStatus.newImageObservations
                    val editedImageObservations = localStatus.editedImageObservations
                    val incompleteImageObservations = localStatus.incompleteImageObservations

                    if (support.newObservations && newObservations.isNotEmpty()) {

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

                        processObservations(newObservations) { observations, localValues ->
                            engine.processNewObservations(brAPIService, observations, localValues, syncedAt)
                        }
                        if (uiState.value.pendingResyncChoicesCount > 0) return@launch
                    }

                    if (support.editedObservations && editedObservations.isNotEmpty()) {

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

                        processObservations(editedObservations) { observations, localValues ->
                            engine.processEditedObservations(brAPIService, observations, localValues, syncedAt)
                        }
                        if (uiState.value.pendingResyncChoicesCount > 0) return@launch
                    }

                    if (newImageObservations.isNotEmpty() && uploadImages) {

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

                        processImages(dataHelper.getImageDetails(context, newImageObservations), syncedAt = syncedAt)

                    }

                    val totalImageEdits =
                        editedImageObservations.size + incompleteImageObservations.size

                    if (totalImageEdits > 0 && uploadImages) {

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
                            isNew = false,
                            syncedAt = syncedAt
                        )
                    }
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

                // Wait out any automatic sync of this study, then compare against fresh local data.
                engine.lockFor(study.studyId).withLock {

                    refreshLocalStatus()

                    engine.getObservations(brAPIService, study)
                        .onCompletion { cause ->

                            Log.d(TAG, "Download flow completed with cause: $cause")

                            // record last checked time for download
                            val checkedDownloadText = engine.syncTimestamp()

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
                                    downloadError = cause.message
                                        ?: context.getString(R.string.download_failed)
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

                                    val resolved = withContext(Dispatchers.IO) {

                                        val traitsById = traitRepo.getTraits().associateBy { it.id }
                                        val resolved = engine.resolveObservationStatus(
                                            update.data,
                                            localStatus,
                                            traitsById
                                        )

                                        engine.saveDownloadedObservations(study, resolved, traitsById)

                                        resolved
                                    }

                                    val inserts = resolved.inserts
                                    val updates = resolved.updates
                                    val conflicts = resolved.conflicts

                                    markStudySynced()

                                    setPendingConflicts(conflicts)

                                    if (uiState.value.pendingConflictsCount > 0) {
                                        // notify UI to prompt user
                                        requestReview()
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
    private suspend fun setPendingConflicts(found: List<BrapiSyncEngine.Conflict>) {

        val conflicts = found.filter { it.localObservation.dbId !in keepLocalDbIds }

        pendingConflicts = conflicts.map { Pair(it.serverObservation, it.localObservation) }

        val traitsById = traitRepo.getTraits().associateBy { it.id }

        val uiConflicts = conflicts.map { c ->
            val trait = traitsById[c.localObservation.internalVariableDbId]
                ?: traitsById[c.serverObservation.internalVariableDbId]
            PendingConflictUi(
                brapiId = c.serverObservation.dbId ?: c.localObservation.dbId ?: "",
                localValue = decodeObservationValueForDisplay(
                    c.localObservation.value ?: "",
                    trait
                ),
                serverValue = decodeObservationValueForDisplay(
                    c.serverObservation.value ?: "",
                    trait
                ),
                localDbId = c.localObservation.dbId,
                serverDbId = c.serverObservation.dbId
            )
        }
        _uiState.update {
            it.copy(
                pendingConflictsCount = uiConflicts.size,
                pendingConflicts = uiConflicts
            )
        }
    }

    /**
     * Apply a user-selected merge strategy to pending conflicts and persist results.
     * Supports Manual strategy where a map of brapiId->choice is provided (true=server, false=local).
     */
    fun applyConflictResolution(
        strategy: MergeStrategy,
        manualChoices: Map<String, Boolean>? = null
    ) {
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
                    if (local != null && local.timestamp > server.timestamp) toKeepLocal.add(local) else toUpdateFromServer.add(
                        server
                    )
                }
            }

            is MergeStrategy.Manual -> {
                // manualChoices: map from brapiId to true (choose server) or false (choose local)
                if (manualChoices == null) return
                Log.d(
                    TAG,
                    "Applying manual conflict resolution with choices: ${manualChoices.keys.size} entries"
                )
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

        keepLocalDbIds.addAll(toKeepLocal.mapNotNull { it.dbId })

        // clear and update ui state
        pendingConflicts = emptyList()

        _uiState.update { it.copy(pendingConflictsCount = 0, pendingConflicts = emptyList()) }

        viewModelScope.launch(Dispatchers.IO) {
            if (toUpdateFromServer.isNotEmpty()) {
                val traitsById = traitRepo.getTraits().associateBy { it.id }
                engine.applyServerValues(toUpdateFromServer, traitsById)
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
                localValue = decodeObservationValueForDisplay(
                    choice.localObservation.value ?: "",
                    trait
                ),
                serverValue = decodeObservationValueForDisplay(
                    choice.serverObservation.value ?: "",
                    trait
                ),
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

        val syncTime = engine.syncTimestamp()
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
                    localObservation.lastSyncedTime = serverSyncTime
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
                dataHelper.markObservationsSynced(updatesToPersist)
                refreshLocalStatus()
            }
        }

        clearPendingResyncChoices()

        _uiState.update {
            it.copy(
                uploadError = ""
            )
        }

        if (!applyAsUpdate && resyncFromUpload) {
            startUpload()
        }
    }

    private suspend fun processObservations(
        observations: List<Observation>,
        processor: suspend (List<Observation>, Map<String, String>) -> Flow<UploadProgressUpdate>
    ) {
        Log.d(TAG, "Starting upload observations")

        val localValuesByFieldBookId = engine.prepareForUpload(observations)

        val preUploadResyncResult = engine.performResyncCheckBeforeUpload(
            brAPIService,
            uiState.value.study?.studyDbId,
            observations,
            localValuesByFieldBookId,
            adoptServerKeys = resyncAdoptServerKeys,
            uploadAsNewKeys = resyncUploadAsNewKeys
        )
        if (preUploadResyncResult.pendingChoices.isNotEmpty()) {
            setPendingResyncChoices(preUploadResyncResult.pendingChoices)
            resyncFromUpload = true
            _uiState.update {
                it.copy(
                    viewMode = ViewMode.IDLE,
                    isUploadFinished = false
                )
            }
            requestReview()
            return
        }

        clearPendingResyncChoices()

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

                //cause is non null when the flow failed or was cancelled, nothing reached the server
                if (cause == null) markStudySynced()

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

    private suspend fun processImages(images: List<FieldBookImage>, isNew: Boolean = true, syncedAt: String) {

        engine.processImages(brAPIService, images, isNew, syncedAt) { event ->

            _uiState.update {
                when (event) {
                    is ImageUploadEvent.Message -> it.copy(
                        progress = it.progress.copy(message = event.text)
                    )

                    is ImageUploadEvent.Inserted -> it.copy(
                        uploadImageInserts = it.uploadImageInserts + 1
                    )

                    is ImageUploadEvent.Edited -> it.copy(
                        uploadImageEdits = it.uploadImageEdits + 1
                    )

                    is ImageUploadEvent.Failed -> it.copy(
                        uploadImageFails = it.uploadImageFails + 1
                    )

                    is ImageUploadEvent.Finished -> it.copy(
                        progress = it.progress.copy(
                            current = it.progress.current + 1,
                            message = event.message
                        )
                    )
                }
            }
        }
    }
}
