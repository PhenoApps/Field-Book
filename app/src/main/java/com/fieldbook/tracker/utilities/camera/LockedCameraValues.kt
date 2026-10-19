package com.fieldbook.tracker.utilities.camera

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop

/**
 * Exposure values the camera had settled on when exposure was locked.
 */
data class LockedExposure(
    val iso: Int,
    val exposureTimeNs: Long,
    val frameDurationNs: Long?,
    val postRawBoost: Int?,
)

/**
 * White balance values the camera had settled on when white balance was locked.
 */
data class LockedWhiteBalance(
    val gains: RggbChannelVector,
    val transform: ColorSpaceTransform,
)

data class LockedCameraValues(
    val exposure: LockedExposure? = null,
    val whiteBalance: LockedWhiteBalance? = null,
) {

    companion object {

        /**
         * Reads the values the camera used for a frame.
         * Components the device doesn't report are left null.
         */
        fun from(result: CaptureResult, exposure: Boolean, whiteBalance: Boolean): LockedCameraValues {

            val lockedExposure = if (exposure) {
                val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                val time = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                if (iso != null && time != null) {
                    LockedExposure(
                        iso = iso,
                        exposureTimeNs = time,
                        frameDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION),
                        postRawBoost = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST)
                    )
                } else null
            } else null

            val lockedWhiteBalance = if (whiteBalance) {
                val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)
                val transform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)
                if (gains != null && transform != null) LockedWhiteBalance(gains, transform) else null
            } else null

            return LockedCameraValues(lockedExposure, lockedWhiteBalance)
        }
    }
}

/**
 * Adds request options that reproduce a locked exposure with auto-exposure turned off.
 * Requires [CameraCapabilities.manualSensor].
 */
@OptIn(ExperimentalCamera2Interop::class)
fun CaptureRequestOptions.Builder.applyExposure(
    exposure: LockedExposure,
    capabilities: CameraCapabilities
): CaptureRequestOptions.Builder {

    val iso = capabilities.clampIso(exposure.iso)

    // the stored time came from the camera itself, so only clamp to the device range
    val time = capabilities.exposureTimeRangeNs?.let {
        exposure.exposureTimeNs.coerceIn(it.lower, it.upper)
    } ?: exposure.exposureTimeNs

    setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
    setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
    setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, time)

    exposure.frameDurationNs?.let {
        setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(it, time))
    }

    // some devices add digital gain after the sensor; restore it so brightness matches
    val boostRange = capabilities.postRawBoostRange
    if (exposure.postRawBoost != null && boostRange != null) {
        setCaptureRequestOption(
            CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST,
            exposure.postRawBoost.coerceIn(boostRange.lower, boostRange.upper)
        )
    }

    return this
}

/**
 * Adds request options that reproduce a locked white balance with auto-white-balance turned off.
 * Requires [CameraCapabilities.manualPostProcessing].
 */
@OptIn(ExperimentalCamera2Interop::class)
fun CaptureRequestOptions.Builder.applyWhiteBalance(
    whiteBalance: LockedWhiteBalance
): CaptureRequestOptions.Builder {

    setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
    setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
    setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, whiteBalance.gains)
    setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_TRANSFORM, whiteBalance.transform)

    return this
}

/**
 * Remembers locked exposure and white balance per trait and camera, so a lock survives the camera
 * being rebound (expanding/collapsing the preview, switching plots or traits).
 *
 * Values live for the app process only; a new session meters again.
 */
object LockedCameraValuesStore {

    private val values = mutableMapOf<String, LockedCameraValues>()

    private fun key(traitId: String, cameraId: String) = "$traitId|$cameraId"

    @Synchronized
    fun get(traitId: String, cameraId: String): LockedCameraValues? = values[key(traitId, cameraId)]

    /**
     * Stores the non-null components of [update], keeping any existing components it doesn't include.
     */
    @Synchronized
    fun merge(traitId: String, cameraId: String, update: LockedCameraValues) {
        val k = key(traitId, cameraId)
        val existing = values[k] ?: LockedCameraValues()
        values[k] = LockedCameraValues(
            exposure = update.exposure ?: existing.exposure,
            whiteBalance = update.whiteBalance ?: existing.whiteBalance
        )
    }

    @Synchronized
    fun clear(traitId: String, cameraId: String) {
        values.remove(key(traitId, cameraId))
    }

    /**
     * Forget a trait's values on every camera, e.g. after its camera settings change.
     */
    @Synchronized
    fun clear(traitId: String) {
        values.keys.removeAll { it.startsWith("$traitId|") }
    }
}
