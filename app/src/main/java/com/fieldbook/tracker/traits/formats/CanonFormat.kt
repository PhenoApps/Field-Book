package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R

class CanonFormat : BasePhotoFormat(
    format = Formats.CANON,
    nameStringResourceId = R.string.traits_format_canon,
    databaseName = "canon",
    iconDrawableResourceId = R.drawable.camera_24px,
)