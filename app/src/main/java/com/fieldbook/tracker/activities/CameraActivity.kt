package com.fieldbook.tracker.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.media.MediaRecorder
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.views.CropImageView
import com.fieldbook.tracker.traits.AbstractCameraTrait
import com.fieldbook.tracker.ui.MediaViewerActivity
import com.fieldbook.tracker.ui.components.widgets.ThreeStateToggle
import com.fieldbook.tracker.ui.theme.AppTheme
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
import androidx.appcompat.widget.Toolbar
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.fieldbook.tracker.utilities.SnackbarUtils

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
    private lateinit var mediaCompose: ComposeView

    private var supportedResolutions: List<Size> = listOf()

    private var traitId: String? = null
    private var studyId: String? = null
    private var obsUnit: String? = null
    private var obsId: String? = null
    private var traitName: String? = null
    private var studyName: String? = null
    private var currentMode = MODE_PHOTO

    private var skipSaveFlag: Boolean = false

    private var launchedForVideoTrait = false
    // flag set when CameraActivity was launched specifically from a photo-trait capture flow
    private var launchedForPhotoTrait = false

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
        const val EXTRA_SKIP_SAVE = "skip_save"
        const val MODE_PHOTO = "photo"
        const val MODE_CROP = "crop"
        const val MODE_VIDEO = "video"
        const val MODE_BARCODE = "barcode"
        const val MODE_AUDIO = "audio"
        const val EXTRA_BARCODE = "barcode_value"
        const val REQUEST_RECORD_AUDIO_PERMISSION = 301
        // Request code used when opening the media viewer so CameraActivity can refresh UI on return
        const val REQUEST_VIEW_MEDIA_CODE = 203
        const val MAX_BARCODE_DETECTIONS = 10
        // new extra key to mark launches from the video trait embiggen button
        const val EXTRA_LAUNCHED_FOR_VIDEO_TRAIT = "launched_for_video_trait"
        // new extra key to mark launches from the photo trait (embiggen / capture from trait UI)
        const val EXTRA_LAUNCHED_FOR_PHOTO_TRAIT = "launched_for_photo_trait"
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

        skipSaveFlag = intent.getBooleanExtra(EXTRA_SKIP_SAVE, false)

        currentMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PHOTO

        launchedForVideoTrait = intent.getBooleanExtra(EXTRA_LAUNCHED_FOR_VIDEO_TRAIT, false)
        launchedForPhotoTrait = intent.getBooleanExtra(EXTRA_LAUNCHED_FOR_PHOTO_TRAIT, false)

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

        // Compose host for media toggle
        mediaCompose = findViewById(R.id.media_mode_compose)

        val toolbar = findViewById<Toolbar>(R.id.act_camera_toolbar)
        toolbar?.let {
            it.title = ""
            it.setNavigationOnClickListener {
                // trigger normal back behavior
                onBackPressedDispatcher.onBackPressed()
            }
        }

        //move ui above gesture bar
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
             // Apply bottom inset for system gestures to the root view so FABs and toggles sit above the gesture bar
             val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom
             v.setPadding(0, 0, 0, bottomInset)

             // Also apply the status bar top inset to the toolbar so the app bar is positioned below the status bar
             try {
                 val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                 val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.act_camera_toolbar)
                 toolbar?.setPadding(0, statusTop, 0, toolbar.paddingBottom)
             } catch (_: Exception) {
                 // ignore if toolbar not found yet
             }

             insets
         }

        // We'll prepare a list of visible mode ids (strings) and set visibility later
        val visibleModes = mutableListOf<String>()

        // Read trait attach media flags and toggle visibility of the three choices.
        try {

            if (currentMode in setOf(MODE_PHOTO, MODE_VIDEO, MODE_AUDIO)) {

                val trait = traitId?.takeIf { it != "-1" }?.let { id ->
                    database.getTraitById(id)
                }

                // Set visibility and build painters list based on trait flags. Default to visible if trait unavailable.
                val showPhoto = trait?.attachPhoto != false
                val showVideo = trait?.attachVideo != false
                val showAudio = trait?.attachAudio != false

                if (showPhoto) visibleModes.add(MODE_PHOTO)
                if (showVideo) visibleModes.add(MODE_VIDEO)
                if (showAudio) visibleModes.add(MODE_AUDIO)

                // Count visible options and hide the compose view if there's 0 or 1 option
                val visibleCount = visibleModes.size
                if (visibleCount <= 1) {
                    mediaCompose.visibility = View.GONE
                    ViewCompat.requestApplyInsets(mediaCompose)
                    val onlyMode = visibleModes.firstOrNull()
                    if (onlyMode != null) {
                        // Prefer an intent-specified mode when present and valid, otherwise set the only available mode
                        if (currentMode !in visibleModes) {
                            currentMode = onlyMode
                        }
                    }
                } else {
                    mediaCompose.visibility = View.VISIBLE
                    ViewCompat.requestApplyInsets(mediaCompose)
                    // pick sensible default photo > video > audio only if currentMode isn't one of the visible modes
                    if (currentMode !in visibleModes) {
                        currentMode = when {
                            visibleModes.contains(MODE_PHOTO) -> MODE_PHOTO
                            visibleModes.contains(MODE_VIDEO) -> MODE_VIDEO
                            visibleModes.contains(MODE_AUDIO) -> MODE_AUDIO
                            else -> currentMode
                        }
                    }
                }

            } else {

                mediaCompose.visibility = View.GONE
                ViewCompat.requestApplyInsets(mediaCompose)
                viewMediaButton.visibility = View.GONE

                if (currentMode == MODE_BARCODE) {
                    captureButton.visibility = View.GONE
                    switchCameraButton.visibility = View.GONE
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: default to photo
            visibleModes.clear()
            visibleModes.add(MODE_PHOTO)
            mediaCompose.visibility = View.VISIBLE
            ViewCompat.requestApplyInsets(mediaCompose)
            currentMode = MODE_PHOTO
        }

        if (launchedForVideoTrait) {
            viewMediaButton.visibility = View.GONE
            mediaCompose.visibility = View.GONE
            currentMode = MODE_VIDEO
        }

        // If multiple states are visible, set Compose content. Build Painter resources inside the composable lambda (painterResource is @Composable).
        if (visibleModes.size > 1 && mediaCompose.isVisible) {
            val modes = visibleModes.toList()
            mediaCompose.setContent {
                AppTheme {

                    val orderedModes = listOf(MODE_PHOTO, MODE_VIDEO, MODE_AUDIO)
                    val enabledSet = modes.toSet()

                    val paintersToShow = orderedModes.map { mode ->
                        when (mode) {
                            MODE_PHOTO -> painterResource(R.drawable.ic_trait_camera)
                            MODE_VIDEO -> painterResource(R.drawable.video)
                            MODE_AUDIO -> painterResource(R.drawable.trait_audio)
                            else -> painterResource(R.drawable.ic_trait_camera)
                        }
                    }

                    val initialIdx = orderedModes.indexOf(currentMode).takeIf { it >= 0 } ?: 0
                    val selectedIdxState = remember { mutableIntStateOf(initialIdx) }

                    ThreeStateToggle(
                        states = paintersToShow,
                        selectedIndex = selectedIdxState.intValue,
                        enabledStates = orderedModes.map { it in enabledSet },
                        onSelected = { idx ->
                            selectedIdxState.intValue = idx
                            val mode = orderedModes.getOrNull(idx)
                            if (mode != null && enabledSet.contains(mode)) {
                                switchMode(mode)
                            }
                        }
                    )
                 }
             }
         }

        cameraXFacade.await(this) {
            bindCameraForInformation()
        }

        setupCameraTitleView()

        setupCameraInsets()

        onBackPressedDispatcher.addCallback(this, standardBackCallback())

        setupUiForMode()

        if (launchedForVideoTrait) {
            viewMediaButton.visibility = View.GONE
        }

        viewMediaButton.setOnClickListener {
            val mediaIntent = Intent(this, MediaViewerActivity::class.java)
            mediaIntent.putExtra(MediaViewerActivity.EXTRA_STUDY_ID, studyId)
            mediaIntent.putExtra(MediaViewerActivity.EXTRA_OBS_UNIT, obsUnit)
            mediaIntent.putExtra(MediaViewerActivity.EXTRA_TRAIT_DB_ID, traitId)
            // start for result so we can refresh camera UI (hide view button when media deleted)
            startActivityForResult(mediaIntent, REQUEST_VIEW_MEDIA_CODE)
        }

        switchCameraButton.setOnClickListener {
            cameraXFacade.toggleCameraSelector()
            bindLifecycle()
        }
    }

    private fun setupUiForMode() {

        // hide capture button when running pure barcode-detection mode (auto-return on first detection)
        when (currentMode) {
            MODE_BARCODE -> {
                captureButton.visibility = View.GONE
                switchCameraButton.visibility = View.GONE
            }
            MODE_AUDIO -> {
                captureButton.visibility = View.VISIBLE
                switchCameraButton.visibility = View.GONE
            }
            else -> {
                captureButton.visibility = View.VISIBLE
                switchCameraButton.visibility = View.VISIBLE
            }
        }

        //if no observations exist in this mode, don't display the shutter or view media buttons
        if (obsId != null) {

            if (obsId == "-1") {

                captureButton.visibility = View.GONE
                switchCameraButton.visibility = View.GONE
                viewMediaButton.visibility = View.GONE
                // also hide bottom media toggle when no observation exists
                mediaCompose.visibility = View.GONE
                // show snackbar informing user they must collect data before attaching media,
                // except when CameraActivity was launched from trait layouts (photo/video capture flows)
                try {
                    // only show the snackbar when not launched from a trait-specific capture flow (photo/video)
                    val launchedFromTrait = launchedForVideoTrait || launchedForPhotoTrait || currentMode == MODE_BARCODE
                    if (!launchedFromTrait) {
                        val root = findViewById<View>(android.R.id.content)
                        SnackbarUtils.showShortSnackbar(root, getString(R.string.no_observation))
                    }
                } catch (_: Exception) {}

            } else {

                database.getObservationById(obsId)?.let { obs ->

                    val hasMedia = obs.getAttachedMediaCount() > 0

                    viewMediaButton.visibility = if (hasMedia) View.VISIBLE else View.GONE
                }
            }
        } else {
            // treat null obsId as no observation: hide the media compose toggle
            mediaCompose.visibility = View.GONE
            // inform user they must collect data before attaching media, except when launched from trait layouts
            try {
                val launchedFromTrait = launchedForVideoTrait || launchedForPhotoTrait || currentMode == MODE_BARCODE
                if (!launchedFromTrait) {
                    val root = findViewById<View>(android.R.id.content)
                    SnackbarUtils.showShortSnackbar(root, getString(R.string.no_observation))
                }
            } catch (_: Exception) {}
        }

        if (launchedForVideoTrait) {
            viewMediaButton.visibility = View.GONE
        }

        captureButton.setOnClickListener {
            when (currentMode) {
                MODE_PHOTO, MODE_CROP -> capturePhotoAndReturn()
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

        setupUiForMode()

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

    private fun capturePhotoAndReturn() {
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
                        val destPath = saveFileToFieldStorage(tmp, traitName ?: "unknown")
                        val intent = Intent()
                        intent.putExtra("media_type", "photo")
                        intent.putExtra("media_path", destPath)
                        intent.putExtra(EXTRA_SKIP_SAVE, skipSaveFlag)
                        setResult(RESULT_OK, intent)
                        finish()
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

                                if (currentMode == MODE_BARCODE || (currentMode != MODE_CROP && obsId == "-1")) {
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
                    // Use facade to bind preview+video use case with the same ResolutionSelector / Recorder aspect ratio
                    val resolution = getSupportedResolutionByPreferences()

                    // Determine whether to show crop region overlay for video based on saved crop coordinates for this trait
                    val showCropForVideo = try {
                        val key = GeneralKeys.getCropCoordinatesKey(traitId?.toInt() ?: -1)
                        val cropCoordinates = prefs.getString(key, "")
                        !cropCoordinates.isNullOrEmpty() && cropCoordinates != CropImageView.DEFAULT_CROP_COORDINATES
                    } catch (_: Exception) {
                        false
                    }

                    cameraXFacade.bindPreviewForVideo(
                        previewView,
                        resolution,
                        traitId,
                        analysis = null,
                        showCropRegion = showCropForVideo
                    ) { camera, _, vc ->
                        // facade provides the VideoCapture configured with the correct Recorder
                        boundCamera = camera
                        videoCapture = vc
                        previewView.visibility = View.VISIBLE
                        barcodeOverlay.visibility = View.VISIBLE
                    }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_VIEW_MEDIA_CODE) {
            // Re-evaluate UI: the media viewer may have deleted media so hide view button accordingly
            try {
                setupUiForMode()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}