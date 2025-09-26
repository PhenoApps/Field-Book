package com.fieldbook.tracker.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.unit.TextUnit

@Composable
fun FieldGridCell(label: String, cellType: CellType, colors: GridColors,
      fontSize: TextUnit, onClick: (() -> Unit)? = null) {

    val bg = when (cellType) {
        CellType.SELECTED -> colors.highlightColor
        CellType.HIGHLIGHTED -> colors.highlightColor.copy(alpha = 0.30f)
        CellType.CORNER -> colors.highlightColor.copy(alpha = 0.50f)
        CellType.REGULAR -> colors.cellBgColor
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(bg)
            .border(1.dp, colors.borderColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Text(
            text = label,
            color = colors.textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(fontSize = fontSize),
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
        )
    }
}
