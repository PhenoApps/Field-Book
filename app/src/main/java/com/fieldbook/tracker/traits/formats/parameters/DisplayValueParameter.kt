package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.traits.ToggleLayoutType
import com.fieldbook.tracker.objects.TraitObject

class DisplayValueParameter() : DefaultToggleParameter(
    nameStringResourceId = R.string.trait_parameter_display_value,
    parameter = Parameters.DISPLAY_VALUE,
    defaultValue = false,
    layoutType = ToggleLayoutType.TRUE_FALSE
) {
    override fun getToggleValue(traitObject: TraitObject?): Boolean =
        traitObject?.categoryDisplayValue == true

    override fun setToggleValue(traitObject: TraitObject, value: Boolean) {
        traitObject.categoryDisplayValue = value
    }
}