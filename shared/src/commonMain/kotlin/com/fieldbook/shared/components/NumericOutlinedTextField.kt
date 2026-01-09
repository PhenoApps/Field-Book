package com.fieldbook.shared.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NumericOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    min: Double? = null,
    max: Double? = null,
    onValidation: (String?) -> Unit = {}
) {

    // compute validation error based on current raw value
    val num = value.toDoubleOrNull()
    var errorText: String? = null
    if (value.isNotBlank() && num == null) {
        errorText = "Must be numeric"
    } else if (num != null) {
        if (min != null && num < min) {
            errorText = "Must be >= $min"
        } else if (max != null && num > max) {
            errorText = "Must be <= $max"
        }
    }

    LaunchedEffect(value, min, max) {
        onValidation(errorText)
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { raw: String ->
                if (raw.isBlank() || raw.toDoubleOrNull() != null) {
                    if (raw != value) onValueChange(raw)
                }
            },
            label = label ?: {},
            isError = !errorText.isNullOrBlank(),
        )

        if (!errorText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(errorText, color = MaterialTheme.colorScheme.error)
        }
    }
}
