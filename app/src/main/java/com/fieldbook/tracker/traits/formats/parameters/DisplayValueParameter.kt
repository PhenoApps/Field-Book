package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import java.util.UUID
import javax.inject.Inject

class DisplayValueParameter @Inject constructor(
    uniqueId: String = UUID.randomUUID().toString(),
    override val nameStringResourceId: Int = R.string.trait_parameter_display_value,
    override val defaultLayoutId: Int = R.layout.list_item_trait_parameter_default_toggle_value,
    override val parameter: Parameters = Parameters.DISPLAY_VALUE
) : BaseFormatParameter(
    uniqueId,
    nameStringResourceId,
    defaultLayoutId,
    parameter
) {
    val attributeName = "displayValue"
    val defaultValue = false
}