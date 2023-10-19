package com.fieldbook.tracker.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.databinding.ActivityScannerBinding
import com.google.common.util.concurrent.ListenableFuture

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private lateinit var cameraSelector: CameraSelector
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var processCameraProvider: ProcessCameraProvider
    private lateinit var cameraPreview: Preview

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        val previewView = findViewById<View>(R.id.preview_view);
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
                {
                    processCameraProvider = cameraProviderFuture.get()
                    bindCameraPreview()
                }, ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindCameraPreview(){
//        val previewView = findViewById<View>(R.id.preview_view);
        cameraPreview = Preview.Builder()
                .setTargetRotation(binding.previewView.display.rotation)
                .build()
        cameraPreview.setSurfaceProvider(binding.previewView.surfaceProvider)
        processCameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview)
    }

    companion object {
        fun startScanner(context: Context, onScan: ()->Unit){
            Intent(context, ScannerActivity::class.java).also{
                context.startActivity(it)
            }
        }
    }
}