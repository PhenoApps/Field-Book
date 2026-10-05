package com.fieldbook.tracker.traits.formats.parameters

import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject

/**
 * Number of digits after which the numeric trait layout automatically moves to the next entry.
 * Blank disables auto advance. Reuses the minimum parameter UI for integer entry, but the value
 * is stored in its own trait attribute.
 */
class AutoAdvanceDigitsParameter : MinimumParameter<Int>(
    nameStringResourceId = R.string.trait_parameter_auto_advance_digits,
    parameter = Parameters.AUTO_ADVANCE_DIGITS,
    allowNegative = false,
    isInteger = true,
    isRequired = false
) {
    override fun createViewHolder(
        itemView: View,
        initialValue: Int?,
        allowNegative: Boolean?,
        isInteger: Boolean?,
        isRequired: Boolean?,
    ) = ViewHolder(itemView, initialValue, allowNegative, isInteger, isRequired)

    inner class ViewHolder(
        itemView: View,
        initialValue: Int?,
        allowNegative: Boolean?,
        isInteger: Boolean?,
        override val isRequired: Boolean?,
    ) : MinimumParameter<Int>.ViewHolder(itemView, initialValue, allowNegative, isInteger, isRequired) {

        init {
            super.initialize()
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            autoAdvanceDigits = numericEt.text.toString().trim()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            numericEt.setText(traitObject?.autoAdvanceDigits ?: "")
            return true
        }

        override fun validate(
            traitRepo: TraitRepository,
            initialTraitObject: TraitObject?
        ) = super.validate(traitRepo, initialTraitObject).apply {
            val digits = numericEt.text.toString().trim()
            if (result == true && digits.isNotEmpty() && (digits.toIntOrNull() ?: 0) < 1) {
                result = false
                error = itemView.context.getString(R.string.trait_error_auto_advance_digits_min)
                numericEt.error = error
            }
        }
    }
}
