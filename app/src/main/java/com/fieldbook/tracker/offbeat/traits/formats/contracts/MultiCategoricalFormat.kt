package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.CategoriesParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class MultiCategoricalFormat : TraitFormat() {

    override val format = Formats.MULTI_CATEGORICAL

    override var defaultLayoutId: Int = R.layout.trait_multicat

    override val parameters: List<BaseFormatParameter> = listOf(
        NameParameter(),
        DetailsParameter(),
        CategoriesParameter(),
    )

    override fun getName() = R.string.traits_format_multicategorical

    override fun getIcon() = R.drawable.ic_trait_multicat

}