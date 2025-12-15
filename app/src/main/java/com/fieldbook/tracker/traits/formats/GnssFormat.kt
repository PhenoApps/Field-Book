package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.MultiMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter

class GnssFormat : TraitFormat(
    format = Formats.GNSS,
    defaultLayoutId = R.layout.trait_gnss,
    layoutView = null,
    databaseName = "gnss",
    nameStringResourceId = R.string.traits_format_gnss,
    iconDrawableResourceId = R.drawable.ic_trait_gnss,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    AutoSwitchPlotParameter(),
    RepeatedMeasureParameter(),
    ResourceFileParameter(),
    MultiMediaParameter()
), Scannable