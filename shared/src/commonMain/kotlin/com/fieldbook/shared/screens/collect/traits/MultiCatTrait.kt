package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.theme.Button

@Composable
fun MultiCatTrait(
    trait: TraitObject?,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = trait?.categories?.split(",")?.map { it.trim() } ?: emptyList()
    var current by remember {
        mutableStateOf(value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet())
    }
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            categories.forEach { cat ->
                val selected = current.contains(cat)
                Button(
                    onClick = {
                        if (selected) current.remove(cat) else current.add(cat)
                        onValueChange(current.joinToString(","))
                    },
                    selected = selected,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(cat)
                }
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}
