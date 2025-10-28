package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.traits.ToggleLayoutType
import com.fieldbook.tracker.objects.TraitObject

class SaveImageParameter() : DefaultToggleParameter(
    nameStringResourceId = R.string.traits_save_image_parameter,
    parameter = Parameters.SAVE_IMAGE,
    layoutType = ToggleLayoutType.TRUE_FALSE
) {
    override fun getToggleValue(traitObject: TraitObject?): Boolean =
        traitObject?.saveImage == true

    override fun setToggleValue(traitObject: TraitObject, value: Boolean) {
        traitObject.saveImage = value
    }
}