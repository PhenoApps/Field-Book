package com.fieldbook.tracker.activities

import android.app.AlertDialog
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.AbstractCameraTrait
import com.fieldbook.tracker.traits.PhotoTraitLayout
import com.fieldbook.tracker.views.CameraTraitSettingsView
import com.fieldbook.tracker.views.FullscreenCameraSettingsView
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executors

class CameraActivity : ThemedActivity() {

    private lateinit var cameraSelector: CameraSelector
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var previewView: PreviewView
    private lateinit var titleTextView: TextView
    private lateinit var shutterButton: ImageButton
    private lateinit var settingsBtn: ImageButton

    private var supportedResolutions: List<Size> = listOf()

    companion object {

        val TAG = CameraActivity::class.simpleName
        const val EXTRA_TITLE = "title"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_camera)

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        previewView = findViewById(R.id.act_camera_pv)
        titleTextView = findViewById(R.id.act_camera_title_tv)
        shutterButton = findViewById(R.id.camerax_capture_btn)
        settingsBtn = findViewById(R.id.camerax_settings_btn)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindLifecycle(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        setupCameraTitleView()
    }

    private fun onSettingsChanged() {

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        cameraProviderFuture.get().unbindAll()

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindLifecycle(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showSettings() {

        AlertDialog.Builder(this)
            .setTitle(R.string.trait_system_photo_settings_title)
            .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                onSettingsChanged()
                dialog.dismiss()
            }
            .setView(FullscreenCameraSettingsView(this, supportedResolutions))
            .show()
    }

    private fun setupCameraTitleView() {

        intent?.getStringExtra(EXTRA_TITLE)?.let { title ->

            titleTextView.text = title

        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindLifecycle(cameraProvider: ProcessCameraProvider) {

        val preview = Preview.Builder()
            .setTargetResolution(PhotoTraitLayout.DEFAULT_CAMERAX_PREVIEW_SIZE)
            .setTargetRotation(previewView.display?.rotation ?: 0)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val builder = ImageCapture.Builder()

        val supportedResolutionPreferredIndex = prefs.getInt(
            GeneralKeys.CAMERA_RESOLUTION,
            0
        )

        if (supportedResolutions.isNotEmpty() && supportedResolutionPreferredIndex < supportedResolutions.size) {

            val supportedResolution = supportedResolutions[supportedResolutionPreferredIndex]

            builder.setTargetResolution(Size(supportedResolution.height, supportedResolution.width))
                .build()

        }

        previewView.display?.rotation?.let { rot ->
            builder.setTargetRotation(rot)
        }

        val imageCapture = builder.build()

        val cameraExecutor = Executors.newSingleThreadExecutor()

        preview.setSurfaceProvider(previewView.surfaceProvider)

        try {

            val camera = cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )

            val info = Camera2CameraInfo.from(camera.cameraInfo)
            val configs =
                info.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            supportedResolutions = (configs?.getOutputSizes(ImageFormat.JPEG) ?: arrayOf()).toList()

            settingsBtn.isEnabled = true

            settingsBtn.setOnClickListener {
                showSettings()
            }

            Log.d(TAG, "Camera lifecycle bound: ${camera.cameraInfo}")

            shutterButton.setOnClickListener {

                val file = File(cacheDir, AbstractCameraTrait.TEMPORARY_IMAGE_NAME)

                val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(outputFileOptions, cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(error: ImageCaptureException) {}
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            setResult(RESULT_OK)
                            finish()
                        }
                    })
            }

        } catch (i: IllegalArgumentException) {

            finishActivity(RESULT_CANCELED)

        }
    }
}