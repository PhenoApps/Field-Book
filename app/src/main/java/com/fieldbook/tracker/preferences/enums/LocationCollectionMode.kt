package com.fieldbook.tracker.preferences.enums

sealed class LocationCollectionMode(val value: String) {
    object OFF : LocationCollectionMode("off")
    object STUDY : LocationCollectionMode("study")
    object OBSERVATION_UNIT : LocationCollectionMode("observation_unit")
    object OBSERVATION : LocationCollectionMode("observation")

    companion object {
        @JvmField
        val DEFAULT: LocationCollectionMode = OBSERVATION

        fun fromValue(value: String?): LocationCollectionMode {
            return when (value) {
                "off" -> OFF
                "study" -> STUDY
                "observation_unit" -> OBSERVATION_UNIT
                "observation" -> OBSERVATION
                else -> DEFAULT
            }
        }
    }
}