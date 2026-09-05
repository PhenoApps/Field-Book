package com.fieldbook.tracker.preferences.enums

sealed class VerificationInterval(val value: String, val hours: Int) {
    object EVERY_OPEN : VerificationInterval("0", 0)
    object EVERY_12H : VerificationInterval("1", 12)
    object EVERY_24H : VerificationInterval("2", 24)
    object NEVER : VerificationInterval("3", 0)

    companion object {
        @JvmField
        val DEFAULT: VerificationInterval = EVERY_OPEN

        fun fromValue(value: String?): VerificationInterval {
            return when (value) {
                "0" -> EVERY_OPEN
                "1" -> EVERY_12H
                "2" -> EVERY_24H
                "3" -> NEVER
                else -> DEFAULT
            }
        }
    }
}
