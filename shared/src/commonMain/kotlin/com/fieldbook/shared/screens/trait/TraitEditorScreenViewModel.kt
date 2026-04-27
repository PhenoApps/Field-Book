package com.fieldbook.shared.screens.trait

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.database.repository.TraitRepository
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dir_trait
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.utilities.CSVUtil
import com.fieldbook.shared.utilities.DocumentFile
import com.fieldbook.shared.utilities.getDirectory
import com.fieldbook.shared.utilities.listFiles
import com.fieldbook.shared.utilities.shareFile
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TraitEditorScreenViewModel(
    private val traitRepository: TraitRepository = TraitRepository(),
    private val settings: Settings = Settings()
) : ViewModel() {

    enum class TraitSortOption(val preferenceValue: String) {
        DEFAULT("position"),
        NAME("observation_variable_name"),
        FORMAT("observation_variable_field_book_format"),
        IMPORT_ORDER("internal_id_observation_variable"),
        VISIBILITY("visible");

        companion object {
            fun fromPreference(value: String?): TraitSortOption {
                return entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
            }
        }
    }

    private val _traits = MutableStateFlow<List<TraitObject>>(emptyList())
    val traits: StateFlow<List<TraitObject>> = _traits.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _sortOption = MutableStateFlow(
        TraitSortOption.fromPreference(
            settings.getString(GeneralKeys.TRAITS_LIST_SORT_ORDER.key, TraitSortOption.DEFAULT.preferenceValue)
        )
    )
    val sortOption: StateFlow<TraitSortOption> = _sortOption.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    init {
        loadTraits()
    }

    fun loadTraits() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val list = traitRepository.getAllTraitsWithAttributes()
                _traits.value = applySort(list, _sortOption.value)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
                _traits.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggleVisibility(traitId: Long?, visible: Boolean) {
        if (traitId == null) return
        viewModelScope.launch {
            traitRepository.updateTraitVisibility(traitId, visible)
            loadTraits()
        }
    }

    fun moveTrait(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val current = _traits.value.toMutableList()
            if (fromIndex < 0 || fromIndex >= current.size || toIndex < 0 || toIndex >= current.size) return@launch
            current.add(toIndex, current.removeAt(fromIndex))

            _sortOption.value = TraitSortOption.DEFAULT
            settings.putString(GeneralKeys.TRAITS_LIST_SORT_ORDER.key, TraitSortOption.DEFAULT.preferenceValue)

            // persist new ordering
            current.forEachIndexed { index, trait ->
                traitRepository.updateTraitPosition(trait.id ?: return@forEachIndexed, index)
            }

            _traits.value = current.toList()
        }
    }

    fun deleteTrait(traitId: Long?) {
        if (traitId == null) return
        viewModelScope.launch {
            traitRepository.deleteTrait(traitId)
            loadTraits()
        }
    }

    fun copyTrait(trait: TraitObject?) {
        if (trait == null) return
        viewModelScope.launch {
            // Build base name without any existing -Copy suffix
            var baseName = trait.name
            if (baseName.contains("-Copy")) {
                baseName = baseName.substring(0, baseName.indexOf("-Copy"))
            }

            val existingNames = traitRepository.getAllTraitNames()

            var newName = ""
            for (i in 0..Int.MAX_VALUE) {
                val candidate = "$baseName-Copy-($i)"
                if (!existingNames.contains(candidate)) {
                    newName = candidate
                    break
                }
            }

            val pos = nextCreatedTraitPosition()

            val newTrait = trait.copy()
            newTrait.name = newName
            newTrait.visible = "true"
            newTrait.realPosition = pos

            traitRepository.insertTrait(newTrait)

            loadTraits()
        }
    }

    fun insertTrait(trait: TraitObject) {
        viewModelScope.launch {
            trait.realPosition = nextCreatedTraitPosition()
            trait.visible = trait.visible ?: "true"
            trait.traitDataSource = trait.traitDataSource ?: "local"
            traitRepository.insertTrait(trait)
            loadTraits()
        }
    }

    fun updateTrait(trait: TraitObject) {
        viewModelScope.launch {
            traitRepository.updateTrait(trait)
            loadTraits()
        }
    }

    fun getTraitForEdit(traitId: Long?): TraitObject? {
        if (traitId == null) return null
        return traitRepository.getTraitWithAttributes(traitId)
    }

    fun refresh() {
        loadTraits()
    }

    fun setSortOption(sortOption: TraitSortOption) {
        _sortOption.value = sortOption
        settings.putString(GeneralKeys.TRAITS_LIST_SORT_ORDER.key, sortOption.preferenceValue)
        loadTraits()
    }

    fun toggleAllVisibility() {
        viewModelScope.launch {
            val current = traitRepository.getAllTraits()
            if (current.isEmpty()) {
                _messages.emit("Create traits before modifying them.")
                return@launch
            }

            val makeVisible = !current.all { it.visible.equals("true", ignoreCase = true) }
            current.forEach { trait ->
                trait.id?.let { traitRepository.updateTraitVisibility(it, makeVisible) }
            }

            settings.putBoolean(GeneralKeys.ALL_TRAITS_VISIBLE.key, !makeVisible)
            loadTraits()
        }
    }

    fun deleteAllTraits() {
        viewModelScope.launch {
            val current = traitRepository.getAllTraits()
            if (current.isEmpty()) {
                _messages.emit("No traits exist.")
                return@launch
            }

            traitRepository.deleteAllTraits()
            loadTraits()
        }
    }

    fun exportTraits(fileName: String) {
        viewModelScope.launch {
            val exportName = fileName.trim()
            if (exportName.isEmpty()) {
                _messages.emit("File name is required")
                return@launch
            }

            val traitDir = getDirectory(Res.string.dir_trait)
            if (traitDir == null || !traitDir.exists()) {
                _messages.emit("Trait storage directory is unavailable")
                return@launch
            }

            val finalName = if (exportName.endsWith(".trt", ignoreCase = true)) exportName else "$exportName.trt"
            val existing = listFiles(traitDir).firstOrNull { it.name().equals(finalName, ignoreCase = true) }
            val file = existing ?: traitDir.createFile("*/*", finalName)

            if (file == null) {
                _messages.emit("Failed to create export file")
                return@launch
            }

            val csv = buildTraitExportCsv()
            file.writeBytes(csv.encodeToByteArray())
            settings.putBoolean(GeneralKeys.TRAITS_EXPORTED.key, true)
            shareFile(file)
            _messages.emit("Exported ${file.name().orEmpty()}")
        }
    }

    fun importTraitsFromFile(file: PlatformFile) {
        viewModelScope.launch {
            if (!file.name.endsWith(".trt", ignoreCase = true)) {
                _messages.emit("Only TRT files can be imported as a trait list")
                return@launch
            }

            _importing.value = true
            try {
                performTraitImport(file.readBytes())
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Error importing")
            } finally {
                _importing.value = false
            }
        }
    }

    fun importTraitsFromDocumentFile(file: DocumentFile) {
        viewModelScope.launch {
            val name = file.name().orEmpty()
            if (!name.endsWith(".trt", ignoreCase = true)) {
                _messages.emit("Only TRT files can be imported as a trait list")
                return@launch
            }

            _importing.value = true
            try {
                performTraitImport(file.readBytes())
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Error importing")
            } finally {
                _importing.value = false
            }
        }
    }

    fun importTraitsFromBytes(bytes: ByteArray) {
        viewModelScope.launch {
            _importing.value = true
            try {
                performTraitImport(bytes)
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Error importing")
            } finally {
                _importing.value = false
            }
        }
    }

    private suspend fun performTraitImport(bytes: ByteArray) {
        val positionOffset = traitRepository.getMaxPositionFromTraits() + 1
        val importedTraits = CSVUtil.parseTraits(
            bytes = bytes,
            positionOffset = positionOffset
        )

        importedTraits.forEach(traitRepository::insertTrait)
        loadTraits()

        _messages.emit(
            if (importedTraits.isEmpty()) {
                "No traits were imported"
            } else {
                "Imported ${importedTraits.size} trait(s)"
            }
        )
    }

    private fun nextCreatedTraitPosition(): Int {
        return maxOf(1, traitRepository.getMaxPositionFromTraits() + 1)
    }

    private fun applySort(
        traits: List<TraitObject>,
        sortOption: TraitSortOption
    ): List<TraitObject> {
        return when (sortOption) {
            TraitSortOption.DEFAULT -> traits.sortedBy { it.realPosition }
            TraitSortOption.NAME -> traits.sortedBy { it.name.lowercase() }
            TraitSortOption.FORMAT -> traits.sortedBy { it.format.orEmpty().lowercase() }
            TraitSortOption.IMPORT_ORDER -> traits.sortedBy { it.id ?: Long.MAX_VALUE }
            TraitSortOption.VISIBILITY -> traits.sortedWith(
                compareByDescending<TraitObject> { it.visible.equals("true", ignoreCase = true) }
                    .thenBy { it.realPosition }
            )
        }
    }

    private fun buildTraitExportCsv(): String {
        val header = listOf(
            "trait",
            "format",
            "defaultValue",
            "minimum",
            "maximum",
            "details",
            "categories",
            "isVisible",
            "realPosition"
        )
        val rows = traitRepository.getAllTraitsWithAttributes()
            .sortedBy { it.id ?: Long.MAX_VALUE }
            .map { trait ->
                listOf(
                    trait.name,
                    trait.format.orEmpty(),
                    trait.defaultValue.orEmpty(),
                    trait.minimum.orEmpty(),
                    trait.maximum.orEmpty(),
                    trait.details.orEmpty(),
                    trait.categories.orEmpty(),
                    trait.visible.orEmpty(),
                    trait.realPosition.toString()
                )
            }

        return buildString {
            appendCsvRow(header)
            rows.forEach { appendCsvRow(it) }
        }
    }

    private fun StringBuilder.appendCsvRow(values: List<String>) {
        append(
            values.joinToString(",") { value ->
                "\"${value.replace("\"", "\"\"")}\""
            }
        )
        append('\n')
    }
}

fun traitEditorScreenViewModelFactory() = viewModelFactory {
    initializer {
        TraitEditorScreenViewModel(
            traitRepository = TraitRepository(),
            settings = Settings()
        )
    }
}
