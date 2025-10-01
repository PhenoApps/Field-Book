package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_location
import com.fieldbook.shared.generated.resources.traits_format_location
import com.fieldbook.shared.traits.Formats

class LocationFormat : TraitFormat(
    format = Formats.LOCATION,
    nameStringResource = Res.string.traits_format_location,
    iconDrawableResource = Res.drawable.ic_trait_location,
)
