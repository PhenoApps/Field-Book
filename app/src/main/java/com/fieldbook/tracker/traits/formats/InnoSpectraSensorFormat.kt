package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R

open class InnoSpectraSensorFormat: BaseSpectralFormat(
    format = Formats.INNO_SPECTRA_SENSOR,
    databaseName = "inno_spectra",
    nameStringResourceId = R.string.traits_format_inno_spectra,
    iconDrawableResourceId = R.drawable.lightbulb,
    stringNameAux = null,
)