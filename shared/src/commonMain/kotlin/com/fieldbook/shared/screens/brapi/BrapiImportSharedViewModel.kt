package com.fieldbook.shared.screens.brapi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fieldbook.shared.brapi.BrAPIServiceFactory
import com.fieldbook.shared.brapi.BrapiFilterCache
import com.fieldbook.shared.brapi.BrapiPaginationManager
import com.fieldbook.shared.brapi.BrapiResult
import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.preferences.PreferenceKeys
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrapiImportSharedViewModel(
    private val settings: Settings = Settings(),
) : ViewModel() {

    private val _filterState = MutableStateFlow(BrapiFilterUiState())
    val filterState: StateFlow<BrapiFilterUiState> = _filterState.asStateFlow()

    private val _filterSelections = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val filterSelections: StateFlow<Map<String, Set<String>>> = _filterSelections.asStateFlow()

    private val _studies = MutableStateFlow<List<BrapiStudyDetails>>(emptyList())
    val studies: StateFlow<List<BrapiStudyDetails>> = _studies.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private var activeFilterId: String? = null

    fun restoreFilterModels(defaultBaseUrl: String, forceRefresh: Boolean = false) {
        BrapiFilterCache.checkClearCache(settings)
        viewModelScope.launch {
            if (!settings.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)) {
                _messages.emit("BrAPI must be enabled first in the BrAPI settings.")
                return@launch
            }

            _loading.value = true
            try {
                val sourceUrl = settings.getString(PreferenceKeys.BRAPI_BASE_URL, defaultBaseUrl)

                if (!forceRefresh) {
                    val cachedStudies = BrapiFilterCache.getStoredModels(sourceUrl).studies
                    if (cachedStudies.isNotEmpty()) {
                        _studies.value = cachedStudies
                        return@launch
                    }
                }

                val paginationManager = BrapiPaginationManager(
                    initialPageSize = settings.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50").toIntOrNull()
                        ?: BrapiPaginationManager.DEFAULT_PAGE_SIZE
                )
                val fetchedStudies = mutableListOf<BrapiStudyDetails>()
                val service = BrAPIServiceFactory.create(
                    baseUrl = settings.getString(PreferenceKeys.BRAPI_BASE_URL, defaultBaseUrl),
                    bearerToken = settings.getStringOrNull(PreferenceKeys.BRAPI_TOKEN),
                    version = settings.getString(PreferenceKeys.BRAPI_VERSION, BrAPIServiceFactory.VERSION_V2),
                )

                var keepLoading = true
                while (keepLoading) {
                    when (val result = service.getStudies(paginationManager = paginationManager)) {
                        is BrapiResult.Success -> {
                            fetchedStudies += result.value
                        }

                        is BrapiResult.Failure -> {
                            _messages.emit(
                                result.message ?: result.statusCode?.let { "BrAPI study filter load failed: HTTP $it" }
                                ?: "Error loading BrAPI filter data."
                            )
                            return@launch
                        }
                    }
                    if (paginationManager.canMoveNext) {
                        paginationManager.nextPage()
                    } else {
                        keepLoading = false
                    }
                }

                val sortedStudies = fetchedStudies.sortedBy { it.studyName.orEmpty().lowercase() }
                BrapiFilterCache.saveStudies(sourceUrl, sortedStudies)
                _studies.value = sortedStudies
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Error loading BrAPI filter data.")
            } finally {
                _loading.value = false
            }
        }
    }

    fun resetCache(defaultBaseUrl: String) {
        BrapiFilterCache.delete(clearPreferences = true, settings = settings)
        _studies.value = emptyList()
        clearFilterSelections()
        restoreFilterModels(defaultBaseUrl, forceRefresh = true)
    }

    fun setFilterContext(
        id: String,
        title: String,
        elements: List<BrapiFilterElement>,
    ) {
        activeFilterId = id
        _filterState.value = BrapiFilterUiState(
            title = title,
            elements = elements,
            selectedIds = _filterSelections.value[id].orEmpty(),
        )
    }

    fun applyActiveFilterSelection(selectedIds: Set<String>) {
        val filterId = activeFilterId ?: return
        _filterSelections.update { current ->
            if (selectedIds.isEmpty()) {
                current - filterId
            } else {
                current + (filterId to selectedIds)
            }
        }
        _filterState.update { current ->
            current.copy(selectedIds = selectedIds)
        }
    }

    fun clearFilterSelections() {
        _filterSelections.value = emptyMap()
        _filterState.update { current ->
            current.copy(selectedIds = emptySet())
        }
    }

    fun getFilterElements(type: BrapiTraitFilterType): List<BrapiFilterElement> {
        return when (type) {
            BrapiTraitFilterType.TRIAL -> _studies.value
                .mapNotNull { study ->
                    study.trialDbId?.takeIf(String::isNotBlank)?.let { trialDbId ->
                        BrapiFilterElement(
                            id = trialDbId,
                            label = study.trialName?.takeIf(String::isNotBlank) ?: trialDbId,
                            count = 1,
                        )
                    }
                }
                .groupBy { it.id }
                .map { (_, elements) -> elements.first().copy(count = elements.size) }
                .sortedBy { it.label.lowercase() }

            BrapiTraitFilterType.STUDY -> {
                val trialIds = _filterSelections.value[BrapiTraitFilterType.TRIAL.name].orEmpty()
                _studies.value
                    .filter { study -> trialIds.isEmpty() || study.trialDbId in trialIds }
                    .map { study ->
                        BrapiFilterElement(
                            id = study.studyDbId,
                            label = study.studyName?.takeIf(String::isNotBlank) ?: study.studyDbId,
                            count = study.observationVariableDbIds.size,
                        )
                    }
                    .distinctBy { it.id }
                    .sortedBy { it.label.lowercase() }
            }

            BrapiTraitFilterType.CROP -> _studies.value
                .mapNotNull { it.commonCropName?.takeIf(String::isNotBlank) }
                .groupingBy { it }
                .eachCount()
                .map { (crop, count) -> BrapiFilterElement(id = crop, label = crop, count = count) }
                .sortedBy { it.label.lowercase() }
        }
    }
}

fun brapiImportSharedViewModelFactory() = viewModelFactory {
    initializer {
        BrapiImportSharedViewModel(settings = Settings())
    }
}
