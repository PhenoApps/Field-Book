package com.fieldbook.tracker.ui.components.buttons

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun CircularBorderedFab(
    onClick: () -> Unit,
    color: Color = AppTheme.colors.primary,
    borderColor: Color = AppTheme.colors.surface.border,
    icon: ImageVector,
    contentDescription: String
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = color,
        shape = CircleShape,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = borderColor,
                shape = CircleShape,
            )
    ) {
        Icon(
            icon,
            contentDescription = contentDescription
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CircularBorderedFabPreview() {
    AppTheme {
        Scaffold (
            floatingActionButton = {
                CircularBorderedFab(
                    onClick = { },
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.traits_new_dialog_title)
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}