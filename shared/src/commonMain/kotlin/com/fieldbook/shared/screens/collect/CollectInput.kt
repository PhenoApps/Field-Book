package com.fieldbook.shared.screens.collect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.screens.collect.traits.AngleTrait
import com.fieldbook.shared.screens.collect.traits.BarcodeTrait
import com.fieldbook.shared.screens.collect.traits.BooleanTrait
import com.fieldbook.shared.screens.collect.traits.CategoricalTrait
import com.fieldbook.shared.screens.collect.traits.CounterTrait
import com.fieldbook.shared.screens.collect.traits.DateTrait
import com.fieldbook.shared.screens.collect.traits.DiseaseRatingTrait
import com.fieldbook.shared.screens.collect.traits.GnsSTrait
import com.fieldbook.shared.screens.collect.traits.LabelPrintTrait
import com.fieldbook.shared.screens.collect.traits.LocationTrait
import com.fieldbook.shared.screens.collect.traits.MultiCatTrait
import com.fieldbook.shared.screens.collect.traits.NumericTrait
import com.fieldbook.shared.screens.collect.traits.PercentTrait
import com.fieldbook.shared.screens.collect.traits.TextTrait
import com.fieldbook.shared.theme.AppColors
import com.fieldbook.shared.traits.Formats

@Composable
fun CollectInput(
    controller: CollectScreenController,
) {
    val trait = controller.traits.getOrNull(controller.currentTraitIndex)
    val value = trait?.let { controller.traitValues[it.id] } ?: ""

    var isEdited by remember(
        controller.currentTraitIndex,
        controller.currentUnitIndex
    ) { mutableStateOf(false) }

    val fontWeight = if (!isEdited) FontWeight.Bold else FontWeight.Normal
    val fontStyle = if (isEdited) FontStyle.Normal else FontStyle.Italic
    val fontColor =
        if (isEdited) AppColors.fb_color_text_dark.color else controller.getDisplayColor()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Value: $value",
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                color = fontColor,
            )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(8.dp)
                .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
        )

        // Host composable that renders different inputs depending on trait format.
        TraitInputHost(
            controller = controller,
            trait = trait,
            value = value,
            onEdited = { isEdited = true }
        )
    }
}

@Composable
fun TraitInputHost(
    controller: CollectScreenController,
    trait: com.fieldbook.shared.database.models.TraitObject?,
    value: String,
    onEdited: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatEnum = trait?.format?.let { formatStr ->
        Formats.entries.find { it.databaseName.equals(formatStr, ignoreCase = true) }
    }

    when (formatEnum) {
        Formats.NUMERIC -> NumericTrait(
            value = value,
            onValueChange = {
                controller.updateCurrentTraitValue(it)
                onEdited()
            },
            modifier = modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(8.dp)
        )

        Formats.TEXT -> TextTrait(
            value = value,
            onValueChange = {
                controller.updateCurrentTraitValue(it)
                onEdited()
            },
            modifier = modifier.fillMaxWidth().padding(8.dp)
        )

        Formats.ANGLE -> AngleTrait(
            value = value,
            onValueChange = {
                controller.updateCurrentTraitValue(it)
                onEdited()
            },
            modifier = modifier.fillMaxWidth().padding(8.dp)
        )

        Formats.CATEGORICAL -> CategoricalTrait(
            trait = trait,
            value = value,
            onValueChange = {
                controller.updateCurrentTraitValue(it)
                onEdited()
            },
            modifier = modifier.fillMaxWidth().padding(8.dp)
        )

        Formats.BOOLEAN -> BooleanTrait(
            value = value,
            onValueChange = {
                controller.updateCurrentTraitValue(it)
                onEdited()
            },
            modifier = modifier.fillMaxWidth().padding(8.dp)
        )

        Formats.COUNTER -> CounterTrait(
            value = value,
            onValueChange = {
                controller.updateCurrentTraitValue(it)
                onEdited()
            },
            modifier = modifier.fillMaxWidth().padding(8.dp)
        )

        Formats.PERCENT -> PercentTrait(
            value = value,
            onValueChange = {
                controller.updateCurrentTraitValue(it)
                onEdited()
            },
            modifier = modifier.fillMaxWidth().padding(8.dp)
        )

        Formats.DATE -> DateTrait(
            value = value,
            onValueChange = {
                controller.updateCurrentTraitValue(it)
                onEdited()
            },
            modifier = modifier.fillMaxWidth().padding(8.dp)
        )

        Formats.MULTI_CATEGORICAL -> MultiCatTrait(
            trait = trait,
            value = value,
            onValueChange = {
                controller.updateCurrentTraitValue(it)
                onEdited()
            },
            modifier = modifier.fillMaxWidth().padding(8.dp)
        )

        Formats.LOCATION -> LocationTrait(
            value = value,
            onValueChange = {
                controller.updateCurrentTraitValue(it)
                onEdited()
            },
            modifier = modifier.fillMaxWidth().padding(8.dp)
        )
        // Add more as needed, or use legacy string fallback for custom/unknown
        else -> when (trait?.format) {
            "barcode" -> BarcodeTrait(
                value = value,
                onValueChange = {
                    controller.updateCurrentTraitValue(it)
                    onEdited()
                },
                modifier = modifier.fillMaxWidth().padding(8.dp)
            )

            "disease", "disease_rating" -> DiseaseRatingTrait(
                value = value,
                onValueChange = {
                    controller.updateCurrentTraitValue(it)
                    onEdited()
                },
                modifier = modifier.fillMaxWidth().padding(8.dp)
            )

            "gnss", "gps" -> GnsSTrait(
                value = value,
                onValueChange = {
                    controller.updateCurrentTraitValue(it)
                    onEdited()
                },
                modifier = modifier.fillMaxWidth().padding(8.dp)
            )

            "labelprint", "label_print" -> LabelPrintTrait(
                value = value,
                onValueChange = {
                    controller.updateCurrentTraitValue(it)
                    onEdited()
                },
                modifier = modifier.fillMaxWidth().padding(8.dp)
            )
            // photo, audio, camera and other complex types are out-of-scope for now
            "photo", "audio", "camera", "usb_camera", "gopro", "canon" -> {
                TextTrait(
                    value = value,
                    onValueChange = {
                        controller.updateCurrentTraitValue(it)
                        onEdited()
                    },
                    modifier = modifier.fillMaxWidth().padding(8.dp)
                )
            }

            else -> {
                TextTrait(
                    value = value,
                    onValueChange = {
                        controller.updateCurrentTraitValue(it)
                        onEdited()
                    },
                    modifier = modifier.fillMaxWidth().padding(8.dp)
                )
            }
        }
    }
}
