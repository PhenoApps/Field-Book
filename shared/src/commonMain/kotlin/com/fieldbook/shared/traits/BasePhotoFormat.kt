package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_camera
import com.fieldbook.shared.generated.resources.traits_format_photo

open class BasePhotoFormat(
    private val databaseName: String = "photo",
) : TraitFormat(
    format = Formats.PHOTO,
    nameStringResource = Res.string.traits_format_photo,
    iconDrawableResource = Res.drawable.ic_trait_camera,
)

