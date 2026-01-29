package com.fieldbook.tracker.traits.formats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R

@Preview
@Composable
fun AttachMediaChoice(
    photoState: MutableState<Boolean> = mutableStateOf(false),
    videoState: MutableState<Boolean> = mutableStateOf(false),
    audioState: MutableState<Boolean> = mutableStateOf(false)
) {
    Box(
        modifier = Modifier
            .wrapContentSize()
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AttachMediaItem(
                state = photoState,
                iconId = R.drawable.ic_trait_camera
            )
            AttachMediaItem(
                state = videoState,
                iconId = R.drawable.video
            )
            AttachMediaItem(
                state = audioState,
                iconId = R.drawable.trait_audio
            )
        }
    }
}

@Composable
fun AttachMediaItem(state: MutableState<Boolean>, iconId: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painter = painterResource(iconId), contentDescription = null)
        Checkbox(checked = state.value, onCheckedChange = { state.value = it })
    }
}