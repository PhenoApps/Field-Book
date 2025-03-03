package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter

class CounterFormat : TraitFormat(
    format = Formats.COUNTER,
    defaultLayoutId = R.layout.trait_counter,
    layoutView = null,
    databaseName = "counter",
    nameStringResourceId = R.string.traits_format_counter,
    iconDrawableResourceId = R.drawable.ic_trait_counter,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter()
), Scannable