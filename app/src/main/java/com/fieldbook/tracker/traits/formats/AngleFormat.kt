package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter

class AngleFormat : TraitFormat(
    format = Formats.ANGLE,
    defaultLayoutId = R.layout.trait_angle,
    layoutView = null,
    databaseName = "angle",
    nameStringResourceId = R.string.traits_format_angle,
    iconDrawableResourceId = R.drawable.ic_trait_angle,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    AutoSwitchPlotParameter(),
    RepeatedMeasureParameter(),
    ResourceFileParameter(),
    AttachMediaParameter()
)