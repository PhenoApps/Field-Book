package com.fieldbook.tracker.preferences.enums

sealed class GeoNavSearchMethod(val value: String) {
    object DISTANCE : GeoNavSearchMethod("distance")
    object TRAPEZOID : GeoNavSearchMethod("trapezoid")

    companion object {
        @JvmField
        val DEFAULT: GeoNavSearchMethod = TRAPEZOID

        fun fromValue(value: String): GeoNavSearchMethod {
            return when (value) {
                "distance" -> DISTANCE
                "trapezoid" -> TRAPEZOID
                else -> DEFAULT
            }
        }
    }
}