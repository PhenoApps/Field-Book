package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DefaultToggleValueParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class BooleanFormat : TraitFormat(
    format = Formats.BOOLEAN,
    defaultLayoutId = R.layout.trait_boolean,
    layoutView = null,
    nameStringResourceId = R.string.traits_format_boolean,
    iconDrawableResourceId = R.drawable.ic_trait_boolean,
    stringNameAux = null,
    NameParameter(),
    DefaultToggleValueParameter(),
    DetailsParameter()
)