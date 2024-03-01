package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats

class CanonFormat : PhotoFormat(
    format = Formats.CANON,
    defaultLayoutId = R.layout.trait_camera,
    layoutView = null,
    nameStringResourceId = R.string.traits_format_canon,
    iconDrawableResourceId = R.drawable.camera_24px,
    stringNameAux = null)