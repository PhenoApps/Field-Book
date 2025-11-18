package com.fieldbook.tracker.ui.grid.datagrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun TableCell(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit = {},
    isClickable: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(backgroundColor)
            .border(
                width = Dp.Hairline,
                color = AppTheme.colors.dataVisualization.dataGrid.cellText,
            )
            .then(if (isClickable) Modifier.clickable(onClick = onClick) else Modifier)
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

@Preview(showBackground = true)
@Composable
private fun TableCellPreview() {
    AppTheme {
        TableCell(
            backgroundColor = AppTheme.colors.dataVisualization.dataGrid.dataFilled,
            text = "Filled",
            textColor = AppTheme.colors.dataVisualization.dataGrid.cellText,
        )
    }
}