package com.fieldbook.shared.screens.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.export.ExportOptions
import com.fieldbook.shared.export.ExportResult
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.utilities.ExportUtil
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExportScreenViewModel(
    private val exportUtil: ExportUtil = ExportUtil(),
    private val studyRepository: StudyRepository = StudyRepository(),
    private val settings: Settings = Settings()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ExportEvent>()
    val events = _events.asSharedFlow()

    private fun update(transform: ExportUiState.() -> ExportUiState) {
        _uiState.value = _uiState.value.transform()
    }

    fun loadDefaults(fieldIds: List<Int>) {
        val resolvedFieldIds = resolveFieldIds(fieldIds)
        val multipleFields = resolvedFieldIds.size > 1
        val fileName = buildDefaultFileName(resolvedFieldIds)
        update {
            copy(
                formatDb = settings.getBoolean(GeneralKeys.EXPORT_FORMAT_DATABASE.key, false),
                formatTable = settings.getBoolean(GeneralKeys.EXPORT_FORMAT_TABLE.key, false),
                onlyUnique = settings.getBoolean(GeneralKeys.EXPORT_COLUMNS_UNIQUE.key, false),
                allColumns = settings.getBoolean(GeneralKeys.EXPORT_COLUMNS_ALL.key, false),
                activeTraits = settings.getBoolean(GeneralKeys.EXPORT_TRAITS_ACTIVE.key, false),
                allTraits = settings.getBoolean(GeneralKeys.EXPORT_TRAITS_ALL.key, false),
                bundleMedia = settings.getBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED.key, false),
                overwrite = settings.getBoolean(GeneralKeys.EXPORT_OVERWRITE.key, false),
                fileName = fileName,
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

        persistOptions(options)

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

    private fun persistOptions(options: ExportOptions) {
        settings.putBoolean(GeneralKeys.EXPORT_FORMAT_DATABASE.key, options.formatDb)
        settings.putBoolean(GeneralKeys.EXPORT_FORMAT_TABLE.key, options.formatTable)
        settings.putBoolean(GeneralKeys.EXPORT_COLUMNS_UNIQUE.key, options.onlyUnique)
        settings.putBoolean(GeneralKeys.EXPORT_COLUMNS_ALL.key, options.allColumns)
        settings.putBoolean(GeneralKeys.EXPORT_TRAITS_ACTIVE.key, options.activeTraits)
        settings.putBoolean(GeneralKeys.EXPORT_TRAITS_ALL.key, options.allTraits)
        settings.putBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED.key, options.bundleMedia)
        settings.putBoolean(GeneralKeys.EXPORT_OVERWRITE.key, options.overwrite)
    }

    private fun buildDefaultFileName(fieldIds: List<Int>): String {
        val suffix = if (fieldIds.size > 1) {
            "multiple_fields"
        } else {
            studyRepository.getById(fieldIds.firstOrNull() ?: -1).exp_name.removeSuffix(".csv").ifBlank { "export" }
        }
        return "${exportUtil.defaultTimestampString()}_$suffix"
    }

    private fun resolveFieldIds(fieldIds: List<Int>): List<Int> {
        if (fieldIds.isNotEmpty()) return fieldIds
        val activeFieldId = settings.getInt(GeneralKeys.SELECTED_FIELD_ID.key, -1)
        return if (activeFieldId >= 0) listOf(activeFieldId) else emptyList()
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

fun exportScreenViewModelFactory() = viewModelFactory {
    initializer {
        ExportScreenViewModel(
            exportUtil = ExportUtil(),
            studyRepository = StudyRepository(),
            settings = Settings()
        )
    }
}
