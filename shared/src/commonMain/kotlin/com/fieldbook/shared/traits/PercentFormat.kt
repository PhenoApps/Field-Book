package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_percent
import com.fieldbook.shared.generated.resources.traits_format_percent

class PercentFormat : TraitFormat(
    format = Formats.PERCENT,
    nameStringResource = Res.string.traits_format_percent,
    iconDrawableResource = Res.drawable.ic_trait_percent,
)
