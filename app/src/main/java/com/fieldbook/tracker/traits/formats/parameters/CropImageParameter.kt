package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.traits.ToggleLayoutType
import com.fieldbook.tracker.objects.TraitObject

class CropImageParameter(initialDefaultValue: Boolean? = null) : DefaultToggleParameter(
    nameStringResourceId = R.string.traits_crop_image_parameter,
    parameter = Parameters.CROP_IMAGE,
    defaultValue = initialDefaultValue,
    layoutType = ToggleLayoutType.TRUE_FALSE
) {
    override fun getToggleValue(traitObject: TraitObject?): Boolean =
        traitObject?.cropImage == true
    override fun setToggleValue(traitObject: TraitObject, value: Boolean) {
        traitObject.cropImage = value
    }
}