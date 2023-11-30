package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class LocationFormat : TraitFormat() {

    override val format = Formats.LOCATION

    override var defaultLayoutId: Int = R.layout.trait_location

    override val parameters: List<BaseFormatParameter> = listOf(
        NameParameter(),
        DetailsParameter(),
    )

    override fun getName() = R.string.traits_format_location

    override fun getIcon() = R.drawable.ic_trait_location

}