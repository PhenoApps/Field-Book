package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.feature.DisplayValue
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.DefaultValueParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter

class GreenSeekerFormat : TraitFormat(
    format = Formats.GREEN_SEEKER,
    defaultLayoutId = R.layout.trait_green_seeker,
    layoutView = null,
    databaseName = "green_seeker",
    nameStringResourceId = R.string.traits_format_green_seeker,
    iconDrawableResourceId = R.drawable.wall_sconce_round_outline,
    stringNameAux = null,
    NameParameter(),
    DefaultValueParameter(),
    DetailsParameter(),
    AutoSwitchPlotParameter(),
    RepeatedMeasureParameter(),
    ResourceFileParameter(),
    AttachMediaParameter()
), DisplayValue