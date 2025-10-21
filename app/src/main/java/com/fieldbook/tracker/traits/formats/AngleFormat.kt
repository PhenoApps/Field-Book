package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.TraitFormat
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter

class AngleFormat : TraitFormat(
    format = Formats.ANGLE,
    defaultLayoutId = R.layout.trait_angle,
    layoutView = null,
    databaseName = "angle",
    nameStringResourceId = R.string.traits_format_angle,
    iconDrawableResourceId = R.drawable.ic_trait_angle,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter()
)