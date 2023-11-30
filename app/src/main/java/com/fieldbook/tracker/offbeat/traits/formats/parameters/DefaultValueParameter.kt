package com.fieldbook.tracker.offbeat.traits.formats.parameters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.offbeat.traits.formats.ValidationResult

class DefaultValueParameter(private val initialDefaultValue: Int? = null) : BaseFormatParameter() {

    override val viewType = Parameters.DEFAULT_VALUE.ordinal
    override fun getName(ctx: Context) = ctx.getString(R.string.traits_create_value_default)

    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_default_value, parent, false)
        return ViewHolder(v)
    }

    inner class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        val defaultValueEt =
            itemView.findViewById<EditText>(R.id.list_item_trait_parameter_default_value_et).also {
                initialDefaultValue?.let { value ->
                    it.setText("$value")
                }
                it.setHint(R.string.traits_create_optional)
            }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            defaultValue = defaultValueEt.text.toString()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                defaultValueEt.setText(traitObject?.defaultValue ?: "")
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }

        override fun validate(
            database: DataHelper,
            initialTraitObject: TraitObject?
        ) = ValidationResult()
    }
}