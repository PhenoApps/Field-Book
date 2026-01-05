package com.fieldbook.shared.screens.collect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InfoBar(controller: CollectScreenController, modifier: Modifier = Modifier) {
    val unit = controller.units.getOrNull(controller.currentUnitIndex)
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        Text("field_name: ${unit?.primary_id ?: "-"}", style = MaterialTheme.typography.bodyLarge)
        Text(
            "plot_id: ${unit?.observation_unit_db_id ?: "-"}",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "row: ${controller.cRange.primaryId ?: "-"}",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(8.dp))
    }
}
