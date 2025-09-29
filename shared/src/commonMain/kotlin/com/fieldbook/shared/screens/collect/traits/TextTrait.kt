package com.fieldbook.shared.screens.collect.traits

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun TextTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var local by remember { mutableStateOf(value) }

    OutlinedTextField(
        value = local,
        onValueChange = { v ->
            local = v
            onValueChange(v)
        },
        modifier = modifier,
        singleLine = true,
        label = { Text("Enter text") }
    )
}

