package com.fieldbook.shared.screens.brapi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.brapi_filter_type_crop
import com.fieldbook.shared.generated.resources.brapi_filter_type_study
import com.fieldbook.shared.generated.resources.brapi_filter_type_trial
import com.fieldbook.shared.generated.resources.dialog_cancel
import com.fieldbook.shared.screens.trait.BrapiFilterElement
import com.fieldbook.shared.screens.trait.BrapiTraitFilterType
import com.fieldbook.shared.screens.trait.TraitEditorScreenViewModel
import com.fieldbook.shared.screens.trait.traitEditorScreenViewModelFactory
import com.fieldbook.shared.theme.TextButton
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrapiTraitFilterScreen(
    onBack: (() -> Unit)? = null,
    viewModel: TraitEditorScreenViewModel = viewModel(
        factory = traitEditorScreenViewModelFactory()
    ),
) {
    val brapiTraits by viewModel.brapiTraits.collectAsState()
    val filterType by viewModel.brapiTraitFilterType.collectAsState()
    val selections by viewModel.brapiTraitFilterSelections.collectAsState()
    val elements = remember(filterType, brapiTraits) {
        viewModel.getBrapiTraitFilterElements(filterType)
    }

    BrapiFilterScreen(
        title = filterType.title(),
        elements = elements,
        selectedIds = selections[filterType].orEmpty(),
        onBack = onBack,
        onApply = { selectedIds ->
            viewModel.setBrapiTraitFilterSelection(filterType, selectedIds)
            onBack?.invoke()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrapiFilterScreen(
    title: String,
    elements: List<BrapiFilterElement>,
    selectedIds: Set<String>,
    onBack: (() -> Unit)? = null,
    onApply: (Set<String>) -> Unit,
) {

    var query by remember(title) { mutableStateOf("") }
    var currentSelectedIds by remember(elements, selectedIds) { mutableStateOf(selectedIds) }
    val filteredElements = remember(elements, query) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            elements
        } else {
            elements.filter { element ->
                element.label.lowercase().contains(normalizedQuery) ||
                    element.id.lowercase().contains(normalizedQuery)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
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
                TextButton(onClick = { currentSelectedIds = emptySet() }) {
                    Text("Clear")
                }
                TextButton(onClick = { onBack?.invoke() }) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
                TextButton(
                    onClick = {
                        onApply(currentSelectedIds)
                    }
                ) {
                    Text("Apply")
                }
            }
        }
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
                label = { Text("Filter") },
            )

            when {
                elements.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "No filter values",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                filteredElements.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "No filter values match",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredElements, key = { it.id }) { element ->
                            BrapiFilterElementRow(
                                element = element,
                                selected = element.id in currentSelectedIds,
                                onSelectedChange = { selected ->
                                    currentSelectedIds = if (selected) {
                                        currentSelectedIds + element.id
                                    } else {
                                        currentSelectedIds - element.id
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
private fun BrapiFilterElementRow(
    element: BrapiFilterElement,
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
                text = element.label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${element.count} results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrapiTraitFilterType.title(): String {
    return when (this) {
        BrapiTraitFilterType.TRIAL -> stringResource(Res.string.brapi_filter_type_trial)
        BrapiTraitFilterType.STUDY -> stringResource(Res.string.brapi_filter_type_study)
        BrapiTraitFilterType.CROP -> stringResource(Res.string.brapi_filter_type_crop)
    }
}
