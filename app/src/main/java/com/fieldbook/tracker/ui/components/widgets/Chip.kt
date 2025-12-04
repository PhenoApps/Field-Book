package com.fieldbook.tracker.ui.components.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun Chip(
    text: String,
    icon: Int? = null,
    backgroundColor: Color? = null,
    strokeColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val isClickable = onClick != null

    var background = backgroundColor
    var stroke = strokeColor

    if (background == null) {
        background =
            if (isClickable) AppTheme.colors.chip.selectableBackground
            else AppTheme.colors.chip.defaultBackground
    }

    if (stroke == null) {
        stroke =
            if (isClickable) AppTheme.colors.chip.selectableStroke
            else null
    }

    Surface(
        shape = CircleShape,
        color = background,
        border = stroke?.let { BorderStroke(1.5.dp, it) },
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            ),
    ) {
        Row(
            modifier = Modifier.padding(
                start = 6.dp, // Match chipStartPadding
                end = 12.dp,  // Standard end padding
                top = 6.dp,
                bottom = 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                AppIcon(
                    modifier = Modifier
                        // .size(24.dp) // Match chipIconSize
                        .padding(end = 8.dp),
                    icon = painterResource(id = icon),
                    contentDescription = null,
                )
            }

            Text(
                text = text,
                style = AppTheme.typography.bodyStyle,
                color = AppTheme.colors.text.primary
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun ChipPreview() {
    AppTheme {
        Chip(
            text = "Text",
            icon = R.drawable.ic_rename,
            strokeColor = AppTheme.colors.chip.selectableStroke,
        )
    }
}