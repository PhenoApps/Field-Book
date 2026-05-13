package com.fieldbook.shared.screens.brapi.field

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.act_brapi_filter_import
import com.fieldbook.shared.generated.resources.brapi_base_url_default
import com.fieldbook.shared.generated.resources.brapi_filter_type_crop_count
import com.fieldbook.shared.generated.resources.brapi_filter_type_season_count
import com.fieldbook.shared.generated.resources.brapi_filter_type_study_count
import com.fieldbook.shared.generated.resources.brapi_filter_type_trial_count
import com.fieldbook.shared.generated.resources.brapi_studies_filter_title
import com.fieldbook.shared.screens.brapi.BrapiFilterChoice
import com.fieldbook.shared.screens.brapi.BrapiFilterType
import com.fieldbook.shared.screens.brapi.BrapiImportListEvent
import com.fieldbook.shared.screens.brapi.BrapiImportListScreen
import com.fieldbook.shared.screens.brapi.BrapiImportListUiState
import com.fieldbook.shared.screens.brapi.BrapiImportSharedViewModel
import com.fieldbook.shared.screens.brapi.BrapiSelectableItem
import com.fieldbook.shared.screens.brapi.brapiImportSharedViewModelFactory
import com.fieldbook.shared.screens.brapi.title
import org.jetbrains.compose.resources.stringResource

@Composable
fun BrapiFieldImportScreen(
    onBack: (() -> Unit)? = null,
    onNavigateToFilter: (() -> Unit)? = null,
    onStudySelected: (() -> Unit)? = null,
    onSnackbarMessage: (String) -> Unit,
    sharedViewModel: BrapiImportSharedViewModel = viewModel(
        factory = brapiImportSharedViewModelFactory()
    ),
    viewModel: BrapiFieldImportViewModel = viewModel(
        factory = brapiFieldImportViewModelFactory()
    ),
) {
    val loading by sharedViewModel.loading.collectAsState()
    val studies by sharedViewModel.studies.collectAsState()
    val filterSelections by sharedViewModel.filterSelections.collectAsState()
    val defaultBrapiBaseUrl = stringResource(Res.string.brapi_base_url_default)
    val seasonFilterTitle = BrapiFilterType.SEASON.title()
    val trialFilterTitle = BrapiFilterType.TRIAL.title()
    val studyFilterTitle = BrapiFilterType.STUDY.title()
    val cropFilterTitle = BrapiFilterType.CROP.title()
    var query by remember { mutableStateOf("") }
    var selectedStudyDbId by remember { mutableStateOf<String?>(null) }

    val filteredStudies = remember(studies, query, filterSelections) {
        studies.applyFieldImportFilters(query, filterSelections)
    }

    LaunchedEffect(filteredStudies, selectedStudyDbId) {
        if (selectedStudyDbId != null && filteredStudies.none { it.studyDbId == selectedStudyDbId }) {
            selectedStudyDbId = null
        }
    }

    LaunchedEffect(sharedViewModel) {
        sharedViewModel.messages.collect { message ->
            onSnackbarMessage(message)
        }
    }

    LaunchedEffect(defaultBrapiBaseUrl) {
        if (studies.isEmpty()) {
            sharedViewModel.restoreFilterModels(defaultBrapiBaseUrl)
        }
    }

    BrapiImportListScreen(
        state = BrapiImportListUiState(
            title = stringResource(Res.string.brapi_studies_filter_title),
            query = query,
            totalItemCount = studies.size,
            items = filteredStudies.map(BrapiStudyDetails::toBrapiSelectableItem),
            selectedIds = selectedStudyDbId?.let { setOf(it) }.orEmpty(),
            loading = loading,
            importing = false,
            filterChoices = listOf(
                BrapiFilterChoice(
                    id = BrapiFilterType.SEASON.name,
                    label = stringResource(
                        Res.string.brapi_filter_type_season_count,
                        sharedViewModel.getFilterElements(BrapiFilterType.SEASON).size.toString()
                    ),
                    selectedElements = sharedViewModel.getSelectedFilterElements(
                        type = BrapiFilterType.SEASON,
                        selections = filterSelections,
                    ),
                ),
                BrapiFilterChoice(
                    id = BrapiFilterType.TRIAL.name,
                    label = stringResource(
                        Res.string.brapi_filter_type_trial_count,
                        sharedViewModel.getFilterElements(BrapiFilterType.TRIAL).size.toString()
                    ),
                    selectedElements = sharedViewModel.getSelectedFilterElements(
                        type = BrapiFilterType.TRIAL,
                        selections = filterSelections,
                    ),
                ),
                BrapiFilterChoice(
                    id = BrapiFilterType.STUDY.name,
                    label = stringResource(
                        Res.string.brapi_filter_type_study_count,
                        sharedViewModel.getFilterElements(BrapiFilterType.STUDY).size.toString()
                    ),
                    selectedElements = sharedViewModel.getSelectedFilterElements(
                        type = BrapiFilterType.STUDY,
                        selections = filterSelections,
                    ),
                ),
                BrapiFilterChoice(
                    id = BrapiFilterType.CROP.name,
                    label = stringResource(
                        Res.string.brapi_filter_type_crop_count,
                        sharedViewModel.getFilterElements(BrapiFilterType.CROP).size.toString()
                    ),
                    selectedElements = sharedViewModel.getSelectedFilterElements(
                        type = BrapiFilterType.CROP,
                        selections = filterSelections,
                    ),
                ),
            ),
            emptyMessage = "No BrAPI studies loaded",
            noMatchesMessage = "No studies match the filter",
            importButtonText = stringResource(Res.string.act_brapi_filter_import),
        ),
        onEvent = { event ->
            when (event) {
                is BrapiImportListEvent.QueryChanged -> query = event.query
                is BrapiImportListEvent.ItemSelectionChanged -> {
                    selectedStudyDbId = if (event.selected) event.id else null
                }

                BrapiImportListEvent.ImportClicked -> {
                    studies.firstOrNull { it.studyDbId == selectedStudyDbId }
                        ?.let { study ->
                            viewModel.setSelectedStudy(study)
                            onStudySelected?.invoke()
                        }
                }

                BrapiImportListEvent.ResetCacheConfirmed -> {
                    selectedStudyDbId = null
                    sharedViewModel.resetCache(defaultBrapiBaseUrl)
                }

                BrapiImportListEvent.ClearFiltersClicked -> {
                    selectedStudyDbId = null
                    sharedViewModel.clearFilterSelections()
                }

                is BrapiImportListEvent.FilterBadgeRemoved -> {
                    selectedStudyDbId = null
                    sharedViewModel.removeFilterSelection(event.filterId, event.elementId)
                }

                is BrapiImportListEvent.FilterChoiceSelected -> {
                    val filterType = BrapiFilterType.valueOf(event.id)
                    sharedViewModel.setFilterContext(
                        id = filterType.name,
                        title = when (filterType) {
                            BrapiFilterType.SEASON -> seasonFilterTitle
                            BrapiFilterType.TRIAL -> trialFilterTitle
                            BrapiFilterType.STUDY -> studyFilterTitle
                            BrapiFilterType.CROP -> cropFilterTitle
                        },
                        elements = sharedViewModel.getFilterElements(filterType),
                    )
                    onNavigateToFilter?.invoke()
                }
            }
        },
        onBack = onBack,
    )
}

