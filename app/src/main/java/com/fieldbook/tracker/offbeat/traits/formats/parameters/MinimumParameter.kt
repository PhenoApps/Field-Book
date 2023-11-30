package com.fieldbook.tracker.offbeat.traits.formats.parameters

import android.content.Context
import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

open class MinimumParameter<T : Number>(
    private val minimumValue: T? = null,
    override val allowNegative: Boolean? = null,
    override val isInteger: Boolean? = null
) :
    DefaultNumericParameter<T>(minimumValue, allowNegative, isInteger) {

    override val viewType = Parameters.MINIMUM.ordinal
    override fun getName(ctx: Context) = ctx.getString(R.string.traits_create_value_minimum)

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
                numericEt.setText(traitObject?.minimum?.toString())
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }
    }
}