package com.fieldbook.tracker.ui.screens.traits.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.FieldFileObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.traits.formats.parameters.CategoriesParameter
import com.fieldbook.tracker.traits.formats.parameters.CloseKeyboardParameter
import com.fieldbook.tracker.traits.formats.parameters.CropImageParameter
import com.fieldbook.tracker.traits.formats.parameters.DecimalPlacesParameter
import com.fieldbook.tracker.traits.formats.parameters.DefaultToggleParameter
import com.fieldbook.tracker.traits.formats.parameters.InvalidValueParameter
import com.fieldbook.tracker.traits.formats.parameters.MathSymbolsParameter
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.MultipleCategoriesParameter
import com.fieldbook.tracker.traits.formats.parameters.Parameters
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter
import com.fieldbook.tracker.traits.formats.parameters.SaveImageParameter
import com.fieldbook.tracker.traits.formats.parameters.UnitParameter
import com.fieldbook.tracker.ui.components.widgets.Chip
import com.fieldbook.tracker.ui.screens.traits.dialogs.BrapiLabelValueDialog
import com.fieldbook.tracker.ui.screens.traits.dialogs.DateFormatDialog
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.StringUtil.capitalizeFirstLetter
import com.fieldbook.tracker.utilities.TraitDetailUtil.getTodayDayOfYear
import com.fieldbook.tracker.utilities.TraitDetailUtil.getTodayFormattedDate
import com.fieldbook.tracker.utilities.TraitDetailUtil.parseCategoryExample

private const val TAG = "TraitOptionsSection"

val excludedParams = setOf(
    Parameters.NAME,
    Parameters.DEFAULT_VALUE,
    Parameters.MAXIMUM,
    Parameters.MINIMUM,
    Parameters.DETAILS,

    // below params are handled separately, exclude these as well
    Parameters.RESOURCE_FILE,
    Parameters.USE_DAY_OF_YEAR,
    Parameters.DISPLAY_VALUE,
)

