package com.fieldbook.tracker.utilities.camera

import android.graphics.Color
import android.graphics.Typeface
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraState
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Zoom stops for the built-in camera's zoom chip.
 *
 * Instead of continuous zoom, the chip cycles through a few stops that suit the device:
 * the ultrawide (when the camera reaches it through a zoom ratio below 1), 1x, 2x,
 * and any optical telephoto lenses.
 */
@OptIn(ExperimentalCamera2Interop::class)
object CameraZoom {

    private const val TAG = "CameraZoom"

    // stops closer together than this fraction are treated as the same stop
    private const val MIN_STOP_SPACING = 0.15f

    // physical lenses magnifying less than this are not offered as telephoto stops
    private const val MIN_TELEPHOTO_RATIO = 1.5f

    /**
     * The zoom ratios offered for [cameraInfo], sorted from widest to tightest.
     */
    fun stops(cameraInfo: CameraInfo): List<Float> {

        val zoom = cameraInfo.zoomState.value ?: return listOf(1f)

        val min = zoom.minZoomRatio
        val max = zoom.maxZoomRatio

        val candidates = buildList {
            if (min * (1 + MIN_STOP_SPACING) < 1f) add(min)
            add(1f)
            if (max >= 2f) add(2f)
            addAll(telephotoStops(cameraInfo, min).filter { it <= max })
        }.sorted()

        return candidates.fold(mutableListOf<Float>()) { kept, stop ->
            val last = kept.lastOrNull()
            if (last == null || stop > last * (1 + MIN_STOP_SPACING)) kept.add(stop)
            kept
        }
    }

    /**
     * Magnification of each telephoto lens behind a logical multi-camera, relative to the 1x lens,
     * rounded to the nearest half stop (e.g. 5x).
     *
     * The widest physical lens is the one reached at [minZoomRatio], which gives the 1x lens's
     * magnification without relying on the order of the logical camera's focal lengths.
     */
    private fun telephotoStops(cameraInfo: CameraInfo, minZoomRatio: Float): List<Float> = try {

        val lenses = cameraInfo.physicalCameraInfos.mapNotNull { magnification(it) }

        val widest = lenses.minOrNull()

        if (widest == null) emptyList() else {

            val main = widest / minOf(minZoomRatio, 1f)

            lenses.map { it / main }
                .filter { it > MIN_TELEPHOTO_RATIO }
                .map { (it * 2).roundToInt() / 2f }
        }

    } catch (e: Exception) {
        Log.w(TAG, "Unable to read physical camera lenses", e)
        emptyList()
    }

    // focal length relative to sensor width, which is proportional to how much a lens magnifies
    private fun magnification(cameraInfo: CameraInfo): Float? {

        val info = Camera2CameraInfo.from(cameraInfo)

        val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull() ?: return null

        val width = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            ?.width ?: return null

        return if (focal > 0f && width > 0f) focal / width else null
    }

    /**
     * The stop in [stops] closest to [ratio], so a ratio saved on another device or lens still maps to a stop here.
     */
    fun nearest(stops: List<Float>, ratio: Float): Float =
        stops.minByOrNull { abs(ln(it / ratio)) } ?: 1f

    /**
     * Formats a zoom ratio for the chip, e.g. "0.7x", "1x" or "2x".
     */
    fun label(ratio: Float): String {
        val rounded = (ratio * 10).roundToInt() / 10f
        return if (rounded % 1f == 0f) "${rounded.toInt()}x" else "${rounded}x"
    }

    fun savedRatio(trait: TraitObject?): Float =
        trait?.cameraZoomRatio?.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
}

/**
 * A view that shows the zoom stops and lets the user pick one.
 */
interface ZoomSelector {

    /** Shows [stops] with [selected] highlighted; [onSelect] is called with the stop the user picks. */
    fun show(stops: List<Float>, selected: Float, onSelect: (Float) -> Unit)

    fun hide()
}

