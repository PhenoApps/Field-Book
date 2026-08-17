package com.fieldbook.tracker.preferences.enums

sealed class GeoNavSearchAngle(val value: String, val degrees: Double) {
    object DEGREES_45 : GeoNavSearchAngle("45", 45.0)
    object DEGREES_90 : GeoNavSearchAngle("90", 90.0)
    object DEGREES_180 : GeoNavSearchAngle("180", 180.0)

    companion object {
        @JvmField
        val DEFAULT: GeoNavSearchAngle = DEGREES_90

        fun fromValue(value: String): GeoNavSearchAngle {
            return when (value) {
                "45" -> DEGREES_45
                "90" -> DEGREES_90
                "180" -> DEGREES_180
                else -> DEFAULT
            }
        }
    }
}