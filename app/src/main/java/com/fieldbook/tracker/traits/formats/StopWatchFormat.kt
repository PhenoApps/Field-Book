package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter

open class StopWatchFormat : TraitFormat(
    format = Formats.STOP_WATCH,
    defaultLayoutId = R.layout.trait_stop_watch,
    layoutView = null,
    databaseName = "stop_watch",
    nameStringResourceId = R.string.traits_format_stop_watch,
    iconDrawableResourceId = R.drawable.timer,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    AutoSwitchPlotParameter(),
    RepeatedMeasureParameter(),
    ResourceFileParameter(),
    AttachMediaParameter()
)