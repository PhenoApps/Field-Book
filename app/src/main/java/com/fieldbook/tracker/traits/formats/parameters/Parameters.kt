package com.fieldbook.tracker.traits.formats.parameters

enum class Parameters {

    FORMAT, NAME, DEFAULT_VALUE, DETAILS, MAXIMUM, MINIMUM, CATEGORIES, CAMERA, CLOSE_KEYBOARD;

    companion object {

        val System = listOf(
            "validValuesMin",
            "validValuesMax",
            "category",
            "closeKeyboardOnOpen"
        )
    }
}