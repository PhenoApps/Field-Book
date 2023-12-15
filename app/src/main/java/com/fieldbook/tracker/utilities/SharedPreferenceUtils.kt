package com.fieldbook.tracker.utilities

import android.content.SharedPreferences
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.preferences.GeneralKeys

class SharedPreferenceUtils {

    companion object {
        fun isHighContrastTheme(prefs: SharedPreferences) = prefs.getString(
            GeneralKeys.THEME,
            "${ThemedActivity.DEFAULT}"
        ) == "${ThemedActivity.HIGH_CONTRAST}"
    }
}