package com.fieldbook.tracker.activities

import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.fieldbook.tracker.R
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var cameraSelector: CameraSelector
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var previewView: PreviewView
    private lateinit var titleTextView: TextView
    private lateinit var shutterButton: ImageButton

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
        shutterButton = findViewById(R.id.act_camera_shutter_btn)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindLifecycle(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        setupCameraTitleView()
    }

    private fun setupCameraTitleView() {

        intent?.getStringExtra(EXTRA_TITLE)?.let { title ->

            titleTextView.text = title

        }
    }

    private fun bindLifecycle(cameraProvider: ProcessCameraProvider) {

        val preview = Preview.Builder()
            .setTargetResolution(Size(1080, 1920))
            .setTargetRotation(previewView.display?.rotation ?: 0)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val imageCapture = ImageCapture.Builder()
            .build()

        previewView.display?.rotation?.let { rot ->
            imageCapture.targetRotation = rot
        }

        val cameraExecutor = Executors.newSingleThreadExecutor()

        preview.setSurfaceProvider(previewView.surfaceProvider)

        val camera = cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            preview,
            imageCapture
        )

        Log.d(TAG, "Camera lifecycle bound: ${camera.cameraInfo}")

        shutterButton.setOnClickListener {

            val file = File(cacheDir, "temp.jpg")

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
    }
}