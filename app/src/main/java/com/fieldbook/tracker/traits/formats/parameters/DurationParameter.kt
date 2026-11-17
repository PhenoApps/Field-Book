package com.fieldbook.tracker.traits.formats.parameters

import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

/**
 * Five minutes, used when a trait has no duration stored yet.
 */
const val DEFAULT_DURATION_SECONDS = 300

/**
 * Observation duration in seconds, stored in its own trait attribute. Defaults to five minutes.
 * Reuses the minimum parameter UI for numeric entry, but neither the parameter type nor the
 * stored attribute is shared with the numeric minimum bound.
 */
class DurationParameter : MinimumParameter<Int>(
    nameStringResourceId = R.string.trait_pollinator_duration_label,
    parameter = Parameters.DURATION,
    minimumValue = DEFAULT_DURATION_SECONDS,
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
            duration = numericEt.text.toString().ifBlank { "$DEFAULT_DURATION_SECONDS" }
        }

        override fun load(traitObject: TraitObject?): Boolean {
            numericEt.setText(traitObject?.duration?.takeIf { it.isNotEmpty() }
                ?: "$DEFAULT_DURATION_SECONDS")
            return true
        }
    }
}
