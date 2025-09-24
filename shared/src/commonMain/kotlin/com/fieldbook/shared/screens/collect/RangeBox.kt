package com.fieldbook.shared.screens.collect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.chevron_left
import com.fieldbook.shared.generated.resources.chevron_right
import org.jetbrains.compose.resources.painterResource

@Composable
fun PlotsProgressBar(currentIndex: Int, total: Int, visible: Boolean = true, modifier: Modifier = Modifier) {
    if (visible && total > 0) {
        val progress = (currentIndex + 1).toFloat() / total
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = Color.LightGray
        )
    }
}

@Composable
fun RangeBox(viewModel: CollectViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        PlotsProgressBar(
            currentIndex = viewModel.currentUnitIndex,
            total = viewModel.units.size,
            visible = true // Optionally, make this conditional on a preference
        )
        Spacer(Modifier.size(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { viewModel.updateCurrentUnitIndex(viewModel.currentUnitIndex - 1) },
                enabled = viewModel.currentUnitIndex > 0,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chevron_left),
                    contentDescription = "Previous Unit",
                    modifier = Modifier.size(56.dp)
                )
            }
            val unit = viewModel.units.getOrNull(viewModel.currentUnitIndex)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("row: ${unit?.position_coordinate_x ?: "-"}", style = MaterialTheme.typography.titleLarge)
                Text("plo: ${unit?.observation_unit_db_id ?: "-"}", style = MaterialTheme.typography.titleLarge)
            }
            IconButton(
                onClick = { viewModel.updateCurrentUnitIndex(viewModel.currentUnitIndex + 1) },
                enabled = viewModel.currentUnitIndex < viewModel.units.size - 1,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chevron_right),
                    contentDescription = "Next Unit",
                    modifier = Modifier.size(56.dp)
                )
            }
        }
    }
}
