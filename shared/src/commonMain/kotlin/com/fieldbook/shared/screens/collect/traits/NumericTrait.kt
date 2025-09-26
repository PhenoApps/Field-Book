package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fieldbook.shared.theme.numericButtonDefaults

@OptIn(ExperimentalFoundationApi::class)
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
        modifier = modifier
    ) {
        buttons.forEach { row ->
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                row.forEach { label ->
                    Button(
                        onClick = {
                            when (label) {
                                "⌫" -> if (value.isNotEmpty()) onValueChange(value.dropLast(1))
                                "." -> if (!value.contains('.')) onValueChange(value + label)
                                else -> onValueChange(value + label)
                            }
                        },
                        modifier = Modifier
                            .numericButtonDefaults()
                            .weight(1f)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    if (label == "⌫") {
                                        onValueChange("")
                                    }
                                }
                            ),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD9D9D9)),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(label, color = Color.Black)
                    }
                }
            }
        }
    }
}
