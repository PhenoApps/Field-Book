package com.fieldbook.tracker.offbeat.traits.formats.contracts

import android.content.Context
import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.ValidationResult
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DefaultNumericParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.MaximumParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.MinimumParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.utilities.SnackbarUtils

open class NumericFormat(
    override val format: Formats = Formats.NUMERIC,
    override var defaultLayoutId: Int = R.layout.trait_numeric,
    override var layoutView: View? = null,
    override var nameStringResourceId: Int = R.string.traits_format_numeric,
    override var iconDrawableResourceId: Int = R.drawable.ic_trait_numeric,
    override var stringNameAux: ((Context) -> String?)? = null,
    override vararg val parameters: BaseFormatParameter =
        arrayOf(
            NameParameter(),
            DefaultNumericParameter<Double>(),
            MinimumParameter<Double>(),
            MaximumParameter<Double>(),
            DetailsParameter()
        )
) : TraitFormat(
    format = format,
    defaultLayoutId = defaultLayoutId,
    layoutView = layoutView,
    nameStringResourceId = nameStringResourceId,
    iconDrawableResourceId = iconDrawableResourceId,
    stringNameAux = null,
    *parameters
) {

    override fun validate(
        context: Context,
        parameterViewHolders: List<BaseFormatParameter.ViewHolder>
    ) = ValidationResult().apply {

        try {

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

                result = false

                return@apply
            }

            val defaultValue = defaultParameter?.numericEt?.text?.toString() ?: ""

            val defaultDouble: Double? = if (defaultValue.isNotBlank()) {

                defaultValue.toDouble()

            } else null

            val maxValue = maxParameter.numericEt.text.toString()
            val minValue = minParameter.numericEt.text.toString()

            val maxDouble: Double? = if (maxValue.isNotBlank()) {

                maxValue.toDouble()

            } else null

            val minDouble: Double? = if (minValue.isNotBlank()) {

                minValue.toDouble()

            } else null

            if (minParameter.isRequired == true && minDouble == null) {

                result = false

                minParameter.numericEt.error = valueRequired

            } else if (maxParameter.isRequired == true && maxDouble == null) {

                result = false

                maxParameter.numericEt.error = valueRequired

            } else if (maxDouble != null && minDouble != null && maxDouble < minDouble) {

                result = false

                maxParameter.textInputLayout.endIconDrawable = null
                minParameter.textInputLayout.endIconDrawable = null

                maxParameter.numericEt.error = magnitudeRelationError
                minParameter.numericEt.error = magnitudeRelationError

            } else if (defaultDouble != null && minDouble != null && minDouble > defaultDouble) {

                result = false

                minParameter.textInputLayout.endIconDrawable = null
                defaultParameter?.textInputLayout?.endIconDrawable = null

                minParameter.numericEt.error = magnitudeRelationError
                defaultParameter?.numericEt?.error = magnitudeRelationError

            } else if (defaultDouble != null && maxDouble != null && defaultDouble > maxDouble) {

                result = false

                maxParameter.textInputLayout.endIconDrawable = null
                defaultParameter?.textInputLayout?.endIconDrawable = null

                maxParameter.numericEt.error = magnitudeRelationError
                defaultParameter?.numericEt?.error = magnitudeRelationError

            }

        } catch (e: Exception) {

            e.printStackTrace()

            result = false

            SnackbarUtils.showLongSnackbar(
                parameterViewHolders.first().itemView,
                context.getString(R.string.traits_create_unknown_error, e.message)
            )
        }
    }
}