package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.View
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CameraActivity
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.ImageAdapter
import com.fieldbook.tracker.database.internalTimeFormatter
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.provider.GenericFileProvider
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.views.CameraTraitSettingsView
import org.threeten.bp.OffsetDateTime
import java.io.File
import java.util.concurrent.ExecutorService

class PhotoTraitLayout : CameraTrait {

    companion object {
        const val TAG = "PhotoTrait"
        const val type = "photo"
        const val PICTURE_REQUEST_CODE = 252
    }

    enum class Mode {
        PREVIEW,
        NO_PREVIEW
    }

    private var supportedResolutions: List<Size> = listOf()
    private var previewViewHolder: ImageAdapter.PreviewViewHolder? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun type() = type

    private fun displayPreviewMode(mode: Mode) {

        controller.getCameraXFacade().await(context) {
            if (mode == Mode.NO_PREVIEW) {
                bindNoPreviewLifecycle()
            } else bindPreviewLifecycle()
        }
    }

    private fun setupCaptureButton(takePictureCallback: () -> Unit) {

        previewViewHolder?.shutterButton?.setOnClickListener {

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

        previewViewHolder = getPreviewViewHolder()

        //we are limited to having everything extend linear layout, there are some cases
        //where the background thread has not loaded all the views and may be null,
        //so reload until we have the preview model
        if (previewViewHolder == null) {

            Handler(Looper.getMainLooper()).postDelayed({
                setup()
            }, 500)

            return

        } else {

            scrollToLast()
        }

        Log.d(TAG, "setup: $previewViewHolder")

        previewViewHolder?.shutterButton?.visibility = View.VISIBLE
        previewViewHolder?.settingsButton?.visibility = View.VISIBLE
        previewViewHolder?.settingsButton?.isEnabled = true
        previewViewHolder?.settingsButton?.setOnClickListener {
            showSettings()
        }

        connectBtn?.visibility = View.GONE

        setupCaptureButton {

            takePicture()

        }

        bindCameraForInformation()

    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraForInformation() {

        controller.getCameraXFacade().bindIdentity { _, sizes ->

            supportedResolutions = sizes

            previewViewHolder?.settingsButton?.isEnabled = true

        }

        //trigger ui update based on preferences
        onSettingsChanged()
    }

    private fun bindNoPreviewLifecycle() {

        previewViewHolder?.previewView?.visibility = View.GONE
        previewViewHolder?.embiggenButton?.visibility = View.GONE

        try {

            val resolution = getSupportedResolutionByPreferences()

            controller.getCameraXFacade().bindFrontCapture(resolution) { camera, executor, capture ->

                setupCaptureUi(camera, executor, capture)
            }

        } catch (_: IllegalArgumentException) {

        }
    }

    private fun setupCaptureUi(camera: Camera, executorService: ExecutorService, capture: ImageCapture) {

        setupCaptureButton {

            val file = File(context.cacheDir, TEMPORARY_IMAGE_NAME)

            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()
            capture.takePicture(outputFileOptions, executorService,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(error: ImageCaptureException) {}
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        makeImage(currentTrait)
                    }
                })
        }
    }

    /**
     * @param orientation this is orientation from Surface class, not to be confused with Exif Orientation
     */
    private fun bindPreviewLifecycle() {

        previewViewHolder?.previewView?.visibility = View.VISIBLE

        previewViewHolder?.previewView?.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        previewViewHolder?.embiggenButton?.visibility = View.VISIBLE

        previewViewHolder?.embiggenButton?.setOnClickListener {

            launchCameraX()

        }

        try {

            val resolution = getSupportedResolutionByPreferences()

            controller.getCameraXFacade().bindPreview(
                previewViewHolder?.previewView,
                resolution
            ) { camera, executor, capture ->

                setupCaptureUi(camera, executor, capture)
            }

        } catch (_: IllegalArgumentException) {

        }
    }

    private fun getSupportedResolutionByPreferences(): Size? {

        val supportedResolutionPreferredIndex = prefs.getInt(
            GeneralKeys.CAMERA_RESOLUTION,
            0
        )

        var resolution: Size? = null

        if (supportedResolutions.isNotEmpty() && supportedResolutionPreferredIndex < supportedResolutions.size) {

            resolution = supportedResolutions[supportedResolutionPreferredIndex]

        }

        return resolution
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

        previewViewHolder?.previewView?.visibility = View.GONE

        previewViewHolder?.embiggenButton?.visibility = View.GONE

        controller.getCameraXFacade().unbind()

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
                OffsetDateTime.now().format(internalTimeFormatter),
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

        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(context.packageManager) != null) {
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            (context as Activity).startActivityForResult(
                takePictureIntent,
                PICTURE_REQUEST_CODE
            )
        } else {
            launchCameraX()
        }
    }

    private fun launchCameraX() {
        val intent = Intent(context, CameraActivity::class.java)
        activity?.startActivityForResult(intent, PICTURE_REQUEST_CODE)
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