package com.fieldbook.tracker.traits.formats.parameters

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.provider.GenericFileProvider
import com.fieldbook.tracker.traits.CanopyCoverageTraitLayout
import com.fieldbook.tracker.traits.formats.ValidationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class CanopySensitivityParameter : BaseFormatParameter(
    nameStringResourceId = R.string.trait_canopy_sensitivity_label,
    defaultLayoutId = R.layout.list_item_trait_parameter_canopy_sensitivity,
    parameter = Parameters.CANOPY_SENSITIVITY
) {

    companion object {
        const val TEST_CAPTURE_REQUEST_CODE = 276
        private const val TEST_TEMP_FILE = "canopy_param_test.jpg"
    }

    private var activity: Activity? = null

    fun setActivity(activity: Activity) {
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
        val previewIv: ImageView = itemView.findViewById(R.id.canopy_param_preview_iv)
        private var testBitmap: Bitmap? = null
        private var previewJob: Job? = null
        private var previewVersion = 0

        override fun bind(parameter: BaseFormatParameter, initialTraitObject: TraitObject?) {
            super.bind(parameter, initialTraitObject)
            val progress = initialTraitObject?.sensitivity?.toIntOrNull()
                ?: CanopyCoverageTraitLayout.DEFAULT_SLIDER_PROGRESS
            setupSeekBar(progress)
            testBtn.setOnClickListener { launchTestCapture() }
        }

        fun setInlineTestVisible(visible: Boolean) {
            testBtn.visibility = if (visible) View.VISIBLE else View.GONE
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
                val frac = CanopyCoverageTraitLayout.DEFAULT_SLIDER_PROGRESS / 100f
                val trackWidth = (seekBar.width - seekBar.paddingStart - seekBar.paddingEnd).toFloat()
                defaultMark.translationX =
                    seekBar.paddingStart + frac * trackWidth - defaultMark.width / 2f
                defaultMark.visibility = View.VISIBLE
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser && abs(progress - CanopyCoverageTraitLayout.DEFAULT_SLIDER_PROGRESS) <= 2) {
                        sb.progress = CanopyCoverageTraitLayout.DEFAULT_SLIDER_PROGRESS
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
                CanopyCoverageTraitLayout.sliderToThreshold(progress)
            )
        }

        fun launchTestCapture() {
            val act = activity ?: return
            val tempFile = File(act.cacheDir, TEST_TEMP_FILE)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            tempFile.createNewFile()
            val uri = GenericFileProvider.getUriForFile(act, GenericFileProvider.AUTHORITY, tempFile)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                clipData = ClipData.newUri(act.contentResolver, "Canopy sensitivity test", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            act.startActivityForResult(intent, TEST_CAPTURE_REQUEST_CODE)
        }

        fun onTestCaptureResult() {
            val act = activity ?: return
            val tempFile = File(act.cacheDir, TEST_TEMP_FILE)
            if (!tempFile.exists() || tempFile.length() == 0L) return
            CoroutineScope(Dispatchers.IO).launch {
                val raw = BitmapFactory.decodeFile(tempFile.absolutePath) ?: return@launch
                withContext(Dispatchers.Main) {
                    testBitmap = raw
                    updateTestPreview()
                }
            }
        }

        private fun updateTestPreview() {
            val raw = testBitmap ?: return
            val t = CanopyCoverageTraitLayout.sliderToThreshold(seekBar.progress)
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
                val isCanopy = g > 0f && r / g < t && b / g < t && 2f * g - r - b > 20f
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
                ?: CanopyCoverageTraitLayout.DEFAULT_SLIDER_PROGRESS
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
