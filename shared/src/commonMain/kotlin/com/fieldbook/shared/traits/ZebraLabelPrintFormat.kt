package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_labelprint
import com.fieldbook.shared.generated.resources.traits_format_labelprint

class ZebraLabelPrintFormat : TraitFormat(
    format = Formats.LABEL_PRINT,
    nameStringResource = Res.string.traits_format_labelprint,
    iconDrawableResource = Res.drawable.ic_trait_labelprint,
)

