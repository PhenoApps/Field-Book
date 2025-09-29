package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_boolean
import com.fieldbook.shared.generated.resources.traits_format_boolean

class BooleanFormat : TraitFormat(
    format = Formats.BOOLEAN,
    nameStringResource = Res.string.traits_format_boolean,
    iconDrawableResource = Res.drawable.ic_trait_boolean,
)
