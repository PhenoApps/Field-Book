package com.fieldbook.tracker.traits.formats.parameters

import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

/**
 * Observation duration in seconds, stored in the trait minimum field. Defaults to five minutes.
 */
class DurationParameter : MinimumParameter<Int>(
    nameStringResourceId = R.string.trait_pollinator_duration_label,
    parameter = Parameters.MINIMUM,
    minimumValue = 300,
    allowNegative = false,
    isInteger = true,
    isRequired = true
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
            minimum = numericEt.text.toString().ifBlank { "300" }
        }

        override fun load(traitObject: TraitObject?): Boolean {
            numericEt.setText(traitObject?.minimum?.takeIf { it.isNotEmpty() } ?: "300")
            return true
        }
    }
}
