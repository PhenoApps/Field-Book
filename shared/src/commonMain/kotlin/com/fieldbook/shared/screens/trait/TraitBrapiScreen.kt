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
import com.fieldbook.shared.generated.resources.brapi_base_url_default
import com.fieldbook.shared.generated.resources.dialog_cancel
import com.fieldbook.shared.generated.resources.import_source_brapi
import com.fieldbook.shared.theme.TextButton
import com.fieldbook.shared.traits.Formats
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraitBrapiScreen(
    onBack: (() -> Unit)? = null,
    viewModel: TraitEditorScreenViewModel = viewModel(
        factory = traitEditorScreenViewModelFactory()
    ),
) {
    val brapiTraits by viewModel.brapiTraits.collectAsState()
    val loading by viewModel.brapiLoading.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val defaultBrapiBaseUrl = stringResource(Res.string.brapi_base_url_default)
    val snackbarHostState = remember { SnackbarHostState() }

    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val filteredTraits = remember(brapiTraits, query) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            brapiTraits
        } else {
            brapiTraits.filter { trait ->
                trait.observationVariableName.lowercase().contains(normalizedQuery) ||
                    trait.observationVariableDbId.lowercase().contains(normalizedQuery) ||
                    trait.commonCropName.orEmpty().lowercase().contains(normalizedQuery)
            }
        }
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
                    TextButton(
                        onClick = { viewModel.loadBrapiTraits(defaultBrapiBaseUrl, forceRefresh = true) },
                        enabled = !loading && !importing,
                    ) {
                        Text("Reload")
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
                label = { Text("Filter traits") },
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
    }
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
