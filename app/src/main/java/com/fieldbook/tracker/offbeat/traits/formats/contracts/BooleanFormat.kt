package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DefaultToggleValueParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class BooleanFormat : TraitFormat() {

    override val format = Formats.BOOLEAN

    override var defaultLayoutId: Int = R.layout.trait_boolean

    override val parameters: List<BaseFormatParameter> = listOf(
        NameParameter(),
        DefaultToggleValueParameter(),
        DetailsParameter()
    )

    override fun getName() = R.string.traits_format_boolean

    override fun getIcon() = R.drawable.ic_trait_boolean

}