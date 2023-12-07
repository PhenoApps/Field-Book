package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DefaultValueParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class TextFormat : TraitFormat(
    format = Formats.TEXT,
    defaultLayoutId = R.layout.trait_text,
    layoutView = null,
    nameStringResourceId = R.string.traits_format_text,
    iconDrawableResourceId = R.drawable.ic_trait_text,
    stringNameAux = null,
    NameParameter(),
    DefaultValueParameter(),
    DetailsParameter()
)