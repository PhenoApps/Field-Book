package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_camera
import com.fieldbook.shared.generated.resources.traits_format_photo
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

open class BasePhotoFormat(
    override val format: Formats = Formats.CAMERA,
    override val nameStringResource: StringResource = Res.string.traits_format_photo,
    override val iconDrawableResource: DrawableResource = Res.drawable.ic_trait_camera,
) : TraitFormat(
    format = format,
    nameStringResource = nameStringResource,
    iconDrawableResource = iconDrawableResource,
)

