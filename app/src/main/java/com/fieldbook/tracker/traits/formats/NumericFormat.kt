package com.fieldbook.tracker.traits.formats

import android.content.Context
import android.view.View
import android.widget.TextView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.traits.formats.parameters.DecimalPlacesParameter
import com.fieldbook.tracker.traits.formats.parameters.DefaultNumericParameter
import com.fieldbook.tracker.traits.formats.parameters.DefaultToggleParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.InvalidValueParameter
import com.fieldbook.tracker.traits.formats.parameters.MathSymbolsParameter
import com.fieldbook.tracker.traits.formats.parameters.MaximumParameter
import com.fieldbook.tracker.traits.formats.parameters.MinimumParameter
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter
import com.fieldbook.tracker.traits.formats.parameters.UnitParameter
import com.fieldbook.tracker.utilities.SnackbarUtils

open class NumericFormat(
    override val format: Formats = Formats.NUMERIC,
    override var defaultLayoutId: Int = R.layout.trait_numeric,
    override var layoutView: View? = null,
    override var databaseName: String = "numeric",
    override var nameStringResourceId: Int = R.string.traits_format_numeric,
    override var iconDrawableResourceId: Int = R.drawable.ic_trait_numeric,
    override var stringNameAux: ((Context) -> String?)? = null,
    override vararg val parameters: BaseFormatParameter =
        arrayOf(
            NameParameter(),
            DefaultNumericParameter<Double>(),
            MinimumParameter<Double>(),
            MaximumParameter<Double>(),
            DecimalPlacesParameter(),
            MathSymbolsParameter(),
            InvalidValueParameter(),
            DetailsParameter(),
            UnitParameter(),
            RepeatedMeasureParameter(),
            ResourceFileParameter(),
            AttachMediaParameter()
        )
) : TraitFormat(
    format = format,
    defaultLayoutId = defaultLayoutId,
    layoutView = layoutView,
    databaseName = "numeric",
    nameStringResourceId = nameStringResourceId,
    iconDrawableResourceId = iconDrawableResourceId,
    stringNameAux = null,
    *parameters
), Scannable, ChartableData {

    override fun validate(
        context: Context,
        parameterViewHolders: List<BaseFormatParameter.ViewHolder>
    ) = ValidationResult().apply {

        try {

            validateNumericBounds(context, parameterViewHolders, this)

            validateDecimalMathParams(context, parameterViewHolders, this)

        } catch (e: Exception) {

            e.printStackTrace()

            result = false

            SnackbarUtils.showLongSnackbar(
                parameterViewHolders.first().itemView,
                context.getString(R.string.traits_create_unknown_error, e.message)
            )
        }
    }

    protected fun validateNumericBounds(
        context: Context,
        parameterViewHolders: List<BaseFormatParameter.ViewHolder>,
        validationResult: ValidationResult
    ) {
        val valueRequired =
            context.getString(R.string.traits_create_warning_percent_value_required)
        val magnitudeRelationError =
            context.getString(R.string.traits_create_warning_percent_magnitude_relation)

        val defaultParameter =
            parameterViewHolders.find { it is DefaultNumericParameter<*>.ViewHolder } as? DefaultNumericParameter<*>.ViewHolder
        val maxParameter =
            parameterViewHolders.find { it is MaximumParameter<*>.ViewHolder } as? MaximumParameter<*>.ViewHolder
        val minParameter =
            parameterViewHolders.find { it is MinimumParameter<*>.ViewHolder } as? MinimumParameter<*>.ViewHolder


        if (maxParameter == null || minParameter == null) {

            validationResult.result = false

            return
        }

        val defaultValue = defaultParameter?.numericEt?.text?.toString() ?: ""

        val defaultDouble: Double? = if (defaultValue.isNotBlank()) {

            defaultValue.toDouble()

        } else null

        val maxValue = maxParameter.numericEt.text.toString()
        val minValue = minParameter.numericEt.text.toString()
        val maxDouble: Double? = if (maxValue.isNotBlank()) maxValue.toDouble() else null
        val minDouble: Double? = if (minValue.isNotBlank()) minValue.toDouble() else null

        if (minParameter.isRequired == true && minDouble == null) {

            validationResult.result = false

            minParameter.numericEt.error = valueRequired

        } else if (maxParameter.isRequired == true && maxDouble == null) {

            validationResult.result = false

            maxParameter.numericEt.error = valueRequired

        } else if (maxDouble != null && minDouble != null && maxDouble < minDouble) {

            validationResult.result = false

            maxParameter.textInputLayout.endIconDrawable = null
            minParameter.textInputLayout.endIconDrawable = null

            maxParameter.numericEt.error = magnitudeRelationError
            minParameter.numericEt.error = magnitudeRelationError

        } else if (defaultDouble != null && minDouble != null && minDouble > defaultDouble) {

            validationResult.result = false

            minParameter.textInputLayout.endIconDrawable = null
            defaultParameter?.textInputLayout?.endIconDrawable = null

            minParameter.numericEt.error = magnitudeRelationError
            defaultParameter?.numericEt?.error = magnitudeRelationError

        } else if (defaultDouble != null && maxDouble != null && defaultDouble > maxDouble) {

            validationResult.result = false

            maxParameter.textInputLayout.endIconDrawable = null
            defaultParameter?.textInputLayout?.endIconDrawable = null

            maxParameter.numericEt.error = magnitudeRelationError
            defaultParameter?.numericEt?.error = magnitudeRelationError

        }
    }

    private fun validateDecimalMathParams(
        context: Context,
        parameterViewHolders: List<BaseFormatParameter.ViewHolder>,
        validationResult: ValidationResult
    ) {
        val mathSymbolsConflictError = context.getString(R.string.traits_create_warning_math_symbols_conflict)

        val decimalPlacesParameter =
            parameterViewHolders.find { it is DecimalPlacesParameter.ViewHolder } as? DecimalPlacesParameter.ViewHolder
        val mathSymbolsParameter = parameterViewHolders.find { viewHolder ->
            val titleView = viewHolder.itemView.findViewById<TextView>(R.id.list_item_trait_parameter_title)
            titleView?.text == context.getString(R.string.trait_parameter_mathematical_symbols)
        } as? DefaultToggleParameter.ViewHolder


        if (decimalPlacesParameter == null || mathSymbolsParameter == null) {

            validationResult.result = false

            return
        }

        val mathSymbolsEnabled = mathSymbolsParameter.toggleButton.isChecked
        val decimalPlaces = when (decimalPlacesParameter.radioGroup.checkedRadioButtonId) {
            R.id.radio_no_restriction -> -1
            R.id.radio_integer -> 0
            R.id.max_decimal_radio -> {
                val customInput = decimalPlacesParameter.maxDecimalEt.text.toString()
                if (customInput.isNotBlank()) customInput.toIntOrNull() ?: -1 else -1
            }
            else -> -1
        }

        if (mathSymbolsEnabled && decimalPlaces >= 0) { // both have to be mutually exclusive
            validationResult.result = false
            mathSymbolsParameter.textInputLayout.error = mathSymbolsConflictError
            decimalPlacesParameter.textInputLayout.error = mathSymbolsConflictError
            return
        }

        mathSymbolsParameter.textInputLayout.error = null
        decimalPlacesParameter.textInputLayout.error = null
    }
}