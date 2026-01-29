package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter

class LocationFormat : TraitFormat(
    format = Formats.LOCATION,
    defaultLayoutId = R.layout.trait_location,
    layoutView = null,
    databaseName = "location",
    nameStringResourceId = R.string.traits_format_location,
    iconDrawableResourceId = R.drawable.ic_trait_location,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    AutoSwitchPlotParameter(),
    RepeatedMeasureParameter(),
    ResourceFileParameter(),
    AttachMediaParameter()
), Scannable