package com.fieldbook.shared.preferences

data class LanguageSelection(
    val id: String,
    val summary: String
)

expect fun resolveSystemLanguageSelection(currentLanguageId: String): LanguageSelection
