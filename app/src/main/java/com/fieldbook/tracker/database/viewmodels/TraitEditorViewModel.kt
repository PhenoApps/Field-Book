package com.fieldbook.tracker.database.viewmodels

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _exportTriggeredSource = MutableStateFlow(ExportTriggerSource.UNKNOWN)
    val exportTriggerSource = _exportTriggeredSource.asStateFlow()

    private val _deleteTriggeredSource = MutableStateFlow(DeleteTriggerSource.UNKNOWN)
    val deleteTriggeredSource = _deleteTriggeredSource.asStateFlow()

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
                    e.message?.let { _events.emit(TraitEditorEvent.ShowError(it)) }
                }
        }
    }

    fun deleteAllTraits() {
        val traits = uiState.value.traits

        viewModelScope.launch {
            runCatching { repo.deleteAllTraits(traits) }
                .onSuccess {
                    _uiState.update { it.copy(traits = emptyList()) }
                    _events.emit(TraitEditorEvent.ShowMessage("All traits deleted"))
                }
                .onFailure {
                    _events.emit(TraitEditorEvent.ShowError("Failed to delete traits: ${it.message}"))
                }
        }
    }

    fun updateTraitVisibility(traitId: String, isVisible: Boolean) {
        // update UI first, if something fails, rollback the UI changes
        val oldList = uiState.value.traits

        val updatedList = oldList.map { trait ->
            if (trait.id == traitId) { // replace with new object
                trait.clone().apply { visible = isVisible }
            } else trait
        }

        _uiState.update { it.copy(traits = updatedList) }

        viewModelScope.launch {
            runCatching { repo.updateVisibility(traitId, isVisible) }
                .onFailure { e -> // rollback
                    _uiState.update { it.copy(traits = oldList) }
                    _events.emit(
                        TraitEditorEvent.ShowError("Failed to update visibility: ${e.message}")
                    )
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
                _events.emit(
                    TraitEditorEvent.ShowError("Failed to toggle traits: ${e.message}")
                )
            }
        }
    }

    fun insertTraits(newTraits: List<TraitObject>) {
        viewModelScope.launch {
            runCatching { repo.insertTraits(newTraits) }
                .onSuccess { insertedCount ->
                    loadTraits()

                    val skipped = newTraits.size - insertedCount

                    var msg = "Imported $insertedCount traits"
                    if (skipped > 0) msg = "$msg, skipped $skipped duplicates."

                    _events.emit(TraitEditorEvent.ShowMessage(msg))
                }
                .onFailure {
                    loadTraits()
                    _events.emit(
                        TraitEditorEvent.ShowError("Failed to import: ${it.message}")
                    )
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

        Log.d(TAG, "moveTraitItem: $fromIndex $toIndex")
        oldList.forEachIndexed { i, oldTrait ->
            Log.d(TAG, "oldList: ${oldTrait.alias}")
            Log.d(TAG, "updatedList: ${updatedList[i].alias}")
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

        Log.d(TAG, "commitTraitOrder: ")

        finalList.forEach { Log.d(TAG, "finalList: ${it.alias}") }

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
                _events.emit(
                    TraitEditorEvent.ShowError("Failed to save order: ${e.message}")
                )
            }
        }
    }

    // FILE RELATED

    fun importTraits(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val traits = repo.importTraits(uri)

                repo.insertTraits(traits)
                loadTraits()

                _events.emit(TraitEditorEvent.ShowMessage("Imported ${traits.size} traits"))
            }.onFailure { e ->
                _events.emit(TraitEditorEvent.ShowError("Import failed: ${e.message}"))
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
                    _events.emit(TraitEditorEvent.ShowMessage("Exported ${traits.size} traits"))
                    _events.emit(TraitEditorEvent.ShareFile(uri))
                },
                onError = { msg ->
                    _events.emit(TraitEditorEvent.ShowError(msg))
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

    fun showExportDialog(source: ExportTriggerSource) {
        _exportTriggeredSource.value = source
        _uiState.update { it.copy(activeDialog = TraitActivityDialog.Export) }
    }

    fun showDeleteDialog(source: DeleteTriggerSource) {
        _deleteTriggeredSource.value = source
        _uiState.update { it.copy(activeDialog = TraitActivityDialog.DeleteAll) }
    }

    fun clearExportTrigger() {
        _exportTriggeredSource.value = ExportTriggerSource.UNKNOWN
    }

    fun clearDeleteTrigger() {
        _deleteTriggeredSource.value = DeleteTriggerSource.UNKNOWN
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
            showDeleteDialog(DeleteTriggerSource.IMPORT_WORKFLOW)
            return
        }

        // import choice dialog
        showDialog(TraitActivityDialog.ImportChoice)
    }

    fun onExportPermissionGranted() {
        showExportDialog(ExportTriggerSource.TOOLBAR)
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

enum class ExportTriggerSource {
    IMPORT_WORKFLOW,
    TOOLBAR,
    UNKNOWN
}

enum class DeleteTriggerSource {
    IMPORT_WORKFLOW,
    TOOLBAR,
    UNKNOWN
}

data class TraitEditorUiState(
    val activeDialog: TraitActivityDialog = TraitActivityDialog.None,
    val traits: List<TraitObject> = emptyList(),
    val isLoading: Boolean = false,
    val sortOrder: String = "position",
)

sealed class TraitEditorEvent {
    data class ShowMessage(val message: String) : TraitEditorEvent()
    data class ShowError(val message: String) : TraitEditorEvent()
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
    object Export : TraitActivityDialog()
    object DeleteAll : TraitActivityDialog()
    object SortTraits : TraitActivityDialog()
}

sealed class TraitExportResult {
    data class Success(val uri: Uri, val count: Int) : TraitExportResult()
    data class Error(val message: String) : TraitExportResult()
}
