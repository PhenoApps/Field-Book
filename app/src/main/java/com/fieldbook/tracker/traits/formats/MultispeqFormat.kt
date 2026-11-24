package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.MultispeqParametersParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter

class MultispeqFormat : TraitFormat(
    format = Formats.MULTISPEQ,
    defaultLayoutId = R.layout.trait_multispeq,
    layoutView = null,
    databaseName = "multispeq",
    nameStringResourceId = R.string.traits_format_multispeq,
    iconDrawableResourceId = R.drawable.ic_trait_multispeq,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    MultispeqParametersParameter(),
    AutoSwitchPlotParameter(),
    RepeatedMeasureParameter(),
)
