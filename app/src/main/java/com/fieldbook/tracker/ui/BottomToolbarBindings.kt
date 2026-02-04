package com.fieldbook.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fieldbook.tracker.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview

enum class MediaOption {
    CAPTURE_MEDIA,
    VIEW_MEDIA
}

enum class MediaType {
    PHOTO, VIDEO, AUDIO
}

interface BottomToolbarListener {
    fun onMissing()
    fun onBarcode()
    fun onDelete()
    fun onDeleteLong()
    fun onMediaOption(option: MediaOption)
}

@Preview
@Composable
fun BottomToolbar(
    listener: BottomToolbarListener? = null,
    isAudioRecording: Boolean = false,
    isMediaEnabled: Boolean = true,
    mediaCount: Int = 0,
    deleteValueButtonEnabled: Boolean = true,
) {
    val bg = colorResource(id = R.color.main_primary)

    Surface(
        color = bg,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                // Show media button if trait supports any attach media; otherwise show barcode button.
                if (isMediaEnabled) {

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .combinedClickable(
                                onClick = { listener?.onMediaOption(MediaOption.CAPTURE_MEDIA) },
                                onLongClick = { listener?.onMediaOption(MediaOption.VIEW_MEDIA) },
                                role = Role.Button
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.star_four_points_circle_outline),
                            contentDescription = null,
                            tint = Color.White
                        )

                        // badge overlay: show when mediaCount > 0, max is 3
                        if (mediaCount > 0) {
                            val displayCount = mediaCount.coerceIn(1, 3)
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .background(color = Color.Red, shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayCount.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.Center),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    IconButton(onClick = { listener?.onBarcode() }) {
                        Icon(painter = painterResource(id = R.drawable.star_four_points_circle_outline), contentDescription = null, tint = Color.White)
                    }
                }

                IconButton(onClick = { listener?.onMissing() }) {
                    Icon(painter = painterResource(id = R.drawable.main_ic_missing), contentDescription = null, tint = Color.White)
                }

                // Delete button: support click and long-press (long triggers onDeleteLong)
                IconButton(
                    onClick = { listener?.onDelete() },
                    enabled = deleteValueButtonEnabled,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                listener?.onDeleteLong()
                            }
                        )
                    }
                ) {
                    Icon(painter = painterResource(id = R.drawable.main_ic_delete_forever), contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

fun bindBottomToolbar(
    composeView: androidx.compose.ui.platform.ComposeView,
    listener: BottomToolbarListener,
    isAudioRecording: Boolean = false,
    isMediaEnabled: Boolean = true,
    mediaCount: Int = 0,
    deleteValueButtonEnabled: Boolean = true,
) {
    composeView.setContent {
        MaterialTheme {
            BottomToolbar(listener, isAudioRecording, isMediaEnabled, mediaCount, deleteValueButtonEnabled)
        }
    }
}