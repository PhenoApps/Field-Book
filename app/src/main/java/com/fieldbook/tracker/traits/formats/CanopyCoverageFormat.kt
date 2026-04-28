package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.feature.DisplayValue
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.CanopySensitivityParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter

class CanopyCoverageFormat : TraitFormat(
    format = Formats.CANOPY_COVERAGE,
    defaultLayoutId = R.layout.trait_canopy_coverage,
    layoutView = null,
    databaseName = "canopy_coverage",
    nameStringResourceId = R.string.traits_format_canopy_coverage,
    iconDrawableResourceId = R.drawable.ic_trait_canopy_coverage,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    CanopySensitivityParameter(),
    AutoSwitchPlotParameter(),
    RepeatedMeasureParameter(),
), DisplayValue
