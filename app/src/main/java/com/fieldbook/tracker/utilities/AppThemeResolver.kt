package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.preferences.PreferenceKeys

/**
 * Central registry for appearance theme → Android style resource mapping.
 */
object AppThemeResolver {

    private interface TextSizedStyles {
        val small: Int
        val medium: Int
        val large: Int
        val extraLarge: Int
    }

    private data class ThemeStyleSet(
        override val small: Int,
        override val medium: Int,
        override val large: Int,
        override val extraLarge: Int,
        val statusBarColorRes: Int,
    ) : TextSizedStyles

    private data class MalThemeStyleSet(
        override val small: Int,
        override val medium: Int,
        override val large: Int,
        override val extraLarge: Int,
    ) : TextSizedStyles

    private val themeStyles = mapOf(
        ThemedActivity.DEFAULT to ThemeStyleSet(
            small = R.style.BaseAppTheme_SmallTextTheme,
            medium = R.style.BaseAppTheme_MediumTextTheme,
            large = R.style.BaseAppTheme_LargeTextTheme,
            extraLarge = R.style.BaseAppTheme_ExtraLargeTextTheme,
            statusBarColorRes = R.color.main_primary_dark,
        ),
        ThemedActivity.HIGH_CONTRAST to ThemeStyleSet(
            small = R.style.BaseAppTheme_HighContrast_SmallTextTheme,
            medium = R.style.BaseAppTheme_HighContrast_MediumTextTheme,
            large = R.style.BaseAppTheme_HighContrast_LargeTextTheme,
            extraLarge = R.style.BaseAppTheme_ExtraLargeTextTheme,
            statusBarColorRes = R.color.high_contrast_primary_dark,
        ),
        ThemedActivity.BLUE to ThemeStyleSet(
            small = R.style.BaseAppTheme_Blue_SmallTextTheme,
            medium = R.style.BaseAppTheme_Blue_MediumTextTheme,
            large = R.style.BaseAppTheme_Blue_LargeTextTheme,
            extraLarge = R.style.BaseAppTheme_Blue_ExtraLargeTextTheme,
            statusBarColorRes = R.color.blue_primary_dark,
        ),
        ThemedActivity.SODA_DARK to ThemeStyleSet(
            small = R.style.BaseAppTheme_SodaDark_SmallTextTheme,
            medium = R.style.BaseAppTheme_SodaDark_MediumTextTheme,
            large = R.style.BaseAppTheme_SodaDark_LargeTextTheme,
            extraLarge = R.style.BaseAppTheme_SodaDark_ExtraLargeTextTheme,
            statusBarColorRes = R.color.soda_dark_window,
        ),
    )

    private val malThemeStyles = mapOf(
        ThemedActivity.DEFAULT to MalThemeStyleSet(
            small = R.style.BaseAppTheme_Mal_SmallTextTheme,
            medium = R.style.BaseAppTheme_Mal_MediumTextTheme,
            large = R.style.BaseAppTheme_Mal_LargeTextTheme,
            extraLarge = R.style.BaseAppTheme_Mal_ExtraLargeTextTheme,
        ),
        ThemedActivity.HIGH_CONTRAST to MalThemeStyleSet(
            small = R.style.BaseAppTheme_Mal_HighContrast_SmallTextTheme,
            medium = R.style.BaseAppTheme_Mal_HighContrast_MediumTextTheme,
            large = R.style.BaseAppTheme_Mal_HighContrast_LargeTextTheme,
            extraLarge = R.style.BaseAppTheme_Mal_HighContrast_ExtraLargeTextTheme,
        ),
        ThemedActivity.BLUE to MalThemeStyleSet(
            small = R.style.BaseAppTheme_Mal_Blue_SmallTextTheme,
            medium = R.style.BaseAppTheme_Mal_Blue_MediumTextTheme,
            large = R.style.BaseAppTheme_Mal_Blue_LargeTextTheme,
            extraLarge = R.style.BaseAppTheme_Mal_Blue_ExtraLargeTextTheme,
        ),
        ThemedActivity.SODA_DARK to MalThemeStyleSet(
            small = R.style.BaseAppTheme_Mal_SodaDark_SmallTextTheme,
            medium = R.style.BaseAppTheme_Mal_SodaDark_MediumTextTheme,
            large = R.style.BaseAppTheme_Mal_SodaDark_LargeTextTheme,
            extraLarge = R.style.BaseAppTheme_Mal_SodaDark_ExtraLargeTextTheme,
        ),
    )

