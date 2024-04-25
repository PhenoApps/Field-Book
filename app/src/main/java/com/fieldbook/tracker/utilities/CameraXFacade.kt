package com.fieldbook.tracker.utilities

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
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
        val DEFAULT_CAMERAX_PREVIEW_SIZE = Size(640, 480)
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

    val preview by lazy {
        Preview.Builder()
            .setTargetResolution(DEFAULT_CAMERAX_PREVIEW_SIZE)
            .build()
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

        onBind(camera, (configs?.getOutputSizes(ImageFormat.JPEG) ?: arrayOf()).toList())
    }

    fun bindFrontCapture(
        targetResolution: Size?,
        onBind: (Camera, ExecutorService, ImageCapture) -> Unit,
    ) {

        unbind()

        try {

            val builder = ImageCapture.Builder()

            if (targetResolution != null) {

                builder.setTargetResolution(targetResolution)
                    .build()

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

        if (targetResolution != null) {

            builder.setTargetResolution(targetResolution)
                .build()

        }

        val imageCapture = builder.build()

        preview.setSurfaceProvider(previewView?.surfaceProvider)

        val camera = cameraXInstance.get().bindToLifecycle(
            context as LifecycleOwner,
            frontSelector,
            preview,
            imageCapture
        )

        Log.d(TAG, "Camera lifecycle bound: ${camera.cameraInfo}")

        onBind.invoke(camera, executor, imageCapture)
    }
}