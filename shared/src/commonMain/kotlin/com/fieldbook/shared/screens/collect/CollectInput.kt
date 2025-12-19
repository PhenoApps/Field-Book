package com.fieldbook.shared.screens.collect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.config.customKamelConfig
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
import com.fieldbook.shared.screens.collect.traits.PhotoTrait
import com.fieldbook.shared.screens.collect.traits.TextTrait
import com.fieldbook.shared.theme.AppColors
import com.fieldbook.shared.traits.Formats
import com.fieldbook.shared.utilities.CategoryJsonUtil
import com.fieldbook.shared.utilities.dateFormatMonthDay
import io.kamel.image.config.LocalKamelConfig

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

    val formatEnum = trait?.format?.let { formatStr ->
        Formats.entries.find { it.databaseName.equals(formatStr, ignoreCase = true) }
    }

    val displayValue = when (formatEnum) {
        Formats.CATEGORICAL -> {
            try {
                val decoded = CategoryJsonUtil.decode(value)
                if (decoded.isNotEmpty()) decoded[0].value ?: value else value
            } catch (_: Throwable) {
                // If it's not valid JSON or decode fails, fall back to raw value
                value
            }
        }
        Formats.MULTI_CATEGORICAL -> {
            try {
                val decoded = CategoryJsonUtil.decode(value)
                decoded.joinToString(":") { it.value ?: "" }
            } catch (_: Throwable) {
                value
            }
        }
        Formats.DATE -> {
            try {
                dateFormatMonthDay(value)
            } catch (_: Throwable) {
                value
            }
        }
        else -> value
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
    ) {
        Spacer(Modifier.height(16.dp))

        if (formatEnum == Formats.TEXT) {
            EditableValueText(
                value = value,
                onValueChange = {
                    controller.updateCurrentTraitValue(it)
                    isEdited = true
                },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                color = fontColor,
            )
        } else if (formatEnum?.isCamera == true) {
            TraitInputHost(
                controller = controller,
                trait = trait,
                value = value,
                onEdited = { isEdited = true }
            )
        } else {
            Text(
                text = displayValue,
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
            TraitInputHost(
                controller = controller,
                trait = trait,
                value = value,
                onEdited = { isEdited = true }
            )
        }
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

            "photo", "camera" -> CompositionLocalProvider(
                LocalKamelConfig provides customKamelConfig
            ) {
                PhotoTrait(
                    value = value,
                    onValueChange = {
                        controller.updateCurrentTraitValue(it)
                        onEdited()
                    },
                    modifier = modifier.fillMaxWidth().padding(8.dp)
                )
            }

            "audio", "usb_camera", "gopro", "canon" -> {
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
                EditableValueText(
                    value = value,
                    onValueChange = {
                        controller.updateCurrentTraitValue(it)
                        onEdited()
                    },
                    modifier = modifier.fillMaxWidth().padding(8.dp),
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    color = AppColors.fb_color_text_dark.color,
                )
            }
        }
    }
}

@Composable
fun EditableValueText(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    fontStyle: FontStyle = FontStyle.Italic,
    color: androidx.compose.ui.graphics.Color,
) {
    var text by remember { mutableStateOf(value) }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        modifier = modifier,
        textStyle = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            color = color,
            textAlign = TextAlign.Center,
        ),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                innerTextField()
            }
        }
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .padding(8.dp)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
    )
}
