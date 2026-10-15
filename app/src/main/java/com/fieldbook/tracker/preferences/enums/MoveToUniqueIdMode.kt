package com.fieldbook.tracker.preferences.enums

enum class MoveToUniqueIdMode(val value: String) {
    DISABLED("0"),
    TEXT_OR_SCAN("1"),
    DIRECT_CAMERA_SCAN("2");

    companion object {
        @JvmField
        val DEFAULT: MoveToUniqueIdMode = DISABLED

        fun fromValue(value: String?): MoveToUniqueIdMode {
            return when (value) {
                "0" -> DISABLED
                "1" -> TEXT_OR_SCAN
                "2" -> DIRECT_CAMERA_SCAN
                else -> DEFAULT
            }
        }
    }

    fun isEnabled(): Boolean {
        return this != DISABLED
    }
}
