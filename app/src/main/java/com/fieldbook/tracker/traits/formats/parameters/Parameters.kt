package com.fieldbook.tracker.traits.formats.parameters

enum class Parameters {

    FORMAT, NAME, DEFAULT_VALUE, DETAILS, MAXIMUM, MINIMUM, CATEGORIES, CAMERA, CLOSE_KEYBOARD,
    CROP_IMAGE, SAVE_IMAGE, USE_DAY_OF_YEAR, DISPLAY_VALUE, RESOURCE_FILE,
    DECIMAL_PLACES,
    MATHEMATICAL_SYMBOLS,
    MULTIPLE_CATEGORIES,
    REPEATED_MEASURES,
    AUTO_SWITCH_PLOT,
    UNIT,
    INVALID_VALUE,
    ATTACH_MEDIA;

    companion object {

        val System = listOf(
            "validValuesMin",
            "validValuesMax",
            "category",
            "closeKeyboardOnOpen",
            "cropImage",
            "attachMedia"
        )
    }
}