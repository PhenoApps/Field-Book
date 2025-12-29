package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.theme.Button

@Composable
fun BooleanTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf(value) }
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
        Button(
            onClick = {
                current = "true"
                onValueChange(current)
            },
            selected = current == "true",
            modifier = Modifier.weight(1f),
        ) { Text("Yes") }
        Spacer(Modifier.size(8.dp))
        Button(
            onClick = {
                current = "false"
                onValueChange(current)
            },
            selected = current == "false",
            modifier = Modifier.weight(1f),
        ) { Text("No") }
    }
}
