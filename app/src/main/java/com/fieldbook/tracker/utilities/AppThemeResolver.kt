package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.ui.theme.colors.AppColors
import com.fieldbook.tracker.ui.theme.colors.BlueAppColors
import com.fieldbook.tracker.ui.theme.colors.DefaultAppColors
import com.fieldbook.tracker.ui.theme.colors.HighContrastAppColors
import com.fieldbook.tracker.ui.theme.colors.SodaDarkAppColors

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

    private val preferenceThemeStyles = mapOf(
        ThemedActivity.SODA_DARK to mapOf(
            ThemedActivity.SMALL to R.style.PreferenceTheme_SodaDark_SmallTextTheme,
            ThemedActivity.MEDIUM to R.style.PreferenceTheme_SodaDark,
            ThemedActivity.LARGE to R.style.PreferenceTheme_SodaDark_LargeTextTheme,
            ThemedActivity.EXTRA_LARGE to R.style.PreferenceTheme_SodaDark_ExtraLargeTextTheme,
        ),
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
    fun preferenceThemeStyle(prefs: SharedPreferences): Int {
        val styles = preferenceThemeStyles[themeIndex(prefs)] ?: return R.style.PreferenceTheme
        return textStyleFor(textIndex(prefs), object : TextSizedStyles {
            override val small = styles.getValue(ThemedActivity.SMALL)
            override val medium = styles.getValue(ThemedActivity.MEDIUM)
            override val large = styles.getValue(ThemedActivity.LARGE)
            override val extraLarge = styles.getValue(ThemedActivity.EXTRA_LARGE)
        })
    }

    @JvmStatic
    fun aboutLibrariesStyle(themeIndex: Int): Int =
        aboutLibrariesStyles[themeIndex] ?: aboutLibrariesStyles.getValue(ThemedActivity.DEFAULT)

    fun statusBarColorRes(themeIndex: Int): Int =
        (themeStyles[themeIndex] ?: themeStyles.getValue(ThemedActivity.DEFAULT)).statusBarColorRes

    /** Resolves themed date picker dialog style (Soda uses [R.style.DatePickerDialogStyle_SodaDark]). */
    @JvmStatic
    fun datePickerDialogStyle(context: Context): Int {
        if (themeIndex(PreferenceManager.getDefaultSharedPreferences(context)) == ThemedActivity.SODA_DARK) {
            return R.style.DatePickerDialogStyle_SodaDark
        }
        return R.style.DatePickerDialogStyle
    }

    /** Resolves themed alert dialog style (Soda uses [R.style.AppAlertDialog.SodaDark]). */
    @JvmStatic
    fun alertDialogStyle(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (isSodaDark(prefs)) {
            val typedValue = TypedValue()
            if (context.theme.resolveAttribute(androidx.appcompat.R.attr.alertDialogTheme, typedValue, true)
                && typedValue.resourceId == R.style.AppAlertDialog_Preference_SodaDark
            ) {
                return R.style.AppAlertDialog_Preference_SodaDark
            }
            return R.style.AppAlertDialog_SodaDark
        }
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(androidx.appcompat.R.attr.alertDialogTheme, typedValue, true)
            && typedValue.resourceId != 0
        ) {
            return typedValue.resourceId
        }
        return R.style.AppAlertDialog
    }

    /** Compose [AppColors] for embedded hosts that cannot rely on [com.fieldbook.tracker.ui.theme.AppTheme] Hilt wiring. */
    @JvmStatic
    fun composeAppColors(prefs: SharedPreferences): AppColors = when (themeIndex(prefs)) {
        ThemedActivity.HIGH_CONTRAST -> HighContrastAppColors
        ThemedActivity.BLUE -> BlueAppColors
        ThemedActivity.SODA_DARK -> SodaDarkAppColors
        else -> DefaultAppColors
    }

    private fun textStyleFor(textIndex: Int, styles: TextSizedStyles): Int = when (textIndex) {
        ThemedActivity.SMALL -> styles.small
        ThemedActivity.MEDIUM -> styles.medium
        ThemedActivity.LARGE -> styles.large
        ThemedActivity.EXTRA_LARGE -> styles.extraLarge
        else -> styles.medium
    }
}
