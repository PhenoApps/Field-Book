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
 * Shown when the user taps the canopy sensitivity chip on a trait that already has observations.
 * The threshold is applied both when analyzing a capture and when redrawing stored overlays, so
 * editing it after collection would make existing values inconsistent with new ones.
 */
@Composable
fun CanopySensitivityLockedDialog(
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        title = stringResource(R.string.canopy_param_locked_title),
        content = {
            Text(
                text = stringResource(R.string.canopy_param_locked_message),
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
private fun CanopySensitivityLockedDialogPreview() {
    AppTheme {
        CanopySensitivityLockedDialog(onDismiss = {})
    }
}