    private val dialogThemeStyles = mapOf(
        ThemedActivity.DEFAULT to R.style.ActivityDialog,
        ThemedActivity.HIGH_CONTRAST to R.style.ActivityDialog_HighContrast,
        ThemedActivity.BLUE to R.style.ActivityDialog_Blue,
        ThemedActivity.SODA_DARK to R.style.ActivityDialog_SodaDark,
    )

    private val aboutLibrariesStyles = mapOf(
        ThemedActivity.DEFAULT to R.style.AboutLibrariesCustom,
        ThemedActivity.HIGH_CONTRAST to R.style.AboutLibrariesCustom_HighContrast,
        ThemedActivity.BLUE to R.style.AboutLibrariesCustom_Blue,
        ThemedActivity.SODA_DARK to R.style.AboutLibrariesCustom_SodaDark,
    )

    @JvmStatic
    fun themeIndex(prefs: SharedPreferences): Int =
        prefs.getString(PreferenceKeys.THEME, "${ThemedActivity.DEFAULT}")?.toInt()
            ?: ThemedActivity.DEFAULT

    @JvmStatic
    fun textIndex(prefs: SharedPreferences): Int =
        prefs.getString(PreferenceKeys.TEXT_THEME, "${ThemedActivity.MEDIUM}")?.toInt()
            ?: ThemedActivity.MEDIUM

    @JvmStatic
    fun isSodaDark(prefs: SharedPreferences): Boolean =
        themeIndex(prefs) == ThemedActivity.SODA_DARK

    @JvmStatic
    fun isHighContrast(prefs: SharedPreferences): Boolean =
        themeIndex(prefs) == ThemedActivity.HIGH_CONTRAST

    @JvmStatic
    fun usesMonochromeLauncherIcon(prefs: SharedPreferences): Boolean =
        isHighContrast(prefs) || isSodaDark(prefs)

    fun activityThemeStyle(prefs: SharedPreferences): Int {
        val styles = themeStyles[themeIndex(prefs)] ?: themeStyles.getValue(ThemedActivity.DEFAULT)
        return textStyleFor(textIndex(prefs), styles)
    }

    @JvmStatic
    fun activityThemeStyle(context: Context): Int =
        activityThemeStyle(PreferenceManager.getDefaultSharedPreferences(context))

    fun malActivityThemeStyle(prefs: SharedPreferences): Int {
        val styles = malThemeStyles[themeIndex(prefs)] ?: malThemeStyles.getValue(ThemedActivity.DEFAULT)
        return textStyleFor(textIndex(prefs), styles)
    }

    fun dialogThemeStyle(themeIndex: Int): Int =
        dialogThemeStyles[themeIndex] ?: dialogThemeStyles.getValue(ThemedActivity.DEFAULT)

    @JvmStatic
    fun aboutLibrariesStyle(themeIndex: Int): Int =
        aboutLibrariesStyles[themeIndex] ?: aboutLibrariesStyles.getValue(ThemedActivity.DEFAULT)

    fun statusBarColorRes(themeIndex: Int): Int =
        (themeStyles[themeIndex] ?: themeStyles.getValue(ThemedActivity.DEFAULT)).statusBarColorRes

    /** Resolves [android.R.attr.alertDialogTheme] from the activity theme (Soda-aware). */
    @JvmStatic
    fun alertDialogStyle(context: Context): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.alertDialogTheme, typedValue, true)
            && typedValue.resourceId != 0
        ) {
            typedValue.resourceId
        } else {
            R.style.AppAlertDialog
        }
    }

    private fun textStyleFor(textIndex: Int, styles: TextSizedStyles): Int = when (textIndex) {
        ThemedActivity.SMALL -> styles.small
        ThemedActivity.MEDIUM -> styles.medium
        ThemedActivity.LARGE -> styles.large
        ThemedActivity.EXTRA_LARGE -> styles.extraLarge
        else -> styles.medium
    }
}
