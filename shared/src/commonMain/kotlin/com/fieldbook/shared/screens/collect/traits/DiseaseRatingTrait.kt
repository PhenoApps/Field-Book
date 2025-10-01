package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DiseaseRatingTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf(value.toIntOrNull() ?: 0) }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            for (i in 1..5) {
                Button(
                    onClick = { current = i; onValueChange(i.toString()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (current == i) MaterialTheme.colorScheme.primary else Color(
                            0xFFD9D9D9
                        )
                    )
                ) { Text(i.toString()) }
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}
