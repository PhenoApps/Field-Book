package com.fieldbook.tracker.offbeat.traits.formats.parameters

import android.content.Context
import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

open class MaximumParameter<T : Number>(
    private val maximumValue: T? = null,
    override val allowNegative: Boolean? = null,
    override val isInteger: Boolean? = null
) :
    DefaultNumericParameter<T>(maximumValue, allowNegative) {

    override val viewType = Parameters.MAXIMUM.ordinal
    override fun getName(ctx: Context) = ctx.getString(R.string.traits_create_value_maximum)

    override fun createViewHolder(
        itemView: View,
        initialValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean?
    ) = ViewHolder(itemView, maximumValue, this.allowNegative)

    open inner class ViewHolder(
        itemView: View,
        maximumValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean? = null
    ) :
        DefaultNumericParameter<T>.ViewHolder(itemView, maximumValue, allowNegative, isInteger) {

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            maximum = numericEt.text.toString()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                numericEt.setText(traitObject?.maximum ?: "")
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }
    }
}