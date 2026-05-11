package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R

/**
 * Video format trait. Reuses the camera trait layout and presenters from BasePhotoFormat
 */
open class VideoFormat : BasePhotoFormat(
    format = Formats.VIDEO,
    defaultLayoutId = R.layout.trait_camera,
    layoutView = null,
    databaseName = "video",
    nameStringResourceId = R.string.traits_format_video,
    iconDrawableResourceId = R.drawable.video,
    stringNameAux = null
)