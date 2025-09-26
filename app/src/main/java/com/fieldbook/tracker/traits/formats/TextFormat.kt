package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.CloseKeyboardParameter
import com.fieldbook.tracker.traits.formats.parameters.DefaultValueParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter

class TextFormat : TraitFormat(
    format = Formats.TEXT,
    defaultLayoutId = R.layout.trait_text,
    layoutView = null,
    databaseName = "text",
    nameStringResourceId = R.string.traits_format_text,
    iconDrawableResourceId = R.drawable.ic_trait_text,
    stringNameAux = null,
    NameParameter(),
    DefaultValueParameter(),
    DetailsParameter(),
    CloseKeyboardParameter(false),
    RepeatedMeasureParameter(),
    ResourceFileParameter()
), Scannable