package com.fieldbook.tracker.preferences.enums

sealed class SkipEntriesMode(val value: String) {
    object DISABLED : SkipEntriesMode("0")
    object CURRENT_TRAIT : SkipEntriesMode("1")
    object ALL_TRAITS : SkipEntriesMode("2")

    companion object {
        @JvmField
        val DEFAULT: SkipEntriesMode = DISABLED

        fun fromValue(value: String?): SkipEntriesMode {
            return when (value) {
                "0" -> DISABLED
                "1" -> CURRENT_TRAIT
                "2" -> ALL_TRAITS
                else -> DEFAULT
            }
        }
    }

    fun isEnabled(): Boolean {
        return this != DISABLED
    }
}
