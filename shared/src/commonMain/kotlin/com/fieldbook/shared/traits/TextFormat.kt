package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_text
import com.fieldbook.shared.generated.resources.traits_format_text

class TextFormat : TraitFormat(
    format = Formats.TEXT,
    nameStringResource = Res.string.traits_format_text,
    iconDrawableResource = Res.drawable.ic_trait_text,
)
