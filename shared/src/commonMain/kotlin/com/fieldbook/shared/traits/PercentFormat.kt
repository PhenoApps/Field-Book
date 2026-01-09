package com.fieldbook.shared.traits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.components.NumericOutlinedTextField
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_percent
import com.fieldbook.shared.generated.resources.traits_format_percent

class PercentFormat : TraitFormat(
    format = Formats.PERCENT,
    nameStringResource = Res.string.traits_format_percent,
    iconDrawableResource = Res.drawable.ic_trait_percent,
) {

    @Composable
    override fun ParametersEditor(trait: TraitObject, onTraitChange: (TraitObject) -> Unit) {
        var defaultVal by remember { mutableStateOf(trait.defaultValue ?: "") }
        var minVal by remember { mutableStateOf(trait.minimum ?: "") }
        var maxVal by remember { mutableStateOf(trait.maximum ?: "100") }

        var generalError by remember { mutableStateOf("") }

        Column {
            NumericOutlinedTextField(
                value = defaultVal,
                onValueChange = { defaultVal = it },
                label = { Text("Default") },
                modifier = Modifier.fillMaxWidth(),
                min = minVal.toDoubleOrNull(),
                max = maxVal.toDoubleOrNull(),
                onValidation = { err ->
                    generalError = err ?: ""
                    trait.defaultValue = defaultVal.ifBlank { null }
                    trait.additionalInfo = generalError.ifBlank { null }
                    onTraitChange(trait)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            NumericOutlinedTextField(
                value = minVal,
                onValueChange = { minVal = it },
                label = { Text("Minimum") },
                modifier = Modifier.fillMaxWidth(),
                min = 0.0,
                max = maxVal.toDoubleOrNull(),
                onValidation = { err ->
                    generalError = err ?: ""
                    trait.minimum = minVal.ifBlank { null }
                    trait.additionalInfo = generalError.ifBlank { null }
                    onTraitChange(trait)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            NumericOutlinedTextField(
                value = maxVal,
                onValueChange = { maxVal = it },
                label = { Text("Maximum") },
                modifier = Modifier.fillMaxWidth(),
                min = minVal.toDoubleOrNull(),
                onValidation = { err ->
                    generalError = err ?: ""
                    trait.maximum = maxVal.ifBlank { null }
                    trait.additionalInfo = generalError.ifBlank { null }
                    onTraitChange(trait)
                }
            )

            if (generalError.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(generalError, color = MaterialTheme.colorScheme.error)
            }
        }
    }

}
