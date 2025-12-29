package com.fieldbook.tracker.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.PointF
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.AbstractCameraTrait
import com.fieldbook.tracker.ui.MediaViewerActivity
import com.fieldbook.tracker.utilities.CameraXFacade
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.InsetHandler
import com.fieldbook.tracker.utilities.Utils
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class CameraActivity : ThemedActivity() {

    @Inject
    lateinit var cameraXFacade: CameraXFacade

    @Inject
    lateinit var database: DataHelper

    private lateinit var previewView: PreviewView
    private lateinit var barcodeOverlay: com.fieldbook.tracker.ui.BarcodeOverlayView
    private lateinit var titleTextView: TextView
    private lateinit var captureButton: FloatingActionButton
    private lateinit var viewMediaButton: FloatingActionButton
    private lateinit var switchCameraButton: FloatingActionButton

    private var supportedResolutions: List<Size> = listOf()

    private var traitId: String? = null
    private var studyId: String? = null
    private var obsUnit: String? = null
    private var obsId: String? = null
    private var traitName: String? = null
    private var studyName: String? = null
    private var currentMode = MODE_PHOTO

    private var imageCapture: ImageCapture? = null

    // video components
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: androidx.camera.video.Recording? = null
    private var videoRecording = false

    private val barcodeScanner: BarcodeScanner by lazy { BarcodeScanning.getClient() }

    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private val overlayBarcodes = mutableListOf<String>()

    companion object {

        val TAG = CameraActivity::class.simpleName
        const val EXTRA_TITLE = "title"
        const val EXTRA_TRAIT_ID = "trait_id"
        const val EXTRA_TRAIT_NAME = "trait_name"
        const val EXTRA_STUDY_ID = "study_id"
        const val EXTRA_OBS_UNIT = "obs_unit"
        const val EXTRA_OBS_ID = "obs_id"
        const val EXTRA_MODE = "mode"
        const val MODE_PHOTO = "photo"
        const val MODE_CROP = "crop"
        const val MODE_VIDEO = "video"
        const val MODE_BARCODE = "barcode"
        const val MODE_AUDIO = "audio"
        const val EXTRA_BARCODE = "barcode_value"
        const val REQUEST_RECORD_AUDIO_PERMISSION = 301
        const val MAX_BARCODE_DETECTIONS = 10
    }

    private var boundCamera: Camera? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var audioRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //get trait id from extras
        traitId = intent.getIntExtra(EXTRA_TRAIT_ID, -1).toString()
        traitName = intent.getStringExtra(EXTRA_TRAIT_NAME)
        studyId = intent.getStringExtra(EXTRA_STUDY_ID)
        obsUnit = intent.getStringExtra(EXTRA_OBS_UNIT)
        obsId = intent.getStringExtra(EXTRA_OBS_ID)

        currentMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PHOTO

        studyName = studyId?.let { studyId -> database.getStudyById(studyId)?.study_name }

        //finish early if not enough information was given to the intent
        if (currentMode != MODE_BARCODE && (studyName == null || obsUnit == null || traitId == "-1")) finish()

        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.act_camera_pv)
        barcodeOverlay = findViewById(R.id.barcode_overlay)

        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE

        titleTextView = findViewById(R.id.act_camera_title_tv)
        captureButton = findViewById(R.id.camerax_capture_btn)
        viewMediaButton = findViewById(R.id.camerax_view_btn)
        switchCameraButton = findViewById(R.id.camerax_switch_btn)

        // media toggle
        val toggleGroup = findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.media_mode_toggle)
        val togglePhoto = findViewById<com.google.android.material.button.MaterialButton>(R.id.toggle_photo)
        val toggleVideo = findViewById<com.google.android.material.button.MaterialButton>(R.id.toggle_video)
        val toggleAudio = findViewById<com.google.android.material.button.MaterialButton>(R.id.toggle_audio)

        // Read trait multimedia flags and toggle visibility of the three choices.
        try {

            if (currentMode in setOf(MODE_PHOTO, MODE_VIDEO, MODE_AUDIO)) {

                val trait = traitId?.takeIf { it != "-1" }?.let { id ->
                    database.getTraitById(id)
                }

                // Set visibility based on trait flags. Default to visible if trait unavailable.
                togglePhoto.visibility = if (trait?.multiMediaPhoto == true) View.VISIBLE else View.GONE
                toggleVideo.visibility = if (trait?.multiMediaVideo == true) View.VISIBLE else View.GONE
                toggleAudio.visibility = if (trait?.multiMediaAudio == true) View.VISIBLE else View.GONE

                // Count visible options and hide the toggle group if there's 0 or 1 option
                val visibleCount = listOf(togglePhoto, toggleVideo, toggleAudio).count { it.isVisible }
                if (visibleCount <= 1) {
                    // hide the group when there's only one (or none) to simplify the UI
                    toggleGroup.visibility = View.GONE
                    // set currentMode based on the one visible option if present
                    when {
                        togglePhoto.isVisible -> currentMode = MODE_PHOTO
                        toggleVideo.isVisible -> currentMode = MODE_VIDEO
                        toggleAudio.isVisible -> currentMode = MODE_AUDIO
                        // else keep currentMode as provided by intent
                    }
                } else {
                    // multiple choices: show toggle group and pick sensible default photo > video > audio
                    toggleGroup.visibility = View.VISIBLE
                    when {
                        togglePhoto.isVisible -> {
                            togglePhoto.isChecked = true
                            currentMode = MODE_PHOTO
                        }
                        toggleVideo.isVisible -> {
                            toggleVideo.isChecked = true
                            currentMode = MODE_VIDEO
                        }
                        toggleAudio.isVisible -> {
                            toggleAudio.isChecked = true
                            currentMode = MODE_AUDIO
                        }
                    }
                }

            } else {

                toggleGroup.visibility = View.GONE
                viewMediaButton.visibility = View.GONE

                if (currentMode == MODE_BARCODE) {
                    captureButton.visibility = View.GONE
                    switchCameraButton.visibility = View.GONE
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: default to photo checked
            togglePhoto.isChecked = true
            currentMode = MODE_PHOTO
        }

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.toggle_photo -> switchMode(MODE_PHOTO)
                R.id.toggle_video -> switchMode(MODE_VIDEO)
                R.id.toggle_audio -> switchMode(MODE_AUDIO)
            }
        }

        cameraXFacade.await(this) {
            bindCameraForInformation()
        }

        setupCameraTitleView()

        setupCameraInsets()

        onBackPressedDispatcher.addCallback(this, standardBackCallback())

        setupUiForMode()

        viewMediaButton.setOnClickListener {
            val mediaIntent = Intent(this, MediaViewerActivity::class.java)
            mediaIntent.putExtra(MediaViewerActivity.EXTRA_STUDY_ID, studyId)
            mediaIntent.putExtra(MediaViewerActivity.EXTRA_OBS_UNIT, obsUnit)
            mediaIntent.putExtra(MediaViewerActivity.EXTRA_TRAIT_DB_ID, traitId)
            startActivity(mediaIntent)
        }

        switchCameraButton.setOnClickListener {
            cameraXFacade.toggleCameraSelector()
            bindLifecycle()
        }
    }

    private fun setupUiForMode() {

        // hide capture button when running pure barcode-detection mode (auto-return on first detection)
        if (currentMode == MODE_BARCODE) {
            captureButton.visibility = View.GONE
        } else {
            captureButton.visibility = View.VISIBLE
        }

        //if no observations exist in this mode, don't display the shutter or view media buttons
        if (obsId != null) {

            if (obsId == "-1") {

                captureButton.visibility = View.GONE

            } else {

                database.getObservationById(obsId)?.let { obs ->

                    val hasMedia = obs.getMultiMediaCount() > 0

                    viewMediaButton.visibility = if (hasMedia) View.VISIBLE else View.GONE
                }
            }
        }

        captureButton.setOnClickListener {
            when (currentMode) {
                MODE_PHOTO, MODE_CROP -> takePhotoWithBarcodeDetection()
                MODE_VIDEO -> toggleVideoRecording()
                MODE_AUDIO -> {
                    // start/stop simple audio recording
                    if (audioRecording) stopAudioRecording() else startAudioRecording()
                }
                MODE_BARCODE -> { /* barcode scanning mode; handled in bindLifecycle */ }
            }
        }
    }

    private fun updateVideoButtonUi() {
        if (videoRecording) {
            captureButton.setImageResource(R.drawable.ic_media_stop)
        } else {
            captureButton.setImageResource(R.drawable.camera_24px)
        }
    }

    private fun updateAudioButtonUi() {
        if (audioRecording) {
            captureButton.setImageResource(R.drawable.ic_media_stop)
        } else {
            captureButton.setImageResource(R.drawable.trait_audio)
        }
    }

    private fun switchMode(newMode: String) {
        if (currentMode == newMode) return
        currentMode = newMode

        when (currentMode) {
            MODE_PHOTO -> {
                captureButton.setImageResource(R.drawable.camera_24px)
            }
            MODE_VIDEO -> updateVideoButtonUi()
            MODE_AUDIO -> updateAudioButtonUi()
            MODE_BARCODE -> {
                captureButton.setImageResource(R.drawable.camera_24px)
            }
        }

        bindLifecycle()
    }

    private fun toggleVideoRecording() {

        if (!videoRecording) {
            // Use external content URI so MediaStoreOutputOptions has a collectionUri
            val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val outputOptions = MediaStoreOutputOptions.Builder(contentResolver, collection).build()

            val pending = videoCapture?.output?.prepareRecording(this, outputOptions)
                ?.apply { if (ActivityCompat.checkSelfPermission(this@CameraActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) withAudioEnabled() }

            currentRecording = pending?.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        videoRecording = true
                        runOnUiThread {
                            updateVideoButtonUi()
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        videoRecording = false
                        runOnUiThread {
                            updateVideoButtonUi()
                        }
                        // copy saved video from MediaStore URI to local field storage
                        try {
                            val savedUri = event.outputResults.outputUri
                            val input: InputStream? = contentResolver.openInputStream(savedUri)
                            if (input != null) {
                                val sanitized = FileUtil.sanitizeFileName(traitName ?: "unknown")
                                val dir = File(filesDir, "plot_data/$studyName/$sanitized/")
                                if (!dir.exists()) dir.mkdirs()
                                val filename = "video_${System.currentTimeMillis()}.mp4"
                                val dest = File(dir, filename)
                                val out: OutputStream = FileOutputStream(dest)
                                input.copyTo(out)
                                out.close()
                                input.close()
                                // return result to calling activity and finish so CollectActivity receives the media_path
                                val result = Intent()
                                result.putExtra("media_type", "video")
                                result.putExtra("media_path", dest.absolutePath)
                                setResult(RESULT_OK, result)
                                finish()
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                    else -> {}
                }
            }
            updateVideoButtonUi()
        } else {
            currentRecording?.stop()
            currentRecording = null
            videoRecording = false
            updateVideoButtonUi()
        }
    }

    @Suppress("DEPRECATION")
    private fun startAudioRecording() {
        // ensure we have permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
            return
        }

        try {
            // stop any video recording and unbind camera
            if (videoRecording) {
                currentRecording?.stop()
                currentRecording = null
                videoRecording = false
            }

            cameraXFacade.unbind()
            previewView.visibility = View.GONE
            barcodeOverlay.visibility = View.GONE

            val sanitized = try { FileUtil.sanitizeFileName(traitName ?: "unknown") } catch (_: Exception) { "audio" }
            val dir = File(filesDir, "plot_data/$studyName/$sanitized/")
            if (!dir.exists()) dir.mkdirs()
            audioFile = File(dir, "audio_${System.currentTimeMillis()}.mp4")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }

            audioRecording = true
            updateAudioButtonUi()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAudioRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            audioRecording = false
            updateAudioButtonUi()

            audioFile?.let { f ->
                val result = Intent()
                result.putExtra("media_type", "audio")
                result.putExtra("media_path", f.absolutePath)
                setResult(RESULT_OK, result)
            }

            finish()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun takePhotoWithBarcodeDetection() {
        val capture = imageCapture ?: return
        val tmp = File(cacheDir, AbstractCameraTrait.TEMPORARY_IMAGE_NAME)
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(tmp).build()
        capture.takePicture(outputFileOptions, cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                }
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // after photo saved to file, run ML Kit on the saved file to detect a barcode
                    runOnUiThread {
                        try {
                            val destPath = saveFileToFieldStorage(tmp, traitName ?: "unknown")
                            val intent = Intent()
                            intent.putExtra("media_type", "photo")
                            intent.putExtra("media_path", destPath)
                            setResult(RESULT_OK, intent)
                            finish()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                    }
                }
            })
    }

    private fun saveFileToFieldStorage(src: File, traitName: String): String {
        try {
            val sanitized = FileUtil.sanitizeFileName(traitName)
            val dir = File(filesDir, "plot_data/$sanitized")
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, "${System.currentTimeMillis()}_${src.name}")
            src.copyTo(dest, overwrite = true)
            return dest.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return src.absolutePath
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraForInformation() {

        cameraXFacade.bindIdentity({ _, sizes ->

            supportedResolutions = sizes

        }) {
            finishActivity(RESULT_CANCELED)
        }

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

    @OptIn(ExperimentalGetImage::class)
    private fun bindLifecycle() {

        try {

            //val resolution = getSupportedResolutionByPreferences()

            when (currentMode) {
                MODE_PHOTO, MODE_CROP, MODE_BARCODE -> {
                    // Bind preview + image capture + analysis for realtime barcode overlays
                    val provider = cameraXFacade.cameraXInstance.get()

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()

                    // Use ML Kit analyzer to get normalized detections (0..1)
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->

                                if (currentMode == MODE_BARCODE) {
                                    val first = barcodes.firstOrNull()
                                    if (first != null) {
                                        runOnUiThread {
                                            val text = first.rawValue ?: first.displayValue ?: ""
                                            finishWithBarcode(text)
                                        }
                                    }
                                }

                                val viewDetections = mutableListOf<Array<PointF>>()
                                val newBarcodeList = mutableListOf<String>()
                                for (b in barcodes.take(MAX_BARCODE_DETECTIONS)) {
                                    // ML Kit returns cornerPoints in the image coordinate space after applying rotationDegrees
                                    val rotW = if (imageProxy.imageInfo.rotationDegrees == 90 || imageProxy.imageInfo.rotationDegrees == 270) imageProxy.height else imageProxy.width
                                    val rotH = if (imageProxy.imageInfo.rotationDegrees == 90 || imageProxy.imageInfo.rotationDegrees == 270) imageProxy.width else imageProxy.height

                                    val pointsArr: Array<PointF>? = b.cornerPoints?.takeIf { it.isNotEmpty() }?.map { p ->
                                        val nx = p.x.toFloat() / rotW.toFloat()
                                        val ny = p.y.toFloat() / rotH.toFloat()
                                        PointF(nx, ny)
                                    }?.toTypedArray() ?: run {
                                        val box = b.boundingBox
                                        if (box != null) {
                                            arrayOf(
                                                PointF(box.left.toFloat() / rotW.toFloat(), box.top.toFloat() / rotH.toFloat()),
                                                PointF(box.right.toFloat() / rotW.toFloat(), box.top.toFloat() / rotH.toFloat()),
                                                PointF(box.right.toFloat() / rotW.toFloat(), box.bottom.toFloat() / rotH.toFloat()),
                                                PointF(box.left.toFloat() / rotW.toFloat(), box.bottom.toFloat() / rotH.toFloat())
                                            )
                                        } else null
                                    }

                                    if (pointsArr != null && pointsArr.isNotEmpty()) {
                                        // pointsArr are normalized relative to the rotated/upright image; pass rotated dimensions and rotation=0
                                        val rotW = if (imageProxy.imageInfo.rotationDegrees == 90 || imageProxy.imageInfo.rotationDegrees == 270) imageProxy.height else imageProxy.width
                                        val rotH = if (imageProxy.imageInfo.rotationDegrees == 90 || imageProxy.imageInfo.rotationDegrees == 270) imageProxy.width else imageProxy.height
                                        val mapped = mapNormalizedPointsToView(pointsArr, rotW, rotH)
                                        viewDetections.add(mapped)
                                    }
                                    val textVal = b.rawValue ?: b.displayValue ?: ""
                                    if (textVal.isNotEmpty()) newBarcodeList.add(textVal)
                                }

                                overlayBarcodes.clear()
                                overlayBarcodes.addAll(newBarcodeList)
                                runOnUiThread { barcodeOverlay.setDetections(viewDetections as List<Array<PointF>>) }
                            }
                            .addOnFailureListener {
                                // ignore failures for realtime overlay
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    }

                    provider.unbindAll()
                    // ensure preview visible in case we previously switched to audio mode
                    previewView.visibility = View.VISIBLE
                    barcodeOverlay.visibility = View.VISIBLE

                    val resolution = getSupportedResolutionByPreferences()

                    cameraXFacade.bindPreview(
                        previewView,
                        resolution,
                        traitId,
                        analysis,
                        showCropRegion = currentMode == MODE_PHOTO
                    ) { camera, _, capture ->

                        boundCamera = camera

                        imageCapture = capture

                    }

                    // set up tap-to-focus on previewView
                    previewView.setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.performClick()
                            try {
                                val factory: MeteringPointFactory = previewView.meteringPointFactory
                                val point: MeteringPoint = factory.createPoint(event.x, event.y)
                                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                                boundCamera?.cameraControl?.startFocusAndMetering(action)
                            } catch (_: Exception) {}
                        }
                        true
                    }

                    // trigger initial center autofocus
                    try {
                        val factory = previewView.meteringPointFactory
                        val centerPoint = factory.createPoint(previewView.width / 2f, previewView.height / 2f)
                        val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        boundCamera?.cameraControl?.startFocusAndMetering(action)
                    } catch (_: Exception) {}

                    barcodeOverlay.setOnDetectionClickListener { index ->
                        val text = overlayBarcodes.getOrNull(index)
                        finishWithBarcode(text)
                    }
                }
                MODE_VIDEO -> {
                    // bind preview + video capture
                    val provider = cameraXFacade.cameraXInstance.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val resolution = getSupportedResolutionByPreferences()

                    cameraXFacade.bindPreview(
                        previewView,
                        resolution,
                        traitId,
                        null,
                        showCropRegion = false
                    ) { camera, _, capture ->

                        boundCamera = camera

                        imageCapture = capture
                    }

                    val recorder = Recorder.Builder().setExecutor(cameraExecutor).build()
                    videoCapture = VideoCapture.withOutput(recorder)

                    provider.unbindAll()
                    previewView.visibility = View.VISIBLE
                    barcodeOverlay.visibility = View.VISIBLE
                    val cam2 = provider.bindToLifecycle(this, cameraXFacade.currentSelector, preview, videoCapture)
                    boundCamera = cam2
                }
                MODE_AUDIO -> {
                    // Audio mode: no camera preview; unbind camera and hide preview
                    cameraXFacade.unbind()
                    previewView.visibility = View.GONE
                    barcodeOverlay.visibility = View.GONE
                }
            }

        } catch (_: IllegalArgumentException) {

        }
    }

    private fun finishWithBarcode(text: String?) {
        if (!text.isNullOrEmpty()) {
            val intent = Intent()
            intent.putExtra(EXTRA_BARCODE, text)
            // return the barcode as result
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    // Map normalized (0..1) image points to previewView coordinates, taking rotation and fitCenter scaling into account.
    private fun mapNormalizedPointsToView(points: Array<PointF>, imgW: Int, imgH: Int): Array<PointF> {
        // points: normalized coordinates (0..1) relative to image with size imgW x imgH.
        val viewW = previewView.width.toFloat().coerceAtLeast(1f)
        val viewH = previewView.height.toFloat().coerceAtLeast(1f)

        val imageAspect = imgW.toFloat() / imgH.toFloat()
        val viewAspect = viewW / viewH

        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (viewAspect > imageAspect) {
            // view is wider than image: image fits height
            scale = viewH / imgH.toFloat()
            offsetX = (viewW - imgW * scale) / 2f
            offsetY = 0f
        } else {
            // view is narrower: image fits width
            scale = viewW / imgW.toFloat()
            offsetX = 0f
            offsetY = (viewH - imgH * scale) / 2f
        }

        return points.map { p ->
            // convert normalized -> image pixel coords
            val imgX = p.x * imgW
            val imgY = p.y * imgH

            // no rotation: points are normalized to the upright/rotated image already
            val rx = imgX
            val ry = imgY

            // scale into view and apply offsets
            val viewX = rx * scale + offsetX
            val viewY = ry * scale + offsetY
            PointF(viewX.coerceIn(0f, viewW), viewY.coerceIn(0f, viewH))
        }.toTypedArray()
    }

    private fun setupCameraTitleView() {

        intent?.getStringExtra(EXTRA_TITLE)?.let { title ->

            titleTextView.text = title

        }
    }

    private fun setupCameraInsets() {
        val rootView = findViewById<View>(android.R.id.content)

        InsetHandler.setupCameraInsets(rootView, titleTextView, captureButton)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioRecording()
            } else {
                Utils.makeToast(this, getString(R.string.audio_permission_required))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (_: Exception) {}
    }
}