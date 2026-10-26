package com.fieldbook.tracker.ui.dialogs.sort

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.components.widgets.HorizontalDivide
import com.fieldbook.tracker.ui.dialogs.builder.AppAlertDialog
import com.fieldbook.tracker.ui.dialogs.components.OptionsDialogListItem
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.sort.FieldSortKey
import com.fieldbook.tracker.viewmodels.FieldSortViewModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val PREVIEW_CELL_PADDING = 8.dp
private val PREVIEW_MAX_CELL_WIDTH = 160.dp

/**
 * Edits a field's entry sort: an ordered list of columns, each with a direction and an optional serpentine flag,
 * above a table previewing the resulting entry order.
 */
@Composable
fun FieldSortDialog(
    keys: List<FieldSortKey>,
    state: FieldSortViewModel.UiState,
    onAddKey: (String) -> Unit,
    onRemoveKey: (Int) -> Unit,
    onMoveKey: (Int, Int) -> Unit,
    onToggleDirection: (Int) -> Unit,
    onToggleSerpentine: (Int) -> Unit,
    onClearKeys: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AppAlertDialog(
        title = stringResource(R.string.dialog_field_sort_title),
        content = {
            when {
                state.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                state.attributes.isEmpty() -> Text(
                    text = stringResource(R.string.dialog_field_sort_no_attributes_found),
                    style = AppTheme.typography.bodyStyle,
                    color = AppTheme.colors.text.primary
                )

                else -> FieldSortContent(
                    keys = keys,
                    state = state,
                    onAddKey = onAddKey,
                    onRemoveKey = onRemoveKey,
                    onMoveKey = onMoveKey,
                    onToggleDirection = onToggleDirection,
                    onToggleSerpentine = onToggleSerpentine,
                )
            }
        },
        positiveButtonText = stringResource(R.string.dialog_save),
        onPositive = onConfirm.takeIf { !state.isLoading },
        negativeButtonText = stringResource(R.string.dialog_cancel),
        onNegative = onCancel,
        neutralButtonText = stringResource(R.string.field_sort_clear),
        onNeutral = onClearKeys.takeIf { keys.isNotEmpty() },
    )
}

@Composable
private fun FieldSortContent(
    keys: List<FieldSortKey>,
    state: FieldSortViewModel.UiState,
    onAddKey: (String) -> Unit,
    onRemoveKey: (Int) -> Unit,
    onMoveKey: (Int, Int) -> Unit,
    onToggleDirection: (Int) -> Unit,
    onToggleSerpentine: (Int) -> Unit,
) {
    var showAttributePicker by remember { mutableStateOf(false) }

    val unusedAttributes = state.attributes.filter { name -> keys.none { it.column == name } }

    //the sort columns and the preview scroll separately, so a long preview never pushes the columns out of view
    Column(modifier = Modifier.fillMaxWidth()) {

        if (keys.isNotEmpty()) {
            SortKeyList(
                keys = keys,
                onRemoveKey = onRemoveKey,
                onMoveKey = onMoveKey,
                onToggleDirection = onToggleDirection,
                onToggleSerpentine = onToggleSerpentine,
                modifier = Modifier.heightIn(max = 220.dp)
            )
        }

        //Material's default disabled color is a faint gray, too low contrast on the high contrast theme
        TextButton(
            onClick = { showAttributePicker = true },
            enabled = unusedAttributes.isNotEmpty(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = AppTheme.colors.text.button,
                disabledContentColor = AppTheme.colors.text.secondary
            )
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (unusedAttributes.isEmpty()) R.string.dialog_field_sort_no_more_attributes_found
                    else R.string.field_sort_add_column
                ),
                style = AppTheme.typography.bodyStyle
            )
        }

        Text(
            text = stringResource(R.string.field_sort_preview),
            style = AppTheme.typography.bodyStyle,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.text.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        PreviewTable(
            columns = state.previewColumns,
            rows = state.preview,
            hiddenCount = state.entryCount - state.preview.size,
            modifier = Modifier.weight(1f, fill = false)
        )
    }

    if (showAttributePicker) {
        AttributePickerDialog(
            attributes = unusedAttributes,
            onSelect = { name ->
                showAttributePicker = false
                onAddKey(name)
            },
            onDismiss = { showAttributePicker = false }
        )
    }
}

