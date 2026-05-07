package com.fieldbook.shared.screens.datagrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.theme.AlertDialog
import com.fieldbook.shared.theme.TextButton
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.lazyTableDimensions
import eu.wewox.lazytable.lazyTablePinConfiguration
import eu.wewox.lazytable.rememberSaveableLazyTableState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataGridScreen(
    modifier: Modifier = Modifier,
    activePlotIndex: Int? = null,
    activeTraitIndex: Int? = null,
    repository: DataGridRepository = remember { DataGridRepository() },
    onBack: (() -> Unit)? = null,
    onSelection: (DataGridSelection) -> Unit = {},
) {
    val activeCell = remember(activePlotIndex, activeTraitIndex) {
        if (activePlotIndex != null && activeTraitIndex != null) {
            DataGridCellKey(activePlotIndex, activeTraitIndex)
        } else {
            null
        }
    }
    var reloadKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(DataGridState(activeCell = activeCell)) }
    var showHeaderPicker by remember { mutableStateOf(false) }

    LaunchedEffect(repository, activeCell, reloadKey) {
        state = DataGridState(activeCell = activeCell)
        state = withContext(Dispatchers.Default) {
            repository.load(activeCell)
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {},
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
                        onClick = { showHeaderPicker = true },
                        enabled = state.availableRowHeaders.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Choose row header"
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

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error.orEmpty(), textAlign = TextAlign.Center)
                }

                state.traits.isEmpty() || state.rows.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No grid data.")
                }

                else -> DataGridTable(
                    state = state,
                    onSelection = onSelection,
                )
            }
        }
    }

    if (showHeaderPicker) {
        HeaderPickerDialog(
            headers = state.availableRowHeaders,
            selectedHeader = state.rowHeaderName,
            onDismiss = { showHeaderPicker = false },
            onHeaderSelected = { header ->
                repository.setRowHeader(header)
                showHeaderPicker = false
                reloadKey += 1
            }
        )
    }
}

@Composable
private fun DataGridTable(
    state: DataGridState,
    onSelection: (DataGridSelection) -> Unit,
) {
    val tableState = rememberSaveableLazyTableState()
    val columnCount = state.traits.size + 1
    val rowCount = state.rows.size + 1

    LaunchedEffect(state.rows, state.traits, state.activeCell) {
        val active = state.activeCell ?: return@LaunchedEffect
        if (active.rowIndexOneBased in 1 until rowCount &&
            active.traitIndexOneBased in 1 until columnCount
        ) {
            tableState.animateToCell(
                column = active.traitIndexOneBased,
                row = active.rowIndexOneBased
            )
        }
    }

    LazyTable(
        state = tableState,
        dimensions = lazyTableDimensions(
            columnSize = { column -> if (column == 0) 128.dp else 104.dp },
            rowSize = { 48.dp }
        ),
        contentPadding = PaddingValues(0.dp),
        pinConfiguration = lazyTablePinConfiguration(columns = 1, rows = 1),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(
            count = columnCount,
            layoutInfo = { LazyTableItem(column = it, row = 0) }
        ) { index ->
            val text = if (index == 0) {
                state.rowHeaderName
            } else {
                state.traits.getOrNull(index - 1)?.name.orEmpty()
            }
            HeaderCell(text = text)
        }

        items(
            count = (rowCount - 1) * columnCount,
            layoutInfo = {
                LazyTableItem(
                    column = it % columnCount,
                    row = (it / columnCount) + 1
                )
            }
        ) { index ->
            val rowIndex = index / columnCount
            val column = index % columnCount
            val row = state.rows.getOrNull(rowIndex)

            if (column == 0) {
                HeaderCell(text = row?.header.orEmpty())
            } else {
                val traitIndex = column - 1
                val trait = state.traits.getOrNull(traitIndex)
                val cell = row?.cells?.getOrNull(traitIndex)
                val activeCell = state.activeCell
                DataCell(
                    value = cell?.value.orEmpty(),
                    highlighted = activeCell == DataGridCellKey(rowIndex + 1, traitIndex + 1),
                    onClick = {
                        if (row != null && cell != null) {
                            onSelection(
                                DataGridSelection(
                                    plotId = row.plotId,
                                    traitIndex = traitIndex,
                                    traitId = trait?.id,
                                    rep = 1,
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HeaderPickerDialog(
    headers: List<String>,
    selectedHeader: String,
    onDismiss: () -> Unit,
    onHeaderSelected: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Row Header") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .heightIn(max = 420.dp)
            ) {
                itemsIndexed(headers) { _, header ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onHeaderSelected(header) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = header == selectedHeader,
                                onClick = { onHeaderSelected(header) }
                            )
                            Text(header)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun HeaderCell(text: String) {
    TableCell(
        text = text,
        backgroundColor = MaterialTheme.colorScheme.surface,
        textColor = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun DataCell(
    value: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val backgroundColor = when {
        highlighted -> colors.primary
        value.isNotBlank() -> colors.primaryContainer
        else -> colors.surfaceVariant
    }
    val textColor = if (highlighted) colors.onPrimary else colors.onSurface

    TableCell(
        text = value,
        backgroundColor = backgroundColor,
        textColor = textColor,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun TableCell(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(backgroundColor)
            .border(Dp.Hairline, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            color = textColor,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }
}
