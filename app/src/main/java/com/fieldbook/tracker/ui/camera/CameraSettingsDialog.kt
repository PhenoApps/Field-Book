package com.fieldbook.tracker.ui.camera

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.ui.theme.ReadablePrimaryTheme
import com.fieldbook.tracker.utilities.camera.CameraSettingsState
import com.fieldbook.tracker.utilities.camera.ExposureMode
import com.fieldbook.tracker.utilities.camera.ShutterFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

// a little shorter than Material's default 4 x 44dp slider thumb
private val SLIDER_THUMB_SIZE = DpSize(4.dp, 36.dp)

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
                        AdvancedSettings(state) { transform -> state = transform(state) }
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

/**
 * Per-trait camera controls. Changes are passed to [update] as transforms of the latest state,
 * so a click that follows a long-press doesn't overwrite the long-press's changes.
 */
@Composable
private fun AdvancedSettings(
    state: CameraSettingsState,
    update: ((CameraSettingsState) -> CameraSettingsState) -> Unit
) {

    if (state.exposureModes.size > 1) {

        SettingTitle(stringResource(R.string.view_trait_photo_settings_exposure_title))

        ChoiceRow(
            options = state.exposureModes,
            selected = state.exposureMode,
            label = { stringResource(it.labelRes()) },
            onSelect = { mode -> update { it.copy(exposureMode = mode) } },
            // long-pressing Manual resets ISO and shutter speed to their defaults
            onLongPress = { mode ->
                if (mode == ExposureMode.MANUAL) update { it.withManualDefaults() }
            }
        )

        SupportingText(stringResource(state.exposureMode.summaryRes()))

        if (state.exposureMode == ExposureMode.MANUAL) {

            if (state.isoSteps.isNotEmpty()) {
                StepSlider(
                    title = stringResource(R.string.view_trait_photo_settings_iso),
                    value = state.isoSteps[state.isoIndex].toString(),
                    index = state.isoIndex,
                    count = state.isoSteps.size,
                    onIndexChange = { index -> update { it.copy(isoIndex = index) } }
                )
            }

            if (state.shutterSteps.isNotEmpty()) {
                StepSlider(
                    title = stringResource(R.string.view_trait_photo_settings_shutter),
                    value = ShutterFormat.label(state.shutterSteps[state.shutterIndex]),
                    index = state.shutterIndex,
                    count = state.shutterSteps.size,
                    onIndexChange = { index -> update { it.copy(shutterIndex = index) } }
                )
            }
        }
    }

    if (state.awbLockAvailable) {
        SwitchRow(
            text = stringResource(R.string.view_trait_photo_settings_awb_lock),
            checked = state.awbLock,
            onCheckedChange = { checked -> update { it.copy(awbLock = checked) } }
        )
    }

    if (state.rawAvailable) {
        SwitchRow(
            text = stringResource(R.string.view_trait_photo_settings_save_raw),
            checked = state.saveRaw,
            onCheckedChange = { checked -> update { it.copy(saveRaw = checked) } }
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

/**
 * @param onLongPress called when an option is held; the option's click still follows on release
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onLongPress: ((T) -> Unit)? = null
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            key(option) {

                val interactionSource = remember { MutableInteractionSource() }

                if (onLongPress != null) {
                    LongPressEffect(interactionSource) { onLongPress(option) }
                }

                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    interactionSource = interactionSource
                ) {
                    Text(text = label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Calls [onLongPress] (with haptic feedback) when a press on [interactionSource] is held for the
 * system long-press timeout. Releasing or cancelling earlier cancels it.
 */
@Composable
private fun LongPressEffect(interactionSource: InteractionSource, onLongPress: () -> Unit) {

    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val haptics = LocalHapticFeedback.current
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    LaunchedEffect(interactionSource) {
        // collectLatest cancels the pending delay as soon as the press is released or cancelled
        interactionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Press) {
                delay(timeout)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnLongPress()
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
 * A slider that snaps to [count] discrete steps, with the current step's [value] shown beside the title.
 * The slider is hidden when there is only one step to choose from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepSlider(
    title: String,
    value: String,
    index: Int,
    count: Int,
    onIndexChange: (Int) -> Unit
) {
    Column {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingTitle(title, modifier = Modifier.weight(1f))
            Text(text = value, style = MaterialTheme.typography.labelLarge)
        }

        if (count > 1) {

            val interactionSource = remember { MutableInteractionSource() }

            Slider(
                value = index.toFloat(),
                onValueChange = { onIndexChange(it.roundToInt().coerceIn(0, count - 1)) },
                valueRange = 0f..(count - 1).toFloat(),
                // steps counts the stops between the two ends
                steps = (count - 2).coerceAtLeast(0),
                interactionSource = interactionSource,
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        thumbSize = SLIDER_THUMB_SIZE
                    )
                },
                modifier = Modifier.semantics { stateDescription = value }
            )
        }
    }
}
