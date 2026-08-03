package com.fieldbook.tracker.screenshots

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.ui.theme.LocalAppColors
import com.fieldbook.tracker.ui.theme.LocalAppTypography
import com.fieldbook.tracker.ui.theme.colors.DefaultAppColors
import com.fieldbook.tracker.ui.theme.colors.SodaDarkAppColors
import com.fieldbook.tracker.ui.theme.toMaterialColorScheme
import com.fieldbook.tracker.ui.theme.toMaterialTypography
import com.fieldbook.tracker.ui.theme.typography.MediumTypography

/**
 * Theme wrapper for Roborazzi soda-dark review screenshots (PR #1477).
 *
 * Keep [FORCE_SODA_DARK] true for the soda-dark gallery. Flip to false only if
 * regenerating a light baseline comparison set.
 */
object ScreenshotThemeConfig {
    const val FORCE_SODA_DARK = true
}

/** Writes theme prefs before harness [ThemedActivity.applyTheme] / activity create. */
fun applyForcedThemePrefs(context: Context) {
    if (!ScreenshotThemeConfig.FORCE_SODA_DARK) return
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putString(PreferenceKeys.THEME, ThemedActivity.SODA_DARK.toString())
        .putString(PreferenceKeys.TEXT_THEME, ThemedActivity.MEDIUM.toString())
        .commit()
}

@Composable
fun ScreenshotAppTheme(content: @Composable () -> Unit) {
    val colors = if (ScreenshotThemeConfig.FORCE_SODA_DARK) SodaDarkAppColors else DefaultAppColors
    val typography = MediumTypography.medium
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(isDark = ScreenshotThemeConfig.FORCE_SODA_DARK),
            typography = typography.toMaterialTypography(),
            content = content,
        )
    }
}
