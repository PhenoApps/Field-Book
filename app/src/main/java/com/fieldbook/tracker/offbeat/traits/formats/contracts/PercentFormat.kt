package com.fieldbook.tracker.offbeat.traits.formats.contracts

import android.content.Context
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

/**
 * Values must be positive, values must be within bounds of minimum and maximum values.
 */
class PercentFormat : TraitFormat() {

    override val format = Formats.PERCENT

    override var defaultLayoutId: Int = R.layout.trait_percent

    override val parameters: List<BaseFormatParameter> = listOf(
        NameParameter(),
        DefaultNumericParameter(0, allowNegative = false, isInteger = true),
        MinimumParameter(minimumValue = 0, allowNegative = false, isInteger = true),
        MaximumParameter(maximumValue = 100, allowNegative = false, isInteger = true),
        DetailsParameter()
    )

    override fun getName() = R.string.traits_format_percent

    override fun getIcon() = R.drawable.ic_trait_percent

    override fun validate(
        context: Context,
        parameterViewHolders: List<BaseFormatParameter.ViewHolder>
    ) = ValidationResult().apply {

        try {

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

            val defaultInteger: Int? = if (defaultValue.isNotBlank()) {

                defaultValue.toInt()

            } else null

            val maxValue = maxParameter.numericEt.text.toString().toInt()
            val minValue = minParameter.numericEt.text.toString().toInt()

            if (maxValue < minValue) {

                result = false

                maxParameter.textInputLayout.error = magnitudeRelationError
                minParameter.textInputLayout.error = magnitudeRelationError

            } else if (defaultInteger != null && minValue > defaultInteger) {

                result = false

                minParameter.textInputLayout.error = magnitudeRelationError
                defaultParameter?.textInputLayout?.error = magnitudeRelationError

            } else if (defaultInteger != null && defaultInteger > maxValue) {

                result = false

                maxParameter.textInputLayout.error = magnitudeRelationError
                defaultParameter?.textInputLayout?.error = magnitudeRelationError

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