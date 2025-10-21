package com.fieldbook.tracker.enums

/**
 * Represents the three possible states for boolean traits in the application.
 * No observation is stored / observation is deleted during UNSET state
 */
enum class ThreeState(val state: String, val value: Int) {
    OFF("FALSE", 0),
    NEUTRAL("UNSET", 1),
    ON("TRUE", 2);

    companion object {
        fun fromString(value: String?): ThreeState {
            return when(value?.uppercase()) {
                ON.state -> ON
                OFF.state -> OFF
                else -> NEUTRAL
            }
        }

        fun fromPosition(position: Int): ThreeState {
            return when(position) {
                ON.value -> ON
                OFF.value -> OFF
                NEUTRAL.value -> NEUTRAL
                else -> NEUTRAL
            }
        }
    }
}