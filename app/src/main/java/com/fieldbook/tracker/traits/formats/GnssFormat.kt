package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter

class GnssFormat : TraitFormat(
    format = Formats.GNSS,
    defaultLayoutId = R.layout.trait_gnss,
    layoutView = null,
    databaseName = "gnss",
    nameStringResourceId = R.string.traits_format_gnss,
    iconDrawableResourceId = R.drawable.ic_trait_gnss,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter()
)