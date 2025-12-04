package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.traits.ToggleLayoutType
import com.fieldbook.tracker.objects.TraitObject

class RepeatedMeasureParameter : DefaultToggleParameter(
    nameStringResourceId = R.string.trait_parameter_repeated_measures,
    parameter = Parameters.REPEATED_MEASURES,
    layoutType = ToggleLayoutType.ENABLED_DISABLED
) {
    override fun getToggleValue(traitObject: TraitObject?): Boolean =
        traitObject?.repeatedMeasures == true

    override fun setToggleValue(traitObject: TraitObject, value: Boolean) {
        traitObject.repeatedMeasures = value
    }
}