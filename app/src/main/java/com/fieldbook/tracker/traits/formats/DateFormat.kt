package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter

class DateFormat : TraitFormat(
    format = Formats.DATE,
    defaultLayoutId = R.layout.trait_date,
    layoutView = null,
    databaseName = "date",
    nameStringResourceId = R.string.traits_format_date,
    iconDrawableResourceId = R.drawable.ic_trait_date,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter()
)