package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class PhotoFormat : TraitFormat(
    format = Formats.CAMERA,
    defaultLayoutId = R.layout.trait_photo,
    layoutView = null,
    nameStringResourceId = R.string.traits_format_photo,
    iconDrawableResourceId = R.drawable.ic_trait_camera,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter()
)