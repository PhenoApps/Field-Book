package com.fieldbook.tracker.preferences.enums

sealed class GeoNavUpdateInterval(val value: String, val millis: Long) {
    object INTERVAL_5S : GeoNavUpdateInterval("5s", 5000)
    object INTERVAL_10S : GeoNavUpdateInterval("10s", 10000)
    object INTERVAL_15S : GeoNavUpdateInterval("15s", 15000)
    object INTERVAL_30S : GeoNavUpdateInterval("30s", 30000)
    object INTERVAL_60S : GeoNavUpdateInterval("60s", 60000)

    companion object {
        @JvmField
        val DEFAULT: GeoNavUpdateInterval = INTERVAL_10S

        fun fromValue(value: String): GeoNavUpdateInterval {
            return when (value) {
                "5s" -> INTERVAL_5S
                "10s" -> INTERVAL_10S
                "15s" -> INTERVAL_15S
                "30s" -> INTERVAL_30S
                "60s" -> INTERVAL_60S
                else -> DEFAULT
            }
        }
    }
}