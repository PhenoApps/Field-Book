package com.fieldbook.tracker.traits.formats.parameters

import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

open class MinimumParameter<T : Number>(
    override val nameStringResourceId: Int = R.string.traits_create_value_minimum,
    override val parameter: Parameters = Parameters.MINIMUM,
    override val defaultLayoutId: Int = R.layout.list_item_trait_parameter_numeric,
    private val minimumValue: T? = null,
    override val allowNegative: Boolean? = true,
    override val isInteger: Boolean? = null,
    override val isRequired: Boolean? = false,
) :
    DefaultNumericParameter<T>(
        nameStringResourceId = nameStringResourceId,
        parameter = parameter,
        defaultLayoutId = defaultLayoutId,
        initialDefaultValue = minimumValue,
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
    ) = ViewHolder(itemView, minimumValue, this.allowNegative, this.isInteger, this.isRequired)

    open inner class ViewHolder(
        itemView: View,
        minimumValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean?,
        override val isRequired: Boolean?,
    ) :
        DefaultNumericParameter<T>.ViewHolder(
            itemView,
            minimumValue,
            allowNegative,
            isInteger,
            isRequired
        ) {

        init {
            super.initialize()
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            minimum = numericEt.text.toString()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                traitObject?.minimum?.let { minValue ->
                    numericEt.setText(minValue)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }
    }
}