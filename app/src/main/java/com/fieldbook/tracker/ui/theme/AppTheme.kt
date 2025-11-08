package com.fieldbook.tracker.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.ui.theme.colors.BlueColors
import com.fieldbook.tracker.ui.theme.colors.DefaultColors
import com.fieldbook.tracker.ui.theme.colors.HighContrastColors
import com.fieldbook.tracker.ui.theme.colors.ThemeColors
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
 *          eg. AppTheme.colors.primary, AppTheme.typography.bodyStyle
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
    val textIndex = prefs.getString(PreferenceKeys.TEXT_THEME, "1")?.toInt() ?: 1
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }

    val colors = when(themeIndex) {
        0 -> DefaultColors
        1 -> HighContrastColors
        2 -> BlueColors
        else -> DefaultColors
    }

    val typography = when (windowSizeClass?.widthSizeClass) {
        WindowWidthSizeClass.Compact -> CompactTypography.all[textIndex]
        WindowWidthSizeClass.Medium -> MediumTypography.all[textIndex]
        WindowWidthSizeClass.Expanded -> ExpandedTypography.all[textIndex]
        else -> CompactTypography.all[textIndex]
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