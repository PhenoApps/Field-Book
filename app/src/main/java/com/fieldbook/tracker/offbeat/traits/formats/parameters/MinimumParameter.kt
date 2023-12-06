package com.fieldbook.tracker.offbeat.traits.formats.parameters

import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

open class MinimumParameter<T : Number>(
    override val nameStringResourceId: Int = R.string.traits_create_value_minimum,
    override val parameter: Parameters = Parameters.MINIMUM,
    override val defaultLayoutId: Int = R.layout.list_item_trait_parameter_numeric,
    private val minimumValue: T? = null,
    override val allowNegative: Boolean? = true,
    override val isInteger: Boolean? = null
) :
    DefaultNumericParameter<T>(
        nameStringResourceId = nameStringResourceId,
        parameter = parameter,
        defaultLayoutId = defaultLayoutId,
        initialDefaultValue = minimumValue,
        allowNegative = allowNegative,
        isInteger = isInteger
    ) {
    override fun createViewHolder(
        itemView: View,
        initialValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean?
    ) = ViewHolder(itemView, minimumValue, this.allowNegative, this.isInteger)

    open inner class ViewHolder(
        itemView: View,
        minimumValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean?
    ) :
        DefaultNumericParameter<T>.ViewHolder(itemView, minimumValue, allowNegative, isInteger) {

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