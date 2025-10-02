package com.fieldbook.tracker.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.View
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
import com.fieldbook.tracker.utilities.InsetHandler
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

    private var supportedResolutions: List<Size> = listOf()

    private var traitId: String? = null

    companion object {

        val TAG = CameraActivity::class.simpleName
        const val EXTRA_TITLE = "title"
        const val EXTRA_TRAIT_ID = "trait_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //get trait id from extras
        traitId = intent.getStringExtra(EXTRA_TRAIT_ID)

        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.act_camera_pv)

        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        titleTextView = findViewById(R.id.act_camera_title_tv)
        shutterButton = findViewById(R.id.camerax_capture_btn)

        cameraXFacade.await(this) {
            bindCameraForInformation()
        }

        setupCameraTitleView()

        setupCameraInsets()

        onBackPressedDispatcher.addCallback(this, standardBackCallback())
    }

    private fun onSettingsChanged() {

        cameraXFacade.unbind()

        cameraXFacade.await(this) {
            bindLifecycle()
        }
    }

    private fun setupCameraTitleView() {

        intent?.getStringExtra(EXTRA_TITLE)?.let { title ->

            titleTextView.text = title

        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraForInformation() {

        cameraXFacade.bindIdentity({ _, sizes ->

            supportedResolutions = sizes

        }) {
            finishActivity(RESULT_CANCELED)
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
                previewView, resolution,
                traitId,
                Handler(Looper.getMainLooper())
            ) { camera, executor, capture ->

                setupCaptureUi(camera, executor, capture)
            }

        } catch (_: IllegalArgumentException) {

        }
    }

    private fun setupCaptureUi(camera: Camera, executor: ExecutorService, capture: ImageCapture) {

        try {

            shutterButton.setOnClickListener {

                val file = File(cacheDir, AbstractCameraTrait.TEMPORARY_IMAGE_NAME)

                val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(outputFileOptions, executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(error: ImageCaptureException) {}
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            runOnUiThread {
                                setResult(RESULT_OK)
                                finish()
                            }
                        }
                    })
            }

        } catch (i: IllegalArgumentException) {

            finishActivity(RESULT_CANCELED)

        }
    }

    private fun setupCameraInsets() {
        val rootView = findViewById<View>(android.R.id.content)

        InsetHandler.setupCameraInsets(rootView, titleTextView, shutterButton)
    }
}