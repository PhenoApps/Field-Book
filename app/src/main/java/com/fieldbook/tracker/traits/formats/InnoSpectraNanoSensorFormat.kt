package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R

open class InnoSpectraNanoSensorFormat: BaseSpectralFormat(
    format = Formats.INNO_SPECTRA_NANO_SENSOR,
    databaseName = "inno_spectra",
    nameStringResourceId = R.string.traits_format_inno_spectra_nano_sensor,
    iconDrawableResourceId = R.drawable.lightbulb,
    stringNameAux = null,
)