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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.chevron_left
import com.fieldbook.shared.generated.resources.chevron_right
import org.jetbrains.compose.resources.painterResource

@Composable
fun PlotsProgressBar(
    currentIndex: Int,
    total: Int,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
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
fun RangeBox(
    controller: CollectScreenController,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(controller.currentUnitIndex) {
        val id = controller.rangeID.getOrNull(controller.currentUnitIndex)
        if (id != null) {
            controller.updateCurrentRange(id)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        PlotsProgressBar(
            currentIndex = controller.currentUnitIndex,
            total = controller.units.size,
            visible = true // Optionally, make this conditional on a preference
        )
        Spacer(Modifier.size(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { controller.updateCurrentUnitIndex(controller.currentUnitIndex - 1) },
                enabled = controller.currentUnitIndex > 0,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chevron_left),
                    contentDescription = "Previous Unit",
                    modifier = Modifier.size(56.dp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "row: ${controller.cRange.primaryId}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "plot: ${controller.cRange.secondaryId}",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            IconButton(
                onClick = { controller.updateCurrentUnitIndex(controller.currentUnitIndex + 1) },
                enabled = controller.currentUnitIndex < controller.units.size - 1,
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
