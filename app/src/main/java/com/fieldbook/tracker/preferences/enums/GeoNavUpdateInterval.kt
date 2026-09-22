package com.fieldbook.tracker.preferences.enums

sealed class GeoNavUpdateInterval(val value: String, val millis: Long) {
    object INTERVAL_1S : GeoNavUpdateInterval("1", 1000)
    object INTERVAL_5S : GeoNavUpdateInterval("5", 5000)
    object INTERVAL_10S : GeoNavUpdateInterval("10", 10000)

    companion object {
        @JvmField
        val DEFAULT: GeoNavUpdateInterval = INTERVAL_1S

        fun fromValue(value: String?): GeoNavUpdateInterval {
            return when (value) {
                "1" -> INTERVAL_1S
                "5" -> INTERVAL_5S
                "10" -> INTERVAL_10S
                else -> DEFAULT
            }
        }
    }
}
