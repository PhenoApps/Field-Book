package com.fieldbook.tracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.fieldbook.tracker.ui.theme.colors.AppColors
import com.fieldbook.tracker.ui.theme.typography.ThemeTypography

/**
 * Maps AppColors to Material 3 ColorScheme
 */
fun AppColors.toMaterialColorScheme(isDark: Boolean = false): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = text.tertiary,
            primaryContainer = lightGray,
            onPrimaryContainer = text.primary,

            secondary = accent,
            onSecondary = background,
            secondaryContainer = accent.copy(alpha = 0.28f),
            onSecondaryContainer = text.primary,

            tertiary = chip.first,
            onTertiary = background,
            tertiaryContainer = chip.first.copy(alpha = 0.35f),
            onTertiaryContainer = text.primary,

            error = status.error,
            onError = Color.White,
            errorContainer = status.error.copy(alpha = 0.3f),
            onErrorContainer = Color.White,

            background = background,
            onBackground = text.primary,

            surface = lightGray,
            onSurface = text.primary,
            surfaceVariant = interactive.selectedItemBackground,
            onSurfaceVariant = text.secondary,

            outline = surface.border,
            outlineVariant = surface.border.copy(alpha = 0.45f),

            scrim = Color.Black.copy(alpha = 0.5f),

            inverseSurface = text.primary,
            inverseOnSurface = background,
            inversePrimary = accent,

            surfaceDim = background,
            surfaceBright = lightGray,
            surfaceContainerLowest = background,
            surfaceContainerLow = interactive.selectedItemBackground.copy(alpha = 0.12f),
            surfaceContainer = lightGray,
            surfaceContainerHigh = primary,
            surfaceContainerHighest = primary.copy(alpha = 0.85f)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = text.highContrast,
            primaryContainer = primaryTransparent,
            onPrimaryContainer = text.primary,

            secondary = accent,
            onSecondary = text.primary,
            secondaryContainer = accent.copy(alpha = 0.12f),
            onSecondaryContainer = text.primary,

            tertiary = chip.first,
            onTertiary = text.primary,
            tertiaryContainer = chip.first.copy(alpha = 0.12f),
            onTertiaryContainer = text.primary,

            error = status.error,
            onError = Color.White,
            errorContainer = status.error.copy(alpha = 0.12f),
            onErrorContainer = status.error,

            background = background,
            onBackground = text.primary,

            surface = background,
            onSurface = text.primary,
            surfaceVariant = interactive.selectedItemBackground,
            onSurfaceVariant = text.secondary,

            outline = surface.border,
            outlineVariant = surface.border.copy(alpha = 0.4f),

            scrim = Color.Black.copy(alpha = 0.32f),

            inverseSurface = text.primary,
            inverseOnSurface = background,
            inversePrimary = primary.copy(alpha = 0.8f),

            surfaceDim = background,
            surfaceBright = background,
            surfaceContainerLowest = background,
            surfaceContainerLow = interactive.selectedItemBackground.copy(alpha = 0.05f),
            surfaceContainer = interactive.selectedItemBackground.copy(alpha = 0.08f),
            surfaceContainerHigh = interactive.selectedItemBackground.copy(alpha = 0.12f),
            surfaceContainerHighest = interactive.selectedItemBackground.copy(alpha = 0.16f)
        )
    }
}

/**
 * Maps app text sizes to Material 3 Typography
 */
fun ThemeTypography.toMaterialTypography(): Typography {
    return Typography(
        displayLarge = TextStyle(fontSize = this.titleSize * 1.5f),
        displayMedium = TextStyle(fontSize = this.titleSize * 1.25f),
        displaySmall = TextStyle(fontSize = this.titleSize),

        headlineLarge = TextStyle(fontSize = this.titleSize * 1.25f),
        headlineMedium = TextStyle(fontSize = this.titleSize),
        headlineSmall = TextStyle(fontSize = this.titleSize * 0.9f),

        titleLarge = TextStyle(fontSize = this.titleSize),
        titleMedium = TextStyle(fontSize = this.titleSize * 0.9f),
        titleSmall = TextStyle(fontSize = this.titleSize * 0.8f),

        bodyLarge = TextStyle(fontSize = this.bodySize * 1.1f),
        bodyMedium = TextStyle(fontSize = this.bodySize),
        bodySmall = TextStyle(fontSize = this.bodySize * 0.9f),

        labelLarge = TextStyle(fontSize = this.subheadingSize * 1.2f),
        labelMedium = TextStyle(fontSize = this.subheadingSize),
        labelSmall = TextStyle(fontSize = this.subheadingSize * 0.9f)
    )
}
