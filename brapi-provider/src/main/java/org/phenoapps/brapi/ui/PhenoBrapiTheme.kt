package org.phenoapps.brapi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun PhenoBrapiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFFF5722),
            secondary = Color(0xFF607D8B),
            background = Color.White,
            surface = Color.White,
        ),
        content = content,
    )
}
