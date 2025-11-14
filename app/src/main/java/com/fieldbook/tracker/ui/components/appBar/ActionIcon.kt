package com.fieldbook.tracker.ui.components.appBar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
fun ActionIcon(icon: Any, contentDescription: String) {
    when (icon) {
        is ImageVector -> {
            Icon(
                imageVector = icon, contentDescription = contentDescription
            )
        }

        is Painter -> {
            Icon(
                painter = icon, contentDescription = contentDescription
            )
        }

        is Int -> {
            Icon(
                painter = painterResource(icon), contentDescription = contentDescription
            )
        }

        else -> {
            Icon(
                imageVector = Icons.Filled.Add, contentDescription = contentDescription
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionIconPreview() {
    AppTheme {
        Row(
            modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionIcon(
                icon = Icons.Filled.Favorite, contentDescription = "Favorite"
            )
            ActionIcon(
                icon = painterResource(R.drawable.arrow_left), contentDescription = "Back"
            )
            ActionIcon(
                icon = R.drawable.ic_sort, contentDescription = "Sort"
            )
            ActionIcon(
                icon = "invalid", contentDescription = "Fallback"
            )
        }
    }
}