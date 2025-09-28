package com.fieldbook.tracker.traits.formats

import android.content.Context
import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.traits.formats.parameters.DefaultNumericParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.MaximumParameter
import com.fieldbook.tracker.traits.formats.parameters.MinimumParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter
import com.fieldbook.tracker.traits.formats.parameters.UnitParameter
import com.fieldbook.tracker.utilities.SnackbarUtils

/**
 * Values must be positive, values must be within bounds of minimum and maximum values.
 */
class PercentFormat : NumericFormat(
    format = Formats.PERCENT,
    defaultLayoutId = R.layout.trait_percent,
    layoutView = null,
    databaseName = "percent",
    nameStringResourceId = R.string.traits_format_percent,
    iconDrawableResourceId = R.drawable.ic_trait_percent,
    stringNameAux = null,
    NameParameter(),
    DefaultNumericParameter(initialDefaultValue = 0, allowNegative = false, isInteger = true),
    MinimumParameter(minimumValue = 0, allowNegative = false, isInteger = true, isRequired = true),
    MaximumParameter(
        maximumValue = 100,
        allowNegative = false,
        isInteger = true,
        isRequired = true
    ),
    DetailsParameter(),
    AutoSwitchPlotParameter(),
    UnitParameter(),
    RepeatedMeasureParameter(),
    ResourceFileParameter()
), Scannable by PercentageScannable() {

    override fun validate(
        context: Context,
        parameterViewHolders: List<BaseFormatParameter.ViewHolder>
    ) = ValidationResult().apply {

        try {

            validateNumericBounds(context, parameterViewHolders, this)

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