package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.DefaultToggleValueParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter

class BooleanFormat : TraitFormat(
    format = Formats.BOOLEAN,
    defaultLayoutId = R.layout.trait_boolean,
    layoutView = null,
    databaseName = "boolean",
    nameStringResourceId = R.string.traits_format_boolean,
    iconDrawableResourceId = R.drawable.ic_trait_boolean,
    stringNameAux = null,
    NameParameter(),
    DefaultToggleValueParameter(),
    DetailsParameter(),
), Scannable