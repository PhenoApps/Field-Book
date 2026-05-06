package com.fieldbook.shared.utilities

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.fieldbook.shared.preferences.PreferenceKeys
import com.russhwolf.settings.Settings

private const val TAG = "TextLangUtil"

actual fun onLanguageChanged() {
    try {
        val id = Settings().getString(PreferenceKeys.LANGUAGE_LOCALE_ID, "")

        if (id.isNotEmpty()) {
            Log.d(TAG, "Language set from preferences: $id")
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(id))
        } else {
            Log.d(TAG, "No language stored in preferences.")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error when setting language from preferences.", e)
    }
}
