package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DefaultNumericParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.MaximumParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.MinimumParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class NumericFormat : TraitFormat() {

    override val format = Formats.NUMERIC

    override var defaultLayoutId: Int = R.layout.trait_numeric

    override val parameters: List<BaseFormatParameter> = listOf(
        NameParameter(),
        DefaultNumericParameter<Double>(),
        MinimumParameter<Double>(),
        MaximumParameter<Double>(),
        DetailsParameter()
    )

    override fun getName() = R.string.traits_format_numeric

    override fun getIcon() = R.drawable.ic_trait_numeric

}