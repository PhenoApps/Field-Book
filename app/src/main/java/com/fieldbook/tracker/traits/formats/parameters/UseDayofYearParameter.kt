package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R

class UseDayOfYearParameter : BaseFormatParameter<Boolean> {
    override val attributeName = "useDayOfYear"
    override val defaultValue = false
    override val nameResourceId = R.string.trait_parameter_use_day_of_year
    
}