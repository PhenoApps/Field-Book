package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_categorical
import com.fieldbook.shared.generated.resources.traits_format_categorical

class CategoricalFormat : TraitFormat(
    format = Formats.CATEGORICAL,
    nameStringResource = Res.string.traits_format_categorical,
    iconDrawableResource = Res.drawable.ic_trait_categorical,
)
