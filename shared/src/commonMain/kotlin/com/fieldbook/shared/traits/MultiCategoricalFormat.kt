package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_multicat
import com.fieldbook.shared.generated.resources.traits_format_multicategorical

class MultiCategoricalFormat : TraitFormat(
    format = Formats.MULTI_CATEGORICAL,
    nameStringResource = Res.string.traits_format_multicategorical,
    iconDrawableResource = Res.drawable.ic_trait_multicat,
)
