package com.fieldbook.shared.screens.collect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.theme.numericButtonDefaults


@Composable
fun CollectInput(viewModel: CollectViewModel) {
    val trait = viewModel.traits.getOrNull(viewModel.currentTraitIndex)
    val value = trait?.let { viewModel.traitValues[it.id] } ?: ""
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(text = "Value: $value", style = MaterialTheme.typography.titleLarge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding( 8.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        NumericTrait(
            value = value,
            onValueChange = { viewModel.updateCurrentTraitValue(it) },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(8.dp)
        )
    }
}

@Composable
fun NumericTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier // Let parent control size
    ) {
        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                row.forEach { label ->
                    Button(
                        onClick = {
                            when (label) {
                                "⌫" -> onValueChange(value.dropLast(1))
                                else -> onValueChange(value + label)
                            }
                        },
                        modifier = Modifier.numericButtonDefaults().weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD9D9D9)),
                        shape = MaterialTheme.shapes.small // Use theme shape
                    ) {
                        Text(label, color = Color.Black)
                    }
                }
            }
        }
    }
}
