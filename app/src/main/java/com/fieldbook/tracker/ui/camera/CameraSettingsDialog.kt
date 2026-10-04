package com.fieldbook.tracker.ui.camera

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.ui.theme.ReadablePrimaryTheme
import com.fieldbook.tracker.utilities.camera.CameraSettingsState
import com.fieldbook.tracker.utilities.camera.ExposureMode
import com.fieldbook.tracker.utilities.camera.ShutterFormat

/**
 * Shows [CameraSettingsDialog] over a View-based [activity] by attaching a temporary ComposeView
 * to its content, removed again once the dialog closes.
 *
 * @param onCropClick opens the crop region editor, null when the trait doesn't crop
 */
fun showCameraSettingsDialog(
    activity: Activity,
    initial: CameraSettingsState,
    onCropClick: (() -> Unit)?,
    onConfirm: (CameraSettingsState) -> Unit
) {

    val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

    val composeView = ComposeView(activity)

    // removing the view disposes the composition, so defer it out of the click handler
    val close = { root.post { root.removeView(composeView) } }

    composeView.setContent {
        AppTheme {
            ReadablePrimaryTheme {
                CameraSettingsDialog(
                    initial = initial,
                    onCropClick = onCropClick,
                    onConfirm = { state ->
                        onConfirm(state)
                        close()
                    },
                    onDismiss = { close() }
                )
            }
        }
    }

    root.addView(composeView)
}

/**
 * Settings for the photo trait's camera, laid out like the label print trait's settings.
 */
@Composable
fun CameraSettingsDialog(
    initial: CameraSettingsState,
    onCropClick: (() -> Unit)?,
    onConfirm: (CameraSettingsState) -> Unit,
    onDismiss: () -> Unit
) {
    var state by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = stringResource(R.string.trait_system_photo_settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                SettingTitle(stringResource(R.string.view_trait_photo_settings_camera_title))

                ChoiceRow(
                    options = listOf(false, true),
                    selected = state.useSystemCamera,
                    label = {
                        stringResource(
                            if (it) R.string.view_trait_photo_settings_camera_system
                            else R.string.view_trait_photo_settings_camera_custom
                        )
                    },
                    onSelect = { state = state.copy(useSystemCamera = it) }
                )

                SupportingText(stringResource(R.string.view_trait_photo_settings_camera_choice_description))

                if (!state.useSystemCamera) {

                    SwitchRow(
                        text = stringResource(R.string.view_trait_photo_settings_preview_title),
                        checked = state.preview,
                        onCheckedChange = { state = state.copy(preview = it) }
                    )

                    if (state.resolutions.isNotEmpty()) {
                        ResolutionSelector(state) { state = state.copy(resolutionIndex = it) }
                    }

                    if (state.advancedAvailable) {
                        AdvancedSettings(state) { state = it }
                    }
                }

                if (onCropClick != null) {
                    OutlinedButton(
                        onClick = onCropClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.view_trait_photo_settings_crop))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state) }) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

/**
 * The selected resolution as a button that opens a dialog listing the available resolutions.
 */
