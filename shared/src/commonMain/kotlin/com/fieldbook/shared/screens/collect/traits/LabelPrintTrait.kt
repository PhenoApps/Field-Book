package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LabelPrintTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Label printing is not supported in the shared UI.")
        Spacer(Modifier.size(8.dp))
        TextTrait(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth())
    }
}
