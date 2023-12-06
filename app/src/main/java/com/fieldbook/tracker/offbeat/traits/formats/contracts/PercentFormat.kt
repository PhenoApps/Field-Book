package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DefaultNumericParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.MaximumParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.MinimumParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

/**
 * Values must be positive, values must be within bounds of minimum and maximum values.
 */
class PercentFormat : NumericFormat(
    format = Formats.PERCENT,
    defaultLayoutId = R.layout.trait_percent,
    layoutView = null,
    nameStringResourceId = R.string.traits_format_percent,
    iconDrawableResourceId = R.drawable.ic_trait_percent,
    stringNameAux = null,
    NameParameter(),
    DefaultNumericParameter(initialDefaultValue = 0, allowNegative = false, isInteger = true),
    MinimumParameter(minimumValue = 0, allowNegative = false, isInteger = true),
    MaximumParameter(maximumValue = 100, allowNegative = false, isInteger = true),
    DetailsParameter()
)