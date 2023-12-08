package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class LocationFormat : TraitFormat(
    format = Formats.LOCATION,
    defaultLayoutId = R.layout.trait_location,
    layoutView = null,
    nameStringResourceId = R.string.traits_format_location,
    iconDrawableResourceId = R.drawable.ic_trait_location,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter()
)