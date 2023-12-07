package com.fieldbook.tracker.offbeat.traits.formats.parameters

import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

open class MaximumParameter<T : Number>(
    override val nameStringResourceId: Int = R.string.traits_create_value_maximum,
    override val parameter: Parameters = Parameters.MAXIMUM,
    override val defaultLayoutId: Int = R.layout.list_item_trait_parameter_numeric,
    private val maximumValue: T? = null,
    override val allowNegative: Boolean? = true,
    override val isInteger: Boolean? = null,
    override val isRequired: Boolean? = false,
) :
    DefaultNumericParameter<T>(
        nameStringResourceId = nameStringResourceId,
        parameter = parameter,
        defaultLayoutId = defaultLayoutId,
        initialDefaultValue = maximumValue,
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
    ) = ViewHolder(itemView, maximumValue, this.allowNegative, this.isInteger, this.isRequired)

    open inner class ViewHolder(
        itemView: View,
        maximumValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean? = null,
        override val isRequired: Boolean? = false,
    ) :
        DefaultNumericParameter<T>.ViewHolder(
            itemView,
            maximumValue,
            allowNegative,
            isInteger,
            isRequired
        ) {

        init {
            super.initialize()
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            maximum = numericEt.text.toString()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                traitObject?.maximum?.let { maxVal ->
                    numericEt.setText(maxVal)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }
    }
}