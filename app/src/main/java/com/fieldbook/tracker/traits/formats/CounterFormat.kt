package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter
import com.fieldbook.tracker.traits.formats.parameters.UnitParameter

class CounterFormat : TraitFormat(
    format = Formats.COUNTER,
    defaultLayoutId = R.layout.trait_counter,
    layoutView = null,
    databaseName = "counter",
    nameStringResourceId = R.string.traits_format_counter,
    iconDrawableResourceId = R.drawable.ic_trait_counter,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    UnitParameter(),
    AutoSwitchPlotParameter(),
    RepeatedMeasureParameter(),
    ResourceFileParameter()
), Scannable