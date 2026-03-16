package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R

open class HardwareFormat(
    override var format: Formats = Formats.HARDWARE,
    override var databaseName: String = "hardware",
    override var nameStringResourceId: Int = R.string.traits_format_hardware,
    override var iconDrawableResourceId: Int = R.drawable.ic_trait_camera,
) : TraitFormat(
    format = format,
    defaultLayoutId = -1,
    databaseName = databaseName,
    nameStringResourceId = nameStringResourceId,
    iconDrawableResourceId = iconDrawableResourceId,
)