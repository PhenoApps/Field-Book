package com.fieldbook.shared.preferences

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleIdentifier
import platform.Foundation.NSLocaleLanguageCode

actual fun resolveSystemLanguageSelection(currentLanguageId: String): LanguageSelection {
    val locale = NSLocale.currentLocale
    val language = locale.objectForKey(NSLocaleLanguageCode) as? String ?: "en"
    val country = locale.objectForKey(NSLocaleCountryCode) as? String
    val languageTag = normalizeLanguageTag(
        listOfNotNull(language, country)
            .joinToString("-")
            .ifBlank { "en-US" }
    )
    val summary = locale.displayNameForKey(NSLocaleIdentifier, languageTag)
        ?: locale.displayNameForKey(NSLocaleLanguageCode, language)
        ?: "English"

    return LanguageSelection(
        id = languageTag,
        summary = summary
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