/**
 * A single chip showing the current stop; each tap moves to the next stop. Suits small previews.
 */
class ZoomChip(private val chip: TextView) : ZoomSelector {

    override fun show(stops: List<Float>, selected: Float, onSelect: (Float) -> Unit) {

        val label = CameraZoom.label(selected)

        chip.text = label
        chip.contentDescription = chip.context.getString(R.string.camera_zoom_content_description, label)
        chip.visibility = View.VISIBLE

        chip.setOnClickListener {
            onSelect(stops[(stops.indexOf(selected) + 1) % stops.size])
        }
    }

    override fun hide() {
        chip.setOnClickListener(null)
        chip.visibility = View.GONE
    }
}

/**
 * A pill listing every stop with the current one highlighted, like the system camera app.
 * Unselected stops drop the "x" (e.g. "0.7", "1x", "2") to keep the pill compact.
 */
class ZoomBar(private val bar: LinearLayout) : ZoomSelector {

    override fun show(stops: List<Float>, selected: Float, onSelect: (Float) -> Unit) {

        val context = bar.context
        val density = context.resources.displayMetrics.density

        bar.removeAllViews()

        stops.forEach { stop ->

            val current = stop == selected
            val label = CameraZoom.label(stop)

            bar.addView(TextView(context).apply {
                text = if (current) label else label.removeSuffix("x")
                contentDescription = context.getString(R.string.camera_zoom_option_content_description, label)
                isSelected = current
                gravity = Gravity.CENTER
                minWidth = (44 * density).toInt()
                minHeight = (36 * density).toInt()
                setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (current) 14f else 12f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (current) Color.BLACK else Color.WHITE)
                if (current) setBackgroundResource(R.drawable.camera_zoom_segment_selected)
                setOnClickListener { if (!current) onSelect(stop) }
            })
        }

        bar.visibility = View.VISIBLE
    }

    override fun hide() {
        bar.removeAllViews()
        bar.visibility = View.GONE
    }
}

/**
 * Keeps the bound camera at the trait's saved zoom stop and drives the optional [ZoomSelector].
 *
 * CameraX resets zoom whenever the camera closes, and ignores zoom requests made before the camera
 * is active, so the stop is applied each time the camera opens rather than once at bind time.
 */
class CameraZoomController(private val owner: LifecycleOwner) {

    private var camera: Camera? = null
    private var selector: ZoomSelector? = null
    private var observed: LiveData<CameraState>? = null

    private var stops: List<Float> = listOf(1f)
    private var ratio = 1f

    private val observer = Observer<CameraState> { state ->
        if (state.type == CameraState.Type.OPEN) camera?.cameraControl?.setZoomRatio(ratio)
    }

    /**
     * Applies [trait]'s saved zoom to [camera]. When [selector] is given, it shows the stops and a
     * picked stop is applied and saved to [trait] (nothing is saved when [trait] is null).
     */
    fun bind(camera: Camera, trait: TraitObject?, selector: ZoomSelector? = null) {

        release()

        this.camera = camera
        this.selector = selector

        stops = CameraZoom.stops(camera.cameraInfo)
        ratio = CameraZoom.nearest(stops, CameraZoom.savedRatio(trait))

        // delivers the current state immediately, so an already open camera is zoomed right away
        observed = camera.cameraInfo.cameraState.also { it.observe(owner, observer) }

        if (selector == null) return

        if (stops.size < 2) {
            selector.hide()
            return
        }

        fun show() {
            selector.show(stops, ratio) { stop ->

                ratio = stop

                camera.cameraControl.setZoomRatio(ratio)

                show()

                trait?.let {
                    it.cameraZoomRatio = ratio.toString()
                    it.saveAttributeValues()
                }
            }
        }

        show()
    }

    /**
     * Stops tracking the current camera and hides its selector.
     */
    fun release() {

        observed?.removeObserver(observer)
        observed = null

        camera = null

        selector?.hide()
        selector = null
    }
}
