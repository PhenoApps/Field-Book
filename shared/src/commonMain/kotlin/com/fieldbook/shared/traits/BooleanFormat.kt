package com.fieldbook.shared.traits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_boolean
import com.fieldbook.shared.generated.resources.traits_format_boolean
import com.fieldbook.shared.theme.Button

class BooleanFormat : TraitFormat(
    format = Formats.BOOLEAN,
    nameStringResource = Res.string.traits_format_boolean,
    iconDrawableResource = Res.drawable.ic_trait_boolean,
) {

    @Composable
    override fun ParametersEditor(trait: TraitObject, onTraitChange: (TraitObject) -> Unit) {
        var selected by remember { mutableStateOf(trait.defaultValue?.lowercase() == "true") }

        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        selected = !selected
                        trait.defaultValue = if (selected) "true" else "false"
                        onTraitChange(trait)
                    }, selected = selected, modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selected) "True" else "False")
                }
            }
        }
    }

}
