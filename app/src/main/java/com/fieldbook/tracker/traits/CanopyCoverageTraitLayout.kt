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
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.internalTimeFormatter
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.DocumentTreeUtil
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
 * Canopy coverage trait using the Canopeo algorithm (Patrignani & Ochsner, 2015,
 * doi:10.2134/agronj15.0150). Classifies pixels as green canopy when:
 *   R/G < threshold  AND  B/G < threshold  AND  2G - R - B > 20
 * FGCC (Fractional Green Canopy Cover) is stored as a percentage string, e.g. "45.3".
 *
 * Extends PhotoTraitLayout for live CameraX preview + system-camera fallback.
 * Sensitivity threshold is a trait parameter (set at creation/edit time), not in the collect screen.
 */
class CanopyCoverageTraitLayout : PhotoTraitLayout {

    companion object {
        const val TAG = "CanopyCoverage"
        const val type = "canopy_coverage"
        const val DEFAULT_SLIDER_PROGRESS = 50
        const val THRESHOLD_MIN = 0.70f
        const val THRESHOLD_RANGE = 0.50f
        private const val MAX_ANALYSIS_WIDTH = 1000
        private const val P3_THRESHOLD = 20f
        private const val DEFAULT_CAMERA_ASPECT_RATIO = 3f / 4f

        fun sliderToThreshold(progress: Int): Float =
            THRESHOLD_MIN + (progress / 100f) * THRESHOLD_RANGE

        fun imagePrefsKey(traitId: String, obsUnit: String, rep: String) =
            "canopy_${traitId}_${obsUnit}_${rep}"
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var capturedImageView: ImageView? = null
    private var canopyPreviewView: PreviewView? = null
    private var canopyFrameCard: CardView? = null
    private var embiggenButton: FloatingActionButton? = null
    private var analysisJob: Job? = null
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
    override fun layoutId() = R.layout.trait_canopy_coverage

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

    override fun makeImage(currentTrait: TraitObject) {
        val file = File(context.cacheDir, TEMPORARY_IMAGE_NAME)
        if (!file.exists() || file.length() == 0L) return
        val obsUnit = currentRange.uniqueId
        val rep = collectActivity.rep
        val imageKey = imagePrefsKey(currentTrait.id, obsUnit, rep)
        val previousUri = prefs.getString(imageKey, null)

        analysisJob?.cancel()
        analysisJob = background.launch {
            val bmp = loadAndScale(file, MAX_ANALYSIS_WIDTH) ?: return@launch
            val t = threshold
            val fgcc = analyze(bmp, t)
            val overlay = buildOverlay(bmp, t)
            val fgccStr = "%.1f".format(fgcc)

            val savedUri = saveCanopyImageToStorage(file, currentTrait, obsUnit)
            if (savedUri != null) {
                if (previousUri != null && previousUri != savedUri.toString()) {
                    deleteImageUri(previousUri)
                }
                prefs.edit()
                    .putString(imageKey, savedUri.toString())
                    .apply()
            }

            withContext(Dispatchers.Main) {
                showCapturedImage(overlay)
                updateObservation(currentTrait, fgccStr)
                loadLayout()
            }
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
        analysisJob?.cancel()
        previewLoadJob?.cancel()
    }

    override fun deleteTraitListener() {
        if (isLocked) return

        analysisJob?.cancel()
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
                .setTitle(R.string.canopy_coverage_overwrite_title)
                .setMessage(R.string.canopy_coverage_overwrite_message)
                .setPositiveButton(R.string.canopy_coverage_overwrite_positive) { dialog, _ ->
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
        val traitId = currentTrait.id
        val obsUnit = currentRange.uniqueId
        val uriStr = prefs.getString(imagePrefsKey(traitId, obsUnit, rep), null)
        if (uriStr == null) {
            showLivePreview()
            return
        }

        val uri = Uri.parse(uriStr)
        val thresholdForPreview = threshold
        previewLoadJob = background.launch {
            val bitmap = try {
                loadAndScale(uri, MAX_ANALYSIS_WIDTH)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load canopy image for rep $rep", e)
                null
            }
            val overlay = bitmap?.let { buildOverlay(it, thresholdForPreview) }
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
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

    private fun saveCanopyImageToStorage(tempFile: File, trait: TraitObject, obsUnit: String): Uri? {
        return try {
            val traitName = FileUtil.sanitizeFileName(trait.name)
            val saveTime = FileUtil.sanitizeFileName(OffsetDateTime.now().format(internalTimeFormatter))
            val fileName = "${obsUnit}_${traitName}_${saveTime}.jpg"
            DocumentTreeUtil.getFieldMediaDirectory(context, traitName)?.let { dir ->
                dir.createFile("image/jpeg", fileName)?.also { docFile ->
                    context.contentResolver.openOutputStream(docFile.uri)?.use { out ->
                        tempFile.inputStream().use { input -> input.copyTo(out) }
                    }
                }?.uri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save canopy image to storage", e)
            null
        }
    }

    private fun deleteStoredImageForRep(rep: String) {
        val key = imagePrefsKey(currentTrait.id, currentRange.uniqueId, rep)

        try {
            prefs.getString(key, null)?.let(::deleteImageUri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete canopy image for rep $rep", e)
        } finally {
            prefs.edit().remove(key).apply()
        }
    }

    private fun deleteImageUri(uriString: String) {
        try {
            val uri = Uri.parse(uriString)
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

    // ── Canopeo algorithm ─────────────────────────────────────────────────────

    private fun loadAndScale(file: File, maxWidth: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sampleSize = 1
            while (max(opts.outWidth, opts.outHeight) / sampleSize > maxWidth) sampleSize *= 2
            opts.inSampleSize = sampleSize
            opts.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, opts)?.let {
                applyExifRotation(it, readExifRotation(file))
            }
        } catch (e: Exception) {
            null
        }
    }

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
        } catch (e: Exception) {
            null
        }
    }

    private fun readExifRotation(file: File): Float {
        return try {
            orientationToDegrees(
                ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            )
        } catch (e: Exception) {
            0f
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun analyze(bmp: Bitmap, t: Float): Float {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        var greenCount = 0
        for (pixel in pixels) {
            val r = Color.red(pixel).toFloat()
            val g = Color.green(pixel).toFloat()
            val b = Color.blue(pixel).toFloat()
            if (g > 0f && r / g < t && b / g < t && 2f * g - r - b > P3_THRESHOLD) greenCount++
        }
        return if (pixels.isEmpty()) 0f else greenCount.toFloat() / pixels.size * 100f
    }

    private fun buildOverlay(source: Bitmap, t: Float): Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val greenOverlay = Color.argb(180, 0, 200, 0)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()
            if (g > 0f && r / g < t && b / g < t && 2f * g - r - b > P3_THRESHOLD) {
                pixels[i] = greenOverlay
            }
        }
        val overlay = source.copy(Bitmap.Config.ARGB_8888, true)
        overlay.setPixels(pixels, 0, overlay.width, 0, 0, overlay.width, overlay.height)
        return overlay
    }

    /** Binary mask used by CanopySensitivityParameter for test-capture preview (canopy=WHITE, other=BLACK). */
    fun buildBinaryMask(source: Bitmap, t: Float): Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()
            pixels[i] = if (g > 0f && r / g < t && b / g < t && 2f * g - r - b > P3_THRESHOLD)
                Color.WHITE else Color.BLACK
        }
        val mask = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        mask.setPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        return mask
    }
}
