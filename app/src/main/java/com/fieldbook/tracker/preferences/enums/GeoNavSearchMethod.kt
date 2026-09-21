package com.fieldbook.tracker.preferences.enums

sealed class GeoNavSearchMethod(val value: String) {
    object DISTANCE : GeoNavSearchMethod("0")
    object TRAPEZOID : GeoNavSearchMethod("1")

    companion object {
        @JvmField
        val DEFAULT: GeoNavSearchMethod = DISTANCE

        fun fromValue(value: String?): GeoNavSearchMethod {
            return when (value) {
                "0" -> DISTANCE
                "1" -> TRAPEZOID
                else -> DEFAULT
            }
        }
    }
}
