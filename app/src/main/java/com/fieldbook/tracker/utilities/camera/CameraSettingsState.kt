package com.fieldbook.tracker.utilities.camera

import android.content.SharedPreferences
import android.util.Size
import androidx.core.content.edit
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys

/**
 * Everything shown in the photo trait's camera settings dialog.
 *
 * Camera choice, preview and resolution are global preferences; the advanced controls are stored
 * per trait. Only options the current camera supports are populated.
 */
data class CameraSettingsState(
    val useSystemCamera: Boolean,
    val preview: Boolean,
    val resolutions: List<Size>,
    // index into [resolutions], as stored in GeneralKeys.CAMERA_RESOLUTION
    val resolutionIndex: Int,
    val advancedAvailable: Boolean,
    val exposureModes: List<ExposureMode>,
    val exposureMode: ExposureMode,
    val isoSteps: List<Int>,
    val isoIndex: Int,
    val shutterSteps: List<Long>,
    val shutterIndex: Int,
    val awbLockAvailable: Boolean,
    val awbLock: Boolean,
    val rawAvailable: Boolean,
    val saveRaw: Boolean,
) {

    /** Resolution indices ordered from smallest to largest pixel count. */
    val resolutionOrder: List<Int>
        get() = resolutions.indices.sortedBy { resolutions[it].width.toLong() * resolutions[it].height }

    companion object {

        fun from(
            prefs: SharedPreferences,
            trait: TraitObject?,
            resolutions: List<Size>,
            capabilities: CameraCapabilities,
            allowRaw: Boolean
        ): CameraSettingsState {

            val systemCamera = prefs.getInt(GeneralKeys.CAMERA_SYSTEM, R.id.view_trait_photo_settings_camera_custom_rb) ==
                    R.id.view_trait_photo_settings_camera_system_rb

            val savedResolution = prefs.getInt(GeneralKeys.CAMERA_RESOLUTION, 0)

            val exposureModes = buildList {
                add(ExposureMode.AUTO)
                if (capabilities.aeLockAvailable) add(ExposureMode.LOCKED)
                if (capabilities.manualSensor) add(ExposureMode.MANUAL)
            }

            // show Auto for a saved mode this device can't use; the stored value is kept unless changed
            val savedMode = ExposureMode.parse(trait?.cameraExposureMode)
            val exposureMode = if (savedMode in exposureModes) savedMode else ExposureMode.AUTO

            val isoSteps = if (capabilities.manualSensor) capabilities.isoSteps() else emptyList()
            val shutterSteps = if (capabilities.manualSensor) capabilities.shutterStepsNs() else emptyList()

            val rawAvailable = allowRaw && capabilities.raw

            val advancedAvailable = trait != null && (exposureModes.size > 1 ||
                    capabilities.awbLockAvailable || rawAvailable)

            return CameraSettingsState(
                useSystemCamera = systemCamera,
                preview = prefs.getBoolean(GeneralKeys.CAMERA_SYSTEM_PREVIEW, true),
                resolutions = resolutions,
                resolutionIndex = if (savedResolution in resolutions.indices) savedResolution else 0,
                advancedAvailable = advancedAvailable,
                exposureModes = exposureModes,
                exposureMode = exposureMode,
                isoSteps = isoSteps,
                isoIndex = CameraCapabilities.nearestIndex(isoSteps, capabilities.clampIso(trait?.cameraIso?.toIntOrNull())),
                shutterSteps = shutterSteps,
                shutterIndex = CameraCapabilities.nearestIndex(shutterSteps, capabilities.clampExposure(trait?.cameraExposureTimeNs?.toLongOrNull())),
                awbLockAvailable = capabilities.awbLockAvailable,
                awbLock = trait?.cameraAwbLock ?: false,
                rawAvailable = rawAvailable,
                saveRaw = trait?.cameraSaveRaw ?: false
            )
        }
    }

    /**
     * Saves what changed relative to [initial]: global preferences, then the trait's advanced
     * camera controls. Unchanged values are left as stored, so settings this device can't show survive.
     */
    fun commit(prefs: SharedPreferences, trait: TraitObject?, initial: CameraSettingsState) {

        prefs.edit {

            if (useSystemCamera != initial.useSystemCamera) {
                putInt(
                    GeneralKeys.CAMERA_SYSTEM,
                    if (useSystemCamera) R.id.view_trait_photo_settings_camera_system_rb
                    else R.id.view_trait_photo_settings_camera_custom_rb
                )
            }

            if (preview != initial.preview) putBoolean(GeneralKeys.CAMERA_SYSTEM_PREVIEW, preview)

            if (resolutionIndex != initial.resolutionIndex) putInt(GeneralKeys.CAMERA_RESOLUTION, resolutionIndex)
        }

        if (trait == null || !advancedAvailable) return

        val exposureChanged = exposureMode != initial.exposureMode
        val awbChanged = awbLock != initial.awbLock

        var changed = false

        if (exposureChanged) {
            trait.cameraExposureMode = exposureMode.value
            changed = true
        }

        // save the values shown whenever manual is selected, even if the steppers weren't touched
        if (exposureMode == ExposureMode.MANUAL) {
            isoSteps.getOrNull(isoIndex)?.let { trait.cameraIso = it.toString(); changed = true }
            shutterSteps.getOrNull(shutterIndex)?.let { trait.cameraExposureTimeNs = it.toString(); changed = true }
        }

        if (awbChanged) {
            trait.cameraAwbLock = awbLock
            changed = true
        }

        if (saveRaw != initial.saveRaw) {
            trait.cameraSaveRaw = saveRaw
            changed = true
        }

        if (changed) trait.saveAttributeValues()

        // a new exposure or white balance choice should be metered fresh rather than restored
        if (exposureChanged || awbChanged) LockedCameraValuesStore.clear(trait.id)
    }
}
