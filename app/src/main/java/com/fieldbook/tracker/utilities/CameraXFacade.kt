package com.fieldbook.tracker.utilities

import android.content.Context
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.Handler
import android.util.Log
import android.util.Size
import android.util.TypedValue
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect.PREVIEW
import androidx.camera.core.CameraEffect.IMAGE_CAPTURE
import androidx.camera.core.CameraEffect.VIDEO_CAPTURE
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
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
class CameraXFacade @Inject constructor(@ActivityContext private val context: Context) {

    companion object {
        val TAG = CameraXFacade::class.java.simpleName
    }

    val cameraXInstance by lazy {
        ProcessCameraProvider.getInstance(context)
    }

    val frontSelector by lazy {

        CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

    }

    val executor by lazy {
        Executors.newSingleThreadExecutor()
    }

    fun await(context: Context, onBindReady: () -> Unit) {
        cameraXInstance.get().unbind()
        cameraXInstance.addListener({
            onBindReady()
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        cameraXInstance.get().unbindAll()
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
        onBind: (Camera, ExecutorService, ImageCapture) -> Unit,
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

            val imageCapture = builder.build()

            val camera = cameraXInstance.get().bindToLifecycle(
                context as LifecycleOwner,
                frontSelector,
                imageCapture
            )

            Log.d(TAG, "Camera lifecycle bound: ${camera.cameraInfo}")

            onBind.invoke(camera, executor, imageCapture)

        } catch (_: IllegalArgumentException) {

        }
    }

    private var cropEffect: OverlayEffect? = null

    fun bindPreview(
        previewView: PreviewView?,
        targetResolution: Size?,
        traitId: String? = null,
        handler: Handler,
        onBind: (Camera, ExecutorService, ImageCapture) -> Unit
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

        val imageCapture = builder.build()

        val p = prevBuilder.build()

        p.surfaceProvider = previewView?.surfaceProvider

        //close previous crop effect or else EGL leak
        cropEffect?.close()

        //create an overlay effect to draw a rectangle on the preview
        cropEffect = OverlayEffect(VIDEO_CAPTURE or PREVIEW or IMAGE_CAPTURE, 0, handler) { e ->
            Log.e(TAG, "OverlayEffect error: $e")
        }

        //parse crop from prefs
        val cropCoordinates = (context as? ThemedActivity)?.prefs?.getString(GeneralKeys.getCropCoordinatesKey(
            traitId?.toInt() ?: -1), "")

        //overlay effect gives a frame that has an 'overlayCanvas'
        cropEffect?.setOnDrawListener { frame ->
            try {
                //check that coordinates are correct, parse and draw the rect
                if (!cropCoordinates.isNullOrEmpty() && cropCoordinates != CropImageView.DEFAULT_CROP_COORDINATES) {
                    //convert the camera sensor coordinates to local preview view coordinates
                    val sensorToUi = previewView!!.sensorToViewTransform
                    if (sensorToUi != null) {
                        val sensorToEffect = frame.sensorToBufferTransform
                        val uiToSensor = Matrix()
                        sensorToUi.invert(uiToSensor)
                        uiToSensor.postConcat(sensorToEffect)

                        //get canvas and clear color, apply affine transformation
                        val canvas = frame.overlayCanvas
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        canvas.setMatrix(uiToSensor)

                        val rect = CropImageView.parseRectCoordinates(cropCoordinates)
                        //check if rect is null or matches the default
                        if (rect != null) {
                            //draw a rectangle on the left of the canvas
                            //converts normalized coordinates to previewView-relative
                            val left = rect.left * previewView.width
                            val top = (rect.top * previewView.height) - 8f
                            val right = rect.right * previewView.width
                            val bottom = (rect.bottom * previewView.height) + 8f

                            //newer APIs allow clipping a rect in two commands, earlier is a little more complex
                            val clipRect = RectF(left, top, right, bottom)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                canvas.clipOutRect(clipRect)
                            } else {
                                canvas.clipRect(clipRect, Region.Op.DIFFERENCE)
                            }

                            val typedValue = TypedValue()
                            context.theme?.resolveAttribute(
                                R.attr.fb_inverse_crop_region_color,
                                typedValue,
                                true
                            )

                            canvas.drawARGB(
                                Color.alpha(typedValue.data),
                                Color.red(typedValue.data),
                                Color.green(typedValue.data),
                                Color.blue(typedValue.data)
                            )

                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                                canvas.clipRect(
                                    Rect(0, 0, previewView.width, previewView.height),
                                    Region.Op.REPLACE
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                //skip invalid region exceptions
                if ("Region.Op" !in e.message.orEmpty())
                    e.printStackTrace()
            }

            true
        }

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(p)
            .addUseCase(imageCapture)
            .addEffect(cropEffect!!)
            .build()

        val camera = cameraXInstance.get().bindToLifecycle(
            context as LifecycleOwner,
            frontSelector,
            useCaseGroup
        )

        Log.d(TAG, "Camera lifecycle bound: ${camera.cameraInfo}")

        onBind.invoke(camera, executor, imageCapture)
    }
}