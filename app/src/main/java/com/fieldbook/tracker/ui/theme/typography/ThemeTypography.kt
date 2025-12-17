package com.fieldbook.tracker.ui.theme.typography

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

@Immutable
data class ThemeTypography(
    val titleSize: TextUnit,
    val bodySize: TextUnit,
    val subheadingSize: TextUnit,
    val titleStyle: TextStyle = TextStyle(fontSize = titleSize, fontWeight = FontWeight.Bold),
    val bodyStyle: TextStyle = TextStyle(fontSize = bodySize),
    val subheadingStyle: TextStyle = TextStyle(fontSize = subheadingSize)
)