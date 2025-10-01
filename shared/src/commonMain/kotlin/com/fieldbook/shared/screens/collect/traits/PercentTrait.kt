package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PercentTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Percent (%)", style = MaterialTheme.typography.bodyMedium)
        TextTrait(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth())
    }
}
