package com.fieldbook.tracker.ui.grid.datagrid

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun DataCell(
    value: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit = {},
) {
    val datagridColors = AppTheme.colors.dataVisualization.dataGrid
    val backgroundColor = when {
        isHighlighted -> datagridColors.activeCell
        value.isNotBlank() -> datagridColors.dataFilled
        else -> datagridColors.emptyCell
    }

    val textColor = if (isHighlighted) datagridColors.activeCellText else datagridColors.cellText

    TableCell(
        text = value,
        backgroundColor = backgroundColor,
        textColor = textColor,
        onClick = onClick,
        isClickable = true
    )
}

@Preview(showBackground = true)
@Composable
private fun DataCellPreview() {
    AppTheme {
        Column {
            DataCell(
                value = "Data filled, Active cell",
                isHighlighted = true,
                onClick = { },
            )
            DataCell(
                value = "Data filled, Inactive cell",
                isHighlighted = false,
                onClick = { },
            )
        }
    }
}