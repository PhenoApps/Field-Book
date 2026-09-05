package com.fieldbook.tracker.preferences.enums

sealed class DisableEntryArrowMode(val value: String) {
    object DISABLED : DisableEntryArrowMode("0")
    object LEFT : DisableEntryArrowMode("1")
    object RIGHT : DisableEntryArrowMode("2")
    object BOTH : DisableEntryArrowMode("3")

    companion object {
        @JvmField
        val DEFAULT: DisableEntryArrowMode = DISABLED

        fun fromValue(value: String?): DisableEntryArrowMode {
            return when (value) {
                "0" -> DISABLED
                "1" -> LEFT
                "2" -> RIGHT
                "3" -> BOTH
                else -> DEFAULT
            }
        }
    }

    fun disablesLeft(): Boolean {
        return this == LEFT || this == BOTH
    }

    fun disablesRight(): Boolean {
        return this == RIGHT || this == BOTH
    }
}