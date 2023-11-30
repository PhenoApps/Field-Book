package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class GoProFormat : TraitFormat() {

    override val format = Formats.GO_PRO

    override var defaultLayoutId: Int = R.layout.trait_go_pro

    override val parameters: List<BaseFormatParameter> = listOf(
        NameParameter(),
        DetailsParameter()
    )

    override fun getName() = R.string.traits_format_go_pro_camera

    override fun getIcon() = R.drawable.ic_trait_gopro

}