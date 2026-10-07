package com.fieldbook.tracker.utilities

import android.content.Context
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.ColorFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Size
import android.util.TypedValue
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ExtendableBuilder
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.camera.CameraCapabilities
import com.fieldbook.tracker.utilities.camera.CameraControlSettings
import com.fieldbook.tracker.utilities.camera.ExposureMode
import com.fieldbook.tracker.utilities.camera.LockedCameraValues
import com.fieldbook.tracker.utilities.camera.LockedCameraValuesStore
import com.fieldbook.tracker.utilities.camera.ThreeAStateMonitor
import com.fieldbook.tracker.utilities.camera.applyExposure
import com.fieldbook.tracker.utilities.camera.applyWhiteBalance
import com.fieldbook.tracker.views.CropImageView
import dagger.hilt.android.qualifiers.ActivityContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@OptIn(ExperimentalCamera2Interop::class)
class CameraXFacade @Inject constructor(@param:ActivityContext private val context: Context) {

    companion object {
        val TAG: String = CameraXFacade::class.java.simpleName
    }

    val cameraXInstance by lazy {
        ProcessCameraProvider.getInstance(context)
    }

    val frontSelector by lazy {

        CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

    }

    // track current lens facing and allow toggling between back and front selectors
    private var isBackFacing = true

    val currentSelector: CameraSelector
        get() = if (isBackFacing) frontSelector else CameraSelector.DEFAULT_FRONT_CAMERA

    val executor: ExecutorService? by lazy {
        Executors.newSingleThreadExecutor()
    }

