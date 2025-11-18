package com.fieldbook.tracker.ui.grid.datagrid

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.ui.theme.AppTheme

/**
 * This is used for the first row in the data grid
 */
@Composable
fun HeaderCell(text: String) {
    TableCell(
        text = text,
        backgroundColor = Color.White,
        textColor = AppTheme.colors.dataVisualization.dataGrid.cellText,
    )
}

@Preview(showBackground = true)
@Composable
private fun HeaderCellPreview() {
    AppTheme {
        HeaderCell("height")
    }
}