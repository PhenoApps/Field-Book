package com.fieldbook.tracker.ui.components.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.theme.AppTheme

/**
 * Supports icons from various sources
 */
@Composable
fun AppIcon(
    modifier: Modifier = Modifier,
    icon: Any,
    contentDescription: String? = null,
    tint: Color = AppTheme.colors.surface.iconTint,
) {
    when (icon) {
        is ImageVector -> Icon(icon, contentDescription, modifier, tint)
        is Painter -> Icon(icon, contentDescription, modifier, tint)
        is Int -> Icon(painterResource(icon), contentDescription, modifier, tint)
        else -> Icon(Icons.Default.Add, contentDescription, modifier, tint)
    }
}

@Preview(showBackground = true)
@Composable
private fun AppIconPreview() {
    AppTheme {
        Row(
            modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppIcon(icon = Icons.Filled.Favorite)
            AppIcon(icon = painterResource(R.drawable.arrow_left))
            AppIcon(icon = R.drawable.ic_sort)
            AppIcon(icon = "invalid")
        }
    }
}