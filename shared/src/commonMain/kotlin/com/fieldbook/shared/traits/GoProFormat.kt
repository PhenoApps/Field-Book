package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_gopro
import com.fieldbook.shared.generated.resources.traits_format_go_pro_camera

class GoProFormat : TraitFormat(
    format = Formats.GO_PRO,
    nameStringResource = Res.string.traits_format_go_pro_camera,
    iconDrawableResource = Res.drawable.ic_trait_gopro,
)

