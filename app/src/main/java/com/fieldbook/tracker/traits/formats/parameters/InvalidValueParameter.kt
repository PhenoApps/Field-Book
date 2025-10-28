package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.traits.ToggleLayoutType
import com.fieldbook.tracker.objects.TraitObject

class InvalidValueParameter : DefaultToggleParameter(
    nameStringResourceId = R.string.trait_parameter_invalid_value,
    parameter = Parameters.INVALID_VALUE,
    layoutType = ToggleLayoutType.ENABLED_DISABLED
) {
    override fun getToggleValue(traitObject: TraitObject?): Boolean =
        traitObject?.invalidValues == true

    override fun setToggleValue(traitObject: TraitObject, value: Boolean) {
        traitObject.invalidValues = value
    }
}