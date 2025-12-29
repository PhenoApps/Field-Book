package com.fieldbook.tracker.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.fieldbook.tracker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private fun parseUri(uriStr: String): Uri {
    return try {
        if (uriStr.startsWith("content://") || uriStr.startsWith("file://") || uriStr.contains("://")) {
            uriStr.toUri()
        } else {
            Uri.fromFile(File(uriStr))
        }
    } catch (e: Exception) {
        uriStr.toUri()
    }
}

@Composable
fun PhotoItem(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var aspectRatio by remember { mutableFloatStateOf(1f) }
    val parsedUri = remember(uri) { parseUri(uri) }

    LaunchedEffect(uri) {
        val b = withContext(Dispatchers.IO) {
            try {
                // Read EXIF orientation
                var rotationDegrees = 0f
                try {
                    context.contentResolver.openInputStream(parsedUri)?.use { exifStream ->
                        val exif = ExifInterface(exifStream)
                        val orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                        rotationDegrees = when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    }
                } catch (ex: Exception) {
                    rotationDegrees = 0f
                }

                val decoded = context.contentResolver.openInputStream(parsedUri)
                    ?.use { BitmapFactory.decodeStream(it) }
                if (decoded != null && rotationDegrees != 0f) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees) }
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                } else decoded
            } catch (e: Exception) {
                null
            }
        }

        bitmap = b
        bitmap?.let { bmp ->
            aspectRatio = if (bmp.height == 0) 1f else bmp.width.toFloat() / bmp.height.toFloat()
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(), contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(
                                parsedUri,
                                context.contentResolver.getType(parsedUri)
                            )
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // fallback: do nothing
                        }
                    },
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.unable_to_load_image),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun VideoItem(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }

    LaunchedEffect(uri) {
        try {
            exoPlayer.setMediaItem(MediaItem.fromUri(parseUri(uri)))
            exoPlayer.prepare()
        } catch (e: Exception) {
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        AndroidView(factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
        }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun AudioItem(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = false } }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }

    LaunchedEffect(uri) {
        try {
            exoPlayer.setMediaItem(MediaItem.fromUri(parseUri(uri)))
            exoPlayer.prepare()
            duration = exoPlayer.duration.coerceAtLeast(0)
        } catch (e: Exception) {
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // update playback state and position periodically
    LaunchedEffect(exoPlayer) {
        while (true) {
            try {
                isPlaying = exoPlayer.isPlaying
                position = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0)
            } catch (e: Exception) {
                // ignore
            }
            delay(250)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            }) {
                if (isPlaying) Icon(
                    Icons.Default.Pause,
                    contentDescription = stringResource(R.string.pause)
                )
                else Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.play)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                // slider
                val dur = if (duration <= 0) 1L else duration
                Slider(
                    value = (position.coerceAtMost(dur)).toFloat() / dur.toFloat(),
                    onValueChange = { frac ->
                        val seekPos = (frac * dur).toLong()
                        exoPlayer.seekTo(seekPos)
                        position = seekPos
                    })
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(formatMillis(position))
                    Text(if (duration <= 0) "--:--" else formatMillis(duration))
                }
            }
        }
    }
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format(
        "%02d:%02d",
        minutes,
        seconds
    )
}

@Composable
fun ConfirmDeleteDialog(show: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        title = { Text(stringResource(R.string.confirm_delete)) },
        text = { Text(stringResource(R.string.are_you_sure_you_want_to_delete_this_media_item)) })
}

// helper to create a ComposeView preview for CollectActivity
fun createMediaPreviewComposeView(
    context: Context,
    uri: String,
    type: String,
): ComposeView {
    val cv = ComposeView(context)

    // If the provided context is a LifecycleOwner / SavedStateRegistryOwner (Activity/FragmentActivity),
    // attach them to the ComposeView so it can resolve owners immediately when attached in dialogs.
    try {
        if (context is androidx.lifecycle.LifecycleOwner) {
            cv.setViewTreeLifecycleOwner(context)
        }
    } catch (_: Exception) {
    }

    try {
        if (context is androidx.savedstate.SavedStateRegistryOwner) {
            cv.setViewTreeSavedStateRegistryOwner(context)
        }
    } catch (_: Exception) {
    }
    // Ensure the composition is disposed when the view is detached to avoid leaks and lifecycle issues
    cv.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    cv.setContent {
        Surface(shape = RoundedCornerShape(8.dp)) {
            when (type) {
                "photo" -> PhotoItem(
                    uri = uri,
                    modifier = Modifier.padding(8.dp),
                )

                "video" -> VideoItem(
                    uri = uri,
                    modifier = Modifier.padding(8.dp),
                )

                else -> AudioItem(
                    uri = uri,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
    return cv
}