package com.fieldbook.tracker.activities

import android.app.AlertDialog
import android.content.res.Configuration
import android.os.Bundle
import android.util.Size
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.AbstractCameraTrait
import com.fieldbook.tracker.utilities.CameraXFacade
import com.fieldbook.tracker.views.FullscreenCameraSettingsView
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.concurrent.ExecutorService
import javax.inject.Inject

@AndroidEntryPoint
class CameraActivity : ThemedActivity() {

    @Inject
    lateinit var cameraXFacade: CameraXFacade

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

        previewView = findViewById(R.id.act_camera_pv)

        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        titleTextView = findViewById(R.id.act_camera_title_tv)
        shutterButton = findViewById(R.id.camerax_capture_btn)
        settingsBtn = findViewById(R.id.camerax_settings_btn)

        cameraXFacade.await(this) {
            bindCameraForInformation()
        }

        setupCameraTitleView()
    }

    private fun onSettingsChanged() {

        cameraXFacade.unbind()

        cameraXFacade.await(this) {
            bindLifecycle()
        }
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
    private fun bindCameraForInformation() {

        cameraXFacade.bindIdentity { _, sizes ->

            supportedResolutions = sizes

            settingsBtn.isEnabled = true

        }

        //trigger ui update based on preferences
        onSettingsChanged()

        bindLifecycle()
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

    private fun bindLifecycle() {

        try {

            val resolution = getSupportedResolutionByPreferences()

            cameraXFacade.bindPreview(
                previewView, resolution
            ) { camera, executor, capture ->

                setupCaptureUi(camera, executor, capture)
            }

        } catch (_: IllegalArgumentException) {

        }
    }

    private fun setupCaptureUi(camera: Camera, executor: ExecutorService, capture: ImageCapture) {

        try {

            settingsBtn.isEnabled = true

            settingsBtn.setOnClickListener {
                showSettings()
            }

            shutterButton.setOnClickListener {

                val file = File(cacheDir, AbstractCameraTrait.TEMPORARY_IMAGE_NAME)

                val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(outputFileOptions, executor,
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