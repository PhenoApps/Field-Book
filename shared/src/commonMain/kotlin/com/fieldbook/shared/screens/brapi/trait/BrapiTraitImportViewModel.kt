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
import com.fieldbook.shared.screens.brapi.BrapiFilterType
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Job
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

    private val _studyFilterLoading = MutableStateFlow(false)
    val studyFilterLoading: StateFlow<Boolean> = _studyFilterLoading.asStateFlow()

    private val _studyFilteredTraits = MutableStateFlow<List<BrapiTraitDetails>?>(null)
    val studyFilteredTraits: StateFlow<List<BrapiTraitDetails>?> = _studyFilteredTraits.asStateFlow()

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

    private var studyFilterTraitsJob: Job? = null
    private var activeStudyFilterKey: String? = null

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

                val paginationManager = BrapiPaginationManager.fromSettings(settings)

                when (val result = buildBrapiService(defaultBaseUrl).getTraits(pageSize = paginationManager.pageSize)) {
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
        studyFilterTraitsJob?.cancel()
        activeStudyFilterKey = null
        _brapiTraits.value = emptyList()
        _studyFilteredTraits.value = null
        _studyFilterLoading.value = false
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

    fun resolveStudyFilterTraits(
        defaultBaseUrl: String,
        selections: Map<String, Set<String>>,
        studies: List<BrapiStudyDetails>,
    ) {
        val trialIds = selections[BrapiFilterType.TRIAL.name].orEmpty()
        val studyIds = selections[BrapiFilterType.STUDY.name].orEmpty()
        val cropIds = selections[BrapiFilterType.CROP.name].orEmpty()
        val hasStudyScopeFilters = trialIds.isNotEmpty() || studyIds.isNotEmpty()

        if (!hasStudyScopeFilters) {
            studyFilterTraitsJob?.cancel()
            activeStudyFilterKey = null
            _studyFilteredTraits.value = null
            _studyFilterLoading.value = false
            return
        }

        val matchingStudies = studies
            .filter { study -> trialIds.isEmpty() || study.trialDbId in trialIds }
            .filter { study -> studyIds.isEmpty() || study.studyDbId in studyIds }
            .filter { study -> cropIds.isEmpty() || study.commonCropName in cropIds }

        val filterKey = listOf(
            trialIds.sorted().joinToString(","),
            studyIds.sorted().joinToString(","),
            cropIds.sorted().joinToString(","),
            matchingStudies.map { it.studyDbId }.sorted().joinToString(","),
        ).joinToString("|")

        if (filterKey == activeStudyFilterKey) return
        activeStudyFilterKey = filterKey
        studyFilterTraitsJob?.cancel()

        studyFilterTraitsJob = viewModelScope.launch {
            _studyFilterLoading.value = true
            try {
                if (matchingStudies.isEmpty()) {
                    _studyFilteredTraits.value = emptyList()
                    return@launch
                }

                val sourceUrl = settings.getString(PreferenceKeys.BRAPI_BASE_URL, defaultBaseUrl)
                val paginationManager = BrapiPaginationManager.fromSettings(settings)
                val service = buildBrapiService(defaultBaseUrl)
                val traitsById = linkedMapOf<String, BrapiTraitDetails>()
                val fetchedStudyTraits = linkedMapOf<String, List<BrapiTraitDetails>>()
                val cachedModels = BrapiFilterCache.getStoredModels(sourceUrl)
                val knownTraits = (_brapiTraits.value + cachedModels.traits.values)
                    .associateBy { it.observationVariableDbId }
                var failedStudyCount = 0

                matchingStudies.forEach { study ->
                    val cachedTraitIds = cachedModels.studyTraitIds[study.studyDbId]
                    if (cachedTraitIds != null && cachedTraitIds.all { it in knownTraits }) {
                        cachedTraitIds.forEach { traitId ->
                            knownTraits[traitId]?.let { trait ->
                                traitsById[trait.observationVariableDbId] = trait
                            }
                        }
                        return@forEach
                    }

                    when (val result = service.getStudyTraits(study.studyDbId, paginationManager.pageSize)) {
                        is BrapiResult.Success -> {
                            fetchedStudyTraits[study.studyDbId] = result.value
                            result.value.forEach { trait ->
                                traitsById[trait.observationVariableDbId] = trait
                            }
                        }

                        is BrapiResult.Failure -> {
                            failedStudyCount++
                        }
                    }
                }

                val resolvedTraits = if (traitsById.isNotEmpty()) {
                    traitsById.values.toList()
                } else {
                    val linkedTraitIds = matchingStudies
                        .flatMap { it.observationVariableDbIds }
                        .toSet()
                    if (linkedTraitIds.isNotEmpty()) {
                        linkedTraitIds.mapNotNull(knownTraits::get)
                    } else {
                        emptyList()
                    }
                }.sortedBy { it.observationVariableName.lowercase() }

                if (fetchedStudyTraits.isNotEmpty()) {
                    BrapiFilterCache.saveStudyTraits(sourceUrl, fetchedStudyTraits)
                }
                if (resolvedTraits.isNotEmpty()) {
                    _brapiTraits.update { current ->
                        (current + resolvedTraits)
                            .distinctBy { it.observationVariableDbId }
                            .sortedBy { it.observationVariableName.lowercase() }
                    }
                }
                _studyFilteredTraits.value = resolvedTraits

                if (failedStudyCount > 0 && resolvedTraits.isEmpty()) {
                    _messages.emit("No traits were found for the selected BrAPI study filter.")
                }
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Error loading traits for the selected BrAPI study filter.")
                _studyFilteredTraits.value = emptyList()
            } finally {
                _studyFilterLoading.value = false
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
        val trialIds = selections[BrapiFilterType.TRIAL.name].orEmpty()
        val studyIds = selections[BrapiFilterType.STUDY.name].orEmpty()
        val cropIds = selections[BrapiFilterType.CROP.name].orEmpty()
        val hasStudyScopeFilters = trialIds.isNotEmpty() || studyIds.isNotEmpty()
        val hasCropFilters = cropIds.isNotEmpty()
        val filteredStudyTraitIds = studies
            .filter { study -> trialIds.isEmpty() || study.trialDbId in trialIds }
            .filter { study -> studyIds.isEmpty() || study.studyDbId in studyIds }
            .filter { study -> cropIds.isEmpty() || study.commonCropName in cropIds }
            .flatMap { it.observationVariableDbIds }
            .toSet()

        return traits
            .filter { trait ->
                when {
                    hasStudyScopeFilters && filteredStudyTraitIds.isNotEmpty() ->
                        trait.observationVariableDbId in filteredStudyTraitIds
                    hasStudyScopeFilters && hasCropFilters -> trait.commonCropName in cropIds
                    hasStudyScopeFilters -> true
                    hasCropFilters && filteredStudyTraitIds.isNotEmpty() ->
                        trait.observationVariableDbId in filteredStudyTraitIds
                    hasCropFilters -> trait.commonCropName in cropIds
                    else -> true
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
