package com.fieldbook.shared.screens.collect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.chevron_left
import com.fieldbook.shared.generated.resources.chevron_right
import org.jetbrains.compose.resources.painterResource

@Composable
fun TraitBox(
    viewModel: CollectViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            val trait = viewModel.traits.getOrNull(viewModel.currentTraitIndex)
            Text(
                trait?.name ?: "-",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            IconButton(
                onClick = { viewModel.updateCurrentTraitIndex(viewModel.currentTraitIndex + 1) },
                enabled = viewModel.currentTraitIndex < viewModel.traits.size - 1,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chevron_right),
                    contentDescription = "Next Trait",
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary

                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatusBar(
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
