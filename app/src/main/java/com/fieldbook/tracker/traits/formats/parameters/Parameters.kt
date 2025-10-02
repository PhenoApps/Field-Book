package com.fieldbook.tracker.traits.formats.parameters

enum class Parameters {

    FORMAT, NAME, DEFAULT_VALUE, DETAILS, MAXIMUM, MINIMUM, CATEGORIES, CAMERA, CLOSE_KEYBOARD, CROP_IMAGE, SAVE_IMAGE;

    companion object {

        val System = listOf(
            "validValuesMin",
            "validValuesMax",
            "category",
            "closeKeyboardOnOpen",
            "cropImage",
            "saveImage"
        )
    }
}