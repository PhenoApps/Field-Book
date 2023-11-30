package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DefaultValueParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class TextFormat : TraitFormat() {

    override val format = Formats.TEXT

    override var defaultLayoutId: Int = R.layout.trait_text

    override val parameters: List<BaseFormatParameter> = listOf(
        NameParameter(),
        DefaultValueParameter(),
        DetailsParameter()
    )

    override fun getName() = R.string.traits_format_text

    override fun getIcon() = R.drawable.ic_trait_text

}