@Composable
private fun SortKeyList(
    keys: List<FieldSortKey>,
    onRemoveKey: (Int) -> Unit,
    onMoveKey: (Int, Int) -> Unit,
    onToggleDirection: (Int) -> Unit,
    onToggleSerpentine: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMoveKey(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(keys, key = { _, key -> key.column }) { index, key ->
            ReorderableItem(reorderableState, key = key.column) { isDragging ->
                SortKeyRow(
                    index = index,
                    key = key,
                    isDragging = isDragging,
                    onToggleDirection = { onToggleDirection(index) },
                    onToggleSerpentine = { onToggleSerpentine(index) },
                    onRemove = { onRemoveKey(index) },
                )
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.SortKeyRow(
    index: Int,
    key: FieldSortKey,
    isDragging: Boolean,
    onToggleDirection: () -> Unit,
    onToggleSerpentine: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)
    val iconTint = AppTheme.colors.surface.iconTint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = AppTheme.colors.background, shape = shape)
            .border(
                width = if (isDragging) 2.dp else 1.dp,
                color = if (isDragging) AppTheme.colors.interactive.accent else AppTheme.colors.surface.border,
                shape = shape
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.list_item_field_sort_drag_content_description),
            tint = iconTint,
            modifier = Modifier
                .draggableHandle()
                .padding(12.dp)
        )

        Text(
            text = key.column,
            style = AppTheme.typography.bodyStyle,
            color = AppTheme.colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onToggleDirection) {
            Icon(
                painterResource(if (key.ascending) R.drawable.sort_ascending else R.drawable.sort_descending),
                contentDescription = stringResource(R.string.dialog_field_sort_toggle_order_content_description),
                tint = iconTint
            )
        }

        //serpentine alternates relative to the column above, so the first column has no toggle
        //checked fills the button rather than recoloring the icon, so the state reads in every theme including high contrast
        if (index > 0) {
            IconToggleButton(
                checked = key.serpentine,
                onCheckedChange = { onToggleSerpentine() },
                colors = IconButtonDefaults.iconToggleButtonColors(
                    contentColor = iconTint,
                    checkedContainerColor = AppTheme.colors.interactive.accent,
                    checkedContentColor = AppTheme.colors.interactive.onAccent
                )
            ) {
                Icon(
                    painterResource(R.drawable.ic_serpentine_pattern),
                    contentDescription = stringResource(R.string.field_sort_serpentine_content_description)
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }

        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.list_item_field_sort_delete_content_description),
                tint = iconTint
            )
        }
    }
}

@Composable
private fun AttributePickerDialog(
    attributes: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        title = stringResource(R.string.field_sort_add_column),
        content = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(attributes) { index, name ->
                    OptionsDialogListItem(
                        title = name,
                        onClick = { onSelect(name) }
                    )

                    if (index < attributes.size - 1) {
                        HorizontalDivide(modifier = Modifier.padding(horizontal = 10.dp))
                    }
                }
            }
        },
        negativeButtonText = stringResource(R.string.dialog_cancel),
        onNegative = onDismiss,
    )
}

/**
 * The first entries in sort order, one column per header.
 * Each column is as wide as its widest header or value, up to a cap past which values are ellipsized.
 * Columns that fit share out the remaining width, and a table too wide for the dialog scrolls sideways with its header.
 * Alternate groups of the first sort column are shaded so e.g. serpentine rows stand out.
 * @param hiddenCount entries left out of the preview, noted at the end of the rows
 */
