package com.fieldbook.tracker.traits.formats.parameters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.exifinterface.media.ExifInterface
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.provider.GenericFileProvider
import com.fieldbook.tracker.traits.CanopyCoverTraitLayout
import com.fieldbook.tracker.traits.formats.ValidationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max

class CanopySensitivityParameter : BaseFormatParameter(
    nameStringResourceId = R.string.trait_canopy_sensitivity_label,
    defaultLayoutId = R.layout.list_item_trait_parameter_canopy_sensitivity,
    parameter = Parameters.CANOPY_SENSITIVITY
) {

    companion object {
        private const val TEST_TEMP_FILE = "canopy_param_test.jpg"

        // matches CanopyCoverTraitLayout's analysis width so the test output value is representative
        private const val MAX_ANALYSIS_WIDTH = 1000
    }

    private var activity: android.app.Activity? = null

    fun setActivity(activity: android.app.Activity) {
        this.activity = activity
    }

    override fun createViewHolder(parent: ViewGroup): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_canopy_sensitivity, parent, false)
        return ViewHolder(v)
    }

    inner class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        private val seekBar: SeekBar = itemView.findViewById(R.id.canopy_param_seekbar)
        private val thresholdTv: TextView = itemView.findViewById(R.id.canopy_param_threshold_tv)
        private val outputTv: TextView = itemView.findViewById(R.id.canopy_param_output_tv)
        private val defaultMark: View = itemView.findViewById(R.id.canopy_param_default_mark)
        private val testBtn: Button = itemView.findViewById(R.id.canopy_param_test_btn)
        private val lockedTv: TextView = itemView.findViewById(R.id.canopy_param_locked_tv)
        val previewIv: ImageView = itemView.findViewById(R.id.canopy_param_preview_iv)
        private var testBitmap: Bitmap? = null
        private var previewJob: Job? = null
        private var previewVersion = 0
        private var isLocked = false
        private var testCaptureLauncher: ActivityResultLauncher<Uri>? = null

        override fun bind(parameter: BaseFormatParameter, initialTraitObject: TraitObject?) {
            super.bind(parameter, initialTraitObject)
            val progress = initialTraitObject?.sensitivity?.toIntOrNull()
                ?: CanopyCoverTraitLayout.DEFAULT_SLIDER_PROGRESS
            setupSeekBar(progress)
            testBtn.setOnClickListener {
                if (isLocked) return@setOnClickListener
                testCaptureLauncher?.let { launcher ->
                    launchTestCapture(launcher)
                }
            }
        }

        fun setTestCaptureLauncher(launcher: ActivityResultLauncher<Uri>) {
            testCaptureLauncher = launcher
        }

        fun setInlineTestVisible(visible: Boolean) {
            testBtn.visibility = if (visible && !isLocked) View.VISIBLE else View.GONE
        }

        /**
         * The threshold is applied when redrawing stored captures as well as when analyzing new
         * ones, so it can no longer be edited once the trait has observations.
         */
        fun setLocked() {
            isLocked = true
            seekBar.isEnabled = false
            testBtn.visibility = View.GONE
            lockedTv.visibility = View.VISIBLE
        }

        fun configureStandaloneDialog() {
            itemView.findViewById<TextView>(R.id.list_item_trait_parameter_title)?.visibility = View.GONE
            setInlineTestVisible(false)
        }

        private fun setupSeekBar(initial: Int) {
            seekBar.max = 100
            seekBar.progress = initial
            updateLabel(initial)

            seekBar.post {
                val frac = CanopyCoverTraitLayout.DEFAULT_SLIDER_PROGRESS / 100f
                val trackWidth = (seekBar.width - seekBar.paddingStart - seekBar.paddingEnd).toFloat()
                defaultMark.translationX =
                    seekBar.paddingStart + frac * trackWidth - defaultMark.width / 2f
                defaultMark.visibility = View.VISIBLE
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser && abs(progress - CanopyCoverTraitLayout.DEFAULT_SLIDER_PROGRESS) <= 2) {
                        sb.progress = CanopyCoverTraitLayout.DEFAULT_SLIDER_PROGRESS
                    }
                    updateLabel(sb.progress)
                    updateTestPreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        private fun updateLabel(progress: Int) {
            thresholdTv.text = itemView.context.getString(
                R.string.canopy_param_threshold_format,
                CanopyCoverTraitLayout.sliderToThreshold(progress)
            )
        }

        fun onTestCaptureResult() {
            val act = activity ?: return
            val tempFile = File(act.cacheDir, TEST_TEMP_FILE)
            if (!tempFile.exists() || tempFile.length() == 0L) return
            CoroutineScope(Dispatchers.IO).launch {
                val raw = decodeScaledAndRotated(tempFile) ?: return@launch
                withContext(Dispatchers.Main) {
                    testBitmap = raw
                    updateTestPreview()
                }
            }
        }

        /**
         * Full resolution captures are too large to threshold on every seek bar change, so decode
         * at the same scale the trait layout analyzes at.
         */
        private fun decodeScaledAndRotated(file: File): Bitmap? = try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sampleSize = 1
            while (max(opts.outWidth, opts.outHeight) / sampleSize > MAX_ANALYSIS_WIDTH) {
                sampleSize *= 2
            }
            opts.inSampleSize = sampleSize
            opts.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, opts)?.let { bmp ->
                applyRotation(bmp, readExifRotation(file))
            }
        } catch (_: Exception) {
            null
        }

        private fun readExifRotation(file: File): Float = try {
            when (ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        }

        private fun applyRotation(bitmap: Bitmap, degrees: Float): Bitmap {
            if (degrees == 0f) return bitmap
            return try {
                val matrix = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    .also { if (it != bitmap) bitmap.recycle() }
            } catch (_: Exception) {
                bitmap
            }
        }

        /** Convenience method for the neutral button in standalone dialog (TraitActivity). */
        fun launchTestCapture() {
            testCaptureLauncher?.let { launcher ->
                launchTestCapture(launcher)
            }
        }

        private fun launchTestCapture(launcher: ActivityResultLauncher<Uri>) {
            val act = activity ?: return
            val tempFile = File(act.cacheDir, TEST_TEMP_FILE)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            tempFile.createNewFile()
            val uri = GenericFileProvider.getUriForFile(act, GenericFileProvider.AUTHORITY, tempFile)
            launcher.launch(uri)
        }

        private fun updateTestPreview() {
            val raw = testBitmap ?: return
            val t = CanopyCoverTraitLayout.sliderToThreshold(seekBar.progress)
            val version = ++previewVersion
            previewJob?.cancel()
            previewJob = CoroutineScope(Dispatchers.IO).launch {
                val (mask, outputValue) = buildBinaryMaskAndOutput(raw, t)
                withContext(Dispatchers.Main) {
                    if (version != previewVersion) return@withContext
                    outputTv.text = itemView.context.getString(
                        R.string.canopy_param_output_format,
                        outputValue
                    )
                    outputTv.visibility = View.VISIBLE
                    previewIv.setImageBitmap(mask)
                    previewIv.visibility = View.VISIBLE
                }
            }
        }

        private fun buildBinaryMaskAndOutput(source: Bitmap, t: Float): Pair<Bitmap, Float> {
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            var canopyPixels = 0
            for (i in pixels.indices) {
                val r = android.graphics.Color.red(pixels[i]).toFloat()
                val g = android.graphics.Color.green(pixels[i]).toFloat()
                val b = android.graphics.Color.blue(pixels[i]).toFloat()
                val isCanopy = g > 0f && r / g < t && b / g < t && 2f * g - r - b > CanopyCoverTraitLayout.P3_THRESHOLD
                if (isCanopy) canopyPixels++
                pixels[i] = if (isCanopy) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            }
            val mask = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            mask.setPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
            val outputValue = if (pixels.isEmpty()) 0f else canopyPixels.toFloat() / pixels.size * 100f
            return mask to outputValue
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            sensitivity = seekBar.progress.toString()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            val progress = traitObject?.sensitivity?.toIntOrNull()
                ?: CanopyCoverTraitLayout.DEFAULT_SLIDER_PROGRESS
            seekBar.progress = progress
            updateLabel(progress)
            return true
        }

        override fun validate(
            traitRepo: TraitRepository,
            initialTraitObject: TraitObject?
        ) = ValidationResult()
    }
}
