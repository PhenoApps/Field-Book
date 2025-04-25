package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R

class DisplayValueParameter : BaseFormatParameter12<Boolean> {
    override val attributeName = "displayValue"
    override val defaultValue = false
    override val nameResourceId = R.string.trait_parameter_display_value
}