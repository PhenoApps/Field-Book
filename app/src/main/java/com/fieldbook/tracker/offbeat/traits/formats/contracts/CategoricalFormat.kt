package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.CategoriesParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

/**
 * TODO add multicategorical checkbox UI catch-all "CategoricalOptionsParameter"
 */
class CategoricalFormat : TraitFormat() {

    override val format = Formats.CATEGORICAL

    override var defaultLayoutId: Int = R.layout.trait_categorical

    override val parameters: List<BaseFormatParameter> = listOf(
        NameParameter(),
        DetailsParameter(),
        CategoriesParameter(),
    )

    override fun getName() = R.string.traits_format_categorical

    override fun getIcon() = R.drawable.ic_trait_categorical

}