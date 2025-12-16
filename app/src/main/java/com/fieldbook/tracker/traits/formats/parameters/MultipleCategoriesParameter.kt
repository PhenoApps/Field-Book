package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.traits.ToggleLayoutType
import com.fieldbook.tracker.objects.TraitObject

class MultipleCategoriesParameter : DefaultToggleParameter(
    nameStringResourceId = R.string.trait_parameter_allow_multicat,
    parameter = Parameters.MULTIPLE_CATEGORIES,
    layoutType = ToggleLayoutType.ENABLED_DISABLED
) {
    override fun getToggleValue(traitObject: TraitObject?): Boolean =
        traitObject?.allowMulticat == true

    override fun setToggleValue(traitObject: TraitObject, value: Boolean) {
        traitObject.allowMulticat = value
    }
}