package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_audio
import com.fieldbook.shared.generated.resources.traits_format_audio
import com.fieldbook.shared.traits.Formats

class AudioFormat : TraitFormat(
    format = Formats.AUDIO,
    nameStringResource = Res.string.traits_format_audio,
    iconDrawableResource = Res.drawable.ic_trait_audio,
)
