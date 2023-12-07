package com.fieldbook.tracker.offbeat.traits.formats.parameters

import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

/**
 * Handles signed decimal UI entry for default values.
 */
open class DefaultNumericParameter<T : Number>(
    override val nameStringResourceId: Int = R.string.traits_create_value_default,
    override val defaultLayoutId: Int = R.layout.list_item_trait_parameter_numeric,
    override val parameter: Parameters = Parameters.DEFAULT_VALUE,
    open val initialDefaultValue: T? = null,
    override val allowNegative: Boolean? = true,
    override val isInteger: Boolean? = null
) :
    NumericParameter<T>(
        nameStringResourceId = nameStringResourceId,
        defaultLayoutId = defaultLayoutId,
        parameter = parameter,
        initialValue = initialDefaultValue,
        allowNegative = allowNegative,
        isInteger = isInteger
    ) {

    override fun createViewHolder(
        itemView: View,
        initialValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean?
    ) = ViewHolder(itemView, this.initialDefaultValue, this.allowNegative, this.isInteger)

    open inner class ViewHolder(
        itemView: View,
        initialValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean?
    ) :
        NumericParameter.ViewHolder<T>(itemView, initialValue, allowNegative, isInteger) {

        init {
            numericEt.setHint(R.string.traits_create_optional)
            super.initialize()
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            defaultValue = numericEt.text.toString()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                traitObject?.defaultValue?.let { defaultValue ->
                    numericEt.setText(defaultValue)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }
    }
}