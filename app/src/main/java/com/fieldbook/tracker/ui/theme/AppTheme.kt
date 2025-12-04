package com.fieldbook.tracker.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.ui.theme.colors.AppColors
import com.fieldbook.tracker.ui.theme.colors.BlueAppColors
import com.fieldbook.tracker.ui.theme.colors.DefaultAppColors
import com.fieldbook.tracker.ui.theme.colors.HighContrastAppColors
import com.fieldbook.tracker.ui.theme.enums.AppTextType
import com.fieldbook.tracker.ui.theme.enums.AppThemeType
import com.fieldbook.tracker.ui.theme.typography.CompactTypography
import com.fieldbook.tracker.ui.theme.typography.ExpandedTypography
import com.fieldbook.tracker.ui.theme.typography.MediumTypography
import com.fieldbook.tracker.ui.theme.typography.ThemeTypography

/**
 * Provides theming for Composables
 * Usages:
 *      - wrap the Composable inside AppTheme
 *          eg. AppTheme {
 *                  Box()
 *             }
 *      - use colors/text sizes
 *          eg. AppTheme.colors.primary, AppTheme.colors.dataVisualization.heatmap.high, AppTheme.typography.bodyStyle
 *
 *  TODO: add darkTheme support
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    val themeIndex = prefs.getString(PreferenceKeys.THEME, "0")?.toInt() ?: 0
    val themeType = when(themeIndex) {
        0 -> AppThemeType.Default
        1 -> AppThemeType.HighContrast
        2 -> AppThemeType.Blue
        else -> AppThemeType.Default
    }

    val textIndex = prefs.getString(PreferenceKeys.TEXT_THEME, "1")?.toInt() ?: 1
    val textType = AppTextType.entries.find { it.index == textIndex } ?: AppTextType.MEDIUM

    // select colors based on theme type
    val colors = remember(themeType) {
        when(themeType) {
            AppThemeType.Default -> DefaultAppColors
            AppThemeType.HighContrast -> HighContrastAppColors
            AppThemeType.Blue -> BlueAppColors
        }
    }

    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }

    // select typography based on window size and text size preference
    val typography = remember(windowSizeClass, textType) {
        when (windowSizeClass?.widthSizeClass) {
            WindowWidthSizeClass.Compact -> CompactTypography.all[textType.index]
            WindowWidthSizeClass.Medium -> MediumTypography.all[textType.index]
            WindowWidthSizeClass.Expanded -> ExpandedTypography.all[textType.index]
            else -> CompactTypography.all[textType.index]
        }
    }

    val materialColorScheme = remember(colors) { colors.toMaterialColorScheme() }

    val materialTypography = remember(typography) { typography.toMaterialTypography() }

    // provide both custom colors and typography
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides typography
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = materialTypography,
            content = content
        )
    }
}

/**
 * Convenience object for accessing theme values
 */
object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current

    val typography: ThemeTypography
        @Composable get() = LocalAppTypography.current
}