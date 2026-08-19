package com.fieldbook.tracker.preferences.enums

sealed class SkipEntriesMode(val value: String) {
    object CURRENT_TRAIT : SkipEntriesMode("0")
    object ALL_TRAITS : SkipEntriesMode("1")
    object DISABLED : SkipEntriesMode("2")

    companion object {
        @JvmField
        val DEFAULT: SkipEntriesMode = DISABLED

        fun fromValue(value: String?): SkipEntriesMode {
            return when (value) {
                "0" -> CURRENT_TRAIT
                "1" -> ALL_TRAITS
                "2" -> DISABLED
                else -> DEFAULT
            }
        }
    }

    fun isEnabled(): Boolean {
        return this != DISABLED
    }
}
