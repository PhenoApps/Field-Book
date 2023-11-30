package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class PhotoFormat : TraitFormat() {

    override val format = Formats.CAMERA

    override var defaultLayoutId: Int = R.layout.trait_photo

    override val parameters: List<BaseFormatParameter> = listOf(
        NameParameter(),
        DetailsParameter()
    )

    override fun getName() = R.string.traits_format_photo

    override fun getIcon() = R.drawable.ic_trait_camera

}