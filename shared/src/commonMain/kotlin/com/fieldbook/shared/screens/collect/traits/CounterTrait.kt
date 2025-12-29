package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.minus
import com.fieldbook.shared.theme.FilledIconButton
import org.jetbrains.compose.resources.painterResource

@Composable
fun CounterTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var count by remember { mutableStateOf(value.toIntOrNull() ?: 0) }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        FilledIconButton(
            onClick = { count--; onValueChange(count.toString()) },
            shape = CircleShape
        ) {
            Icon(
                painter = painterResource(Res.drawable.minus),
                contentDescription = "Increment",
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.size(8.dp))
        FilledIconButton(
            onClick = { count++; onValueChange(count.toString()) },
            shape = CircleShape,
            modifier = Modifier.size(156.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Increment",
                modifier = Modifier.size(38.dp)
            )
        }
    }
}
