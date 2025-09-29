package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_angle
import com.fieldbook.shared.generated.resources.traits_format_angle

class AngleFormat : TraitFormat(
    format = Formats.ANGLE,
    nameStringResource = Res.string.traits_format_angle,
    iconDrawableResource = Res.drawable.ic_trait_angle,
)
