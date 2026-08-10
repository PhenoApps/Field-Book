package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.feature.DisplayValue
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.CanopySensitivityParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter

class CanopyCoverFormat : TraitFormat(
    format = Formats.CANOPY_COVER,
    defaultLayoutId = R.layout.trait_canopy_cover,
    layoutView = null,
    databaseName = "canopy_cover",
    nameStringResourceId = R.string.traits_format_canopy_cover,
    iconDrawableResourceId = R.drawable.ic_trait_canopy_cover,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    CanopySensitivityParameter(),
    AutoSwitchPlotParameter(),
    RepeatedMeasureParameter(),
), DisplayValue
