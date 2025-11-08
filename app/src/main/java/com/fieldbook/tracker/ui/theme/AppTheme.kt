package com.fieldbook.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.ui.theme.colors.BlueColors
import com.fieldbook.tracker.ui.theme.colors.DefaultColors
import com.fieldbook.tracker.ui.theme.colors.HighContrastColors
import com.fieldbook.tracker.ui.theme.colors.ThemeColors
import com.fieldbook.tracker.ui.theme.typography.ExtraLargeTypography
import com.fieldbook.tracker.ui.theme.typography.LargeTypography
import com.fieldbook.tracker.ui.theme.typography.MediumTypography
import com.fieldbook.tracker.ui.theme.typography.SmallTypography
import com.fieldbook.tracker.ui.theme.typography.ThemeTypography
import com.fieldbook.tracker.ui.theme.typography.getResponsiveTypography

/**
 * Provides theming for Composables
 * Usages:
 *      - wrap the Composable inside AppTheme
 *          eg. AppTheme {
 *                  Box()
 *             }
 *      - use colors/text sizes
 *          eg. AppTheme.colors.primary, AppTheme.typography.bodyStyle
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    val themeIndex = prefs.getString(PreferenceKeys.THEME, "0")?.toInt() ?: 0
    val textIndex = prefs.getString(PreferenceKeys.TEXT_THEME, "1")?.toInt() ?: 1

    val colors = when(themeIndex) {
        0 -> DefaultColors
        1 -> HighContrastColors
        2 -> BlueColors
        else -> DefaultColors
    }

    val baseTypography = when(textIndex) {
        0 -> SmallTypography
        1 -> MediumTypography
        2 -> LargeTypography
        3 -> ExtraLargeTypography
        else -> MediumTypography
    }

    val typography = getResponsiveTypography(baseTypography).let { responsiveTypo ->
        responsiveTypo.copy(
            titleStyle = responsiveTypo.titleStyle.copy(color = colors.titleText),
            bodyStyle = responsiveTypo.bodyStyle.copy(color = colors.textDark),
            subheadingStyle = responsiveTypo.subheadingStyle.copy(color = colors.subheading)
        )
    }

    // enable theme/text related values available to composables
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides typography
    ) {
        MaterialTheme(
            content = content
        )
    }
}

// Convenience object for accessing theme values
object AppTheme {
    val colors: ThemeColors
        @Composable get() = LocalAppColors.current

    val typography: ThemeTypography
        @Composable get() = LocalAppTypography.current
}