private fun List<BrapiStudyDetails>.applyFieldImportFilters(
    query: String,
    selections: Map<String, Set<String>>,
): List<BrapiStudyDetails> {
    val normalizedQuery = query.trim().lowercase()
    val trialIds = selections[BrapiFilterType.TRIAL.name].orEmpty()
    val studyIds = selections[BrapiFilterType.STUDY.name].orEmpty()
    val cropIds = selections[BrapiFilterType.CROP.name].orEmpty()
    val seasonIds = selections[BrapiFilterType.SEASON.name].orEmpty()

    return filter { study ->
        (trialIds.isEmpty() || study.trialDbId in trialIds) &&
            (studyIds.isEmpty() || study.studyDbId in studyIds) &&
            (cropIds.isEmpty() || study.commonCropName in cropIds) &&
            (seasonIds.isEmpty() || study.seasons.any { it in seasonIds })
    }.filter { study ->
        normalizedQuery.isBlank() ||
            study.studyName.orEmpty().lowercase().contains(normalizedQuery) ||
            study.studyDbId.lowercase().contains(normalizedQuery) ||
            study.locationName.orEmpty().lowercase().contains(normalizedQuery) ||
            study.trialName.orEmpty().lowercase().contains(normalizedQuery) ||
            study.commonCropName.orEmpty().lowercase().contains(normalizedQuery)
    }
}

private fun BrapiStudyDetails.toBrapiSelectableItem(): BrapiSelectableItem {
    return BrapiSelectableItem(
        id = studyDbId,
        title = studyName?.takeIf(String::isNotBlank) ?: studyDbId,
        description = listOfNotNull(
            locationName,
            trialName,
            commonCropName,
            studyDbId,
        ).joinToString(" - "),
    )
}
