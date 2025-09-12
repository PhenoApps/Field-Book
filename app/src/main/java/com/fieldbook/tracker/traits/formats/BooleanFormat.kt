package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.DefaultRadioValueParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter

class BooleanFormat : TraitFormat(
    format = Formats.BOOLEAN,
    defaultLayoutId = R.layout.trait_boolean,
    layoutView = null,
    databaseName = "boolean",
    nameStringResourceId = R.string.traits_format_boolean,
    iconDrawableResourceId = R.drawable.ic_trait_boolean,
    stringNameAux = null,
    NameParameter(),
    DefaultRadioValueParameter(),
    DetailsParameter(),
    ResourceFileParameter()
), Scannable