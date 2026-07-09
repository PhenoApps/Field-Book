package com.fieldbook.tracker.database.viewmodels

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.database.viewmodels.TraitEditorViewModel.Companion.SORT_FIELD_DEFAULT
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


/**
 * All dialogs, and trait lists are saved as state using StateFlow
 * All events (navigation, toasts, perms) are emitted as one-time events using SharedFlow
 *
 * The reordering library fires onMove as soon as an item starts dragging.
 * To avoid unnecessary intermediate db updates, we need to manage the db updates behavior
 * Commit only if
 * - [wasPreviouslyDragging] and currently not dragging (dragging ended) AND
 * - [lastCommittedSortedList] and the updated trait list is not the same
 */
@HiltViewModel
class TraitEditorViewModel @Inject constructor(
    private val repo: TraitRepository,
    private val prefs: SharedPreferences,
) : ViewModel() {

    companion object {
        private const val TAG = "TraitEditorViewModel"
        private const val FIELD_TRAIT_SORT_ORDER_PREFIX = "field_trait_sort_order_"

        /** Sort key meaning "follow the main editor's global sort preference" (viewer-only). */
        const val SORT_FIELD_DEFAULT = "field_default"
    }

    private val _uiState = MutableStateFlow(TraitEditorUiState())
    val uiState: StateFlow<TraitEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TraitEditorEvent>()
    val events = _events.asSharedFlow()

    // to manage db updates on reorder
    private var lastCommittedSortedTraitIds: List<String> = emptyList()
    private var wasPreviouslyDragging = false
    private var scopedStudyId: Int? = null
    private var usePerFieldTraitList: Boolean = false
    private var isViewerMode: Boolean = false
    private var isScopeConfigured: Boolean = false

    init {
        val sortOrder =
            prefs.getString(GeneralKeys.TRAITS_LIST_SORT_ORDER, "position") ?: "position"
        _uiState.update { it.copy(sortOrder = sortOrder, isLoading = true) }
    }

    private fun notifyCollectReload() {
        CollectActivity.reloadData = true
    }

    // PREFERENCES

    fun isTutorialEnabled() = prefs.getBoolean(PreferenceKeys.TIPS, false)

    fun isBrapiEnabled() = prefs.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)

    fun isBrapiNewUi() = prefs.getBoolean(PreferenceKeys.EXPERIMENTAL_NEW_BRAPI_UI, true)

    fun getBrapiDisplayName(default: String) =
        prefs.getString(PreferenceKeys.BRAPI_DISPLAY_NAME, default) ?: default

    fun previouslyExported() = prefs.getBoolean(GeneralKeys.TRAITS_EXPORTED, false)

    /**
     * Resolves the effective sort-order to pass to the repository.
     *
     * In viewer mode the preference lives under a field-specific key; otherwise the global key.
     * The special key [SORT_FIELD_DEFAULT] is passed through to the repo, which will
     * apply the global sort preference to the scoped traits.
     * "position" in viewer mode means "use the field-specific DB order" (custom reorder).
     */
    private fun resolveSortOrder(): String {
        if (isViewerMode && scopedStudyId != null) {
            return prefs.getString(
                "$FIELD_TRAIT_SORT_ORDER_PREFIX${scopedStudyId}",
                SORT_FIELD_DEFAULT
            ) ?: SORT_FIELD_DEFAULT
        }
        return prefs.getString(GeneralKeys.TRAITS_LIST_SORT_ORDER, "position") ?: "position"
    }

    fun updateSortOrder(sortOrder: String) {
        if (!isViewerMode) {
            prefs.edit { putString(GeneralKeys.TRAITS_LIST_SORT_ORDER, sortOrder) }
        } else {
            scopedStudyId?.let { studyId ->
                prefs.edit { putString("$FIELD_TRAIT_SORT_ORDER_PREFIX$studyId", sortOrder) }
            }
        }
        _uiState.update { it.copy(sortOrder = sortOrder) }

        viewModelScope.launch {
            loadTraitsInternal(showLoading = false)
            _events.emit(TraitEditorEvent.ScrollToTop)
        }
    }

    // IN-MEMORY UPDATES (does not fetch from db)

    fun addTraitObject(newTrait: TraitObject) {
        _uiState.update { state ->
            state.copy(
                traits = state.traits + newTrait,
                selectedTraitIds = state.selectedTraitIds - newTrait.id
            )
        }
    }

    fun removeTraitObject(id: String) {
        val newList = _uiState.value.traits.filterNot { it.id == id }
        _uiState.update { state ->
            state.copy(
                traits = newList,
                selectedTraitIds = state.selectedTraitIds - id
            )
        }
        lastCommittedSortedTraitIds = newList.map { it.id }

        // Perform background reorder if in global mode to ensure positions are gapless
        if (!isViewerMode) {
            viewModelScope.launch {
                runCatching {
                    repo.updateTraitOrder(newList)
                }.onFailure { e ->
                    Log.e(TAG, "Soft reorder failed after trait deletion", e)
                }
            }
        }
    }

    fun updateTraitInList(updatedTrait: TraitObject) {
        _uiState.update { state ->
            state.copy(
                traits = state.traits.map {
                    if (it.id == updatedTrait.id) updatedTrait else it
                }
            )
        }
    }

    fun toggleTraitSelection(id: String) {
        _uiState.update { state ->
            val newSelection = if (id in state.selectedTraitIds) {
                state.selectedTraitIds - id
            } else {
                state.selectedTraitIds + id
            }
            state.copy(selectedTraitIds = newSelection)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedTraitIds = emptySet()) }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedTraitIds = state.traits.map { it.id }.toSet())
        }
    }


    // DB RELATED

    private suspend fun loadTraitsInternal(showLoading: Boolean = true) {
        if (!isScopeConfigured) return

        if (showLoading) {
            _uiState.update { it.copy(isLoading = true) }
        }

        runCatching {
            repo.getTraits(
                studyId = scopedStudyId,
                usePerFieldList = usePerFieldTraitList,
                sortOrder = resolveSortOrder()
            )
        }
            .onSuccess { traits ->
                _uiState.update {
                    it.copy(traits = traits, isLoading = false)
                }
                lastCommittedSortedTraitIds = traits.map { it.id }
            }
            .onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false)
                }
                Log.e(TAG, "Error loading traits", e)
                _events.emit(TraitEditorEvent.ShowToast(R.string.error_loading_traits))
            }
    }

    fun loadTraits(showLoading: Boolean = true) {
        viewModelScope.launch {
            loadTraitsInternal(showLoading)
        }
    }

    fun configureTraitScope(studyId: Int?, enablePerFieldScope: Boolean, isViewer: Boolean) {
        val normalizedStudyId = studyId?.takeIf { it >= 0 }
        if (scopedStudyId == normalizedStudyId && usePerFieldTraitList == enablePerFieldScope && isViewerMode == isViewer && isScopeConfigured) {
            return
        }

        isViewerMode = isViewer
        scopedStudyId = normalizedStudyId
        usePerFieldTraitList = enablePerFieldScope
        isScopeConfigured = true

        // Field-specific list defaults to the same sort mode as the main list.
        val sortOrder = if (isViewerMode && scopedStudyId != null) {
            prefs.getString(
                "$FIELD_TRAIT_SORT_ORDER_PREFIX${scopedStudyId}",
                SORT_FIELD_DEFAULT
            ) ?: SORT_FIELD_DEFAULT
        } else {
            prefs.getString(GeneralKeys.TRAITS_LIST_SORT_ORDER, "position") ?: "position"
        }
        _uiState.update { it.copy(sortOrder = sortOrder) }

        viewModelScope.launch {
            loadTraitsInternal(showLoading = false)
            _events.emit(TraitEditorEvent.ScrollToTop)
        }
    }

    fun loadAvailableTraitsForCurrentStudy() {
        scopedStudyId ?: return
        if (!usePerFieldTraitList) return

        viewModelScope.launch {
            runCatching {
                val allTraits = repo.getUnscopedTraits()
                val currentIds = uiState.value.traits.map { it.id }.toSet()
                allTraits.filterNot { it.id in currentIds }
            }.onSuccess { available ->
                _uiState.update { it.copy(availableTraits = available) }
            }.onFailure { e ->
                Log.e(TAG, "Error loading available traits", e)
                _events.emit(TraitEditorEvent.ShowToast(R.string.error_loading_traits))
            }
        }
    }

    fun addTraitsToCurrentStudy(traitIds: Set<String>) {
        val currentStudyId = scopedStudyId ?: return
        if (!usePerFieldTraitList || traitIds.isEmpty()) return

        viewModelScope.launch {
            runCatching {
                repo.addTraitsToStudy(currentStudyId, traitIds)
            }.onSuccess {
                loadTraits()
                loadAvailableTraitsForCurrentStudy()
                _events.emit(
                    TraitEditorEvent.ShowMessageWithArgs(
                        R.string.traits_viewer_added_count,
                        listOf(traitIds.size)
                    )
                )
            }.onFailure { e ->
                Log.e(TAG, "Error assigning traits to study", e)
                _events.emit(TraitEditorEvent.ShowToast(R.string.error_importing_traits))
            }
        }
    }

    fun requestRemoveTrait(trait: TraitObject) {
        val traitName = trait.alias.ifBlank { trait.name }
        showDialog(TraitActivityDialog.RemoveFromField(setOf(trait.id), traitName))
    }

    fun requestRemoveSelectedTraits() {
        val selectedIds = _uiState.value.selectedTraitIds
        if (selectedIds.isEmpty()) return
        
        val message = if (selectedIds.size == 1) {
            val trait = _uiState.value.traits.find { it.id == selectedIds.first() }
            trait?.alias?.ifBlank { trait.name } ?: ""
        } else {
            selectedIds.size.toString()
        }
        
        showDialog(TraitActivityDialog.RemoveFromField(selectedIds, message))
    }

    fun removeTraits(traitIds: Set<String>) {
        if (traitIds.isEmpty()) return

        hideDialog()

        // Optimistic local removal
        val newList = _uiState.value.traits.filterNot { it.id in traitIds }
        _uiState.update { state ->
            state.copy(
                traits = newList,
                selectedTraitIds = state.selectedTraitIds - traitIds
            )
        }
        lastCommittedSortedTraitIds = newList.map { it.id }

        viewModelScope.launch {
            runCatching {
                if (isViewerMode && usePerFieldTraitList) {
                    val currentStudyId = scopedStudyId ?: return@runCatching
                    repo.removeTraitsFromStudy(currentStudyId, traitIds)
                } else {
                    traitIds.forEach { repo.deleteTrait(it) }
                    repo.updateTraitOrder(newList)
                }
            }.onSuccess {
                if (isViewerMode) {
                    loadAvailableTraitsForCurrentStudy()
                    _events.emit(TraitEditorEvent.ShowToast(R.string.traits_viewer_removed_trait))
                }
                notifyCollectReload()
            }.onFailure { e ->
                // Rollback (simplified, just reload quietly)
                loadTraits(showLoading = false)
                Log.e(TAG, "Error removing traits", e)
                val errorMsg = if (isViewerMode) R.string.error_updating_trait_visibility else R.string.error_deleting_traits
                _events.emit(TraitEditorEvent.ShowToast(errorMsg))
            }
        }
    }

    private fun rollbackRemovedTrait(trait: TraitObject, index: Int) {
        _uiState.update { state ->
            val updated = state.traits.toMutableList()
            val safeIndex = index.coerceIn(0, updated.size)
            updated.add(safeIndex, trait)
            state.copy(traits = updated)
        }
    }

    fun syncCurrentStudyWithMainList() {
        val currentStudyId = scopedStudyId ?: return
        if (!usePerFieldTraitList) return

        viewModelScope.launch {
            runCatching {
                repo.syncStudyWithMainList(currentStudyId)
            }.onSuccess {
                loadTraits()
                loadAvailableTraitsForCurrentStudy()
                _events.emit(TraitEditorEvent.ShowToast(R.string.traits_viewer_synced_with_main_list))
            }.onFailure { e ->
                Log.e(TAG, "Error syncing field trait list with main list", e)
                _events.emit(TraitEditorEvent.ShowToast(R.string.error_loading_traits))
            }
        }
    }

    fun deleteAllTraits() {
        val traits = uiState.value.traits

        viewModelScope.launch {
            runCatching { repo.deleteAllTraits(traits) }
                .onSuccess {
                    _uiState.update { it.copy(traits = emptyList()) }
                    notifyCollectReload()
                    _events.emit(TraitEditorEvent.ShowToast(R.string.message_all_traits_deleted))
                }
                .onFailure { e ->
                    Log.e(TAG, "Error deleting traits", e)
                    _events.emit(TraitEditorEvent.ShowToast(R.string.error_deleting_traits))
                }
        }
    }

    fun updateTraitVisibility(traitId: String, isVisible: Boolean) {
        val trait = uiState.value.traits.find { it.id == traitId } ?: return
        val scopedStudy = scopedStudyId
        val useScopedVisibility = usePerFieldTraitList && scopedStudy != null

        val updatedTrait = trait.clone().apply { visible = isVisible }
        updateTraitInList(updatedTrait)

        viewModelScope.launch {
            runCatching {
                if (useScopedVisibility) {
                    repo.updateStudyTraitVisibility(scopedStudy, traitId, isVisible)
                    // Create/update field-specific trait list when visibility changes
                    val currentTraits = _uiState.value.traits
                    repo.updateStudyTraitOrder(scopedStudy, currentTraits.map { it.id })
                } else {
                    repo.updateVisibility(traitId, isVisible)
                }
            }
                .onSuccess { notifyCollectReload() }
                .onFailure { e -> // rollback
                    updateTraitInList(trait)
                    notifyCollectReload()
                    Log.e(TAG, "Error updating trait visibility", e)
                    _events.emit(TraitEditorEvent.ShowToast(R.string.error_updating_trait_visibility))
                }
        }
    }

    fun toggleAllTraitsVisibility() {
        val oldList = _uiState.value.traits
        val scopedStudy = scopedStudyId
        val useScopedVisibility = usePerFieldTraitList && scopedStudy != null

        val newVisibility = !oldList.all { it.visible }

        val updatedList = oldList.map { it.clone().apply { visible = newVisibility } }

        _uiState.update { it.copy(traits = updatedList) }

        viewModelScope.launch {
            runCatching {
                if (useScopedVisibility) {
                    repo.updateAllStudyTraitVisibility(
                        studyId = scopedStudy,
                        traitIds = oldList.map { it.id }.toSet(),
                        isVisible = newVisibility,
                    )
                    // Create/update field-specific trait list when toggling all
                    repo.updateStudyTraitOrder(scopedStudy, updatedList.map { it.id })
                } else {
                    oldList.forEach {
                        repo.updateVisibility(it.id, newVisibility)
                    }
                }
            }
                .onSuccess { notifyCollectReload() }
                .onFailure { e ->
                _uiState.update { it.copy(traits = oldList) }
                Log.e(TAG, "Error toggling visibility for all traits", e)
                _events.emit(TraitEditorEvent.ShowToast(R.string.error_toggling_all_traits_visibility))
            }
        }
    }

    fun toggleVisibilityForSelectedTraits() {
        val selectedIds = _uiState.value.selectedTraitIds
        if (selectedIds.isEmpty()) return

        val currentTraits = _uiState.value.traits.filter { it.id in selectedIds }
        val allVisible = currentTraits.all { it.visible }
        val newVisibility = !allVisible

        val scopedStudy = scopedStudyId
        val useScopedVisibility = usePerFieldTraitList && scopedStudy != null

        // Update in-memory state for immediate feedback
        _uiState.update { state ->
            val updatedTraits = state.traits.map {
                if (it.id in selectedIds) it.clone().apply { visible = newVisibility }
                else it
            }
            state.copy(traits = updatedTraits)
        }

        viewModelScope.launch {
            runCatching {
                if (useScopedVisibility) {
                    repo.updateAllStudyTraitVisibility(
                        studyId = scopedStudy,
                        traitIds = selectedIds,
                        isVisible = newVisibility,
                    )
                    // Update field order as well if needed (optional, keeping it simple for now)
                    val updatedTraits = _uiState.value.traits
                    repo.updateStudyTraitOrder(scopedStudy, updatedTraits.map { it.id })
                } else {
                    selectedIds.forEach {
                        repo.updateVisibility(it, newVisibility)
                    }
                }
            }
                .onSuccess { 
                    notifyCollectReload()
                }
                .onFailure { e ->
                    loadTraits() // Rollback by reloading
                    Log.e(TAG, "Error toggling visibility for selected traits", e)
                    _events.emit(TraitEditorEvent.ShowToast(R.string.error_toggling_all_traits_visibility))
                }
        }
    }

    fun insertTraits(newTraits: List<TraitObject>) {
        viewModelScope.launch {
            runCatching { repo.insertTraitsList(newTraits) }
                .onSuccess { insertedCount ->
                    loadTraits()
                    notifyCollectReload()

                    val skipped = newTraits.size - insertedCount

                    val messageRes = if (skipped > 0) {
                        R.string.message_traits_imported_with_skipped
                    } else {
                        R.string.message_traits_imported
                    }

                    _events.emit(
                        TraitEditorEvent.ShowMessageWithArgs(
                            messageRes,
                            listOf(insertedCount, skipped)
                        )
                    )
                }
                .onFailure { e ->
                    loadTraits()
                    Log.e(TAG, "Error importing traits", e)
                    _events.emit(TraitEditorEvent.ShowToast(R.string.error_importing_traits))
                }
        }
    }

    // REORDER RELATED

    fun moveTraitItem(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.traits
        val oldList = current.toMutableList()

        if (fromIndex !in oldList.indices || toIndex !in oldList.indices) return

        val updatedList = current.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }

        _uiState.update { it.copy(traits = updatedList) }
    }

    /**
     * Commit the trait order changes only if drag ended
     * That is, if previously dragging and currently not dragging anymore
     */
    fun onDragStateChanged(isCurrentlyDragging: Boolean) {
        // Detect drag start: previously not dragging, now dragging
        if (!wasPreviouslyDragging && isCurrentlyDragging) {
            // If a custom sort order is active, reset to default "position" to allow manual reordering
            if (_uiState.value.sortOrder != "position") {
                // Update the correct preference key (field-specific in viewer mode, global otherwise)
                if (isViewerMode) {
                    scopedStudyId?.let { studyId ->
                        prefs.edit { putString("$FIELD_TRAIT_SORT_ORDER_PREFIX$studyId", "position") }
                    }
                } else {
                    prefs.edit { putString(GeneralKeys.TRAITS_LIST_SORT_ORDER, "position") }
                }
                _uiState.update { it.copy(sortOrder = "position") }
            }
        }

        // Detect drag end: previously dragging, now stopped
        if (wasPreviouslyDragging && !isCurrentlyDragging) { // drag ended
            commitTraitOrder()
        }
        wasPreviouslyDragging = isCurrentlyDragging
    }

    fun commitTraitOrder() {
        val finalList = _uiState.value.traits
        val finalIds = finalList.map { it.id }
        if (finalIds == lastCommittedSortedTraitIds) return

        viewModelScope.launch {
            runCatching {
                val scopedStudy = scopedStudyId
                val useScopedOrder = usePerFieldTraitList && scopedStudy != null

                if (useScopedOrder) {
                    // Create/update field-specific trait list with new order
                    repo.updateStudyTraitOrder(scopedStudy, finalList.map { it.id })
                } else {
                    // Update global trait positions
                    repo.updateTraitOrder(finalList)
                }
                notifyCollectReload()

                lastCommittedSortedTraitIds = finalIds

                // Keep global sort preference untouched in field-specific viewer mode.
                if (!isViewerMode) {
                    prefs.edit {
                        putString(GeneralKeys.TRAITS_LIST_SORT_ORDER, "position")
                    }
                } else {
                    scopedStudyId?.let { studyId ->
                        prefs.edit { putString("$FIELD_TRAIT_SORT_ORDER_PREFIX$studyId", "position") }
                    }
                }

                _uiState.update { it.copy(sortOrder = "position") }
                // Reload quietly to guarantee UI reflects persisted DB-backed field order.
                loadTraits(showLoading = false)
            }.onFailure { e ->
                Log.e(TAG, "Failed to save trait order", e)
                _events.emit(TraitEditorEvent.ShowToast(R.string.error_saving_trait_order))
            }
        }
    }

    // FILE RELATED

    fun importTraits(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val traits =
                    repo.parseTraits(
                        sourceUri = uri,
                        onError = { resId ->
                            _events.emit(TraitEditorEvent.ShowToast(resId))
                        },
                    )

                insertTraits(traits)
            }.onFailure { e ->
                Log.e(TAG, "Failed to import traits", e)
                _events.emit(TraitEditorEvent.ShowToast(R.string.error_importing_traits))
            }
        }
    }

    fun exportTraits(fileName: String) {
        val traits = uiState.value.traits

        viewModelScope.launch {
            repo.exportTraitsAsJson(
                fileName = fileName,
                traits = traits,
                onSuccess = { uri ->
                    _events.emit(
                        TraitEditorEvent.ShowMessageWithArgs(
                            R.string.message_traits_exported,
                            listOf(traits.size)
                        )
                    )
                    _events.emit(TraitEditorEvent.ShareFile(uri))
                },
                onError = { resId ->
                    _events.emit(TraitEditorEvent.ShowToast(resId))
                }
            )
        }
    }

    // DIALOG STATES

    fun showDialog(nextDialog: TraitActivityDialog) {
        _uiState.update { it.copy(activeDialog = nextDialog) }
    }

    fun hideDialog() {
        _uiState.update { it.copy(activeDialog = TraitActivityDialog.None) }
    }

    fun showExportDialog(source: DialogTriggerSource) {
        _uiState.update { it.copy(activeDialog = TraitActivityDialog.Export(source)) }
    }

    fun showDeleteDialog(source: DialogTriggerSource) {
        _uiState.update { it.copy(activeDialog = TraitActivityDialog.DeleteAll(source)) }
    }

    /**
     * Handles cancel or export dialog button in export dialog
     *
     * can be triggered during IMPORT_WORKFLOW OR via TOOLBAR
     * if triggered via import workflow, show DeleteAllDialog -> Import Local/Cloud file
     */
    fun handleExportDialogAction(source: DialogTriggerSource, fileName: String? = null) {
        hideDialog()

        fileName?.let { exportTraits(it) }

        if (source == DialogTriggerSource.IMPORT_WORKFLOW) {
            showDeleteDialog(DialogTriggerSource.IMPORT_WORKFLOW)
        }
    }

    /**
     * Handles cancel or delete dialog button in delete all dialog
     *
     * can be triggered during IMPORT_WORKFLOW OR via TOOLBAR
     * if triggered via IMPORT_WORKFLOW, show Import Local/Cloud file
     */
    fun handleDeleteDialogAction(source: DialogTriggerSource, shouldDelete: Boolean = false) {
        hideDialog()

        if (shouldDelete) {
            deleteAllTraits()
        }

        if (source == DialogTriggerSource.IMPORT_WORKFLOW) {
            showDialog(TraitActivityDialog.ImportChoice)
        }
    }

    // ONE-TIME EVENTS

    fun onImportPermissionGranted() {
        val traits = uiState.value.traits

        if (traits.isNotEmpty() && !previouslyExported()) {
            // export check dialog
            showDialog(TraitActivityDialog.ExportCheck)
            return
        }

        if (traits.isNotEmpty()) {
            // delete all dialog
            showDeleteDialog(DialogTriggerSource.IMPORT_WORKFLOW)
            return
        }

        // import choice dialog
        showDialog(TraitActivityDialog.ImportChoice)
    }

    fun onExportPermissionGranted(source: DialogTriggerSource) {
        showExportDialog(source)
    }

    fun requestImportPermission() {
        viewModelScope.launch {
            _events.emit(TraitEditorEvent.RequestStoragePermissionForImport)
        }
    }

    fun requestExportPermission(source: DialogTriggerSource) {
        viewModelScope.launch {
            _events.emit(TraitEditorEvent.RequestStoragePermissionForExport(source))
        }
    }

    fun openLocalFilePicker() = viewModelScope.launch {
        _events.emit(TraitEditorEvent.OpenFileExplorer)
    }

    fun openCloudPicker() = viewModelScope.launch {
        _events.emit(TraitEditorEvent.OpenCloudFilePicker)
    }

    fun openBrapiActivity() = viewModelScope.launch {
        _events.emit(TraitEditorEvent.NavigateToBrapi)
    }
}

