package com.fieldbook.shared.screens.trait

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiTraitDetails
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.act_brapi_list_filter_reset_cache_message
import com.fieldbook.shared.generated.resources.act_brapi_list_filter_reset_cache_title
import com.fieldbook.shared.generated.resources.brapi_base_url_default
import com.fieldbook.shared.generated.resources.brapi_filter_type_crop_count
import com.fieldbook.shared.generated.resources.brapi_filter_type_study_count
import com.fieldbook.shared.generated.resources.brapi_filter_type_trial_count
import com.fieldbook.shared.generated.resources.dialog_brapi_filter_choices_title
import com.fieldbook.shared.generated.resources.dialog_cancel
import com.fieldbook.shared.generated.resources.filter_variant
import com.fieldbook.shared.generated.resources.import_source_brapi
import com.fieldbook.shared.generated.resources.lock_reset
import com.fieldbook.shared.generated.resources.menu_filter_brapi_reset_cache_title
import com.fieldbook.shared.generated.resources.results
import com.fieldbook.shared.generated.resources.search_bar_hint
import com.fieldbook.shared.theme.AlertDialog
import com.fieldbook.shared.theme.TextButton
import com.fieldbook.shared.traits.Formats
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraitBrapiScreen(
    onBack: (() -> Unit)? = null,
    onNavigateToFilter: ((BrapiTraitFilterType) -> Unit)? = null,
    viewModel: TraitEditorScreenViewModel = viewModel(
        factory = traitEditorScreenViewModelFactory()
    ),
) {
    val brapiTraits by viewModel.brapiTraits.collectAsState()
    val loading by viewModel.brapiLoading.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val filterSelections by viewModel.brapiTraitFilterSelections.collectAsState()
    val defaultBrapiBaseUrl = stringResource(Res.string.brapi_base_url_default)
    val snackbarHostState = remember { SnackbarHostState() }

    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showResetCacheDialog by remember { mutableStateOf(false) }
    var showFilterChoiceDialog by remember { mutableStateOf(false) }
    val filteredTraits = remember(brapiTraits, query, filterSelections) {
        viewModel.applyBrapiTraitFilters(brapiTraits, query, filterSelections)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(defaultBrapiBaseUrl) {
        if (brapiTraits.isEmpty()) {
            viewModel.loadBrapiTraits(defaultBrapiBaseUrl)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.import_source_brapi)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showResetCacheDialog = true },
                        enabled = !loading && !importing,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.lock_reset),
                            contentDescription = stringResource(Res.string.menu_filter_brapi_reset_cache_title),
                        )
                    }
                    IconButton(
                        onClick = { showFilterChoiceDialog = true },
                        enabled = !loading && !importing,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.filter_variant),
                            contentDescription = "Filter",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onBack?.invoke() },
                    enabled = !loading && !importing,
                ) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
                Button(
                    enabled = !loading && !importing && selectedIds.isNotEmpty(),
                    onClick = {
                        viewModel.importBrapiTraits(
                            selectedTraits = brapiTraits.filter { it.observationVariableDbId in selectedIds },
                            defaultBaseUrl = defaultBrapiBaseUrl,
                        )
                        onBack?.invoke()
                    },
                ) {
                    Text("Import")
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(
                            Res.string.search_bar_hint,
                            "${filteredTraits.size} ${stringResource(Res.string.results)}"
                        )
                    )
                },
            )

            when {
                loading || importing -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                brapiTraits.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "No BrAPI traits loaded",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                filteredTraits.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "No traits match the filter",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredTraits, key = { it.observationVariableDbId }) { trait ->
                            BrapiTraitRow(
                                trait = trait,
                                selected = trait.observationVariableDbId in selectedIds,
                                onSelectedChange = { selected ->
                                    selectedIds = if (selected) {
                                        selectedIds + trait.observationVariableDbId
                                    } else {
                                        selectedIds - trait.observationVariableDbId
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        if (showResetCacheDialog) {
            AlertDialog(
                onDismissRequest = { showResetCacheDialog = false },
                title = { Text(stringResource(Res.string.act_brapi_list_filter_reset_cache_title)) },
                text = { Text(stringResource(Res.string.act_brapi_list_filter_reset_cache_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showResetCacheDialog = false
                        viewModel.resetBrapiTraitCache(defaultBrapiBaseUrl)
                    }) {
                        Text(stringResource(Res.string.menu_filter_brapi_reset_cache_title))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetCacheDialog = false }) {
                        Text(stringResource(Res.string.dialog_cancel))
                    }
                }
            )
        }

        if (showFilterChoiceDialog) {
            BrapiFilterChoiceDialog(
                trialCount = viewModel.getBrapiTraitFilterElements(BrapiTraitFilterType.TRIAL).size,
                studyCount = viewModel.getBrapiTraitFilterElements(BrapiTraitFilterType.STUDY).size,
                cropCount = viewModel.getBrapiTraitFilterElements(BrapiTraitFilterType.CROP).size,
                onDismiss = { showFilterChoiceDialog = false },
                onSelect = { type ->
                    showFilterChoiceDialog = false
                    viewModel.setBrapiTraitFilterType(type)
                    onNavigateToFilter?.invoke(type)
                }
            )
        }
    }
}

@Composable
private fun BrapiFilterChoiceDialog(
    trialCount: Int,
    studyCount: Int,
    cropCount: Int,
    onDismiss: () -> Unit,
    onSelect: (BrapiTraitFilterType) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_brapi_filter_choices_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.brapi_filter_type_trial_count, trialCount.toString()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(BrapiTraitFilterType.TRIAL) }
                        .padding(vertical = 12.dp),
                )
                Text(
                    text = stringResource(Res.string.brapi_filter_type_study_count, studyCount.toString()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(BrapiTraitFilterType.STUDY) }
                        .padding(vertical = 12.dp),
                )
                Text(
                    text = stringResource(Res.string.brapi_filter_type_crop_count, cropCount.toString()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(BrapiTraitFilterType.CROP) }
                        .padding(vertical = 12.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun BrapiTraitRow(
    trait: BrapiTraitDetails,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectedChange(!selected) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = onSelectedChange,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trait.observationVariableName,
                style = MaterialTheme.typography.bodyMedium,
            )
            val detail = listOfNotNull(
                trait.commonCropName,
                trait.format,
                trait.observationVariableDbId,
            ).joinToString(" - ")
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Formats.findTrait(trait.format)?.iconDrawableResource?.let { icon ->
            Icon(
                painter = org.jetbrains.compose.resources.painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
