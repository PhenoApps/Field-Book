package com.fieldbook.tracker.ui.screens.traits.dialogs

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.dialogs.builder.AppAlertDialog
import com.fieldbook.tracker.ui.theme.AppTheme

/**
 * Shown when a camera trait is switched to video, with the camera specific explanation of how its
 * videos are stored that the format supplies.
 */
@Composable
fun CaptureVideoNoticeDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    CaptureModeMessageDialog(
        title = title,
        message = message,
        onDismiss = onDismiss
    )
}

/**
 * Shown when the user taps the photo/video chip on a trait that already has observations.
 * Switching would mix photos and videos under one trait.
 */
@Composable
fun CaptureModeLockedDialog(
    onDismiss: () -> Unit,
) {
    CaptureModeMessageDialog(
        title = stringResource(R.string.capture_mode_locked_title),
        message = stringResource(R.string.capture_mode_locked_message),
        onDismiss = onDismiss
    )
}

@Composable
private fun CaptureModeMessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        title = title,
        content = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        },
        positiveButtonText = stringResource(R.string.dialog_ok),
        onPositive = onDismiss,
        // no negative button, but wire the callback so tapping outside/back dismisses
        onNegative = onDismiss
    )
}

@Preview
@Composable
private fun CaptureVideoNoticeDialogPreview() {
    AppTheme {
        CaptureVideoNoticeDialog(
            title = stringResource(R.string.gopro_video_notice_title),
            message = stringResource(R.string.gopro_video_notice_message),
            onDismiss = {}
        )
    }
}

@Preview
@Composable
private fun CaptureModeLockedDialogPreview() {
    AppTheme {
        CaptureModeLockedDialog(onDismiss = {})
    }
}
