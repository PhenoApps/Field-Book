package com.fieldbook.tracker.preferences.enums

sealed class GeoNavSearchAngle(val value: String, val degrees: Double) {
    object DEGREES_22_5 : GeoNavSearchAngle("22.5", 22.5)
    object DEGREES_45 : GeoNavSearchAngle("45", 45.0)
    object DEGREES_67_5 : GeoNavSearchAngle("67.5", 67.5)
    object DEGREES_90 : GeoNavSearchAngle("90", 90.0)

    companion object {
        @JvmField
        val DEFAULT: GeoNavSearchAngle = DEGREES_22_5

        fun fromValue(value: String?): GeoNavSearchAngle {
            return when (value) {
                "22.5" -> DEGREES_22_5
                "45" -> DEGREES_45
                "67.5" -> DEGREES_67_5
                "90" -> DEGREES_90
                else -> DEFAULT
            }
        }
    }
}
