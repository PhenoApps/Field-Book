package com.fieldbook.tracker.utilities.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Log
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import com.fieldbook.tracker.objects.TraitObject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Exposure behaviour for the built-in camera, stored per trait as [TraitObject.cameraExposureMode].
 */
enum class ExposureMode(val value: String) {
    AUTO("auto"),
    LOCKED("locked"),
    MANUAL("manual");

    companion object {
        fun parse(value: String?) = entries.firstOrNull { it.value == value } ?: AUTO
    }
}

/**
 * The camera control settings requested by a trait.
 * Values may be unsupported by the current device; check against [CameraCapabilities] before applying.
 */
data class CameraControlSettings(
    val exposureMode: ExposureMode = ExposureMode.AUTO,
    val iso: Int? = null,
    val exposureTimeNs: Long? = null,
    val awbLock: Boolean = false,
    val saveRaw: Boolean = false,
    // identifies whose locked values to remember/restore (the trait id); null disables remembering
    val lockKey: String? = null,
) {

    val needsLock get() = exposureMode == ExposureMode.LOCKED || awbLock

    companion object {

        val DEFAULT = CameraControlSettings()

        const val DEFAULT_ISO = 100

        // 1/125 s
        const val DEFAULT_EXPOSURE_NS = 8_000_000L

        // cap manual exposure at 1 s so the preview stays usable
        const val MAX_EXPOSURE_NS = 1_000_000_000L

        fun from(trait: TraitObject?, allowRaw: Boolean = true): CameraControlSettings {

            if (trait == null) return DEFAULT

            return CameraControlSettings(
                exposureMode = ExposureMode.parse(trait.cameraExposureMode),
                iso = trait.cameraIso.toIntOrNull(),
                exposureTimeNs = trait.cameraExposureTimeNs.toLongOrNull(),
                awbLock = trait.cameraAwbLock,
                saveRaw = allowRaw && trait.cameraSaveRaw,
                lockKey = trait.id.ifEmpty { null }
            )
        }
    }
}

/**
 * The camera controls supported by a specific camera device.
 */
data class CameraCapabilities(
    val cameraId: String?,
    val manualSensor: Boolean,
    val manualPostProcessing: Boolean,
    val raw: Boolean,
    val isoRange: Range<Int>?,
    val exposureTimeRangeNs: Range<Long>?,
    val postRawBoostRange: Range<Int>?,
    val aeLockAvailable: Boolean,
    val awbLockAvailable: Boolean,
) {

    val hasAnyAdvancedControl get() = manualSensor || raw || aeLockAvailable || awbLockAvailable

    /**
     * Standard full ISO stops within the device range, including the range end points.
     */
    fun isoSteps(): List<Int> {

        val range = isoRange ?: return emptyList()

        return (ISO_STOPS.filter { it in range.lower..range.upper } + range.lower + range.upper)
            .distinct()
            .sorted()
    }

    /**
     * Standard shutter speeds (in nanoseconds) within the device range, capped at
     * [CameraControlSettings.MAX_EXPOSURE_NS].
     */
    fun shutterStepsNs(): List<Long> {

        val range = exposureTimeRangeNs ?: return emptyList()

        val upper = minOf(range.upper, CameraControlSettings.MAX_EXPOSURE_NS)

        if (upper < range.lower) return emptyList()

        return SHUTTER_STOPS_NS.filter { it in range.lower..upper }
            .ifEmpty { listOf(range.lower) }
    }

    fun clampIso(value: Int?): Int {
        val range = isoRange ?: return value ?: CameraControlSettings.DEFAULT_ISO
        return (value ?: CameraControlSettings.DEFAULT_ISO).coerceIn(range.lower, range.upper)
    }

    fun clampExposure(value: Long?): Long {
        val range = exposureTimeRangeNs ?: return value ?: CameraControlSettings.DEFAULT_EXPOSURE_NS
        val upper = maxOf(range.lower, minOf(range.upper, CameraControlSettings.MAX_EXPOSURE_NS))
        return (value ?: CameraControlSettings.DEFAULT_EXPOSURE_NS).coerceIn(range.lower, upper)
    }

    companion object {

        private const val TAG = "CameraCapabilities"

        private val ISO_STOPS = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)

        private val SHUTTER_STOPS_NS = listOf(
            8000, 4000, 2000, 1000, 500, 250, 125, 60, 30, 15, 8, 4, 2
        ).map { 1_000_000_000L / it } + 1_000_000_000L

        val NONE = CameraCapabilities(
            cameraId = null,
            manualSensor = false,
            manualPostProcessing = false,
            raw = false,
            isoRange = null,
            exposureTimeRangeNs = null,
            postRawBoostRange = null,
            aeLockAvailable = false,
            awbLockAvailable = false
        )

        @OptIn(ExperimentalCamera2Interop::class)
        fun from(cameraInfo: CameraInfo): CameraCapabilities {

            return try {

                val info = Camera2CameraInfo.from(cameraInfo)

                val capabilities = info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?: intArrayOf()

                val isoRange = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)

                val exposureRange = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

                val manualSensor = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capabilities
                        && isoRange != null && exposureRange != null

                val raw = try {
                    ImageCapture.OUTPUT_FORMAT_RAW_JPEG in
                            ImageCapture.getImageCaptureCapabilities(cameraInfo).supportedOutputFormats
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to query RAW support", e)
                    false
                }

                CameraCapabilities(
                    cameraId = info.cameraId,
                    manualSensor = manualSensor,
                    manualPostProcessing = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in capabilities,
                    raw = raw,
                    isoRange = isoRange,
                    exposureTimeRangeNs = exposureRange,
                    postRawBoostRange = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE),
                    aeLockAvailable = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true,
                    awbLockAvailable = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true
                )

            } catch (e: Exception) {

                Log.w(TAG, "Unable to read camera capabilities", e)

                NONE
            }
        }

        /**
         * Index of the value in [steps] closest to [value].
         */
        fun <T : Number> nearestIndex(steps: List<T>, value: T): Int {
            if (steps.isEmpty()) return 0
            return steps.indices.minBy { abs(steps[it].toDouble() - value.toDouble()) }
        }
    }
}

object ShutterFormat {

    /**
     * Formats an exposure time as a photographer-style label, e.g. "1/125" or "1s".
     */
    fun label(ns: Long): String {
        return if (ns >= 1_000_000_000L) {
            val seconds = ns / 1_000_000_000.0
            if (seconds % 1.0 == 0.0) "${seconds.toInt()}s" else "${seconds}s"
        } else {
            "1/${(1_000_000_000.0 / ns).roundToInt()}"
        }
    }
}
