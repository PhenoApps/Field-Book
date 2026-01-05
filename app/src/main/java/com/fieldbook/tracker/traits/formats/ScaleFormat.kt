package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.DefaultValueParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter

class ScaleFormat : TraitFormat(
    format = Formats.SCALE,
    defaultLayoutId = R.layout.trait_scale,
    layoutView = null,
    databaseName = "scale",
    nameStringResourceId = R.string.traits_format_scale,
    iconDrawableResourceId = R.drawable.scale,
    stringNameAux = null,
    NameParameter(),
    DefaultValueParameter(),
    DetailsParameter(),
    AutoSwitchPlotParameter(),
    RepeatedMeasureParameter(),
    ResourceFileParameter(),
    AttachMediaParameter()
)