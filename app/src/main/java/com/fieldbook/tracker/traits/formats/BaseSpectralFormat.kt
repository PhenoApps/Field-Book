package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.DefaultValueParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter

open class BaseSpectralFormat(
    format: Formats = Formats.BASE_SPECTRAL,
    defaultLayoutId: Int = R.layout.traits_spectral,
    layoutView: android.view.View? = null,
    databaseName: String = "spectral",
    nameStringResourceId: Int = R.string.traits_format_spectral,
    iconDrawableResourceId: Int = R.drawable.cube_scan,
    stringNameAux: ((android.content.Context) -> String?)? = null,
) : TraitFormat(
    format = format,
    defaultLayoutId = defaultLayoutId,
    layoutView = layoutView,
    databaseName = databaseName,
    nameStringResourceId = nameStringResourceId,
    iconDrawableResourceId = iconDrawableResourceId,
    stringNameAux = stringNameAux,
    NameParameter(),
    DetailsParameter(),
    RepeatedMeasureParameter(),
    ResourceFileParameter()
)