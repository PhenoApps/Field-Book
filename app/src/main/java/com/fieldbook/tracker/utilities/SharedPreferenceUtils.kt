package com.fieldbook.tracker.utilities

import android.content.SharedPreferences
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.preferences.GeneralKeys

class SharedPreferenceUtils {

    companion object {

        fun getThemeResource(prefs: SharedPreferences) = when ((getTheme(prefs) ?: "0").toInt()) {
            ThemedActivity.DEFAULT -> R.style.BaseAppTheme
            ThemedActivity.BLUE -> R.style.BaseAppTheme_Blue
            else -> R.style.BaseAppTheme_HighContrast
        }

        fun getTheme(prefs: SharedPreferences) =
            prefs.getString(GeneralKeys.THEME, "${ThemedActivity.DEFAULT}")

        fun isHighContrastTheme(prefs: SharedPreferences) =
            getTheme(prefs) == "${ThemedActivity.HIGH_CONTRAST}"
    }
}