@Composable
private fun ResolutionSelector(state: CameraSettingsState, onChange: (Int) -> Unit) {

    var showPicker by remember { mutableStateOf(false) }

    val selected = state.resolutions[state.resolutionIndex]

    val title = stringResource(R.string.view_trait_photo_settings_resolution_title)

    Column {

        SettingTitle(title)

        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.view_trait_photo_settings_resolution_value, selected.width, selected.height))
        }
    }

    if (showPicker) {
        ResolutionPickerDialog(
            title = title,
            state = state,
            onSelect = {
                onChange(it)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * Single-choice list of resolutions, largest first; choosing one closes the dialog.
 */
@Composable
private fun ResolutionPickerDialog(
    title: String,
    state: CameraSettingsState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .verticalScroll(rememberScrollState())
            ) {
                state.resolutionOrder.asReversed().forEach { index ->

                    val size = state.resolutions[index]
                    val isSelected = index == state.resolutionIndex

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(index) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Text(
                            text = stringResource(R.string.view_trait_photo_settings_resolution_value, size.width, size.height),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun AdvancedSettings(state: CameraSettingsState, onChange: (CameraSettingsState) -> Unit) {

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingTitle(stringResource(R.string.view_trait_photo_settings_advanced_title))

    if (state.exposureModes.size > 1) {

        SettingTitle(stringResource(R.string.view_trait_photo_settings_exposure_title))

        ChoiceRow(
            options = state.exposureModes,
            selected = state.exposureMode,
            label = { stringResource(it.labelRes()) },
            onSelect = { onChange(state.copy(exposureMode = it)) }
        )

        SupportingText(stringResource(state.exposureMode.summaryRes()))

        if (state.exposureMode == ExposureMode.MANUAL) {

            if (state.isoSteps.isNotEmpty()) {
                val title = stringResource(R.string.view_trait_photo_settings_iso)
                Column {
                    SettingTitle(title)
                    ValueStepper(
                        value = state.isoSteps[state.isoIndex].toString(),
                        title = title,
                        canDecrease = state.isoIndex > 0,
                        canIncrease = state.isoIndex < state.isoSteps.lastIndex,
                        onDecrease = { onChange(state.copy(isoIndex = state.isoIndex - 1)) },
                        onIncrease = { onChange(state.copy(isoIndex = state.isoIndex + 1)) }
                    )
                }
            }

            if (state.shutterSteps.isNotEmpty()) {
                val title = stringResource(R.string.view_trait_photo_settings_shutter)
                Column {
                    SettingTitle(title)
                    ValueStepper(
                        value = ShutterFormat.label(state.shutterSteps[state.shutterIndex]),
                        title = title,
                        canDecrease = state.shutterIndex > 0,
                        canIncrease = state.shutterIndex < state.shutterSteps.lastIndex,
                        onDecrease = { onChange(state.copy(shutterIndex = state.shutterIndex - 1)) },
                        onIncrease = { onChange(state.copy(shutterIndex = state.shutterIndex + 1)) }
                    )
                }
            }
        }
    }

    if (state.awbLockAvailable) {
        SwitchRow(
            text = stringResource(R.string.view_trait_photo_settings_awb_lock),
            checked = state.awbLock,
            onCheckedChange = { onChange(state.copy(awbLock = it)) }
        )
    }

    if (state.rawAvailable) {
        SwitchRow(
            text = stringResource(R.string.view_trait_photo_settings_save_raw),
            checked = state.saveRaw,
            onCheckedChange = { onChange(state.copy(saveRaw = it)) }
        )
    }
}

private fun ExposureMode.labelRes() = when (this) {
    ExposureMode.AUTO -> R.string.view_trait_photo_settings_exposure_auto
    ExposureMode.LOCKED -> R.string.view_trait_photo_settings_exposure_locked
    ExposureMode.MANUAL -> R.string.view_trait_photo_settings_exposure_manual
}

private fun ExposureMode.summaryRes() = when (this) {
    ExposureMode.AUTO -> R.string.view_trait_photo_settings_exposure_auto_summary
    ExposureMode.LOCKED -> R.string.view_trait_photo_settings_exposure_locked_summary
    ExposureMode.MANUAL -> R.string.view_trait_photo_settings_exposure_manual_summary
}

/**
 * Title for a setting; every setting (including switch rows) uses this so they all match.
 */
@Composable
private fun SettingTitle(text: String, modifier: Modifier = Modifier.padding(bottom = 4.dp)) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier
    )
}

@Composable
private fun SupportingText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(text = label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingTitle(text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A value between round - and + buttons, matching the label print trait's copies stepper.
 */
@Composable
private fun ValueStepper(
    value: String,
    title: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(onClick = onDecrease, enabled = canDecrease) {
            Icon(
                Icons.Default.Remove,
                contentDescription = stringResource(R.string.view_trait_photo_settings_decrease, title)
            )
        }

        // outlined like the buttons below it
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .border(ButtonDefaults.outlinedButtonBorder(enabled = true), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = value, style = MaterialTheme.typography.labelLarge)
        }

        FilledTonalIconButton(onClick = onIncrease, enabled = canIncrease) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.view_trait_photo_settings_increase, title)
            )
        }
    }
}
