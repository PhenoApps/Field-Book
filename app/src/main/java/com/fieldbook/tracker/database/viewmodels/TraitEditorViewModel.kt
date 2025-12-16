package com.fieldbook.tracker.database.viewmodels

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.database.repository.TraitRepository
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
    }

    private val _uiState = MutableStateFlow(TraitEditorUiState())
    val uiState: StateFlow<TraitEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TraitEditorEvent>()
    val events = _events.asSharedFlow()

    // to manage db updates on reorder
    private var lastCommittedSortedList: List<TraitObject> = emptyList()
    private var wasPreviouslyDragging = false

    init {
        val sortOrder =
            prefs.getString(GeneralKeys.TRAITS_LIST_SORT_ORDER, "position") ?: "position"
        _uiState.update { it.copy(sortOrder = sortOrder) }
        loadTraits()
    }

    // PREFERENCES

    fun isTutorialEnabled() = prefs.getBoolean(PreferenceKeys.TIPS, false)

    fun isBrapiEnabled() = prefs.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)

    fun isBrapiNewUi() = prefs.getBoolean(PreferenceKeys.EXPERIMENTAL_NEW_BRAPI_UI, true)

    fun getBrapiDisplayName(default: String) =
        prefs.getString(PreferenceKeys.BRAPI_DISPLAY_NAME, default) ?: default

    fun previouslyExported() = prefs.getBoolean(GeneralKeys.TRAITS_EXPORTED, false)

    fun updateSortOrder(sortOrder: String) {
        prefs.edit { putString(GeneralKeys.TRAITS_LIST_SORT_ORDER, sortOrder) }
        _uiState.update { it.copy(sortOrder = sortOrder) }

        loadTraits()
    }

    // IN-MEMORY UPDATES (does not fetch from db)

    fun addTraitObject(newTrait: TraitObject) {
        _uiState.update { state ->
            state.copy(
                traits = state.traits + newTrait
            )
        }
    }

    fun removeTraitObject(id: String) {
        _uiState.update { state ->
            state.copy(
                traits = state.traits.filterNot { it.id == id }
            )
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


    // DB RELATED

    fun loadTraits() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            runCatching { repo.getTraits() }
                .onSuccess { traits ->
                    _uiState.update {
                        it.copy(traits = traits, isLoading = false)
                    }

                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false)
                    }
                    Log.e(TAG, "Error loading traits", e)
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

        val updatedTrait = trait.clone().apply { visible = isVisible }
        updateTraitInList(updatedTrait)

        viewModelScope.launch {
            runCatching { repo.updateVisibility(traitId, isVisible) }
                .onFailure { e -> // rollback
                    updateTraitInList(trait)
                    Log.e(TAG, "Error updating trait visibility", e)
                    _events.emit(TraitEditorEvent.ShowToast(R.string.error_updating_trait_visibility))
                }
        }
    }

    fun toggleAllTraitsVisibility() {
        val oldList = _uiState.value.traits

        val newVisibility = !oldList.all { it.visible }

        val updatedList = oldList.map { it.clone().apply { visible = newVisibility } }

        _uiState.update { it.copy(traits = updatedList) }

        viewModelScope.launch {
            runCatching {
                oldList.forEach {
                    repo.updateVisibility(it.id, newVisibility)
                }
            }.onFailure { e ->
                _uiState.update { it.copy(traits = oldList) }
                Log.e(TAG, "Error toggling visibility for all traits", e)
                _events.emit(TraitEditorEvent.ShowToast(R.string.error_toggling_all_traits_visibility))
            }
        }
    }

    fun insertTraits(newTraits: List<TraitObject>) {
        viewModelScope.launch {
            runCatching { repo.insertTraitsList(newTraits) }
                .onSuccess { insertedCount ->
                    loadTraits()

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
        if (wasPreviouslyDragging && !isCurrentlyDragging) { // drag ended
            commitTraitOrder()
        }
        wasPreviouslyDragging = isCurrentlyDragging
    }

    fun commitTraitOrder() {
        val finalList = _uiState.value.traits
        if (finalList == lastCommittedSortedList) return

        viewModelScope.launch {
            runCatching {
                repo.updateTraitOrder(finalList)
                lastCommittedSortedList = finalList

                // update pref and state
                prefs.edit {
                    putString(GeneralKeys.TRAITS_LIST_SORT_ORDER, "position")
                }

                _uiState.update { it.copy(sortOrder = "position") }
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

    fun onExportPermissionGranted() {
        showExportDialog(DialogTriggerSource.TOOLBAR)
    }

    fun requestImportPermission() {
        viewModelScope.launch {
            _events.emit(TraitEditorEvent.RequestStoragePermissionForImport)
        }
    }

    fun requestExportPermission() {
        viewModelScope.launch {
            _events.emit(TraitEditorEvent.RequestStoragePermissionForExport)
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
    val isLoading: Boolean = false,
    val sortOrder: String = "position",
) {
    val hasTraits: Boolean get() = traits.isNotEmpty()
}

sealed class TraitEditorEvent {
    data class ShowMessageWithArgs(val resId: Int, val args: List<Any>) : TraitEditorEvent()
    data class ShowToast(val resId: Int) : TraitEditorEvent()
    data class ShareFile(val fileUri: Uri) : TraitEditorEvent()
    object NavigateToBrapi : TraitEditorEvent()
    object RequestStoragePermissionForImport : TraitEditorEvent()
    object RequestStoragePermissionForExport : TraitEditorEvent()
    object OpenFileExplorer : TraitEditorEvent()
    object OpenCloudFilePicker : TraitEditorEvent()
}

// only one dialog can be active at a time
sealed class TraitActivityDialog {
    object None : TraitActivityDialog()

    object NewTrait : TraitActivityDialog()
    object ImportChoice : TraitActivityDialog()
    object ExportCheck : TraitActivityDialog()
    data class Export(val source: DialogTriggerSource) : TraitActivityDialog()
    data class DeleteAll(val source: DialogTriggerSource) : TraitActivityDialog()
    object SortTraits : TraitActivityDialog()
}