package com.fieldbook.tracker.traits

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.AttributeSet
import android.net.Uri
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.fieldbook.tracker.activities.CameraActivity
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.internalTimeFormatter
import com.fieldbook.tracker.utilities.FileUtil
import org.threeten.bp.OffsetDateTime
import java.io.File
import java.io.FileInputStream

/**
 * The Video trait uses the same UI as the photo trait but not has a capture start/stop toggle button.
 * Previously captured videos can be replayed in the list item one at a time.
 * Deletion or set to NA will ask the user and show a preview first.
 */
class VideoTraitLayout : PhotoTraitLayout {

    companion object {
        const val TAG = "VideoTrait"
        const val type = "video"
    }

    private var videoCaptureInstance: VideoCapture<Recorder>? = null
    private var currentRecording: androidx.camera.video.Recording? = null
    private var videoRecording = false

    constructor(context: android.content.Context?) : super(context)
    constructor(context: android.content.Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: android.content.Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun type() = type

    // Start/stop inline recording when shutter pressed. If inline capture is unavailable, fallback to fullscreen CameraActivity.
    override fun capture() {
        val vc = videoCaptureInstance
        val act = activity

        if (vc == null || act == null) {
            // fallback to fullscreen camera activity if inline capture is not ready
            launchCameraX(CameraActivity.MODE_VIDEO)
            return
        }

        if (!videoRecording) {
            // Prepare recording to MediaStore and start
            try {
                val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val outputOptions = MediaStoreOutputOptions.Builder(act.contentResolver, collection).build()

                val pending = vc.output.prepareRecording(act, outputOptions)
                    .apply {
                        // enable audio only if permission granted
                        if (ContextCompat.checkSelfPermission(act, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            withAudioEnabled()
                        }
                    }

                currentRecording = pending.start(ContextCompat.getMainExecutor(act)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            videoRecording = true
                            shutterButton?.setImageResource(com.fieldbook.tracker.R.drawable.ic_media_stop)
                            shutterButton?.setColorFilter(android.graphics.Color.BLACK)
                        }
                        is VideoRecordEvent.Finalize -> {
                            videoRecording = false
                            shutterButton?.setImageResource(com.fieldbook.tracker.R.drawable.camera_24px)
                            shutterButton?.clearColorFilter()

                            // Copy the saved media from MediaStore URI into field storage using saveToStorage
                            try {
                                val savedUri = event.outputResults.outputUri
                                // Use the helper that opens the InputStream inside the saver coroutine
                                saveVideoToStorageFromUri(savedUri)
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launchCameraX(CameraActivity.MODE_VIDEO)
            }
        } else {
            // stop recording
            try {
                currentRecording?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            currentRecording = null
            videoRecording = false
            shutterButton?.setImageResource(com.fieldbook.tracker.R.drawable.camera_24px)
            shutterButton?.clearColorFilter()
        }
    }

    fun saveVideoToStorageFromFile(srcFile: File) {
        try {
            if (!srcFile.exists()) return
            saveToStorage(
                currentRange,
                currentTrait,
                saveTime = FileUtil.sanitizeFileName(
                    OffsetDateTime.now().format(
                        internalTimeFormatter
                    )
                ),
                saveState = SaveState.SINGLE_SHOT,
                fileSuffix = ".mp4"
            ) { destUri ->

                var inStream: FileInputStream? = null
                try {
                    inStream = FileInputStream(srcFile)
                    activity?.contentResolver?.openOutputStream(destUri)?.use { out ->
                        inStream.use { ins ->
                            ins.copyTo(out)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try { inStream?.close() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveVideoToStorageFromUri(srcUri: Uri) {
        try {
            saveToStorage(
                currentRange,
                currentTrait,
                saveTime = FileUtil.sanitizeFileName(
                    OffsetDateTime.now().format(
                        internalTimeFormatter
                    )
                ),
                saveState = SaveState.SINGLE_SHOT,
                fileSuffix = ".mp4"
            ) { destUri ->

                try {
                    activity?.contentResolver?.openInputStream(srcUri)?.use { ins ->
                        activity?.contentResolver?.openOutputStream(destUri)?.use { out ->
                            ins.copyTo(out)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Public handler to accept externally-captured media returned by CameraActivity
    // This will skip CollectActivity's confirm dialog and follow the same saving logic used by inline recording
    fun handleExternalMedia(mediaType: String?, mediaPath: String?) {
        if (mediaType == null || mediaPath == null) return

        if (mediaType == "video") {
            try {
                val f = File(mediaPath)
                if (!f.exists()) return
                // Use the file-based saver so the FileInputStream is opened inside the background writer
                saveVideoToStorageFromFile(f)

                // After saving, refresh layout/toolbars on UI thread
                activity?.runOnUiThread {
                    try {
                        (activity as? CollectActivity)?.traitLayoutRefresh()
                    } catch (e: Exception) { e.printStackTrace() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Override launcher to use CollectActivity.REQUEST_MEDIA_VIDEO_TRAIT so CollectActivity receives media for video embiggen
    override fun launchCameraX(mode: String) {
        controller.getCameraXFacade().unbind()
        val intent = Intent(context, CameraActivity::class.java)
        intent.putExtra(CameraActivity.EXTRA_TRAIT_ID, currentTrait.id.toInt())
        intent.putExtra(CameraActivity.EXTRA_STUDY_ID, (activity as? CollectActivity)?.studyId)
        intent.putExtra(CameraActivity.EXTRA_OBS_UNIT, currentRange.uniqueId)
        intent.putExtra(CameraActivity.EXTRA_MODE, mode)
        // mark that CameraActivity was launched from VideoTrait so it can behave accordingly
        intent.putExtra(CameraActivity.EXTRA_LAUNCHED_FOR_VIDEO_TRAIT, true)
        // Use the dedicated request code so CollectActivity can perform the same saving flow used for embiggen
        activity?.startActivityForResult(intent, CollectActivity.REQUEST_MEDIA_VIDEO_TRAIT)
    }

    // Bind preview with video use-case so inline preview is live and video capture is available
    override fun bindPreviewLifecycleForMode(resolution: android.util.Size?) {

        controller.getCameraXFacade().bindPreviewForVideo(
            previewHolder?.previewView,
            resolution,
            currentTrait.id,
            analysis = null,
            showCropRegion = false
        ) { _, _, videoCapture ->
             videoCaptureInstance = videoCapture
         }
    }

    override fun loadLayout() {
        super.loadLayout()

        shutterButton?.visibility = VISIBLE
        settingsButton?.visibility = GONE
        connectBtn?.visibility = GONE
        loadAdapterItems()

        try {
            awaitPreviewHolder {
                previewHolder?.embiggenButton?.visibility = VISIBLE
                previewHolder?.embiggenButton?.setOnClickListener {
                    try {
                        capture()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}