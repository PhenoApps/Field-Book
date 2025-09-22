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
    traits: List<String>,
    traitsValue: Map<String, Any?>, // presence means hasObservation
    currentIndex: Int,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (loading) {
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
            .height(32.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        traits.forEachIndexed { index, trait ->
            val isSelected = index == currentIndex
            val hasObservation = traitsValue.containsKey(trait)
            val iconRes = when {
                isSelected && hasObservation -> Res.drawable.square_rounded_filled
                isSelected && !hasObservation -> Res.drawable.square_rounded_outline
                !isSelected && hasObservation -> Res.drawable.circle_filled
                else -> Res.drawable.circle_outline
            }
            val color = when {
                isSelected && hasObservation -> Color(0xFF388E3C) // green
                isSelected && !hasObservation -> Color(0xFF8D6E63) // brown
                else -> Color(0xFF8D6E63) // brown for outline/fill
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Transparent,
                    shape = if (isSelected) androidx.compose.foundation.shape.RoundedCornerShape(6.dp) else androidx.compose.foundation.shape.CircleShape,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF8D6E63))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = trait,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