    fun await(context: Context, onBindReady: () -> Unit) {
        cameraXInstance.get().unbind()
        cameraXInstance.addListener({
            onBindReady()
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        monitor?.cancel()
        cameraXInstance.get().unbindAll()
    }

    // tracks AE/AWB convergence for the currently bound session
    private var monitor: ThreeAStateMonitor? = null

    // the camera control settings used by the last bind
    private var activeControls: CameraControlSettings = CameraControlSettings.DEFAULT

    /** True when the last bind captures RAW (DNG) alongside JPEG. */
    var isRawCaptureActive = false
        private set

    /** True when the last bind requested RAW but had to fall back to JPEG only. */
    var rawCaptureFellBack = false
        private set

    /** Invoked on the main thread once AE and/or AWB locks have been engaged. */
    var onLocksApplied: ((ae: Boolean, awb: Boolean) -> Unit)? = null

    private fun capabilitiesFor(selector: CameraSelector): CameraCapabilities = try {
        CameraCapabilities.from(cameraXInstance.get().getCameraInfo(selector))
    } catch (e: Exception) {
        Log.w(TAG, "Unable to read capabilities for selector", e)
        CameraCapabilities.NONE
    }

    /**
     * Applies manual ISO and shutter speed as static options, so they cover both the repeating
     * preview requests and still-capture requests.
     */
    private fun <T> applyManualExposure(
        builder: ExtendableBuilder<T>,
        controls: CameraControlSettings,
        capabilities: CameraCapabilities
    ) {

        if (controls.exposureMode != ExposureMode.MANUAL || !capabilities.manualSensor) return

        val exposure = capabilities.clampExposure(controls.exposureTimeNs)

        val extender = Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, capabilities.clampIso(controls.iso))
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure)

        // long exposures need a frame duration at least as long as the exposure
        if (exposure > 33_333_333L) {
            extender.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, exposure)
        }
    }

    private fun <T> attachMonitor(builder: ExtendableBuilder<T>) {
        monitor?.cancel()
        monitor = ThreeAStateMonitor().also {
            Camera2Interop.Extender(builder).setSessionCaptureCallback(it)
        }
    }

    /**
     * Locks exposure and/or white balance as requested by [controls].
     *
     * Values remembered for this trait and camera (see [LockedCameraValuesStore]) are restored
     * directly as manual values when the device supports it, so a lock survives the camera being
     * rebound. Anything not restored is metered: once AE/AWB converge the lock is engaged, and the
     * values the camera locked to are remembered for the next bind.
     *
     * [onLocked] runs on the main thread once the locks are in place.
     */
    fun applyLocks(
        camera: Camera,
        controls: CameraControlSettings = activeControls,
        onLocked: (() -> Unit)? = null
    ) {

        val capabilities = CameraCapabilities.from(camera.cameraInfo)

        val wantAe = controls.exposureMode == ExposureMode.LOCKED && capabilities.aeLockAvailable
        val wantAwb = controls.awbLock && capabilities.awbLockAvailable

        val control = Camera2CameraControl.from(camera.cameraControl)

        // drop any lock sequence still waiting from an earlier bind or re-meter
        monitor?.cancel()

        val traitKey = controls.lockKey
        val cameraId = capabilities.cameraId

        val stored = if (traitKey != null && cameraId != null) {
            LockedCameraValuesStore.get(traitKey, cameraId)
        } else null

        // exact values can only be set back with auto-exposure / auto-white-balance turned off
        val restoredExposure = stored?.exposure?.takeIf { wantAe && capabilities.manualSensor }
        val restoredWhiteBalance = stored?.whiteBalance?.takeIf { wantAwb && capabilities.manualPostProcessing }

        fun restoredOptions() = CaptureRequestOptions.Builder().apply {
            restoredExposure?.let { applyExposure(it, capabilities) }
            restoredWhiteBalance?.let { applyWhiteBalance(it) }
        }

        // setCaptureRequestOptions replaces the previous options, so this also clears any old lock
        control.setCaptureRequestOptions(restoredOptions().build())

        val meterAe = wantAe && restoredExposure == null
        val meterAwb = wantAwb && restoredWhiteBalance == null

        if (!meterAe && !meterAwb) {
            if (wantAe || wantAwb) {
                Log.d(TAG, "Restored camera locks: ae=$wantAe awb=$wantAwb")
                notifyLocked(wantAe, wantAwb, onLocked)
            }
            return
        }

        val stateMonitor = monitor ?: return

        stateMonitor.awaitConverged(meterAe, meterAwb) {

            val options = restoredOptions().apply {
                if (meterAe) setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                if (meterAwb) setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
            }.build()

            control.setCaptureRequestOptions(options)

            Log.d(TAG, "Applied camera locks: ae=$wantAe awb=$wantAwb")

            notifyLocked(wantAe, wantAwb, onLocked)

            // remember what the camera locked to so the next bind can restore it
            if (traitKey != null && cameraId != null) {
                stateMonitor.awaitLockedFrame(meterAe, meterAwb) { result ->
                    result?.let {
                        val values = LockedCameraValues.from(it, meterAe, meterAwb)
                        LockedCameraValuesStore.merge(traitKey, cameraId, values)
                        Log.d(TAG, "Remembered locked values for trait $traitKey camera $cameraId: $values")
                    }
                }
            }
        }
    }

    private fun notifyLocked(ae: Boolean, awb: Boolean, onLocked: (() -> Unit)?) {
        onLocksApplied?.invoke(ae, awb)
        onLocked?.invoke()
    }

    /**
     * Forgets the remembered values, re-meters exposure and white balance (at [point] if given),
     * then locks and remembers the new values.
     */
    fun remeterAndLock(
        camera: Camera,
        point: MeteringPoint?,
        controls: CameraControlSettings = activeControls,
        onLocked: (() -> Unit)? = null
    ) {

        monitor?.cancel()

        val cameraId = CameraCapabilities.from(camera.cameraInfo).cameraId

        val traitKey = controls.lockKey

        if (traitKey != null && cameraId != null) {
            LockedCameraValuesStore.clear(traitKey, cameraId)
        }

        // release the locks (and any restored manual values) so the camera adjusts again
        Camera2CameraControl.from(camera.cameraControl)
            .setCaptureRequestOptions(CaptureRequestOptions.Builder().build())

        if (point == null) {
            applyLocks(camera, controls, onLocked)
            return
        }

        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
        ).disableAutoCancel().build()

        camera.cameraControl.startFocusAndMetering(action).addListener({
            applyLocks(camera, controls, onLocked)
        }, ContextCompat.getMainExecutor(context))
    }

    /** Toggle between back and front camera selectors. */
    fun toggleCameraSelector() {
        isBackFacing = !isBackFacing
    }

    fun bindIdentity(
        onBind: (Camera, List<Size>) -> Unit,
        onFail: () -> Unit
    ) {

        unbind()

        try {

            val camera = cameraXInstance.get().bindToLifecycle(
                context as LifecycleOwner,
                frontSelector,
            )

            val info = Camera2CameraInfo.from(camera.cameraInfo)

            val configs =
                info.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            val allSizes = (configs?.getOutputSizes(ImageFormat.JPEG) ?: arrayOf()).toList()

            //get all sizes that are 4:3 aspect ratio
            val aspectRatioSizes = allSizes.filter {
                it.width.toFloat() / it.height.toFloat() == 4f / 3f
            }

            onBind(camera, aspectRatioSizes)

        } catch (e: Exception) {

            e.printStackTrace()

            onFail()

        }
    }

    private fun buildResolutionSelector(targetResolution: Size): ResolutionSelector {

        val resolutionStrategy = ResolutionStrategy(targetResolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER)

        val aspectRatioStrategy = AspectRatioStrategy(
            AspectRatio.RATIO_4_3,
            AspectRatioStrategy.FALLBACK_RULE_NONE
        )

        return ResolutionSelector.Builder()
            .setResolutionStrategy(resolutionStrategy)
            .setAspectRatioStrategy(aspectRatioStrategy)
            .build()
    }

    /**
     * Binds with RAW + JPEG output when requested and supported, falling back to JPEG only
     * if the device rejects the stream combination.
     */
    private fun bindWithRawFallback(
        controls: CameraControlSettings,
        capabilities: CameraCapabilities,
        bindOnce: (raw: Boolean) -> Pair<Camera, ImageCapture>
    ): Pair<Camera, ImageCapture> {

        activeControls = controls

        val wantRaw = controls.saveRaw && capabilities.raw

        // only report a fallback when a RAW-capable camera rejected the stream combination
        rawCaptureFellBack = false

        val result = try {

            bindOnce(wantRaw).also { isRawCaptureActive = wantRaw }

        } catch (e: IllegalArgumentException) {

            if (!wantRaw) throw e

            Log.w(TAG, "RAW + JPEG capture unsupported in this configuration, falling back to JPEG", e)

            unbind()

            rawCaptureFellBack = true

            bindOnce(false).also { isRawCaptureActive = false }
        }

        applyLocks(result.first, controls)

        return result
    }

    fun bindFrontCapture(
        targetResolution: Size?,
        controls: CameraControlSettings = CameraControlSettings.DEFAULT,
        onBind: (Camera, ExecutorService?, ImageCapture) -> Unit,
    ) {

        unbind()

        try {

            val capabilities = capabilitiesFor(currentSelector)

            val (camera, imageCapture) = bindWithRawFallback(controls, capabilities) { raw ->

                val builder = ImageCapture.Builder()

                if (targetResolution != null) {
                    builder.setResolutionSelector(buildResolutionSelector(targetResolution))
                }

                if (raw) builder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)

                applyManualExposure(builder, controls, capabilities)

                attachMonitor(builder)

                val imageCapture = builder.build()

                val camera = cameraXInstance.get().bindToLifecycle(
                    context as LifecycleOwner,
                    currentSelector,
                    imageCapture
                )

                camera to imageCapture
            }

            Log.d(TAG, "Camera lifecycle bound: ${camera.cameraInfo}")

            onBind.invoke(camera, executor, imageCapture)

        } catch (_: IllegalArgumentException) {

        }
    }

    // previously used an OverlayEffect; replaced with a Drawable that is added to PreviewView.overlay
    private var cropDrawable: CropOverlayDrawable? = null

    @Suppress("UNUSED_PARAMETER")
    fun bindPreview(
        previewView: PreviewView?,
        targetResolution: Size?,
        traitId: String? = null,
        analysis: ImageAnalysis?,
        showCropRegion: Boolean,
        controls: CameraControlSettings = CameraControlSettings.DEFAULT,
        onBind: (Camera, ExecutorService?, ImageCapture) -> Unit
    ) {

        unbind()

        // remove previous drawable overlay if present
        try {
            previewView?.let { pv ->
                cropDrawable?.let { pv.overlay.remove(it) }
                cropDrawable = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing previous crop drawable: ${e.message}")
        }

        //parse crop from prefs
        val cropCoordinates = (context as? ThemedActivity)?.prefs?.getString(GeneralKeys.getCropCoordinatesKey(
            traitId?.toInt() ?: -1), "")

        // If crop coordinates are valid, add a drawable to the PreviewView overlay that dims outside the crop rect
        if (showCropRegion && !cropCoordinates.isNullOrEmpty() && cropCoordinates != CropImageView.DEFAULT_CROP_COORDINATES && previewView != null) {
            val rect = CropImageView.parseRectCoordinates(cropCoordinates)
            if (rect != null) {
                val typedValue = TypedValue()
                context.theme?.resolveAttribute(
                    R.attr.fb_inverse_crop_region_color,
                    typedValue,
                    true
                )
                val color = typedValue.data

                cropDrawable = CropOverlayDrawable(previewView, rect, color)
                // Ensure drawable bounds cover the preview view
                // Add overlay after layout to ensure width/height are known
                previewView.post {
                    try {
                        cropDrawable?.setBounds(0, 0, previewView.width, previewView.height)
                        cropDrawable?.let { previewView.overlay.add(it) }
                        previewView.invalidate()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error adding crop drawable: ${e.message}")
                    }
                }

                // listen for layout changes to update bounds
                previewView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    cropDrawable?.setBounds(0, 0, v.width, v.height)
                    v.invalidate()
                }
            }
        }

        val capabilities = capabilitiesFor(currentSelector)

        try {
            val (camera, imageCapture) = bindWithRawFallback(controls, capabilities) { raw ->

                val builder = ImageCapture.Builder()
                val prevBuilder = Preview.Builder()

                if (targetResolution != null) {
                    val resolutionSelector = buildResolutionSelector(targetResolution)
                    builder.setResolutionSelector(resolutionSelector)
                    prevBuilder.setResolutionSelector(resolutionSelector)
                }

                if (raw) builder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)

                applyManualExposure(builder, controls, capabilities)
                applyManualExposure(prevBuilder, controls, capabilities)

                attachMonitor(prevBuilder)

                val imageCapture = builder.build()

                val p = prevBuilder.build()

                p.surfaceProvider = previewView?.surfaceProvider

                val useCaseGroupBuilder = UseCaseGroup.Builder()

                useCaseGroupBuilder.addUseCase(p)

                useCaseGroupBuilder.addUseCase(imageCapture)

                analysis?.let { a -> useCaseGroupBuilder.addUseCase(a) }

                val camera = cameraXInstance.get().bindToLifecycle(
                    context as LifecycleOwner,
                    currentSelector,
                    useCaseGroupBuilder.build()
                )

                camera to imageCapture
            }

            Log.d(TAG, "Camera lifecycle bound: ${camera.cameraInfo}")

            onBind.invoke(camera, executor, imageCapture)
        } catch (e: Exception) {
            Log.e(TAG, "bindPreview: failed to bind camera lifecycle", e)
            throw e
        }
    }

    @OptIn(ExperimentalGetImage::class)
    fun bindPreviewForVideo(
        previewView: PreviewView?,
        targetResolution: Size?,
        traitId: String? = null,
        analysis: ImageAnalysis? = null,
        showCropRegion: Boolean = false,
        onBind: (Camera, ExecutorService?, VideoCapture<Recorder>) -> Unit
    ) {

        unbind()

        val prevBuilder = Preview.Builder()

        if (targetResolution != null) {

            val resolutionStrategy = ResolutionStrategy(targetResolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER)

            val aspectRatioStrategy = AspectRatioStrategy(
                AspectRatio.RATIO_4_3,
                AspectRatioStrategy.FALLBACK_RULE_NONE
            )

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(resolutionStrategy)
                .setAspectRatioStrategy(aspectRatioStrategy)
                .build()

            prevBuilder.setResolutionSelector(resolutionSelector)
        }

        val p = prevBuilder.build()

        p.surfaceProvider = previewView?.surfaceProvider

        try {
            previewView?.let { pv ->
                cropDrawable?.let { pv.overlay.remove(it) }
                cropDrawable = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing previous crop drawable: ${e.message}")
        }

        val cropCoordinates = (context as? ThemedActivity)?.prefs?.getString(GeneralKeys.getCropCoordinatesKey(
            traitId?.toInt() ?: -1), "")

        if (showCropRegion && !cropCoordinates.isNullOrEmpty() && cropCoordinates != CropImageView.DEFAULT_CROP_COORDINATES && previewView != null) {
            val rect = CropImageView.parseRectCoordinates(cropCoordinates)
            if (rect != null) {
                val typedValue = TypedValue()
                context.theme?.resolveAttribute(
                    R.attr.fb_inverse_crop_region_color,
                    typedValue,
                    true
                )
                val color = typedValue.data

                cropDrawable = CropOverlayDrawable(previewView, rect, color)
                previewView.post {
                    try {
                        cropDrawable?.setBounds(0, 0, previewView.width, previewView.height)
                        cropDrawable?.let { previewView.overlay.add(it) }
                        previewView.invalidate()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error adding crop drawable: ${e.message}")
                    }
                }

                previewView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    cropDrawable?.setBounds(0, 0, v.width, v.height)
                    v.invalidate()
                }
            }
        }

        // create Recorder and VideoCapture use case
        val exec = executor ?: Executors.newSingleThreadExecutor()
        val recorder = Recorder.Builder().setExecutor(exec)
            .setAspectRatio(AspectRatio.RATIO_4_3)
            .build()
        val videoCapture = VideoCapture.withOutput(recorder)

        val useCaseGroupBuilder = UseCaseGroup.Builder()
        useCaseGroupBuilder.addUseCase(p)
        useCaseGroupBuilder.addUseCase(videoCapture)
        analysis?.let { a -> useCaseGroupBuilder.addUseCase(a) }

        val useCaseGroup = useCaseGroupBuilder.build()

        try {
            val camera = cameraXInstance.get().bindToLifecycle(
                context as LifecycleOwner,
                currentSelector,
                useCaseGroup
            )

            Log.d(TAG, "Camera lifecycle bound for video: ${camera.cameraInfo}")

            onBind.invoke(camera, executor, videoCapture)
        } catch (e: Exception) {
            Log.e(TAG, "bindPreviewForVideo: failed to bind camera lifecycle", e)
            throw e
        }
    }

    // Drawable that dims the area outside of a normalized crop rect (values 0..1)
    private class CropOverlayDrawable(
        private val previewView: PreviewView,
        private val normalizedRect: RectF,
        private val dimColor: Int
    ) : Drawable() {

        private val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        override fun draw(canvas: Canvas) {
            try {
                // full canvas dims
                val w = previewView.width.toFloat()
                val h = previewView.height.toFloat()

                if (w <= 0f || h <= 0f) return

                // compute crop rect in view coordinates
                val left = normalizedRect.left * w
                val top = normalizedRect.top * h
                val right = normalizedRect.right * w
                val bottom = normalizedRect.bottom * h

                // add a small padding to the crop rect visually
                val pad = 8f
                val topAdj = (top - pad).coerceAtLeast(0f)
                val bottomAdj = (bottom + pad).coerceAtMost(h)
                val leftAdj = (left - pad).coerceAtLeast(0f)
                val rightAdj = (right + pad).coerceAtMost(w)

                // draw four rectangles around the crop area (top, left, right, bottom)
                paint.color = dimColor
                // top
                canvas.drawRect(0f, 0f, w, topAdj, paint)
                // left
                canvas.drawRect(0f, topAdj, leftAdj, bottomAdj, paint)
                // right
                canvas.drawRect(rightAdj, topAdj, w, bottomAdj, paint)
                // bottom
                canvas.drawRect(0f, bottomAdj, w, h, paint)

            } catch (e: Exception) {
                // swallow layout/draw errors
                Log.w(TAG, "CropOverlayDrawable draw error: ${e.message}")
            }
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}