package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R

open class BaseExperimentalFormat(
    override var format: Formats = Formats.BASE_EXPERIMENTAL,
    override var databaseName: String = "base experimental",
    override var nameStringResourceId: Int = R.string.traits_format_experimental,
    override var iconDrawableResourceId: Int = R.drawable.ic_experimental,
) : TraitFormat(
    format = format,
    defaultLayoutId = -1,
    databaseName = databaseName,
    nameStringResourceId = nameStringResourceId,
    iconDrawableResourceId = iconDrawableResourceId,
)
