package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.traits.ToggleLayoutType
import com.fieldbook.tracker.objects.TraitObject

class AllowOtherParameter : DefaultToggleParameter(
    nameStringResourceId = R.string.trait_parameter_allow_other,
    parameter = Parameters.ALLOW_OTHER,
    layoutType = ToggleLayoutType.ENABLED_DISABLED
) {
    override fun getToggleValue(traitObject: TraitObject?): Boolean =
        traitObject?.allowOther == true

    override fun setToggleValue(traitObject: TraitObject, value: Boolean) {
        traitObject.allowOther = value
    }
}
