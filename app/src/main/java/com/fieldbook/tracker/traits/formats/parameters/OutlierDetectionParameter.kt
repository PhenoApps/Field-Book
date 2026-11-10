package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.traits.ToggleLayoutType
import com.fieldbook.tracker.objects.TraitObject

class OutlierDetectionParameter : DefaultToggleParameter(
    nameStringResourceId = R.string.trait_parameter_outlier_detection,
    parameter = Parameters.OUTLIER_DETECTION,
    layoutType = ToggleLayoutType.ENABLED_DISABLED
) {
    override fun getToggleValue(traitObject: TraitObject?): Boolean =
        traitObject?.outlierDetection == true

    override fun setToggleValue(traitObject: TraitObject, value: Boolean) {
        traitObject.outlierDetection = value
    }
}
