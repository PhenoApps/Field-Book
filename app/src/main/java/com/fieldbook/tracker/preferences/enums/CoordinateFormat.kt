package com.fieldbook.tracker.preferences.enums

sealed class CoordinateFormat(val value: String) {
    object ISO_6709 : CoordinateFormat("0")
    object GEOJSON : CoordinateFormat("1")

    companion object {
        @JvmField
        val DEFAULT: CoordinateFormat = ISO_6709

        fun fromValue(value: String?): CoordinateFormat {
            return when (value) {
                "1" -> GEOJSON
                else -> DEFAULT
            }
        }
    }
}