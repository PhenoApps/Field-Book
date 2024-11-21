package com.fieldbook.tracker.traits.formats.parameters

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
    override val isInteger: Boolean? = null,
    override val isRequired: Boolean? = false,
) :
    NumericParameter<T>(
        nameStringResourceId = nameStringResourceId,
        defaultLayoutId = defaultLayoutId,
        parameter = parameter,
        initialValue = initialDefaultValue,
        allowNegative = allowNegative,
        isInteger = isInteger,
        isRequired = isRequired
    ) {

    override fun createViewHolder(
        itemView: View,
        initialValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean?,
        isRequired: Boolean?,
    ) = ViewHolder(
        itemView,
        this.initialDefaultValue,
        this.allowNegative,
        this.isInteger,
        this.isRequired
    )

    open inner class ViewHolder(
        itemView: View,
        initialValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean?,
        isRequired: Boolean?,
    ) :
        NumericParameter.ViewHolder<T>(
            itemView,
            initialValue,
            allowNegative,
            isInteger,
            isRequired
        ) {

        init {
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