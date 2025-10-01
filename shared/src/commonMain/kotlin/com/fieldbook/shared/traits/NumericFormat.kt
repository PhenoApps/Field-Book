package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_numeric
import com.fieldbook.shared.generated.resources.traits_format_numeric

class NumericFormat : TraitFormat(
    format = Formats.NUMERIC,
    nameStringResource = Res.string.traits_format_numeric,
    iconDrawableResource = Res.drawable.ic_trait_numeric,
)
