package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.traits.ToggleLayoutType
import com.fieldbook.tracker.objects.TraitObject

class CloseKeyboardParameter(initialDefaultValue: Boolean? = null) : DefaultToggleParameter(
    nameStringResourceId = R.string.traits_create_close_keyboard,
    parameter = Parameters.CLOSE_KEYBOARD,
    defaultValue = initialDefaultValue,
    layoutType = ToggleLayoutType.TRUE_FALSE
) {
    override fun getToggleValue(traitObject: TraitObject?): Boolean =
        traitObject?.closeKeyboardOnOpen == true

    override fun setToggleValue(traitObject: TraitObject, value: Boolean) {
        traitObject.closeKeyboardOnOpen = value
    }
}