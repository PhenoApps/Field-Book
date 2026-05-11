package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

class MathSymbolsParameter : DefaultToggleParameter(
    nameStringResourceId = R.string.trait_parameter_mathematical_symbols,
    parameter = Parameters.MATHEMATICAL_SYMBOLS
) {
    override fun getToggleValue(traitObject: TraitObject?): Boolean =
        traitObject?.mathSymbolsEnabled == true

    override fun setToggleValue(traitObject: TraitObject, value: Boolean) {
        traitObject.mathSymbolsEnabled = value

        if (value) { // reset decimal places to default
            traitObject.maxDecimalPlaces = "-1"
        }
    }
}