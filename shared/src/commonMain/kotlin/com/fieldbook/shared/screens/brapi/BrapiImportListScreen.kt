package com.fieldbook.shared.screens.brapi

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.act_brapi_list_filter_reset_cache_message
import com.fieldbook.shared.generated.resources.act_brapi_list_filter_reset_cache_title
import com.fieldbook.shared.generated.resources.dialog_brapi_filter_choices_title
import com.fieldbook.shared.generated.resources.dialog_cancel
import com.fieldbook.shared.generated.resources.filter_variant
import com.fieldbook.shared.generated.resources.lock_reset
import com.fieldbook.shared.generated.resources.menu_filter_brapi_reset_cache_title
import com.fieldbook.shared.generated.resources.results
import com.fieldbook.shared.generated.resources.search_bar_hint
import com.fieldbook.shared.theme.AlertDialog
import com.fieldbook.shared.theme.TextButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrapiImportListScreen(
    state: BrapiImportListUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (BrapiImportListEvent) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var showResetCacheDialog by remember { mutableStateOf(false) }
    var showFilterChoiceDialog by remember { mutableStateOf(false) }
    val busy = state.loading || state.importing

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
                actions = {
                    IconButton(
                        onClick = { showResetCacheDialog = true },
                        enabled = !busy,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.lock_reset),
                            contentDescription = stringResource(Res.string.menu_filter_brapi_reset_cache_title),
                        )
                    }
                    IconButton(
                        onClick = { showFilterChoiceDialog = true },
                        enabled = !busy,
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
                    enabled = !busy,
                ) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
                Button(
                    enabled = !busy && state.selectedIds.isNotEmpty(),
                    onClick = { onEvent(BrapiImportListEvent.ImportClicked) },
                ) {
                    Text(state.importButtonText)
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
                value = state.query,
                onValueChange = { onEvent(BrapiImportListEvent.QueryChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(
                            Res.string.search_bar_hint,
                            "${state.items.size} ${stringResource(Res.string.results)}"
                        )
                    )
                },
            )

            when {
                busy -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                state.totalItemCount == 0 -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = state.emptyMessage,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                state.items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = state.noMatchesMessage,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.items, key = { it.id }) { item ->
                            BrapiSelectableItemRow(
                                item = item,
                                selected = item.id in state.selectedIds,
                                onSelectedChange = { selected ->
                                    onEvent(BrapiImportListEvent.ItemSelectionChanged(item.id, selected))
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
                        onEvent(BrapiImportListEvent.ResetCacheConfirmed)
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
                choices = state.filterChoices,
                onDismiss = { showFilterChoiceDialog = false },
                onSelect = { choice ->
                    showFilterChoiceDialog = false
                    onEvent(BrapiImportListEvent.FilterChoiceSelected(choice.id))
                }
            )
        }
    }
}

@Composable
private fun BrapiFilterChoiceDialog(
    choices: List<BrapiFilterChoice>,
    onDismiss: () -> Unit,
    onSelect: (BrapiFilterChoice) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_brapi_filter_choices_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { choice ->
                    Text(
                        text = choice.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice) }
                            .padding(vertical = 12.dp),
                    )
                }
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
private fun BrapiSelectableItemRow(
    item: BrapiSelectableItem,
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
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.icon?.let { icon ->
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
