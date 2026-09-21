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
import android.os.Build
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
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

    // Use cases from the most recent bind, kept so target rotation can be updated on a
    // configuration change without rebinding (a rebind would drop an in-flight recording).
    private var boundImageCapture: ImageCapture? = null
    private var boundPreview: Preview? = null
    private var boundAnalysis: ImageAnalysis? = null
    private var boundVideoCapture: VideoCapture<Recorder>? = null

    fun unbind() {
        cameraXInstance.get().unbindAll()
        boundImageCapture = null
        boundPreview = null
        boundAnalysis = null
        boundVideoCapture = null
    }

    /**
     * The display rotation the camera use cases should target.
     *
     * Until Android 16 this was arrived at by accident: [ProcessCameraProvider.bindToLifecycle]
     * defaults each use case's target rotation to the display rotation at bind time, and every
     * camera surface lived inside a portrait locked activity, so it was always ROTATION_0. With
     * orientation restrictions ignored on large screens that is no longer guaranteed, and a
     * wrong target rotation changes both the EXIF orientation of saved images and which edge of
     * the frame the normalized crop rect lands on.
     */
    private fun currentRotation(): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read display rotation, defaulting to ROTATION_0", e)
        Surface.ROTATION_0
    }

    /**
     * Re-applies the current display rotation to the bound use cases. Safe to call at any time;
     * it does not rebind, so an in-flight video recording is unaffected. Call from
     * [android.app.Activity.onConfigurationChanged] on any activity hosting a camera surface.
     */
    fun updateTargetRotation() {
        val rotation = currentRotation()
        boundImageCapture?.targetRotation = rotation
        boundPreview?.targetRotation = rotation
        boundAnalysis?.targetRotation = rotation
        boundVideoCapture?.targetRotation = rotation
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

    fun bindFrontCapture(
        targetResolution: Size?,
        onBind: (Camera, ExecutorService?, ImageCapture) -> Unit,
    ) {

        unbind()

        try {

            val builder = ImageCapture.Builder()

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

                builder.setResolutionSelector(resolutionSelector)
            }

            builder.setTargetRotation(currentRotation())

            val imageCapture = builder.build()

            val camera = cameraXInstance.get().bindToLifecycle(
                context as LifecycleOwner,
                currentSelector,
                imageCapture
            )

            boundImageCapture = imageCapture

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
        onBind: (Camera, ExecutorService?, ImageCapture) -> Unit
    ) {

        unbind()

        val builder = ImageCapture.Builder()
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

            builder.setResolutionSelector(resolutionSelector)
            prevBuilder.setResolutionSelector(resolutionSelector)
        }

        val rotation = currentRotation()
        builder.setTargetRotation(rotation)
        prevBuilder.setTargetRotation(rotation)
        analysis?.targetRotation = rotation

        val imageCapture = builder.build()

        val p = prevBuilder.build()

        p.surfaceProvider = previewView?.surfaceProvider

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

        val useCaseGroupBuilder = UseCaseGroup.Builder()

        useCaseGroupBuilder.addUseCase(p)

        useCaseGroupBuilder.addUseCase(imageCapture)

        analysis?.let { a -> useCaseGroupBuilder.addUseCase(a) }

        val useCaseGroup = useCaseGroupBuilder.build()

        try {
            val camera = cameraXInstance.get().bindToLifecycle(
                context as LifecycleOwner,
                currentSelector,
                useCaseGroup
            )

            boundImageCapture = imageCapture
            boundPreview = p
            boundAnalysis = analysis

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

        val rotation = currentRotation()
        prevBuilder.setTargetRotation(rotation)
        analysis?.targetRotation = rotation

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
        // Builder rather than withOutput(): the latter offers no target rotation hook, and
        // without it a recording started in one orientation is saved with the wrong rotation
        val videoCapture = VideoCapture.Builder(recorder)
            .setTargetRotation(rotation)
            .build()

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

            boundPreview = p
            boundAnalysis = analysis
            boundVideoCapture = videoCapture

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