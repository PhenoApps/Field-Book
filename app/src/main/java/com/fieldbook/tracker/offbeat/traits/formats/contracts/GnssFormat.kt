package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

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