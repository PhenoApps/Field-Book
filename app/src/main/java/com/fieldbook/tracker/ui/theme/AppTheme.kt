package com.fieldbook.tracker.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fieldbook.tracker.ui.theme.colors.AppColors
import com.fieldbook.tracker.ui.theme.colors.BlueAppColors
import com.fieldbook.tracker.ui.theme.colors.DefaultAppColors
import com.fieldbook.tracker.ui.theme.colors.HighContrastAppColors
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
    themeViewModel: ThemeViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val activity = LocalActivity.current

    val themeType by themeViewModel.themeType.collectAsState()
    val textType by themeViewModel.textType.collectAsState()

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