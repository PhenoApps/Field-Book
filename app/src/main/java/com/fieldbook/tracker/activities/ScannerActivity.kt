package com.fieldbook.tracker.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.databinding.ActivityScannerBinding
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.InsetHandler
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.AndroidEntryPoint
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class ScannerActivity : ThemedActivity() {

    @Inject
    lateinit var database: DataHelper

    private lateinit var binding: ActivityScannerBinding
    private lateinit var cameraSelector: CameraSelector
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var processCameraProvider: ProcessCameraProvider
    private lateinit var cameraPreview: Preview
    private lateinit var imageCapture: ImageCapture
    private lateinit var imageAnalysis: ImageAnalysis

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                processCameraProvider = cameraProviderFuture.get()
                bindCameraLifecycle()
                bindInputAnalyser()
            }, ContextCompat.getMainExecutor(this)
        )

        setupShutterButton()

        setupScannerInsets()

        onBackPressedDispatcher.addCallback(this, standardBackCallback())
    }

    private fun setupShutterButton() {

        binding.actScannerCaptureBtn.setOnClickListener {

            if (::imageCapture.isInitialized) {

                takePhoto()
            }
        }
    }

    private fun takePhoto() {


        Toast.makeText(this, "Saving pictures is not fully implemented yet.", Toast.LENGTH_SHORT)
            .show()


        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture

        // Create time stamped name and MediaStore entry.
        val timestamp = SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", Locale.US)
            .format(Calendar.getInstance().time)

        val traitDbId = intent.getStringExtra(INPUT_EXTRA_TRAIT_DB_ID)
        val plotId = intent.getStringExtra(INPUT_EXTRA_PLOT_ID)
        val rep = intent.getStringExtra(INPUT_EXTRA_REP)

        if (traitDbId != null) {

            val trait = database.allTraitObjects.first { it.id == traitDbId.toString() }
            val traitName = FileUtil.sanitizeFileName(trait.name)

            val name = plotId + "_" + traitName + "_" + rep + "_" + timestamp + ".jpg"

            // get trait media folder and create a new file
            DocumentTreeUtil.getFieldMediaDirectory(this@ScannerActivity, traitName)
                ?.let { documentFile ->

                    documentFile.createFile("image/jpeg", name)?.let { file ->

                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(this),
                            object : ImageCapture.OnImageCapturedCallback() {

                                override fun onCaptureSuccess(image: ImageProxy) {
                                    super.onCaptureSuccess(image)

                                    contentResolver.openOutputStream(file.uri).use { outputStream ->

                                        if (outputStream != null) {
                                            image.toBitmap()
                                                .compress(
                                                    Bitmap.CompressFormat.JPEG,
                                                    90,
                                                    outputStream
                                                )
                                        }
                                    }

                                    val msg = "Photo capture succeeded: ${file.uri}"
                                    Log.d(TAG, msg)

                                    val intent = Intent().also {
                                        it.putExtra(EXTRA_PHOTO_URI, file.uri.toString())
                                    }

                                    setResult(RESULT_OK, intent)
                                    finish()
                                }
                            })
                    }
                }
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap {

        val buffer: ByteBuffer = this.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())

        buffer.get(bytes)

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun bindCameraLifecycle() {
        val display = binding.previewView.display
        cameraPreview = if (display != null) {
            Preview.Builder()
                .setTargetRotation(display.rotation)
                .build()
        } else {
            Preview.Builder()
                .build()
        }
        imageCapture = ImageCapture.Builder().build()
        cameraPreview.surfaceProvider = binding.previewView.surfaceProvider

        try {

            processCameraProvider.unbindAll()
            processCameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview, imageCapture)

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    private fun bindInputAnalyser() {
        val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
        )

        val display = binding.previewView.display
        imageAnalysis = if (display != null) {
            ImageAnalysis.Builder()
                .setTargetRotation(display.rotation)
                .build()
        } else {
            ImageAnalysis.Builder()
                .build()
        }

        val cameraExecutor = Executors.newSingleThreadExecutor()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(barcodeScanner, imageProxy)

        }

        processCameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)

    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(
        barcodeScanner: BarcodeScanner,
        imageProxy: ImageProxy
    ) {
        val inputImage =
            InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)

        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val intent = Intent().also {
                        it.putExtra("barcode", barcodes[0].displayValue)
                    }

                    //set the result with the intent data to be processed in onActivityResult
                    setResult(RESULT_OK, intent)
                    finish()
                }
            }.addOnFailureListener {
                it.printStackTrace()
            }.addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun setupScannerInsets() {
        InsetHandler.setupCameraInsets(binding.root, binding.previewView, binding.actScannerCaptureBtn)
    }

    companion object {

        const val TAG = "ScannerActivity"
        const val INPUT_EXTRA_TRAIT_DB_ID = "trait_db_id"
        const val INPUT_EXTRA_PLOT_ID = "plot_id"
        const val INPUT_EXTRA_REP = "rep"
        const val EXTRA_BARCODE = "barcode"
        const val EXTRA_PHOTO_URI = "photo_uri"

        fun startScanner(
            activity: Activity,
            traitDbId: String?,
            plotId: String?,
            rep: String?,
            requestCode: Int
        ) {
            Intent(activity, ScannerActivity::class.java).also {
                it.putExtra(INPUT_EXTRA_TRAIT_DB_ID, traitDbId)
                it.putExtra(INPUT_EXTRA_PLOT_ID, plotId)
                it.putExtra(INPUT_EXTRA_REP, rep)
                (activity as ComponentActivity).startActivityForResult(it, requestCode)
            }
        }

        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA

        private fun Context.isPermissionGranted(permission: String): Boolean {
            return ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }

        private fun Context.cameraPermissionRequest() {
            AlertDialog.Builder(this, R.style.AppAlertDialog)
                .setTitle("Camera Permission Required")
                .setMessage("Camera permission is necessary for scanning barcodes")
                .setPositiveButton("Allow") { _, _ ->
                    openPermissionSettings()
                }.setNegativeButton("Cancel") { _, _ ->
                }
                .show()
        }

        private fun Context.openPermissionSettings() {
            Intent(ACTION_APPLICATION_DETAILS_SETTINGS).also {
                val uri: Uri = Uri.fromParts("package", packageName, null)
                it.data = uri
                startActivity(it)
            }
        }

        fun Activity.requestCameraAndStartScanner(
            requestCode: Int,
            traitDbId: String? = null,
            plotId: String? = null,
            rep: String? = null
        ) {
            if (isPermissionGranted(CAMERA_PERMISSION)) {
                startScanner(this, traitDbId, plotId, rep, requestCode)
            } else {
                cameraPermissionRequest()
            }
        }
    }
}