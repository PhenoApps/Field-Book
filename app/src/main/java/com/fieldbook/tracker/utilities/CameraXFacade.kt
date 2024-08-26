package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
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
        cameraXInstance.addListener({
            onBindReady()
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        cameraXInstance.get().unbindAll()
    }

    fun bindIdentity(
        onBind: (Camera, List<Size>) -> Unit
    ) {

        unbind()

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

    fun bindPreview(
        previewView: PreviewView?,
        targetResolution: Size?,
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

        p.setSurfaceProvider(previewView?.surfaceProvider)

        val camera = cameraXInstance.get().bindToLifecycle(
            context as LifecycleOwner,
            frontSelector,
            p,
            imageCapture
        )

        Log.d(TAG, "Camera lifecycle bound: ${camera.cameraInfo}")

        onBind.invoke(camera, executor, imageCapture)
    }
}