@Composable
private fun PreviewTable(
    columns: List<String>,
    rows: List<FieldSortViewModel.PreviewRow>,
    hiddenCount: Int,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val bodyStyle = AppTheme.typography.bodyStyle
    val headerStyle = bodyStyle.copy(fontWeight = FontWeight.Bold)

    val contentWidths = remember(columns, rows, bodyStyle, density) {
        columns.indices.map { column ->
            val widest = rows.maxOfOrNull { textMeasurer.measure(it.values[column], bodyStyle, maxLines = 1).size.width } ?: 0
            val header = textMeasurer.measure(columns[column], headerStyle, maxLines = 1).size.width
            with(density) { maxOf(widest, header).toDp() } + PREVIEW_CELL_PADDING * 2
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {

        val capped = contentWidths.map { minOf(it, PREVIEW_MAX_CELL_WIDTH) }
        val total = capped.fold(0.dp) { sum, width -> sum + width }
        val extra = if (capped.isNotEmpty() && total < maxWidth) (maxWidth - total) / capped.size else 0.dp
        val cellWidths = capped.map { it + extra }
        val tableWidth = cellWidths.fold(0.dp) { sum, width -> sum + width }

        //the theme's selected item color rather than a translucent gray, which washes out on the high contrast theme
        val shading = AppTheme.colors.interactive.selectedItemBackground

        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {

            PreviewTableRow(
                values = columns,
                cellWidths = cellWidths,
                style = headerStyle,
            )

            HorizontalDivide(
                modifier = Modifier.width(tableWidth),
                color = AppTheme.colors.surface.border
            )

            LazyColumn {
                items(rows, key = { it.position }) { row ->
                    PreviewTableRow(
                        values = row.values,
                        cellWidths = cellWidths,
                        style = bodyStyle,
                        modifier = if (row.groupIndex % 2 == 1) Modifier.background(shading) else Modifier
                    )
                }

                if (hiddenCount > 0) {
                    item(key = "more") {
                        Text(
                            text = stringResource(R.string.field_sort_more_entries, hiddenCount),
                            style = bodyStyle,
                            color = AppTheme.colors.text.secondary,
                            modifier = Modifier
                                .width(tableWidth)
                                .padding(vertical = 6.dp, horizontal = PREVIEW_CELL_PADDING)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTableRow(
    values: List<String>,
    cellWidths: List<Dp>,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        values.forEachIndexed { index, value ->
            Text(
                text = value,
                style = style,
                color = AppTheme.colors.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(cellWidths.getOrElse(index) { PREVIEW_MAX_CELL_WIDTH })
                    .padding(vertical = 6.dp, horizontal = PREVIEW_CELL_PADDING)
            )
        }
    }
}

@Preview
@Composable
private fun FieldSortDialogPreview() {
    AppTheme {
        FieldSortDialog(
            keys = listOf(FieldSortKey("Row"), FieldSortKey("Column", serpentine = true)),
            state = FieldSortViewModel.UiState(
                isLoading = false,
                attributes = listOf("Column", "Plot", "Row", "plot_id"),
                previewColumns = listOf("plot_id", "Row", "Column"),
                preview = listOf(
                    FieldSortViewModel.PreviewRow(1, listOf("plot_1", "1", "1"), 0),
                    FieldSortViewModel.PreviewRow(2, listOf("plot_2", "1", "2"), 0),
                    FieldSortViewModel.PreviewRow(3, listOf("plot_4", "2", "2"), 1),
                    FieldSortViewModel.PreviewRow(4, listOf("plot_3", "2", "1"), 1),
                ),
                entryCount = 4,
            ),
            onAddKey = { },
            onRemoveKey = { },
            onMoveKey = { _, _ -> },
            onToggleDirection = { },
            onToggleSerpentine = { },
            onClearKeys = { },
            onConfirm = { },
            onCancel = { },
        )
    }
}
