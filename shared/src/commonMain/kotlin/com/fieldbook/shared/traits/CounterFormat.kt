package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_counter
import com.fieldbook.shared.generated.resources.traits_format_counter

class CounterFormat : TraitFormat(
    format = Formats.COUNTER,
    nameStringResource = Res.string.traits_format_counter,
    iconDrawableResource = Res.drawable.ic_trait_counter,
)
