package com.fieldbook.shared.screens.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.shared.export.ExportOptions
import com.fieldbook.shared.export.ExportResult
import com.fieldbook.shared.utilities.ExportUtil
import com.fieldbook.shared.utilities.nowMillis
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExportScreenViewModel(
    private val exportUtil: ExportUtil = ExportUtil()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ExportEvent>()
    val events = _events.asSharedFlow()

    private fun update(transform: ExportUiState.() -> ExportUiState) {
        _uiState.value = _uiState.value.transform()
    }

    fun loadDefaults(multipleFields: Boolean) {
        update {
            copy(
                formatDb = true,
                formatTable = true,
                onlyUnique = true,
                allColumns = false,
                activeTraits = true,
                allTraits = false,
                bundleMedia = false,
                overwrite = false,
                fileName = "${nowMillis()}_export",
                multipleFields = multipleFields
            )
        }
    }

    fun onToggleFormatDb() = update { copy(formatDb = !formatDb) }
    fun onToggleFormatTable() = update { copy(formatTable = !formatTable) }
    fun onSelectOnlyUnique() = update { copy(onlyUnique = true, allColumns = false) }
    fun onSelectAllColumns() = update { copy(onlyUnique = false, allColumns = true) }
    fun onSelectActiveTraits() = update { copy(activeTraits = true, allTraits = false) }
    fun onSelectAllTraits() = update { copy(activeTraits = false, allTraits = true) }
    fun onToggleBundle() = update { copy(bundleMedia = !bundleMedia) }
    fun onToggleOverwrite() = update { copy(overwrite = !overwrite) }
    fun onFileNameChange(value: String) = update { copy(fileName = value) }

    fun onSave(fieldIds: List<Int>) {
        val s = _uiState.value

        // Validation
        if (!s.formatDb && !s.formatTable) {
            viewModelScope.launch { _events.emit(ExportEvent.ShowMessage("Select at least one format")) }
            return
        }
        if (!s.onlyUnique && !s.allColumns) {
            viewModelScope.launch { _events.emit(ExportEvent.ShowMessage("Select a column option")) }
            return
        }
        if (!s.activeTraits && !s.allTraits) {
            viewModelScope.launch { _events.emit(ExportEvent.ShowMessage("Select a trait option")) }
            return
        }

        val options = ExportOptions(
            formatDb = s.formatDb,
            formatTable = s.formatTable,
            onlyUnique = s.onlyUnique,
            allColumns = s.allColumns,
            activeTraits = s.activeTraits,
            allTraits = s.allTraits,
            bundleMedia = s.bundleMedia,
            overwrite = s.overwrite,
            fileName = s.fileName,
            multipleFields = s.multipleFields
        )

        viewModelScope.launch {
            _events.emit(ExportEvent.ShowProgress)
            exportUtil.exportLocalWithOptions(fieldIds, options) { result ->
                viewModelScope.launch {
                    when (result) {
                        is ExportResult.Success -> _events.emit(ExportEvent.Completed(result.message))
                        is ExportResult.Failure -> _events.emit(ExportEvent.Failed(result.error.message ?: "Error"))
                        ExportResult.NoData -> _events.emit(ExportEvent.Failed("No data to export"))
                    }
                }
            }
        }
    }
}

sealed class ExportEvent {
    data class ShowMessage(val message: String) : ExportEvent()
    object ShowProgress : ExportEvent()
    data class Completed(val message: String) : ExportEvent()
    data class Failed(val message: String) : ExportEvent()
}

data class ExportUiState(
    val formatDb: Boolean = true,
    val formatTable: Boolean = true,
    val onlyUnique: Boolean = true,
    val allColumns: Boolean = false,
    val activeTraits: Boolean = true,
    val allTraits: Boolean = false,
    val bundleMedia: Boolean = false,
    val overwrite: Boolean = false,
    val fileName: String = "",
    val multipleFields: Boolean = false
)
