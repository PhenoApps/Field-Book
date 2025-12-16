package com.fieldbook.tracker.traits.formats.parameters

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.text.isDigitsOnly
import androidx.core.widget.addTextChangedListener
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult
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
open class NumericParameter<T : Number>(
    override val nameStringResourceId: Int = R.string.traits_format_numeric,
    override val defaultLayoutId: Int = R.layout.list_item_trait_parameter_numeric,
    override val parameter: Parameters,
    open val initialValue: T? = null,
    open val allowNegative: Boolean? = true,
    open val isInteger: Boolean? = false,
    open val isRequired: Boolean? = false,
) : BaseFormatParameter(
    nameStringResourceId = nameStringResourceId,
    defaultLayoutId = defaultLayoutId,
    parameter = parameter
) {

    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder? {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_numeric, parent, false)
        return createViewHolder(v, initialValue, allowNegative, isInteger, isRequired)
    }

    open fun createViewHolder(
        itemView: View,
        initialValue: T?,
        allowNegative: Boolean?,
        isInteger: Boolean?,
        isRequired: Boolean?,
    ): ViewHolder<T>? = null

    abstract class ViewHolder<T>(
        itemView: View,
        private val initialValue: T?,
        private val allowNegative: Boolean? = true,
        private val isInteger: Boolean? = false,
        open val isRequired: Boolean? = false,
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
        private val integerRequired =
            itemView.context.getString(R.string.trait_creator_parameter_warning_integer_required)

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

        private fun setupTextWatchers() {

            numericEt.addTextChangedListener {

                validateTextEntry(it.toString())
            }
        }

        private fun setHint() {

            textInputLayout.hint = itemView.context.getString(
                if (isRequired == true) {
                    R.string.required
                } else R.string.traits_create_optional
            )
        }

        fun initialize() {

            setDefaultValue()

            setupTextWatchers()

            setHint()
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

            if (allowNegative == false && value < BigDecimal(0)) throw Exception(negativeNumber)
        }

        private fun validateTextEntry(userInputValue: String) = ValidationResult().apply {

            textInputLayout.endIconDrawable = null

            if (userInputValue.isNotBlank()) {

                textInputLayout.setEndIconDrawable(android.R.drawable.ic_notification_clear_all)

                textInputLayout.setEndIconOnClickListener {

                    numericEt.text?.clear()

                    textInputLayout.endIconDrawable = null
                }

            } else {

                textInputLayout.endIconDrawable = null

            }

            try {

                val value = userInputValue.toBigDecimal()

                if (userInputValue.isDigitsOnly()) {

                    validateInteger(value)

                } else {

                    if (isInteger == true) throw Exception(integerRequired)

                    validateDouble(value)
                }

                numericEt.error = null

            } catch (e: Exception) {

                if (userInputValue.isEmpty()) {

                    result = true

                    error = null

                } else {

                    result = false

                    error = e.message

                    numericEt.error = error

                    textInputLayout.endIconDrawable = null

                }
            }
        }

        override fun validate(
            traitRepo: TraitRepository,
            initialTraitObject: TraitObject?
        ) = validateTextEntry(numericEt.text.toString())
    }
}