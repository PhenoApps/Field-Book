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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.act_brapi_filter_apply
import com.fieldbook.shared.generated.resources.act_brapi_filter_clear
import com.fieldbook.shared.generated.resources.dialog_cancel
import com.fieldbook.shared.theme.TextButton
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrapiFilterScreen(
    state: BrapiFilterUiState,
    onBack: (() -> Unit)? = null,
    onApply: (Set<String>) -> Unit,
) {

    var query by remember(state.title) { mutableStateOf("") }
    var currentSelectedIds by remember(state.elements, state.selectedIds) { mutableStateOf(state.selectedIds) }
    val filteredElements = remember(state.elements, query) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            state.elements
        } else {
            state.elements.filter { element ->
                element.label.lowercase().contains(normalizedQuery) ||
                    element.id.lowercase().contains(normalizedQuery)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
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
                    Text(stringResource(Res.string.act_brapi_filter_clear))
                }
                TextButton(onClick = { onBack?.invoke() }) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
                TextButton(
                    onClick = {
                        onApply(currentSelectedIds)
                    }
                ) {
                    Text(stringResource(Res.string.act_brapi_filter_apply))
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
                state.elements.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = state.emptyMessage,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                filteredElements.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = state.noMatchesMessage,
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
