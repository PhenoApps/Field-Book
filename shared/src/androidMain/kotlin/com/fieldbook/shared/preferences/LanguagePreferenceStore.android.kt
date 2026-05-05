package com.fieldbook.shared.preferences

import android.os.Build
import android.os.LocaleList
import java.util.Locale

actual fun resolveSystemLanguageSelection(currentLanguageId: String): LanguageSelection {
    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val defaultLocales = LocaleList.getAdjustedDefault()
        val defaultLocale = defaultLocales[0]
        if (
            defaultLocales.size() > 1 &&
            defaultLocale != null &&
            normalizeLanguageTag(defaultLocale.toLanguageTag()) == currentLanguageId
        ) {
            defaultLocales[1] ?: defaultLocale
        } else {
            defaultLocale
        }
    } else {
        Locale.getDefault()
    } ?: Locale.US

    return LanguageSelection(
        id = normalizeLanguageTag(locale.toLanguageTag()),
        summary = locale.getDisplayLanguage(locale).ifBlank { "English" }
    )
}

private fun normalizeLanguageTag(languageTag: String): String {
    return when {
        languageTag.startsWith("iw") -> languageTag.replace("iw", "he")
        languageTag.startsWith("ji") -> languageTag.replace("ji", "yi")
        languageTag.startsWith("in") -> languageTag.replace("in", "id")
        else -> languageTag
    }
}
