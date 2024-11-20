package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R

class GoProFormat : BasePhotoFormat(
    format = Formats.GO_PRO,
    nameStringResourceId = R.string.traits_format_go_pro_camera,
    databaseName = "gopro",
    iconDrawableResourceId = R.drawable.ic_trait_gopro,
)