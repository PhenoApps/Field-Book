package com.fieldbook.tracker.traits.formats.parameters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ToggleButton
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult

class InvalidValueParameter() : BaseFormatParameter(
    nameStringResourceId = R.string.trait_parameter_invalid_value,
    defaultLayoutId = R.layout.list_item_trait_parameter_default_enabled_toggle,
    parameter = Parameters.INVALID_VALUE
) {
    val defaultValue: Boolean? = null

    override fun createViewHolder(parent: ViewGroup): BaseFormatParameter.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(defaultLayoutId, parent, false)
        return ViewHolder(view)
    }

    inner class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        internal val toggleButton: ToggleButton =
            itemView.findViewById<ToggleButton>(R.id.dialog_new_trait_default_enabled_toggle_btn).also {
                defaultValue?.let { value ->
                    it.isChecked = value
                }
            }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            invalidValues = toggleButton.isChecked
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                toggleButton.isChecked = traitObject?.invalidValues == true
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

    override fun toggleValue(trait: TraitObject): Boolean {
        trait.invalidValues = !trait.invalidValues
        return trait.invalidValues
    }
}