package com.fieldbook.tracker.ui.grid.datagrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DataGridHeaderCell(
    text: String,
    colors: DataGridUiColors,
    sortIconRes: Int? = null,
    onClick: (() -> Unit)? = null,
    wrapContent: Boolean = false,
    zoom: Float = 1f
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(Color.White)
            .border(Dp.Hairline, Color(colors.cellTextColor))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                color = Color(colors.cellTextColor),
                textAlign = TextAlign.Center,
                fontSize = (13 * zoom).sp,
                overflow = if (wrapContent) TextOverflow.Clip else TextOverflow.Ellipsis,
                maxLines = if (wrapContent) Int.MAX_VALUE else 1,
                softWrap = wrapContent,
            )
            if (sortIconRes != null) {
                Icon(
                    painter = painterResource(id = sortIconRes),
                    contentDescription = null,
                    tint = Color(colors.cellTextColor),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun DataGridRowHeaderCell(
    text: String,
    colors: DataGridUiColors,
    wrapContent: Boolean = false,
    zoom: Float = 1f
) {
    DataGridTableCell(
        text = text,
        backgroundColor = Color.White,
        textColor = Color(colors.cellTextColor),
        borderColor = Color(colors.cellTextColor),
        wrapContent = wrapContent,
        zoom = zoom
    )
}

@Composable
fun DataGridDataCell(
    value: String,
    colors: DataGridUiColors,
    isHighlighted: Boolean = false,
    isSelected: Boolean = false,
    heatmapColor: Color? = null,
    wrapContent: Boolean = false,
    zoom: Float = 1f,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val backgroundColor = when {
        isHighlighted -> Color(colors.activeCellBgColor)
        isSelected -> Color(colors.activeCellBgColor).copy(alpha = 0.6f)
        heatmapColor != null -> heatmapColor
        value.isNotBlank() -> Color(colors.filledCellBgColor)
        else -> Color(colors.emptyCellBgColor)
    }

    val textColor =
        if (isHighlighted || isSelected) Color(colors.activeCellTextColor)
        else Color(colors.cellTextColor)

    val borderModifier = if (isSelected) {
        Modifier.border(2.dp, Color(colors.activeCellBgColor))
    } else {
        Modifier.border(Dp.Hairline, Color(colors.cellTextColor))
    }

    DataGridTableCell(
        text = value,
        backgroundColor = backgroundColor,
        textColor = textColor,
        borderColor = Color(colors.cellTextColor),
        borderModifier = borderModifier,
        onClick = onClick,
        onLongClick = onLongClick,
        isClickable = true,
        wrapContent = wrapContent,
        zoom = zoom
    )
}

@Composable
fun DataGridTableCell(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    borderColor: Color,
    borderModifier: Modifier = Modifier.border(Dp.Hairline, borderColor),
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    isClickable: Boolean = false,
    wrapContent: Boolean = false,
    zoom: Float = 1f
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(backgroundColor)
            .then(borderModifier)
            .then(
                if (isClickable) Modifier
                    .clickable(onClick = onClick)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                else Modifier
            )
    ) {
        Text(
            text = text,
            color = textColor,
            textAlign = TextAlign.Center,
            fontSize = (14 * zoom).sp,
            overflow = if (wrapContent) TextOverflow.Clip else TextOverflow.Ellipsis,
            maxLines = if (wrapContent) Int.MAX_VALUE else 1,
            softWrap = wrapContent,
        )
    }
}
