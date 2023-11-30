package com.fieldbook.tracker.offbeat.traits.formats.parameters

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.text.isDigitsOnly
import androidx.core.widget.addTextChangedListener
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.offbeat.traits.formats.ValidationResult
import java.math.BigDecimal

/**
 * Handles generic UI input for Number values by:
 * 1. setting input type to decimal/signed/number
 * 2. validating doubles and integers
 *
 * @param initialValue this will be set in the numeric edit text on init
 * @param allowNegative
 * @param isInteger, allowNegative and isInteger refer to changes in InputType for the edit text
 */
abstract class NumericParameter<T : Number>(
    open val initialValue: T? = null,
    open val allowNegative: Boolean? = true,
    open val isInteger: Boolean? = false
) : BaseFormatParameter() {
    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_numeric, parent, false)
        return createViewHolder(v, initialValue, allowNegative, isInteger)
    }

    abstract fun createViewHolder(
        itemView: View,
        initialValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean?
    ): ViewHolder<T>

    abstract class ViewHolder<T>(
        itemView: View,
        private val initialValue: T?,
        private val allowNegative: Boolean?,
        private val isInteger: Boolean?
    ) : BaseFormatParameter.ViewHolder(itemView) {

        //warning string resources
        private val infiniteNumber =
            itemView.context.getString(R.string.trait_creator_parameter_warning_infinite)
        private val nanNumber =
            itemView.context.getString(R.string.trait_creator_parameter_warning_nan)
        private val negativeNumber =
            itemView.context.getString(R.string.trait_creator_parameter_warning_negative)
        private val tooBig =
            itemView.context.getString(R.string.trait_creator_parameter_warning_too_big)
        private val tooSmall =
            itemView.context.getString(R.string.trait_creator_parameter_warning_too_small)

        val numericEt: EditText =
            itemView.findViewById<EditText?>(R.id.list_item_trait_parameter_numeric_et).also {
                it.inputType = InputType.TYPE_CLASS_NUMBER
                if (allowNegative == true) it.inputType =
                    it.inputType or InputType.TYPE_NUMBER_FLAG_SIGNED
                if (isInteger != true) it.inputType =
                    it.inputType or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

        init {
            initialize()
        }

        private fun setDefaultValue() {

            initialValue?.let { value ->

                numericEt.setText("$value")

            }
        }

        fun initialize() {

            setDefaultValue()

            numericEt.addTextChangedListener {

                validateTextEntry(it.toString())
            }
        }

        private fun validateDouble(value: BigDecimal) {

            if (value > BigDecimal(0) && value < Double.MIN_VALUE.toBigDecimal()) throw Exception(
                tooSmall
            )
            if (value.abs() > Double.MAX_VALUE.toBigDecimal()) throw Exception(tooBig)

            val doubleValue = value.toDouble()

            if (doubleValue.isInfinite()) throw Exception(infiniteNumber)
            if (doubleValue.isNaN()) throw Exception(nanNumber)
            if (allowNegative == false && doubleValue < 0.0) throw Exception(negativeNumber)
        }

        private fun validateInteger(value: BigDecimal) {

            if (value < Int.MIN_VALUE.toBigDecimal()) throw Exception(tooSmall)
            if (value > Int.MAX_VALUE.toBigDecimal()) throw Exception(tooBig)
        }

        private fun validateTextEntry(userInputValue: String) = ValidationResult().apply {

            try {

                val value = userInputValue.toBigDecimal()

                if (userInputValue.isDigitsOnly()) {
                    validateInteger(value)
                } else validateDouble(value)

                textInputLayout.error = null

            } catch (e: Exception) {

                if (userInputValue.isEmpty()) {

                    result = true

                    error = null

                } else {

                    result = false

                    error = e.message

                    textInputLayout.error = error
                }
            }
        }

        override fun validate(
            database: DataHelper,
            initialTraitObject: TraitObject?
        ) = validateTextEntry(numericEt.text.toString())
    }
}