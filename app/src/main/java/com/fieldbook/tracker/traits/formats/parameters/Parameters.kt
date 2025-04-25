package com.fieldbook.tracker.traits.formats.parameters

enum class Parameters {

    FORMAT, NAME, DEFAULT_VALUE, DETAILS, MAXIMUM, MINIMUM, CATEGORIES, CAMERA, CLOSE_KEYBOARD, CROP_IMAGE, USE_DAY_OF_YEAR, DISPLAY_VALUE, RESOURCE_FILE;

    companion object {

        val System = listOf(
            "validValuesMin",
            "validValuesMax",
            "category",
            "closeKeyboardOnOpen",
            "cropImage",
            "useDayOfYear",
            "displayValue",
            "resourceFile"
        )
    }
}