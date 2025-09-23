package com.fieldbook.shared.screens.collect

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import org.jetbrains.compose.resources.painterResource
import com.fieldbook.shared.generated.resources.Res
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import com.fieldbook.shared.generated.resources.circle_filled
import com.fieldbook.shared.generated.resources.circle_outline
import com.fieldbook.shared.generated.resources.square_rounded_filled
import com.fieldbook.shared.generated.resources.square_rounded_outline

@Composable
fun StatusBar(
    viewModel: CollectViewModel,
    modifier: Modifier = Modifier,
) {
    if (viewModel.traitValuesLoading) {
        Box(
            modifier = modifier
                .height(32.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        return
    }
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .height(32.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        viewModel.traits.forEachIndexed { index, trait ->
            val hasObservation = viewModel.traitValues[trait.id] != null
            val isCurrent = index == viewModel.currentTraitIndex
            Surface(
                color = if (isCurrent) Color(0xFF4CAF50) else Color.Transparent,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (hasObservation) Res.drawable.circle_filled else Res.drawable.circle_outline
                    ),
                    contentDescription = trait.name,
                    tint = if (isCurrent) Color.White else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
