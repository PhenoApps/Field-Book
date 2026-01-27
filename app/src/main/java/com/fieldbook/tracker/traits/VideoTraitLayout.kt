package com.fieldbook.tracker.traits

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Size
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
import com.fieldbook.tracker.views.VideoCameraSettingsView
import org.threeten.bp.OffsetDateTime
import java.io.File
import java.io.FileInputStream
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.fieldbook.tracker.activities.CropImageActivity
import com.fieldbook.tracker.fragments.CropImageFragment
import com.fieldbook.tracker.provider.GenericFileProvider
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.views.CropImageView
import android.media.MediaMetadataRetriever
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.fieldbook.tracker.R.*

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

    // resolutions specific to video mode (populated from camera identity)
    private var videoSupportedResolutions: List<Size> = listOf()

    constructor(context: android.content.Context?) : super(context)
    constructor(context: android.content.Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: android.content.Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun type() = type

    // Start/stop inline recording when shutter pressed. If inline capture is unavailable, fallback to fullscreen CameraActivity.
    override fun capture() {
        val vc = videoCaptureInstance
        val act = activity

        // If this trait requires a crop region and one doesn't exist yet, take a single temporary picture
        // and launch the crop activity so the user can define the crop rect. Do not save this image to field storage.
        try {
            if (isCropRequired() && !isCropExist()) {
                // create temporary file in cache
                val tmpFile = File(context.cacheDir, TEMPORARY_IMAGE_NAME)
                try {
                    if (tmpFile.exists()) tmpFile.delete()
                } catch (_: Exception) {
                }
                tmpFile.createNewFile()

                // Bind a front-capture ImageCapture use-case temporarily to take a single picture
                controller.getCameraXFacade().bindFrontCapture(null) { _, executor, imageCapture ->

                    val outputFileOptions = ImageCapture.OutputFileOptions.Builder(tmpFile).build()

                    executor?.let { exec ->
                        imageCapture.takePicture(
                            outputFileOptions,
                            exec,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onError(error: ImageCaptureException) {
                                    error.printStackTrace()
                                    // Rebind video preview after failure
                                    try {
                                        controller.getCameraXFacade().unbind()
                                        bindPreviewLifecycleForMode(null)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }

                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    try {
                                        // Build a content:// URI for the cached file so CropImageActivity can write to it
                                        val tmpUri = GenericFileProvider.getUriForFile(
                                            context,
                                            GenericFileProvider.AUTHORITY,
                                            tmpFile
                                        )

                                        // Launch crop activity with the temporary image URI; the crop activity will write crop coordinates to prefs
                                        val intent = Intent(context, CropImageActivity::class.java)
                                        intent.putExtra(
                                            CropImageFragment.EXTRA_TRAIT_ID,
                                            currentTrait.id.toInt()
                                        )
                                        intent.putExtra(
                                            CropImageFragment.EXTRA_IMAGE_URI,
                                            tmpUri.toString()
                                        )
                                        activity?.startActivity(intent)

                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        // Rebind the video preview so user returns to video mode after defining crop
                                        try {
                                            controller.getCameraXFacade().unbind()
                                            bindPreviewLifecycleForMode(null)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            })
                    }
                }

                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (vc == null || act == null) {
            // fallback to fullscreen camera activity if inline capture is not ready
            launchCameraX(CameraActivity.MODE_VIDEO)
            return
        }

        if (!videoRecording) {
            // Prepare recording to MediaStore and start
            try {
                val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val outputOptions =
                    MediaStoreOutputOptions.Builder(act.contentResolver, collection).build()

                val pending = vc.output.prepareRecording(act, outputOptions)
                    .apply {
                        // enable audio only if permission granted
                        if (ContextCompat.checkSelfPermission(
                                act,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            withAudioEnabled()
                        }
                    }

                currentRecording = pending.start(ContextCompat.getMainExecutor(act)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            videoRecording = true
                            shutterButton?.setImageResource(drawable.ic_media_stop)
                            shutterButton?.setColorFilter(android.graphics.Color.BLACK)
                        }
                        is VideoRecordEvent.Finalize -> {
                            videoRecording = false
                            shutterButton?.setImageResource(drawable.camera_24px)
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
            shutterButton?.setImageResource(drawable.camera_24px)
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
                    // If crop is required and exists, run FFmpeg crop; otherwise simple copy
                    val cropRequired = isCropRequired() && isCropExist()

                    if (cropRequired) {
                        val cropRect = preferences.getString(GeneralKeys.getCropCoordinatesKey(currentTrait.id.toInt()), "") ?: ""
                        val rect = CropImageView.parseRectCoordinates(cropRect)

                        if (rect != null) {
                            // prepare temp files
                            val tmpIn = srcFile

                            try {
                                // attempt crop+copy using helper
                                runFfmpegCropOrCopy(tmpIn, rect, destUri)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // fallback copy
                                inStream = FileInputStream(srcFile)
                                activity?.contentResolver?.openOutputStream(destUri)?.use { out ->
                                    inStream.use { ins ->
                                        ins.copyTo(out)
                                    }
                                }
                            }

                        } else {
                            // invalid rect, simple copy
                            inStream = FileInputStream(srcFile)
                            activity?.contentResolver?.openOutputStream(destUri)?.use { out ->
                                inStream.use { ins ->
                                    ins.copyTo(out)
                                }
                            }
                        }
                    } else {
                        inStream = FileInputStream(srcFile)
                        activity?.contentResolver?.openOutputStream(destUri)?.use { out ->
                            inStream.use { ins ->
                                ins.copyTo(out)
                            }
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
                    // If crop is required and exists, run FFmpeg crop; otherwise simple copy
                    val cropRequired = isCropRequired() && isCropExist()

                    if (cropRequired) {
                        val cropRect = preferences.getString(GeneralKeys.getCropCoordinatesKey(currentTrait.id.toInt()), "") ?: ""
                        val rect = CropImageView.parseRectCoordinates(cropRect)

                        if (rect != null) {
                            // copy srcUri to temp input file
                            val tmpIn = File(context.cacheDir, "fb_video_in_${System.currentTimeMillis()}.mp4")

                            activity?.contentResolver?.openInputStream(srcUri)?.use { ins ->
                                tmpIn.outputStream().use { out ->
                                    ins.copyTo(out)
                                }
                            }

                            try {
                                // attempt crop+copy using helper
                                runFfmpegCropOrCopy(tmpIn, rect, destUri)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // fallback to simple copy
                                activity?.contentResolver?.openInputStream(srcUri)?.use { ins ->
                                    activity?.contentResolver?.openOutputStream(destUri)?.use { out ->
                                        ins.copyTo(out)
                                    }
                                }
                            } finally {
                                try { tmpIn.delete() } catch (_: Exception) {}
                            }

                        } else {
                            // invalid rect, simple copy
                            activity?.contentResolver?.openInputStream(srcUri)?.use { ins ->
                                activity?.contentResolver?.openOutputStream(destUri)?.use { out ->
                                    ins.copyTo(out)
                                }
                            }
                        }
                    } else {
                        // crop not required - simple copy
                        activity?.contentResolver?.openInputStream(srcUri)?.use { ins ->
                            activity?.contentResolver?.openOutputStream(destUri)?.use { out ->
                                ins.copyTo(out)
                            }
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

    /**
     * Helper: read metadata from input file, compute crop/transpose, run FFmpeg to create a cropped temporary
     * output, and copy the result into destUri. If anything fails or dimensions are invalid, this will throw
     * an exception so callers can fallback to a simple copy.
     */
    private fun runFfmpegCropOrCopy(tmpIn: File, rect: android.graphics.RectF, destUri: Uri) {
        // tmpOut will be created in cache
        val tmpOut = File(context.cacheDir, "fb_video_crop_out_${System.currentTimeMillis()}.mp4")

        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(tmpIn.absolutePath)

            val rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

            val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

            val displayW = if (rotation == 90 || rotation == 270) h else w
            val displayH = if (rotation == 90 || rotation == 270) w else h

            mmr.release()

            if (w <= 0 || h <= 0) {
                throw IllegalStateException("Invalid video dimensions: $w x $h")
            }

            var cropX = (rect.left * displayW).toInt()
            var cropY = (rect.top * displayH).toInt()
            var cropW = ((rect.right - rect.left) * displayW).toInt()
            var cropH = ((rect.bottom - rect.top) * displayH).toInt()

            if (cropW <= 0 || cropH <= 0) {
                throw IllegalStateException("Computed non-positive crop size: $cropW x $cropH")
            }

            // ensure even sizes (encoder requirement)
            if (cropW % 2 != 0) cropW -= 1
            if (cropH % 2 != 0) cropH -= 1
            if (cropX < 0) cropX = 0
            if (cropY < 0) cropY = 0

            // Clamp to frame dimensions after transpose
            if (cropW > displayW) cropW = displayW
            if (cropH > displayH) cropH = displayH
            if (cropX > displayW - cropW) cropX = displayW - cropW
            if (cropY > displayH - cropH) cropY = displayH - cropH

            if (cropW <= 0 || cropH <= 0) {
                throw IllegalStateException("Crop dimensions invalid after clamping: $cropW x $cropH")
            }

            Log.d(TAG, "cropW: $cropW, cropH: $cropH, cropX: $cropX, cropY: $cropY")
            Log.d(TAG, "w: $w, h: $h, rotation: $rotation")

            val cropFilter = "crop=${cropW}:${cropH}:${cropX}:${cropY}"

            val cmd = "-y -i \"${tmpIn.absolutePath}\" -vf \"$cropFilter\" -c:v mpeg4 -qscale:v 3 -c:a aac -b:a 64k \"${tmpOut.absolutePath}\""
            Log.d(TAG, "runFfmpegCropOrCopy cmd: $cmd")

            FFmpegKit.execute(cmd)

            // copy tmpOut into destUri if exists, otherwise throw to let caller fallback
            if (tmpOut.exists()) {
                activity?.contentResolver?.openOutputStream(destUri)?.use { out ->
                    tmpOut.inputStream().use { it.copyTo(out) }
                }
            } else {
                throw IllegalStateException("FFmpeg did not produce output file")
            }

        } finally {
            try { tmpOut.delete() } catch (_: Exception) {}
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
    override fun bindPreviewLifecycleForMode(resolution: Size?) {

        controller.getCameraXFacade().bindPreviewForVideo(
            previewHolder?.previewView,
            resolution,
            currentTrait.id,
            analysis = null,
            showCropRegion = true
        ) { _, _, videoCapture ->
            videoCaptureInstance = videoCapture
        }
    }

    override fun loadLayout() {
        super.loadLayout()

        shutterButton?.visibility = VISIBLE
        settingsButton?.visibility = VISIBLE
        settingsButton?.isEnabled = false
        settingsButton?.setOnClickListener { showSettings() }
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

        // populate available resolutions for settings UI
        try {
            controller.getCameraXFacade().bindIdentity({ _, sizes ->
                videoSupportedResolutions = sizes
                settingsButton?.isEnabled = true
            }, {})
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun showSettings() {

        val settingsView = VideoCameraSettingsView(context, videoSupportedResolutions)
        AlertDialog.Builder(context, style.AppAlertDialog)
            .setTitle(string.trait_system_photo_settings_title)
            .setPositiveButton(string.dialog_ok) { dialog, _ ->
                settingsView.commitChanges()
                onSettingsChanged()
                dialog.dismiss()
            }
            .setView(settingsView)
            .show()
    }
}