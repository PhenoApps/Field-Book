package com.fieldbook.tracker.screenshots

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.fieldbook.tracker.ui.theme.LocalAppColors
import com.fieldbook.tracker.ui.theme.LocalAppTypography
import com.fieldbook.tracker.ui.theme.colors.DefaultAppColors
import com.fieldbook.tracker.ui.theme.toMaterialColorScheme
import com.fieldbook.tracker.ui.theme.toMaterialTypography
import com.fieldbook.tracker.ui.theme.typography.MediumTypography

@Composable
fun ScreenshotAppTheme(content: @Composable () -> Unit) {
    val colors = DefaultAppColors
    val typography = MediumTypography.medium
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            content = content,
        )
    }
}
