package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import java.util.UUID
import javax.inject.Inject

class UseDayOfYearParameter @Inject constructor(
    uniqueId: String = UUID.randomUUID().toString(),
    override val nameStringResourceId: Int = R.string.trait_parameter_use_day_of_year,
    override val defaultLayoutId: Int = R.layout.list_item_trait_parameter_default_toggle_value,
    override val parameter: Parameters = Parameters.USE_DAY_OF_YEAR 
) : BaseFormatParameter(
    uniqueId,
    nameStringResourceId,
    defaultLayoutId,
    parameter
) {
    val attributeName = "useDayOfYear"
    val defaultValue = false
}