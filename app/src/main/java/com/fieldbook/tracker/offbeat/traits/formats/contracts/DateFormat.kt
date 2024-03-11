package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class DateFormat : TraitFormat(
    format = Formats.DATE,
    defaultLayoutId = R.layout.trait_date,
    layoutView = null,
    nameStringResourceId = R.string.traits_format_date,
    iconDrawableResourceId = R.drawable.ic_trait_date,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter()
)