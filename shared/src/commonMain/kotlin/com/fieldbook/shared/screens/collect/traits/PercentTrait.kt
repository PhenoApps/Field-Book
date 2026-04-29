package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PercentTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minimum: String? = null,
    maximum: String? = null,
    defaultValue: String? = null,
    locked: Boolean = false
) {
    val min = minimum?.toIntOrNull() ?: 0
    val max = (maximum?.toIntOrNull() ?: 100).coerceAtLeast(min)
    val defaultInt = defaultValue?.toIntOrNull() ?: 0

    val parsedFromValue: Int = when {
        value == "NA" -> min
        value.isEmpty() -> defaultInt.coerceIn(min, max)
        else -> value.toIntOrNull()?.coerceIn(min, max) ?: defaultInt.coerceIn(min, max)
    }

    var sliderFloat by remember(value, min, max, defaultInt) {
        mutableStateOf(parsedFromValue.toFloat())
    }

    Column(modifier = modifier) {
        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(min.toString(), style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.weight(0.06f))

            Slider(
                value = sliderFloat,
                onValueChange = {
                    sliderFloat = it
                },
                onValueChangeFinished = {
                    onValueChange(sliderFloat.toInt().toString())
                },
                valueRange = min.toFloat()..max.toFloat(),
                enabled = !locked && value != "NA",
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors()
            )

            Spacer(Modifier.weight(0.06f))

            Text(max.toString(), style = MaterialTheme.typography.bodySmall)
        }
    }
}