/**
 * Used to specify how are (Export and Delete All)
 * dialogs invoked
 *
 * If they were invoked through
 * - toolbar item, nothing to show next
 * - import workflow ("Import from File"), decide what to show next
 */
enum class DialogTriggerSource { IMPORT_WORKFLOW, TOOLBAR }

data class TraitEditorUiState(
    val activeDialog: TraitActivityDialog = TraitActivityDialog.None,
    val traits: List<TraitObject> = emptyList(),
    val availableTraits: List<TraitObject> = emptyList(),
    val selectedTraitIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val sortOrder: String = "position",
) {
    val hasTraits: Boolean get() = traits.isNotEmpty()
    val isSelectionMode: Boolean get() = selectedTraitIds.isNotEmpty()
}

sealed class TraitEditorEvent {
    data class ShowMessageWithArgs(val resId: Int, val args: List<Any>) : TraitEditorEvent()
    data class ShowToast(val resId: Int) : TraitEditorEvent()
    data class ShareFile(val fileUri: Uri) : TraitEditorEvent()
    object NavigateToBrapi : TraitEditorEvent()
    object RequestStoragePermissionForImport : TraitEditorEvent()
    data class RequestStoragePermissionForExport(val source: DialogTriggerSource) : TraitEditorEvent()
    object OpenFileExplorer : TraitEditorEvent()
    object OpenCloudFilePicker : TraitEditorEvent()
    object ScrollToTop : TraitEditorEvent()
}

// only one dialog can be active at a time
sealed class TraitActivityDialog {
    object None : TraitActivityDialog()

    object NewTrait : TraitActivityDialog()
    object ImportChoice : TraitActivityDialog()
    object ExportCheck : TraitActivityDialog()
    data class Export(val source: DialogTriggerSource) : TraitActivityDialog()
    data class DeleteAll(val source: DialogTriggerSource) : TraitActivityDialog()
    data class RemoveFromField(val traitIds: Set<String>, val message: String) : TraitActivityDialog()
    object SortTraits : TraitActivityDialog()
}