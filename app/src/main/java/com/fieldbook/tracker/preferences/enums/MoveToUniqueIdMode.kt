package com.fieldbook.tracker.preferences.enums

enum class MoveToUniqueIdMode(val value: String) {
    TEXT_OR_SCAN("0"),
    DIRECT_CAMERA_SCAN("1"),
    DISABLED("2");

    companion object {
        @JvmField
        val DEFAULT: MoveToUniqueIdMode = DISABLED

        fun fromValue(value: String?): MoveToUniqueIdMode {
            return when (value) {
                "0" -> TEXT_OR_SCAN
                "1" -> DIRECT_CAMERA_SCAN
                "2" -> DISABLED
                else -> DEFAULT
            }
        }
    }

    fun isEnabled(): Boolean {
        return this != DISABLED
    }
}
