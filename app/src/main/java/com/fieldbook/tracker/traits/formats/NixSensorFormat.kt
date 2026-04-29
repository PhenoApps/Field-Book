package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R

open class NixSensorFormat: BaseSpectralFormat(
    format = Formats.NIX,
    databaseName = "nix",
    nameStringResourceId = R.string.traits_format_nix,
    iconDrawableResourceId = R.drawable.diamond_stone,
    stringNameAux = null,
)