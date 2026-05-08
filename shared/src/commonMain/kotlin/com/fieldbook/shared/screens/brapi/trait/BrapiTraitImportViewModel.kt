package com.fieldbook.shared.screens.brapi.trait

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fieldbook.shared.brapi.BrAPIService
import com.fieldbook.shared.brapi.BrAPIServiceFactory
import com.fieldbook.shared.brapi.BrapiFilterCache
import com.fieldbook.shared.brapi.BrapiPaginationManager
import com.fieldbook.shared.brapi.BrapiResult
import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiTraitDetails
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.database.repository.TraitRepository
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.preferences.PreferenceKeys
import com.fieldbook.shared.screens.brapi.BrapiTraitFilterType
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrapiTraitImportViewModel(
    private val traitRepository: TraitRepository = TraitRepository(),
    private val settings: Settings = Settings(),
) : ViewModel() {

    private val _brapiTraits = MutableStateFlow<List<BrapiTraitDetails>>(emptyList())
    val brapiTraits: StateFlow<List<BrapiTraitDetails>> = _brapiTraits.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private val _importCompleted = MutableSharedFlow<Unit>()
    val importCompleted = _importCompleted.asSharedFlow()

    fun loadTraits(defaultBaseUrl: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!settings.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)) {
                _messages.emit("BrAPI must be enabled first in the BrAPI settings.")
                return@launch
            }

            _loading.value = true
            try {
                val sourceUrl = settings.getString(PreferenceKeys.BRAPI_BASE_URL, defaultBaseUrl)

                if (!forceRefresh) {
                    val cachedTraits = BrapiFilterCache.getStoredModels(sourceUrl).traits.values
                        .sortedBy { it.observationVariableName.lowercase() }
                    if (cachedTraits.isNotEmpty()) {
                        _brapiTraits.value = cachedTraits
                        return@launch
                    }
                }

                val pageSize = settings.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50").toIntOrNull()
                    ?: BrapiPaginationManager.DEFAULT_PAGE_SIZE

                when (val result = buildBrapiService(defaultBaseUrl).getTraits(pageSize = pageSize)) {
                    is BrapiResult.Success -> {
                        BrapiFilterCache.saveTraits(sourceUrl, result.value)
                        _brapiTraits.value = result.value.sortedBy { it.observationVariableName.lowercase() }
                        if (result.value.isEmpty()) {
                            _messages.emit("No BrAPI traits were found.")
                        }
                    }

                    is BrapiResult.Failure -> {
                        _messages.emit(
                            result.message ?: result.statusCode?.let { "BrAPI trait import failed: HTTP $it" }
                            ?: "Error loading traits from BrAPI."
                        )
                    }
                }
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Error loading traits from BrAPI.")
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearAndReloadTraits(defaultBaseUrl: String) {
        _brapiTraits.value = emptyList()
        _selectedIds.value = emptySet()
        loadTraits(defaultBaseUrl, forceRefresh = true)
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setItemSelected(id: String, selected: Boolean) {
        _selectedIds.update { current ->
            if (selected) {
                current + id
            } else {
                current - id
            }
        }
    }

    fun applyFilters(
        traits: List<BrapiTraitDetails>,
        query: String,
        selections: Map<String, Set<String>>,
        studies: List<BrapiStudyDetails>,
    ): List<BrapiTraitDetails> {
        val normalizedQuery = query.trim().lowercase()
        val trialIds = selections[BrapiTraitFilterType.TRIAL.name].orEmpty()
        val studyIds = selections[BrapiTraitFilterType.STUDY.name].orEmpty()
        val cropIds = selections[BrapiTraitFilterType.CROP.name].orEmpty()
        val filteredStudyTraitIds = studies
            .filter { study -> trialIds.isEmpty() || study.trialDbId in trialIds }
            .filter { study -> studyIds.isEmpty() || study.studyDbId in studyIds }
            .filter { study -> cropIds.isEmpty() || study.commonCropName in cropIds }
            .flatMap { it.observationVariableDbIds }
            .toSet()
        val hasStudyFilters = trialIds.isNotEmpty() || studyIds.isNotEmpty() || cropIds.isNotEmpty()

        return traits
            .filter { trait ->
                if (hasStudyFilters && filteredStudyTraitIds.isNotEmpty()) {
                    trait.observationVariableDbId in filteredStudyTraitIds
                } else {
                    cropIds.isEmpty() || trait.commonCropName in cropIds
                }
            }
            .filter { trait ->
                normalizedQuery.isBlank() ||
                    trait.observationVariableName.lowercase().contains(normalizedQuery) ||
                    trait.observationVariableDbId.lowercase().contains(normalizedQuery) ||
                    trait.commonCropName.orEmpty().lowercase().contains(normalizedQuery)
            }
    }

    fun importSelectedTraits(defaultBaseUrl: String) {
        viewModelScope.launch {
            val selectedTraits = _brapiTraits.value.filter { it.observationVariableDbId in _selectedIds.value }
            if (selectedTraits.isEmpty()) {
                _messages.emit("No traits are selected")
                return@launch
            }

            _importing.value = true
            try {
                val source = settings.getString(PreferenceKeys.BRAPI_BASE_URL, defaultBaseUrl).hostForDisplay()
                val existing = traitRepository.getAllTraitsWithAttributes()
                val existingNames = existing.map { it.name }.toSet()
                val existingByExternalId = existing
                    .filter {
                        !it.externalDbId.isNullOrBlank() &&
                            it.traitDataSource?.equals(source, ignoreCase = true) == true
                    }
                    .associateBy { it.externalDbId }

                var nextPosition = traitRepository.getMaxPositionFromTraits() + 1
                var saved = 0
                var skippedExistingName: String? = null

                selectedTraits.forEach { brapiTrait ->
                    val currentByExternalId = existingByExternalId[brapiTrait.observationVariableDbId]
                    when {
                        brapiTrait.observationVariableName in existingNames && currentByExternalId == null -> {
                            skippedExistingName = brapiTrait.observationVariableName
                        }

                        currentByExternalId != null -> {
                            traitRepository.updateTrait(
                                brapiTrait.toTraitObject(source, currentByExternalId.realPosition).apply {
                                    id = currentByExternalId.id
                                    visible = currentByExternalId.visible
                                }
                            )
                            saved++
                        }

                        else -> {
                            traitRepository.insertTrait(brapiTrait.toTraitObject(source, nextPosition++))
                            saved++
                        }
                    }
                }

                settings.putBoolean(GeneralKeys.TRAITS_EXPORTED.key, false)

                _messages.emit(
                    when {
                        saved == 0 && skippedExistingName != null -> "Trait already exists. $skippedExistingName was not saved."
                        saved == 0 -> "Error saving traits. No traits were saved."
                        saved < selectedTraits.size -> "Some traits were not saved."
                        else -> "Selected traits saved successfully."
                    }
                )
                _importCompleted.emit(Unit)
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Error saving traits from BrAPI.")
            } finally {
                _importing.value = false
            }
        }
    }

    private fun buildBrapiService(defaultBaseUrl: String): BrAPIService {
        return BrAPIServiceFactory.create(
            baseUrl = settings.getString(PreferenceKeys.BRAPI_BASE_URL, defaultBaseUrl),
            bearerToken = settings.getStringOrNull(PreferenceKeys.BRAPI_TOKEN),
            version = settings.getString(PreferenceKeys.BRAPI_VERSION, BrAPIServiceFactory.VERSION_V2),
        )
    }

    private fun BrapiTraitDetails.toTraitObject(source: String, position: Int): TraitObject {
        return TraitObject(
            name = observationVariableName,
            format = format,
            defaultValue = defaultValue,
            minimum = minimum,
            maximum = maximum,
            categories = categories,
            visible = "true",
            realPosition = position,
            externalDbId = observationVariableDbId,
            traitDataSource = source,
            commonCropName = commonCropName,
            language = language,
            dataType = dataType,
            ontologyDbId = ontologyDbId,
            ontologyName = ontologyName,
            details = details,
        )
    }

    private fun String.hostForDisplay(): String {
        return trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
    }
}

fun brapiTraitImportViewModelFactory() = viewModelFactory {
    initializer {
        BrapiTraitImportViewModel(
            traitRepository = TraitRepository(),
            settings = Settings(),
        )
    }
}
