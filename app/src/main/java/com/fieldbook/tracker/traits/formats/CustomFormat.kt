package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R

open class CustomFormat(
    override var format: Formats = Formats.CUSTOM,
    override var databaseName: String = "custom",
    override var nameStringResourceId: Int = R.string.traits_format_custom,
    override var iconDrawableResourceId: Int = R.drawable.pencil_ruler,
) : TraitFormat(
    format = format,
    defaultLayoutId = -1,
    databaseName = databaseName,
    nameStringResourceId = nameStringResourceId,
    iconDrawableResourceId = iconDrawableResourceId,
)