package com.fieldbook.tracker.traits.formats.parameters

import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

/**
 * Five minutes, used when a trait has no duration stored yet.
 */
const val DEFAULT_DURATION_SECONDS = 300

/**
 * Observation duration in seconds, stored in the trait minimum field. Defaults to five minutes.
 * Uses its own parameter type so it is displayed as a chip on the trait detail screen,
 * unlike the minimum bound it shares storage with.
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
            minimum = numericEt.text.toString().ifBlank { "$DEFAULT_DURATION_SECONDS" }
        }

        override fun load(traitObject: TraitObject?): Boolean {
            numericEt.setText(traitObject?.minimum?.takeIf { it.isNotEmpty() }
                ?: "$DEFAULT_DURATION_SECONDS")
            return true
        }
    }
}
