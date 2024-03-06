package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class CounterFormat : TraitFormat(
    format = Formats.COUNTER,
    defaultLayoutId = R.layout.trait_counter,
    layoutView = null,
    nameStringResourceId = R.string.traits_format_counter,
    iconDrawableResourceId = R.drawable.ic_trait_counter,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter()
)