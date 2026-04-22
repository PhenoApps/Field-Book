package com.fieldbook.shared.screens.trait

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.database.repository.TraitRepository
import com.fieldbook.shared.utilities.CSVUtil
import com.fieldbook.shared.utilities.DocumentFile
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TraitEditorScreenViewModel(
    private val traitRepository: TraitRepository = TraitRepository()
) : ViewModel() {

    private val _traits = MutableStateFlow<List<TraitObject>>(emptyList())
    val traits: StateFlow<List<TraitObject>> = _traits.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    init {
        loadTraits()
    }

    fun loadTraits() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val list = traitRepository.getAllTraitsOrdered().sortedBy { it.realPosition }
                _traits.value = list
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

            val pos = traitRepository.getMaxPositionFromTraits() + 1

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
            trait.realPosition = traitRepository.getMaxPositionFromTraits() + 1
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
}
