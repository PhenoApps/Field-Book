package com.fieldbook.tracker.traits.formats.parameters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.widget.addTextChangedListener
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult

class DecimalPlacesParameter : BaseFormatParameter(
    nameStringResourceId = R.string.trait_parameter_decimal_places,
    defaultLayoutId = R.layout.list_item_trait_parameter_decimal_places_radio,
    parameter = Parameters.DECIMAL_PLACES
) {
    private val defaultValue = -1 // -1 means unlimited decimal places

    override fun createViewHolder(parent: ViewGroup): BaseFormatParameter.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(defaultLayoutId, parent, false)
        return ViewHolder(view)
    }

    inner class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {
        internal val radioGroup: RadioGroup = itemView.findViewById(R.id.decimal_places_radio_group)
        private val decimalInputContainer: LinearLayout = itemView.findViewById(R.id.decimal_input_container)

        private val noRestrictionRadio: RadioButton = itemView.findViewById(R.id.radio_no_restriction)
        private val integerRadio: RadioButton = itemView.findViewById(R.id.radio_integer)
        private val maxDecimalRadio: RadioButton = itemView.findViewById(R.id.max_decimal_radio)
        internal val maxDecimalEt: EditText = itemView.findViewById(R.id.max_decimal_edit_text)

        init {
            noRestrictionRadio.isChecked = true
            updateDecimalInputVisibility()

            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                updateDecimalInputVisibility()
            }

            maxDecimalEt.addTextChangedListener { validateDecimalInput() }
        }

        private fun updateDecimalInputVisibility() {
            val isDecimalInputChecked = maxDecimalRadio.isChecked
            decimalInputContainer.visibility = if (isDecimalInputChecked) View.VISIBLE else View.GONE
            maxDecimalEt.isEnabled = isDecimalInputChecked

            if (!isDecimalInputChecked) {
                maxDecimalEt.error = null
            }
        }

        override fun merge(traitObject: TraitObject): TraitObject {
            val decimalPlaces = when (radioGroup.checkedRadioButtonId) {
                R.id.radio_no_restriction -> -1
                R.id.radio_integer -> 0
                R.id.max_decimal_radio -> {
                    val input = maxDecimalEt.text.toString()
                    if (input.isNotBlank()) input.toInt() else 1
                }
                else -> defaultValue
            }

            traitObject.maxDecimalPlaces = decimalPlaces.toString()
            return traitObject
        }

        override fun load(traitObject: TraitObject?): Boolean {
            initialTraitObject = traitObject

            val decimalPlaces = traitObject?.maxDecimalPlaces?.toIntOrNull() ?: defaultValue

            when {
                decimalPlaces == -1 -> noRestrictionRadio.isChecked = true
                decimalPlaces == 0 -> integerRadio.isChecked = true
                decimalPlaces > 0 -> {
                    maxDecimalRadio.isChecked = true
                    maxDecimalEt.setText(decimalPlaces.toString())
                }
                else -> noRestrictionRadio.isChecked = true
            }

            return true
        }

        private fun validateDecimalInput() = ValidationResult().apply {
            val input = maxDecimalEt.text.toString()

            val errorMessage = when {
                input.isBlank() -> itemView.context.getString(R.string.trait_parameter_decimal_places_custom_required)
                else -> {
                    try {
                        val value = input.toInt()
                        if (value !in 1..10) {
                            itemView.context.getString(R.string.trait_parameter_decimal_places_custom_error)
                        } else null
                    } catch (_: NumberFormatException) {
                        itemView.context.getString(R.string.trait_parameter_invalid_number)
                    }
                }
            }

            maxDecimalEt.error = errorMessage
            result = errorMessage == null
            error = errorMessage
        }

        override fun validate(database: DataHelper, initialTraitObject: TraitObject?): ValidationResult {
            return if (maxDecimalRadio.isChecked) validateDecimalInput() else ValidationResult()
        }
    }
}