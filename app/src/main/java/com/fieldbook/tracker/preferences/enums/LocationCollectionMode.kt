package com.fieldbook.tracker.preferences.enums

sealed class LocationCollectionMode(val value: String) {
    object OFF : LocationCollectionMode("0")
    object STUDY : LocationCollectionMode("1")
    object OBSERVATION_UNIT : LocationCollectionMode("2")
    object OBSERVATION : LocationCollectionMode("3")

    companion object {
        @JvmField
        val DEFAULT: LocationCollectionMode = OFF

        fun fromValue(value: String?): LocationCollectionMode {
            return when (value) {
                "0" -> OFF
                "1" -> STUDY
                "2" -> OBSERVATION_UNIT
                "3" -> OBSERVATION
                else -> DEFAULT
            }
        }
    }
}
