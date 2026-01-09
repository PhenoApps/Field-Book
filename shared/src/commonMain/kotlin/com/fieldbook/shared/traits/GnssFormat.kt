package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_gnss
import com.fieldbook.shared.generated.resources.traits_format_gnss

class GnssFormat : TraitFormat(
    iconDrawableResource = Res.drawable.ic_trait_gnss,
    nameStringResource = Res.string.traits_format_gnss,
    format = Formats.GNSS,
)


