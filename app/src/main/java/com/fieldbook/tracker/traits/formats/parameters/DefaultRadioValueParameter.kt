package com.fieldbook.tracker.traits.formats.parameters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.enums.ThreeState
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult

class DefaultRadioValueParameter(private val initialDefaultValue: Int? = null) :
    BaseFormatParameter(
        nameStringResourceId = R.string.traits_create_value_default,
        defaultLayoutId = R.layout.list_item_trait_parameter_default_radio_value,
        parameter = Parameters.DEFAULT_VALUE
    ) {

    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_default_radio_value, parent, false)
        return ViewHolder(v)
    }

    inner class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {
        private val defaultValueRadioGroup: RadioGroup? = itemView.findViewById(R.id.dialog_new_trait_default_radio_group)
        private val falseRadioBtn: RadioButton? = itemView.findViewById(R.id.radio_false)
        private val unsetRadioBtn: RadioButton? = itemView.findViewById(R.id.radio_unset)
        private val trueRadioBtn: RadioButton? = itemView.findViewById(R.id.radio_true)

        private fun selectDefaultRadio(value: Int) {
            when (value) {
                ThreeState.OFF.value -> falseRadioBtn?.isChecked = true
                ThreeState.ON.value -> trueRadioBtn?.isChecked = true
                ThreeState.NEUTRAL.value -> unsetRadioBtn?.isChecked = true
            }
        }

        init {
            initialDefaultValue?.let { selectDefaultRadio(it) } ?: run { unsetRadioBtn?.isChecked = true }
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            defaultValue = when (defaultValueRadioGroup?.checkedRadioButtonId) {
                R.id.radio_false -> ThreeState.OFF.state
                R.id.radio_true -> ThreeState.ON.state
                R.id.radio_unset -> ThreeState.NEUTRAL.state
                else -> ThreeState.NEUTRAL.state
            }
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                val state = ThreeState.fromString(traitObject?.defaultValue)
                selectDefaultRadio(state.value)
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