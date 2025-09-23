package com.fieldbook.shared.screens.collect

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.generated.resources.chevron_left
import com.fieldbook.shared.generated.resources.chevron_right
import org.jetbrains.compose.resources.painterResource

@Composable
fun TraitBox(
    viewModel: CollectViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { viewModel.updateCurrentTraitIndex(viewModel.currentTraitIndex - 1) },
                enabled = viewModel.currentTraitIndex > 0,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chevron_left),
                    contentDescription = "Previous Trait",
                    modifier = Modifier.size(56.dp),
                    tint = Color(0xFF4CAF50)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Trait", style = MaterialTheme.typography.titleLarge)
                val trait = viewModel.traits.getOrNull(viewModel.currentTraitIndex)
                trait?.let {
                    Text("ID: ${it.id}", style = MaterialTheme.typography.bodyLarge)
                    Text("Name: ${it.name}", style = MaterialTheme.typography.bodyMedium)
                    Text("Format: ${it.format}", style = MaterialTheme.typography.bodySmall)
                    it.minimum?.let { min -> Text("Min: $min", style = MaterialTheme.typography.bodySmall) }
                    it.maximum?.let { max -> Text("Max: $max", style = MaterialTheme.typography.bodySmall) }
                }
            }
            IconButton(
                onClick = { viewModel.updateCurrentTraitIndex(viewModel.currentTraitIndex + 1) },
                enabled = viewModel.currentTraitIndex < viewModel.traits.size - 1,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chevron_right),
                    contentDescription = "Next Trait",
                    modifier = Modifier.size(56.dp),
                    tint = Color(0xFF4CAF50)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        StatusBar(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
