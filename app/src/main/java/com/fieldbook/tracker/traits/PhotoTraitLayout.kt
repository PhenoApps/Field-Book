package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.View
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CameraActivity
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.provider.GenericFileProvider
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.views.CameraTraitSettingsView
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executors

class PhotoTraitLayout : CameraTrait {

    companion object {
        const val TAG = "PhotoTrait"
        const val type = "photo"
        const val PICTURE_REQUEST_CODE = 252
        val DEFAULT_CAMERAX_PREVIEW_SIZE = Size(640, 480)
    }

    enum class Mode {
        PREVIEW,
        NO_PREVIEW
    }

    private lateinit var cameraSelector: CameraSelector
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    private var supportedResolutions: List<Size> = listOf()

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun type() = type

    private fun displayPreviewMode(mode: Mode) {

        cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        cameraProviderFuture.get().unbindAll()

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            if (mode == Mode.NO_PREVIEW) {
                bindNoPreviewLifecycle(cameraProvider)
            } else bindPreviewLifecycle(cameraProvider)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupCaptureButton(takePictureCallback: () -> Unit) {

        captureBtn?.setOnClickListener {

            if (!isLocked) {

                if (checkPictureLimit()) {

                    takePictureCallback()

                } else {

                    Utils.makeToast(
                        context,
                        context.getString(R.string.traits_create_photo_maximum)
                    )
                }
            }
        }
    }

    private fun setup() {

        captureBtn?.visibility = View.VISIBLE
        connectBtn?.visibility = View.GONE
        settingsBtn?.visibility = View.VISIBLE

        setupCaptureButton {

            takePicture()

        }

        settingsBtn?.isEnabled = false

        cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindCameraForInformation(cameraProvider)
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraForInformation(cameraProvider: ProcessCameraProvider) {

        cameraProvider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val camera = cameraProvider.bindToLifecycle(
            context as LifecycleOwner,
            cameraSelector,
        )

        val info = Camera2CameraInfo.from(camera.cameraInfo)
        val configs =
            info.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        supportedResolutions = (configs?.getOutputSizes(ImageFormat.JPEG) ?: arrayOf()).toList()

        settingsBtn?.isEnabled = true

        cameraProvider.unbindAll()

        //trigger ui update based on preferences
        onSettingsChanged()
    }

    private fun bindNoPreviewLifecycle(cameraProvider: ProcessCameraProvider) {

        previewView?.visibility = View.GONE

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val imageCapture = ImageCapture.Builder()
            .build()

        val cameraExecutor = Executors.newSingleThreadExecutor()

        try {

            val camera = cameraProvider.bindToLifecycle(
                context as LifecycleOwner,
                cameraSelector,
                imageCapture
            )

            Log.d(CameraActivity.TAG, "Camera lifecycle bound: ${camera.cameraInfo}")

            setupCaptureButton {

                val file = File(context.cacheDir, TEMPORARY_IMAGE_NAME)

                val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(outputFileOptions, cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(error: ImageCaptureException) {}
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            makeImage(currentTrait)
                        }
                    })
            }

        } catch (_: IllegalArgumentException) {

        }
    }

    private fun bindPreviewLifecycle(cameraProvider: ProcessCameraProvider) {

        previewView?.visibility = View.VISIBLE

        val preview = Preview.Builder()
            .setTargetResolution(DEFAULT_CAMERAX_PREVIEW_SIZE)
            .setTargetRotation(previewView?.display?.rotation ?: 0)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val supportedResolutionPreferredIndex = prefs.getInt(
            GeneralKeys.CAMERA_RESOLUTION,
            0
        )

        val builder = ImageCapture.Builder()

        if (supportedResolutions.isNotEmpty() && supportedResolutionPreferredIndex < supportedResolutions.size) {

            val supportedResolution = supportedResolutions[supportedResolutionPreferredIndex]

            builder.setTargetResolution(Size(supportedResolution.height, supportedResolution.width))
                .build()

        }

        previewView?.display?.rotation?.let { rot ->
            builder.setTargetRotation(rot)
        }

        val imageCapture = builder.build()

        val cameraExecutor = Executors.newSingleThreadExecutor()

        preview.setSurfaceProvider(previewView?.surfaceProvider)

        try {

            val camera = cameraProvider.bindToLifecycle(
                context as LifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )

            Log.d(CameraActivity.TAG, "Camera lifecycle bound: ${camera.cameraInfo}")

            setupCaptureButton {

                val file = File(context.cacheDir, TEMPORARY_IMAGE_NAME)

                val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(outputFileOptions, cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(error: ImageCaptureException) {}
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            makeImage(currentTrait)
                        }
                    })
            }

        } catch (_: IllegalArgumentException) {

        }
    }

    override fun loadLayout() {
        super.loadLayout()
        setup()
    }

    override fun showSettings() {

        AlertDialog.Builder(context)
            .setTitle(R.string.trait_system_photo_settings_title)
            .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                onSettingsChanged()
                dialog.dismiss()
            }
            .setView(CameraTraitSettingsView(context, supportedResolutions))
            .show()
    }

    override fun onSettingsChanged() {

        val systemEnabled = preferences.getInt(GeneralKeys.CAMERA_SYSTEM, R.id.view_trait_photo_settings_camera_system_rb)

        if (systemEnabled == R.id.view_trait_photo_settings_camera_system_rb) {

            setupSystemCameraMode()

        } else {

            setupCameraXMode()

        }
    }

    private fun setupSystemCameraMode() {

        previewView?.visibility = View.GONE

        cameraProviderFuture.get().unbindAll()

        setupCaptureButton {

            takePicture()
        }
    }

    private fun setupCameraXMode() {

        displayPreviewMode(
            if (prefs.getBoolean(
                    GeneralKeys.CAMERA_SYSTEM_PREVIEW,
                    false
                )
            ) Mode.PREVIEW else Mode.NO_PREVIEW
        )
    }

    fun makeImage(currentTrait: TraitObject) {

        val file = File(context.cacheDir, TEMPORARY_IMAGE_NAME)

        file.inputStream().use { stream ->

            val data = stream.readBytes()

            saveJpegToStorage(
                currentTrait.format,
                data,
                currentRange,
                org.phenoapps.androidlibrary.Utils.getDateTime(),
                SaveState.SINGLE_SHOT
            )
        }
    }


    /**
     * When button is pressed, create a cached image and switch to the camera intent.
     * CollectActivity will receive REQUEST_IMAGE_CAPTURE and call this layout's makeImage() method.
     */
    private fun takePicture() {

        val file = File(context.cacheDir, TEMPORARY_IMAGE_NAME)

        file.createNewFile()

        val uri =
            GenericFileProvider.getUriForFile(context, "com.fieldbook.tracker.fileprovider", file)

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val cameraxIntent = Intent(activity, CameraActivity::class.java)

        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(context.packageManager) != null) {
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            (context as Activity).startActivityForResult(
                takePictureIntent,
                PICTURE_REQUEST_CODE
            )
        } else {
            (context as CollectActivity).startActivityForResult(
                cameraxIntent,
                PICTURE_REQUEST_CODE
            )
        }
    }

    override fun refreshLock() {
        super.refreshLock()
        (context as CollectActivity).traitLockData()
        try {
            loadLayout()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
}