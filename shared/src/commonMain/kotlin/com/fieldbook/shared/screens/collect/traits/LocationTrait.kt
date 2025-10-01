package com.fieldbook.shared.screens.collect.traits

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LocationTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextTrait(value = value, onValueChange = onValueChange, modifier = modifier)
}

