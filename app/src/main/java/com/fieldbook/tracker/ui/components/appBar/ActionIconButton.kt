package com.fieldbook.tracker.ui.components.appBar

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.components.widgets.AppIcon
import com.fieldbook.tracker.ui.theme.AppTheme

/**
 * Menu bar icon button for toolbar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionIconButton(action: TopAppBarAction) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Above
        ),
        tooltip = {
            PlainTooltip {
                Text(action.title)
            }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = action.onClick) {
            if (action.icon != null) {
                AppIcon(icon = action.icon, contentDescription = action.contentDescription)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionIconButtonPreview() {
    AppTheme {
        ActionIconButton(action = TopAppBarAction(
            icon = R.drawable.ic_keyboard_close,
            title = "Title",
            contentDescription = "Description",
            onClick = { },
        ))
    }
}