@Composable
fun TraitOptionsSection(
    trait: TraitObject,
    onUpdateTrait: (TraitObject) -> Unit,
    onResourceFilePickerDialog: () -> Unit,
    onShowParameterEditDialog: (BaseFormatParameter, TraitObject, (TraitObject) -> Unit) -> Unit,
) {
    val context = LocalContext.current

    var showDateFormatDialog by remember { mutableStateOf(false) }
    var showBrapiLabelValueDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // resource file chip
            val resourceFileDefault =
                stringResource(R.string.trait_parameter_resource_file).capitalizeFirstLetter()

            val displayText =
                if (trait.resourceFile.isNotEmpty()) {
                    runCatching {
                        FieldFileObject.create(context, trait.resourceFile.toUri(), null, null).stem
                    }.getOrDefault(resourceFileDefault)
                } else {
                    resourceFileDefault
                }

            Chip(
                text = displayText,
                icon = R.drawable.ic_tb_folder,
                onClick = onResourceFilePickerDialog,
            )

            // date format
            if (trait.format == "date") {
                val formatString =
                    if (trait.useDayOfYear) getTodayDayOfYear()
                    else getTodayFormattedDate()

                Chip(
                    text = stringResource(R.string.trait_detail_chip_format_date, formatString),
                    icon = R.drawable.ic_calendar_edit,
                    onClick = {
                        showDateFormatDialog = true
                    }
                )
            }

            // display label/value for brapi categorical trait
            val isBrapiTrait =
                trait.externalDbId?.isNotEmpty() == true ||
                        trait.traitDataSource.contains("brapi", ignoreCase = true)


            val traitHasBrapiCategories =
                isBrapiTrait && trait.format == "categorical" &&
                        trait.categories.isNotEmpty()

            if (traitHasBrapiCategories) {
                val firstCategory = parseCategoryExample(trait.categories)
                val chipLabel = if (trait.categoryDisplayValue) {
                    stringResource(R.string.trait_brapi_value_display, firstCategory.second)
                } else {
                    stringResource(R.string.trait_brapi_label_display, firstCategory.first)
                }

                Chip(
                    text = chipLabel,
                    icon = R.drawable.ic_tag_edit,
                    onClick = {
                        showBrapiLabelValueDialog = true
                    }
                )
            }

            val format = Formats.entries.find { it.getDatabaseName() == trait.format }
            val formatDefinition = format?.getTraitFormatDefinition()

            formatDefinition?.let { definition ->

                val displayableParams =
                    definition.parameters.filter { it.parameter !in excludedParams }

                displayableParams.forEach { param ->
                    Chip(
                        text = getParamText(context, param, trait),
                        icon = getParamIcon(param, trait),
                        onClick = {
                            if (param is DefaultToggleParameter) {
                                val updatedTrait = trait.clone()
                                param.toggleValue(updatedTrait)
                                onUpdateTrait(updatedTrait)
                            } else { // not a toggle parameter, show dialog
                                onShowParameterEditDialog(param, trait) { updatedTrait ->
                                    onUpdateTrait(updatedTrait)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDateFormatDialog) {
        DateFormatDialog(
            currentUseDayOfYear = trait.useDayOfYear,
            onSave = { useDayOfYear ->
                showDateFormatDialog = false
                val updatedTrait = trait.clone()
                updatedTrait.useDayOfYear = useDayOfYear
                onUpdateTrait(updatedTrait)
            },
            onDismiss = { showDateFormatDialog = false }
        )
    }

    if (showBrapiLabelValueDialog) {
        BrapiLabelValueDialog(
            categories = trait.categories,
            displaysValue = trait.categoryDisplayValue,
            onSave = { displayValue ->
                showBrapiLabelValueDialog = false
                val updatedTrait = trait.clone().apply {
                    categoryDisplayValue = displayValue
                }
                onUpdateTrait(updatedTrait)
            },
            onDismiss = { showBrapiLabelValueDialog = false }
        )
    }
}

private fun getParamIcon(param: BaseFormatParameter, trait: TraitObject): Int {
    return when (param) {
        is DefaultToggleParameter -> {
            val isEnabled = param.getToggleValue(trait)
            when (param) {
                is AutoSwitchPlotParameter -> if (isEnabled) R.drawable.ic_auto_switch else R.drawable.ic_auto_switch_off
                is CloseKeyboardParameter -> if (isEnabled) R.drawable.ic_keyboard_close else R.drawable.ic_keyboard_close_off
                is CropImageParameter -> if (isEnabled) R.drawable.ic_crop_image else R.drawable.ic_crop_image_off
                is InvalidValueParameter -> if (isEnabled) R.drawable.ic_outlier else R.drawable.ic_outlier_off
                is MathSymbolsParameter -> if (isEnabled) R.drawable.ic_symbol else R.drawable.ic_symbol_off
                is MultipleCategoriesParameter -> if (isEnabled) R.drawable.ic_multicat else R.drawable.ic_single_cat
                is RepeatedMeasureParameter -> if (isEnabled) R.drawable.ic_repeated_measures else R.drawable.ic_repeated_measures_off
                is SaveImageParameter -> if (isEnabled) R.drawable.ic_transfer else R.drawable.ic_transfer_off
                else -> R.drawable.ic_tag_edit
            }
        }

        is CategoriesParameter -> R.drawable.ic_trait_categorical
        is DecimalPlacesParameter -> R.drawable.ic_decimal
        is UnitParameter -> R.drawable.ic_tag_edit
        is ResourceFileParameter -> R.drawable.ic_tb_folder
        is AttachMediaParameter -> R.drawable.multimedia
        else -> R.drawable.ic_tag_edit
    }
}

private fun getParamText(context: Context, param: BaseFormatParameter, trait: TraitObject): String {
    return when (param) {
        is AutoSwitchPlotParameter -> context.getString(R.string.trait_detail_chip_automatic_switch)
        is InvalidValueParameter -> context.getString(R.string.trait_detail_chip_invalid_value)
        is MathSymbolsParameter -> context.getString(R.string.trait_detail_chip_math_symbols)
        is MultipleCategoriesParameter -> {
            val isEnabled = param.getToggleValue(trait)
            if (isEnabled) context.getString(R.string.trait_detail_chip_multiple_categories)
            else context.getString(R.string.trait_detail_chip_single_category)
        }

        is SaveImageParameter -> context.getString(R.string.trait_detail_chip_transfer_images)
        else -> param.getName(context).capitalizeFirstLetter()
    }
}

@Preview(showBackground = true)
@Composable
private fun TraitOptionsSectionPreview() {
    AppTheme {
        TraitOptionsSection(
            trait = TraitObject().apply {
                format = "numeric"
            },
            onUpdateTrait = { },
            onResourceFilePickerDialog = { },
            onShowParameterEditDialog = { param, traitObj, onUpdated ->
                onUpdated(traitObj)
            }
        )
    }
}