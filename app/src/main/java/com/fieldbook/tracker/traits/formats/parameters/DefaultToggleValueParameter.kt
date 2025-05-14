package com.fieldbook.tracker.traits.formats.parameters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.enums.ThreeState
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult
import com.fieldbook.tracker.views.ThreeStateToggleButton

class DefaultToggleValueParameter(private val initialDefaultValue: Boolean? = null) :
    BaseFormatParameter(
        nameStringResourceId = R.string.traits_create_value_default,
        defaultLayoutId = R.layout.list_item_trait_parameter_default_toggle_value,
        parameter = Parameters.DEFAULT_VALUE
    ) {

    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_default_toggle_value, parent, false)
        return ViewHolder(v)
    }

    inner class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        val defaultValueToggle: ThreeStateToggleButton? =
            itemView.findViewById<ThreeStateToggleButton>(R.id.dialog_new_trait_default_toggle_btn).also {
                initialDefaultValue?.let { value ->
                    it.setState(if (value) ThreeState.ON.state else ThreeState.OFF.state)
                } ?: run {// if initialDefaultValue is null
                    it.setState(ThreeState.NEUTRAL.state)
                }
            }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            defaultValue = defaultValueToggle?.getState()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                defaultValueToggle?.setState(traitObject?.defaultValue)
                return true
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