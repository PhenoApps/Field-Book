package com.fieldbook.shared.screens.brapi.trait

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiTraitDetails
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.act_brapi_filter_import
import com.fieldbook.shared.generated.resources.brapi_base_url_default
import com.fieldbook.shared.generated.resources.brapi_filter_type_crop_count
import com.fieldbook.shared.generated.resources.brapi_filter_type_study_count
import com.fieldbook.shared.generated.resources.brapi_filter_type_trial_count
import com.fieldbook.shared.generated.resources.import_source_brapi
import com.fieldbook.shared.screens.brapi.BrapiFilterChoice
import com.fieldbook.shared.screens.brapi.BrapiFilterType
import com.fieldbook.shared.screens.brapi.BrapiImportListEvent
import com.fieldbook.shared.screens.brapi.BrapiImportListScreen
import com.fieldbook.shared.screens.brapi.BrapiImportListUiState
import com.fieldbook.shared.screens.brapi.BrapiImportSharedViewModel
import com.fieldbook.shared.screens.brapi.BrapiSelectableItem
import com.fieldbook.shared.screens.brapi.brapiImportSharedViewModelFactory
import com.fieldbook.shared.screens.brapi.title
import com.fieldbook.shared.traits.Formats
import org.jetbrains.compose.resources.stringResource

@Composable
fun BrapiTraitImportScreen(
    onBack: (() -> Unit)? = null,
    onNavigateToFilter: (() -> Unit)? = null,
    onImportComplete: (() -> Unit)? = null,
    sharedViewModel: BrapiImportSharedViewModel = viewModel(
        factory = brapiImportSharedViewModelFactory()
    ),
    viewModel: BrapiTraitImportViewModel = viewModel(
        factory = brapiTraitImportViewModelFactory()
    ),
) {
    val brapiTraits by viewModel.brapiTraits.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val query by viewModel.query.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val filterLoading by sharedViewModel.loading.collectAsState()
    val studies by sharedViewModel.studies.collectAsState()
    val filterSelections by sharedViewModel.filterSelections.collectAsState()
    val defaultBrapiBaseUrl = stringResource(Res.string.brapi_base_url_default)
    val snackbarHostState = remember { SnackbarHostState() }
    val trialFilterTitle = BrapiFilterType.TRIAL.title()
    val studyFilterTitle = BrapiFilterType.STUDY.title()
    val cropFilterTitle = BrapiFilterType.CROP.title()

    val filteredTraits = remember(brapiTraits, query, filterSelections) {
        viewModel.applyFilters(brapiTraits, query, filterSelections, studies)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.importCompleted.collect {
            onImportComplete?.invoke()
        }
    }

    LaunchedEffect(sharedViewModel) {
        sharedViewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(defaultBrapiBaseUrl) {
        if (studies.isEmpty()) {
            sharedViewModel.restoreFilterModels(defaultBrapiBaseUrl)
        }
        if (brapiTraits.isEmpty()) {
            viewModel.loadTraits(defaultBrapiBaseUrl)
        }
    }

    BrapiImportListScreen(
        state = BrapiImportListUiState(
            title = stringResource(Res.string.import_source_brapi),
            query = query,
            totalItemCount = brapiTraits.size,
            items = filteredTraits.map(BrapiTraitDetails::toBrapiSelectableItem),
            selectedIds = selectedIds,
            loading = loading || filterLoading,
            importing = importing,
            filterChoices = listOf(
                BrapiFilterChoice(
                    id = BrapiFilterType.TRIAL.name,
                    label = stringResource(
                        Res.string.brapi_filter_type_trial_count,
                        sharedViewModel.getFilterElements(BrapiFilterType.TRIAL).size.toString()
                    ),
                ),
                BrapiFilterChoice(
                    id = BrapiFilterType.STUDY.name,
                    label = stringResource(
                        Res.string.brapi_filter_type_study_count,
                        sharedViewModel.getFilterElements(BrapiFilterType.STUDY).size.toString()
                    ),
                ),
                BrapiFilterChoice(
                    id = BrapiFilterType.CROP.name,
                    label = stringResource(
                        Res.string.brapi_filter_type_crop_count,
                        sharedViewModel.getFilterElements(BrapiFilterType.CROP).size.toString()
                    ),
                ),
            ),
            emptyMessage = "No BrAPI traits loaded",
            noMatchesMessage = "No traits match the filter",
            importButtonText = stringResource(Res.string.act_brapi_filter_import),
        ),
        snackbarHostState = snackbarHostState,
        onEvent = { event ->
            when (event) {
                is BrapiImportListEvent.QueryChanged -> viewModel.setQuery(event.query)
                is BrapiImportListEvent.ItemSelectionChanged -> viewModel.setItemSelected(event.id, event.selected)

                BrapiImportListEvent.ImportClicked -> {
                    viewModel.importSelectedTraits(defaultBrapiBaseUrl)
                }

                BrapiImportListEvent.ResetCacheConfirmed -> {
                    sharedViewModel.resetCache(defaultBrapiBaseUrl)
                    viewModel.clearAndReloadTraits(defaultBrapiBaseUrl)
                }

                is BrapiImportListEvent.FilterChoiceSelected -> {
                    val filterType = BrapiFilterType.valueOf(event.id)
                    sharedViewModel.setFilterContext(
                        id = filterType.name,
                        title = when (filterType) {
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

private fun BrapiTraitDetails.toBrapiSelectableItem(): BrapiSelectableItem {
    return BrapiSelectableItem(
        id = observationVariableDbId,
        title = observationVariableName,
        description = listOfNotNull(
            commonCropName,
            format,
            observationVariableDbId,
        ).joinToString(" - "),
        icon = Formats.findTrait(format)?.iconDrawableResource,
    )
}
