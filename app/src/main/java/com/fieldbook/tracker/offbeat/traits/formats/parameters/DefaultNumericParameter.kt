package com.fieldbook.tracker.offbeat.traits.formats.parameters

import android.content.Context
import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

/**
 * Handles signed decimal UI entry for default values.
 */
open class DefaultNumericParameter<T : Number>(
    private val initialDefaultValue: T? = null,
    override val allowNegative: Boolean? = null,
    override val isInteger: Boolean? = null
) :
    NumericParameter<T>(initialDefaultValue, allowNegative, isInteger) {

    override val viewType = Parameters.DEFAULT_VALUE.ordinal
    override fun getName(ctx: Context) = ctx.getString(R.string.traits_create_value_default)

    override fun createViewHolder(
        itemView: View,
        initialValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean?
    ) = ViewHolder(itemView, this.initialValue, this.allowNegative, this.isInteger)

    open inner class ViewHolder(
        itemView: View,
        initialValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean?
    ) :
        NumericParameter.ViewHolder<T>(itemView, initialValue, allowNegative, isInteger) {

        init {
            numericEt.setHint(R.string.traits_create_optional)
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            defaultValue = numericEt.text.toString()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                numericEt.setText(traitObject?.defaultValue ?: "")
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }
    }
}