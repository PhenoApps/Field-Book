package com.fieldbook.shared.traits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_plus
import com.fieldbook.shared.generated.resources.ic_tb_delete
import com.fieldbook.shared.generated.resources.ic_trait_categorical
import com.fieldbook.shared.generated.resources.traits_format_categorical
import com.fieldbook.shared.utilities.BrAPIScaleValidValuesCategories
import com.fieldbook.shared.utilities.CategoryJsonUtil
import org.jetbrains.compose.resources.painterResource

class CategoricalFormat : TraitFormat(
    format = Formats.CATEGORICAL,
    nameStringResource = Res.string.traits_format_categorical,
    iconDrawableResource = Res.drawable.ic_trait_categorical,
) {

    @Composable
    override fun ParametersEditor(trait: TraitObject, onTraitChange: (TraitObject) -> Unit) {
        val initialList = CategoryJsonUtil.decodeDefinition(trait.categories)
            .map { it.label ?: it.value ?: "" }
            .filter { it.isNotEmpty() }
        val items = remember { mutableStateListOf<String>().apply { addAll(initialList) } }
        var input by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Add category") },
                    modifier = Modifier.weight(1f)
                )

                FilledIconButton(onClick = {
                    val v = input.trim()
                    if (v.isNotEmpty()) {
                        items.add(v)
                        input = ""
                        error = ""
                        trait.categories = encode(items)
                        onTraitChange(trait)
                    } else {
                        error = "Cannot add empty category"
                    }
                }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_plus),
                        contentDescription = "Add"
                    )
                }
            }

            if (error.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(8.dp))

            items.forEachIndexed { idx, value ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var v by remember { mutableStateOf(value) }
                    OutlinedTextField(
                        value = v,
                        onValueChange = {
                            v = it
                            items[idx] = it
                            trait.categories = encode(items)
                            onTraitChange(trait)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Category") }
                    )

                    FilledIconButton(onClick = {
                        items.removeAt(idx)
                        trait.categories = encode(items)
                        onTraitChange(trait)
                    }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_tb_delete),
                            contentDescription = "Delete"
                        )
                    }
                }
            }
        }
    }

    private fun encode(items: SnapshotStateList<String>): String = CategoryJsonUtil.encode(
        ArrayList(items.map { BrAPIScaleValidValuesCategories(label = it, value = it) })
    )

}
