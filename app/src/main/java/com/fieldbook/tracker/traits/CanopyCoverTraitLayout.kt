package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.internalTimeFormatter
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.fieldbook.tracker.utilities.ExifUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.OffsetDateTime
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Canopy cover trait using the Canopeo algorithm (Patrignani & Ochsner, 2015,
 * doi:10.2134/agronj15.0150). Classifies pixels as green canopy when:
 *   R/G < threshold  AND  B/G < threshold  AND  2G - R - B > 20
 * FGCC (Fractional Green Canopy Cover) is stored as a percentage string, e.g. "45.3".
 *
 * The source image is attached to the same observation row via photo_uri, so the value and the
 * image it was computed from travel together through export, database import and deletion.
 *
 * Extends PhotoTraitLayout for live CameraX preview + system-camera fallback.
 * Sensitivity threshold is a trait parameter (set at creation/edit time), not in the collect screen.
 */
class CanopyCoverTraitLayout : PhotoTraitLayout {

    companion object {
        const val TAG = "CanopyCover"
        const val type = "canopy_cover"
        const val DEFAULT_SLIDER_PROGRESS = 50
        const val THRESHOLD_MIN = 0.70f
        const val THRESHOLD_RANGE = 0.50f
        const val P3_THRESHOLD = 20f
        private const val MAX_ANALYSIS_WIDTH = 1000
        private const val DEFAULT_CAMERA_ASPECT_RATIO = 3f / 4f

        /** Translucent green painted over classified canopy pixels in the capture preview. */
        private val CANOPY_OVERLAY_COLOR = Color.argb(180, 0, 200, 0)

        fun sliderToThreshold(progress: Int): Float =
            THRESHOLD_MIN + (progress / 100f) * THRESHOLD_RANGE

        /**
         * The Canopeo classification, for one packed ARGB pixel. Single definition on purpose -
         * the collect preview and the sensitivity parameter's test preview must classify
         * identically or the number a user tunes against is not the number they collect.
         */
        fun isCanopyPixel(pixel: Int, threshold: Float): Boolean {
            val r = Color.red(pixel).toFloat()
            val g = Color.green(pixel).toFloat()
            val b = Color.blue(pixel).toFloat()
            return g > 0f &&
                r / g < threshold &&
                b / g < threshold &&
                2f * g - r - b > P3_THRESHOLD
        }
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var capturedImageView: ImageView? = null
    private var canopyPreviewView: PreviewView? = null
    private var canopyFrameCard: CardView? = null
    private var embiggenButton: FloatingActionButton? = null
    private var previewLoadJob: Job? = null
    private var currentPreviewBitmap: Bitmap? = null
    private var previewResolution: Size? = null

    // True while the captured-image overlay is displayed; used to prevent bindPreviewLifecycleForMode
    // from unhiding the live preview when the camera re-binds in the background.
    private var showingCapturedImage = false

    private val threshold: Float
        get() {
            val progress = currentTrait?.sensitivity?.toIntOrNull() ?: DEFAULT_SLIDER_PROGRESS
            return sliderToThreshold(progress)
        }

    override fun type() = type
    override fun layoutId() = R.layout.trait_canopy_cover

    override fun init(act: Activity) {
        super.init(act)
        capturedImageView = act.findViewById(R.id.canopy_captured_iv)
        canopyPreviewView = act.findViewById(R.id.canopy_preview_view)
        canopyFrameCard = act.findViewById(R.id.canopy_frame_card)
        embiggenButton = act.findViewById(R.id.canopy_embiggen_btn)
        embiggenButton?.setOnClickListener { expandImage() }
        capturedImageView?.setOnClickListener { expandImage() }
    }

    // Skip the RecyclerView-based PreviewViewHolder polling; we have a plain PreviewView.
    override fun awaitPreviewHolder(callback: () -> Unit) = callback()

    override fun bindPreviewLifecycleForMode(resolution: Size?) {
        previewResolution = resolution
        updatePreviewFrameAspectRatio(resolution)
        canopyPreviewView?.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        if (isSystemCameraSelected()) {
            showLivePreview()
            return
        }
        canopyFrameCard?.visibility = VISIBLE
        if (!showingCapturedImage) {
            canopyPreviewView?.visibility = VISIBLE
        }
        controller.getCameraXFacade().bindPreview(
            canopyPreviewView,
            resolution,
            currentTrait?.id,
            null,
            showCropRegion = false
        ) { _, executor, capture ->
            shutterButton?.setOnClickListener {
                if (!isLocked) {
                    captureWithOverwriteWarning {
                        controller.getSoundHelper().playShutter()
                        val file = File(context.cacheDir, TEMPORARY_IMAGE_NAME)
                        file.createNewFile()
                        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                        executor?.let {
                            capture.takePicture(opts, it, object : ImageCapture.OnImageSavedCallback {
                                override fun onError(e: ImageCaptureException) {}
                                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                    makeImage(currentTrait)
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onSettingsChanged() {
        super.onSettingsChanged()
        if (isSystemCameraSelected()) {
            if (collectInputView.text.isEmpty()) {
                showLivePreview()
            } else {
                showStoredImageForRep(collectActivity.rep)
            }
            setupSystemCameraCaptureButton()
        } else if (collectInputView.text.isEmpty()) {
            showLivePreview()
        }
    }

    /** Returns whether the capture was accepted; analysis runs on IO dispatcher. */
    override fun makeImage(currentTrait: TraitObject): Boolean {
        val act = context as? CollectActivity ?: return false
        val file = File(context.cacheDir, TEMPORARY_IMAGE_NAME)
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "makeImage: temp file is missing or empty")
            return false
        }

        // Capture observation context at invocation time before any async work
        val studyId = act.studyId
        val traitId = currentTrait.id
        val obsUnit = currentRange.uniqueId
        val rep = act.rep
        val traitName = FileUtil.sanitizeFileName(currentTrait.name)
        val saveTime = FileUtil.sanitizeFileName(
            OffsetDateTime.now().format(
                internalTimeFormatter
            )
        )
        val fileName = "${obsUnit}_${traitName}_${saveTime}.jpg"

        // Save image & record observation with FGCC value all in this IO coroutine
        background.launch {
            val savedUri = saveCanopyImageToStorage(file, traitName, fileName)?.also { uri ->
                writeExif(uri, studyId, obsUnit, traitId, saveTime)
            }

            // Run image analysis on the saved image
            val overlay = savedUri?.let { uri ->
                val bmp = loadAndScale(uri, MAX_ANALYSIS_WIDTH)
                if (bmp != null) {
                    // One read of the threshold drives both the value and the overlay, so the
                    // preview always depicts the number that was actually recorded
                    val analysis = analyzeWithOverlay(bmp, threshold)
                    bmp.recycle()

                    // Record the value against the original captured context. All DB operations
                    // happen in this single IO coroutine - no race conditions. The source image
                    // rides along on photo_uri, so the value and the image it was computed from
                    // cannot drift apart the way separate keying allowed.
                    saveObservation(
                        act, studyId, obsUnit, traitId, rep,
                        "%.1f".format(analysis.fgcc), uri
                    )

                    analysis.overlay
                } else null
            } ?: return@launch

            // Update UI on main thread
            withContext(Dispatchers.Main) {
                // The entry, trait or rep can all change while the file write and analysis run.
                // This result belongs to the context the capture started in, not to whatever is
                // on screen now - the observation is already saved either way.
                // NB: the makeImage parameter shadows the currentTrait property, so the live
                // trait has to come from getCurrentTrait().
                if (collectActivity.rep != rep ||
                    getCurrentTrait()?.id != traitId ||
                    currentRange.uniqueId != obsUnit
                ) return@withContext

                showCapturedImage(overlay)
                act.updateCurrentTraitStatus(true)
                act.refreshInfoBarAdapter()
                act.refreshRepeatedValuesToolbarIndicator()
                loadLayout()

                // Writing the observation directly bypasses BaseTraitLayout.updateObservation,
                // which is where every other format advances the entry, so do it here. A capture
                // that produced no value returns above and must not advance.
                handleAutoSwitchToNextPlot(currentTrait)
            }
        }

        return true
    }

    /**
     * Mirrors CollectActivity.updateObservation: update the observation already recorded at [rep],
     * or insert one when there is none. Capturing must not silently add a repeated measure - the
     * overwrite prompt tells the user the current value is being replaced, and repeats are added
     * deliberately through the repeat controls, the same as every other trait.
     */
    private fun saveObservation(
        act: CollectActivity,
        studyId: String,
        obsUnit: String,
        traitId: String,
        rep: String,
        value: String,
        imageUri: Uri
    ) {
        try {
            val dataHelper = act.getDatabase()
            val person = act.person.orEmpty()
            val location = try { act.locationByPreferences } catch (_: Exception) { "" }
            val existing = observationForRep(studyId, obsUnit, traitId, rep)

            if (existing == null) {
                dataHelper.insertObservation(
                    obsUnit, traitId, value,
                    person, location, "", studyId, null, null, null, rep,
                    null, null, imageUri.toString()
                )
                return
            }

            // Nothing references the image being replaced once the row points at the new one
            existing.photo_uri
                ?.takeIf { it.isNotEmpty() && it != imageUri.toString() }
                ?.let(::deleteImageUri)

            existing.value = value
            existing.photo_uri = imageUri.toString()
            existing.collector = person
            existing.geo_coordinates = location
            existing.observation_time_stamp = OffsetDateTime.now().format(internalTimeFormatter)

            dataHelper.updateObservationModels(dataHelper.db, listOf(existing))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save canopy observation for rep $rep", e)
        }
    }

    override fun afterLoadExists(act: CollectActivity, value: String?) {
        super.afterLoadExists(act, value)
        if (value.isNullOrEmpty()) {
            showLivePreview()
        } else {
            showStoredImageForRep(act.rep)
        }
    }

    override fun afterLoadNotExists(act: CollectActivity) {
        super.afterLoadNotExists(act)
        showLivePreview()
    }

    override fun refreshLayout(onNew: Boolean?) {
        super.refreshLayout(onNew)
        if (onNew == true || collectInputView.text.isEmpty()) {
            showLivePreview()
        } else {
            showStoredImageForRep(collectActivity.rep)
        }
    }

    override fun onExit() {
        previewLoadJob?.cancel()
    }

    override fun deleteTraitListener() {
        if (isLocked) return

        previewLoadJob?.cancel()

        val rep = collectActivity.rep
        deleteStoredImageForRep(rep)
        collectActivity.removeTrait(currentTrait)

        val hasRemainingObservation = getObservations().any { it.value.isNotEmpty() }

        if (collectInputView.isRepeatEnabled()) {
            collectInputView.repeatView.userDeleteCurrentRep()
        } else {
            collectInputView.prepareEmptyObservationsMode()
            showLivePreview()
        }

        collectActivity.updateCurrentTraitStatus(hasRemainingObservation)
        collectActivity.refreshRepeatedValuesToolbarIndicator()

        if (prefs.getBoolean(PreferenceKeys.DELETE_OBSERVATION_SOUND, false)) {
            controller.getSoundHelper().playDelete()
        }
    }

    // ── View state helpers ────────────────────────────────────────────────────

    private fun showCapturedImage(bmp: Bitmap) {
        currentPreviewBitmap = bmp
        updatePreviewFrameAspectRatio(bmp.width, bmp.height)
        showingCapturedImage = true
        canopyPreviewView?.visibility = GONE
        capturedImageView?.setImageBitmap(bmp)
        capturedImageView?.visibility = VISIBLE
        canopyFrameCard?.visibility = VISIBLE
        embiggenButton?.visibility = VISIBLE
    }

    private fun showLivePreview() {
        previewLoadJob?.cancel()
        currentPreviewBitmap = null
        updatePreviewFrameAspectRatio(previewResolution)
        showingCapturedImage = false
        capturedImageView?.visibility = GONE
        capturedImageView?.setImageBitmap(null)
        embiggenButton?.visibility = GONE
        if (isSystemCameraSelected()) {
            canopyPreviewView?.visibility = GONE
            canopyFrameCard?.visibility = GONE
        } else {
            canopyPreviewView?.visibility = VISIBLE
            canopyFrameCard?.visibility = VISIBLE
        }
    }

    private fun setupSystemCameraCaptureButton() {
        shutterButton?.setOnClickListener {
            if (!isLocked) {
                captureWithOverwriteWarning {
                    controller.getSoundHelper().playShutter()
                    captureWithSystemCamera()
                }
            }
        }
    }

    private fun captureWithSystemCamera() {
        super.capture()
    }

    private fun captureWithOverwriteWarning(captureImage: () -> Unit) {
        if (collectInputView.text.isNotEmpty()) {
            AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(R.string.canopy_cover_overwrite_title)
                .setMessage(R.string.canopy_cover_overwrite_message)
                .setPositiveButton(R.string.canopy_cover_overwrite_positive) { dialog, _ ->
                    dialog.dismiss()
                    captureImage()
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            captureImage()
        }
    }

    private fun isSystemCameraSelected(): Boolean {
        return prefs.getInt(
            GeneralKeys.CAMERA_SYSTEM,
            R.id.view_trait_photo_settings_camera_custom_rb
        ) == R.id.view_trait_photo_settings_camera_system_rb
    }

    private fun showStoredImageForRep(rep: String) {
        previewLoadJob?.cancel()
        val studyId = collectActivity.studyId
        val traitId = currentTrait.id
        val obsUnit = currentRange.uniqueId
        val thresholdForPreview = threshold
        previewLoadJob = background.launch {
            val uriStr = photoUriForRep(studyId, obsUnit, traitId, rep)

            val bitmap = if (uriStr.isNullOrEmpty()) null else try {
                loadAndScale(uriStr.toUri(), MAX_ANALYSIS_WIDTH)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load canopy image for rep $rep", e)
                null
            }
            val overlay = bitmap?.let { source ->
                analyzeWithOverlay(source, thresholdForPreview).overlay
                    .also { source.recycle() }
            }
            withContext(Dispatchers.Main) {
                if (collectActivity.rep == rep && currentTrait.id == traitId && currentRange.uniqueId == obsUnit) {
                    if (overlay != null) {
                        showCapturedImage(overlay)
                    } else {
                        showLivePreview()
                    }
                }
            }
        }
    }

    /** Observation recorded at [rep] for this context, or null if there is none. */
    private fun observationForRep(
        studyId: String,
        obsUnit: String,
        traitId: String,
        rep: String
    ): ObservationModel? = try {
        database.getRepeatedValues(studyId, obsUnit, traitId)
            .firstOrNull { it.rep == rep }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to look up canopy observation for rep $rep", e)
        null
    }

    /** Source image attached to the observation at [rep], or null if there is none. */
    private fun photoUriForRep(
        studyId: String,
        obsUnit: String,
        traitId: String,
        rep: String
    ): String? = observationForRep(studyId, obsUnit, traitId, rep)?.photo_uri

    private fun expandImage() {
        val bitmap = currentPreviewBitmap ?: return
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
        }
        val imageView = ImageView(context).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }
        val closeButton = ImageButton(context).apply {
            setImageResource(R.drawable.close)
            setBackgroundResource(R.drawable.circle_background)
            contentDescription = context.getString(android.R.string.cancel)
            setOnClickListener { dialog.dismiss() }
        }
        val size = resources.getDimensionPixelSize(R.dimen.fb_trait_fab_size_small)
        val margin = (16f * resources.displayMetrics.density).roundToInt()
        val closeParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
            setMargins(margin, margin, margin, margin)
        }

        container.addView(imageView)
        container.addView(closeButton, closeParams)
        dialog.setContentView(container)
        dialog.show()
    }

    // ── Image storage ─────────────────────────────────────────────────────────

    private fun updatePreviewFrameAspectRatio(resolution: Size?) {
        val aspectRatio = resolution?.let {
            val shortEdge = min(it.width, it.height)
            val longEdge = max(it.width, it.height)
            if (shortEdge > 0 && longEdge > 0) shortEdge.toFloat() / longEdge.toFloat()
            else DEFAULT_CAMERA_ASPECT_RATIO
        } ?: DEFAULT_CAMERA_ASPECT_RATIO
        updatePreviewFrameAspectRatio(aspectRatio)
    }

    private fun updatePreviewFrameAspectRatio(width: Int, height: Int) {
        val aspectRatio = if (width > 0 && height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            DEFAULT_CAMERA_ASPECT_RATIO
        }
        updatePreviewFrameAspectRatio(aspectRatio)
    }

    private fun updatePreviewFrameAspectRatio(rawAspectRatio: Float) {
        val aspectRatio = rawAspectRatio.coerceIn(0.25f, 4f)
        val maxWidthPx = resources.getDimensionPixelSize(R.dimen.camera_preview_portrait_width)
        val maxHeightPx = resources.getDimensionPixelSize(R.dimen.camera_preview_portrait_height)
        var widthPx = maxWidthPx
        var heightPx = (widthPx / aspectRatio).roundToInt()
        if (heightPx > maxHeightPx) {
            heightPx = maxHeightPx
            widthPx = (heightPx * aspectRatio).roundToInt()
        }
        canopyFrameCard?.layoutParams = canopyFrameCard?.layoutParams?.apply {
            width = widthPx
            height = heightPx
        }
    }



    /**
     * CollectActivity already clears photo_uri media when an observation is deleted through the
     * toolbar, but deleteTraitListener is reachable from other paths too. Deleting here as well
     * keeps the file from being orphaned; a second delete of a missing file is a no-op.
     */
    private fun deleteStoredImageForRep(rep: String) {
        try {
            photoUriForRep(collectActivity.studyId, currentRange.uniqueId, currentTrait.id, rep)
                ?.takeIf { it.isNotEmpty() }
                ?.let(::deleteImageUri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete canopy image for rep $rep", e)
        }
    }

    private fun deleteImageUri(uriString: String) {
        try {
            val uri = uriString.toUri()
            val deleted = DocumentFile.fromSingleUri(context, uri)?.delete() == true
            if (!deleted && uri.scheme == "content") {
                context.contentResolver.delete(uri, null, null)
            } else if (!deleted && uri.scheme == "file") {
                uri.path?.let { path -> File(path).delete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete canopy image $uriString", e)
        }
    }

    /**
     * Stamp the image with the same study/entry/trait provenance every other Field Book image
     * carries, matching AbstractCameraTrait.writeExif.
     *
     * Written before the image is read back for analysis: saveStringToExif rewrites the target
     * file in place, so doing it later would race the preview reload that follows a capture.
     */
    private fun writeExif(
        uri: Uri,
        studyId: String,
        entryId: String,
        traitId: String,
        timestamp: String
    ) {
        try {
            ExifUtil.saveVariableUnitModelToExif(
                context,
                collectActivity.person.orEmpty(),
                timestamp,
                database.getStudyById(studyId),
                database.getObservationUnitById(entryId),
                database.getObservationVariableById(traitId),
                uri,
                controller.getRotationRelativeToDevice()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write EXIF metadata to canopy image", e)
        }
    }

    /** Create file in field media storage and copy temp file - returns URI. No DB insert. */
    private fun saveCanopyImageToStorage(
        tempFile: File,
        traitName: String,
        fileName: String,
    ): Uri? {
        return try {
            DocumentTreeUtil.getFieldMediaDirectory(context, traitName)?.let { dir ->
                dir.createFile("image/jpeg", fileName)?.let { docFile ->
                    context.contentResolver.openOutputStream(docFile.uri)?.use { out ->
                        tempFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    docFile.uri
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save canopy image to storage", e)
            null
        }
    }

    // ── Canopeo algorithm ─────────────────────────────────────────────────────


    private fun loadAndScale(uri: Uri, maxWidth: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            var sampleSize = 1
            while (max(opts.outWidth, opts.outHeight) / sampleSize > maxWidth) sampleSize *= 2
            opts.inSampleSize = sampleSize
            opts.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }?.let {
                applyExifRotation(it, readExifRotation(uri))
            }
        } catch (_: Exception) {
            null
        }
    }


    private fun readExifRotation(uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                orientationToDegrees(
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                )
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
    }

    private fun orientationToDegrees(orientation: Int): Float {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }

    private fun applyExifRotation(bitmap: Bitmap, rotationDegrees: Float): Bitmap {
        if (rotationDegrees == 0f) return bitmap
        return try {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it != bitmap) bitmap.recycle()
            }
        } catch (_: Exception) {
            bitmap
        }
    }

    /** FGCC percentage and the preview overlay, both produced by one pass over [source]. */
    private data class CanopyAnalysis(val fgcc: Float, val overlay: Bitmap)

    /**
     * Counts canopy pixels and tints them in a single pass. The overlay is built straight from the
     * pixel array rather than copying [source] first - every pixel of such a copy was immediately
     * overwritten, so it only bought an extra full-bitmap allocation.
     *
     * [source] is not read after this returns; callers own recycling it.
     */
    private fun analyzeWithOverlay(source: Bitmap, threshold: Float): CanopyAnalysis {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var canopyCount = 0
        for (i in pixels.indices) {
            if (isCanopyPixel(pixels[i], threshold)) {
                canopyCount++
                pixels[i] = CANOPY_OVERLAY_COLOR
            }
        }

        val fgcc = if (pixels.isEmpty()) 0f else canopyCount.toFloat() / pixels.size * 100f
        val overlay = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return CanopyAnalysis(fgcc, overlay)
    }
}
