package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class AudioFormat : TraitFormat() {

    override val format = Formats.AUDIO

    override var defaultLayoutId: Int = R.layout.trait_audio

    override val parameters: List<BaseFormatParameter> = listOf(
        NameParameter(),
        DetailsParameter(),
    )

    override fun getName() = R.string.traits_format_audio

    override fun getIcon(): Int = R.drawable.trait_audio

}