package com.fieldbook.tracker.traits

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.Surface
import android.widget.ImageView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.R
import com.fieldbook.tracker.devices.camera.UsbCameraApi
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.views.UsbCameraTraitSettingsView
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera
import com.serenegiant.widget.CameraViewInterface
import com.serenegiant.widget.UVCCameraTextureView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class UsbCameraTraitLayout : CameraTrait, UsbCameraApi.Callbacks {

    companion object {
        const val TAG = "UsbTrait"
        const val type = "usb camera"

        /** Cap live preview bandwidth; still capture uses the selected resolution. */
        private const val MAX_PREVIEW_PIXELS = 1280 * 720
    }

    private var surface: Surface? = null
    private var lastBitmap: Bitmap? = null
    private var laidOutPreviewWidth: Int = 0
    private var laidOutPreviewHeight: Int = 0
    private var traitUvcView: UVCCameraTextureView? = null

    /** Last TextureView we bound preview to; identity changes after trait re-inflation. */
    private var boundUvcView: UVCCameraTextureView? = null
    private var capturing = false

    /** True after leaving the trait or getting a new TextureView; forces stop+rebind. */
    private var needsPreviewRebind = false

    /** Last view used as the UVC stream sink (on-screen or off-screen). */
    private var boundStreamSink: UVCCameraTextureView? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun type(): String {
        return type
    }

    /** Sink for the UVC stream. Off-screen activity view when UI preview is disabled. */
    private fun previewUvc(): UVCCameraTextureView {
        if (!prefs.getBoolean(GeneralKeys.USB_CAMERA_PREVIEW, true)) {
            return controller.getUvcView()
        }
        return traitUvcView ?: controller.getUvcView()
    }


    override fun loadLayout() {
        super.loadLayout()
        traitUvcView = activity?.findViewById(R.id.trait_camera_uvc_tv)
            ?: findViewById(R.id.trait_camera_uvc_tv)
        setup()
    }

    /**
     * TraitBoxView re-inflates when switching formats, destroying the TextureView.
     * Keep the USB session, but drop the dead Surface so attach can rebind on return.
     */
    override fun onExit() {
        releasePreviewSurface()
        traitUvcView = null
        boundUvcView = null
        boundStreamSink = null
        laidOutPreviewWidth = 0
        laidOutPreviewHeight = 0
        needsPreviewRebind = true
        super.onExit()
    }

    override fun onConnected(camera: UVCCamera?, sizes: List<Size>) {
        val size = choosePreviewStreamSize(sizes)
        // Layout first, then bind after the TextureView has a SurfaceTexture.
        updatePreviewSize(size.width, size.height)
        startCaptureUi()
        previewUvc().post {
            if (controller.getUsbApi().isConnected()) {
                setCameraPreviewSize(camera ?: controller.getUsbApi().camera, size)
                applyCameraControls(camera ?: controller.getUsbApi().camera)
            }
        }
    }

    override fun onDisconnected() {
        ui.launch {
            initUi()
        }
    }

    override fun getUsbCameraInterface(): CameraViewInterface {
        return previewUvc()
    }

    override fun getPreviewView(): ImageView? {
        return imageView
    }

    override fun onSettingsChanged() {
        // After unplug, never re-show the preview card (frame callbacks used to).
        if (!controller.getUsbApi().isConnected()) {
            previewCardView?.visibility = GONE
            imageView?.visibility = GONE
            return
        }

        val showPreview = prefs.getBoolean(GeneralKeys.USB_CAMERA_PREVIEW, true)
        previewCardView?.visibility = if (showPreview) VISIBLE else GONE
        // Only hide the on-screen view; stream may be bound to the off-screen sink.
        traitUvcView?.visibility = if (showPreview) VISIBLE else GONE

        val sizes = controller.getUsbApi().supportedSizes?.distinct() ?: emptyList()
        val previewSize = choosePreviewStreamSize(sizes)

        applyCameraControls(controller.getUsbApi().camera)

        val sink = previewUvc()
        if (sink !== boundStreamSink) {
            needsPreviewRebind = true
            // Force aspectRatio onto the new sink (cached size was for the old one).
            laidOutPreviewWidth = 0
            laidOutPreviewHeight = 0
        }

        // Live preview stays bandwidth-safe; shutter captures at selectedCaptureSize().
        // Always keep the stream running so captureStill works with preview UI off.
        updatePreviewSize(previewSize.width, previewSize.height)
        if (!capturing) {
            bindPreviewWhenReady(sink, previewSize)
        }
    }

    /** Push AF/AWB prefs into the open UVCCamera (native setters). */
    private fun applyCameraControls(camera: UVCCamera?) {
        if (camera == null) return
        camera.updateCameraParams()
        camera.autoFocus = prefs.getBoolean(GeneralKeys.USB_CAMERA_AUTO_FOCUS, true)
        // Library method is misspelled "Blance" — matches UVCCamera JNI.
        camera.autoWhiteBlance = prefs.getBoolean(GeneralKeys.USB_CAMERA_AUTO_WHITE_BALANCE, true)
    }

    /**
     * After GONE to VISIBLE the on-screen TextureView has no SurfaceTexture until laid out.
     * Retry a few frames so enabling preview in settings rebinds without relaunching.
     */
    private fun bindPreviewWhenReady(
        sink: UVCCameraTextureView,
        size: Size,
        retries: Int = 15
    ) {
        sink.post {
            if (!controller.getUsbApi().isConnected() || capturing) return@post
            if (sink.isAvailable && sink.surfaceTexture != null) {
                setCameraPreviewSize(controller.getUsbApi().camera, size)
            } else if (retries > 0) {
                sink.requestLayout()
                previewCardView?.requestLayout()
                bindPreviewWhenReady(sink, size, retries - 1)
            }
        }
    }

    private fun setup() {
        // LayoutCollections keeps this instance across trait switches, but inflateTrait
        // creates a new TextureView. Only then reset size cache so preview can re-layout
        // and rebind. Plot navigation keeps the same view and should not restart preview.
        val preview = traitUvcView
        if (preview != null && preview !== boundUvcView) {
            releasePreviewSurface()
            laidOutPreviewWidth = 0
            laidOutPreviewHeight = 0
            needsPreviewRebind = true
            boundUvcView = preview
        }

        imageView?.visibility = VISIBLE

        previewCardView?.layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToBottom = recyclerView?.id ?: ConstraintLayout.LayoutParams.PARENT_ID
        }

        if (controller.getUsbApi().isConnected()) {

            startCaptureUi()

        } else initUi()

        controller.getUsbApi().attach(this)

        settingsButton?.setOnClickListener {

            showResolutionChoiceDialog()
        }

        onSettingsChanged()
    }

    private fun releasePreviewSurface() {
        try {
            surface?.release()
        } catch (_: Exception) {
        }
        surface = null
    }

    private fun initUi() {
        try {
            traitUvcView?.visibility = GONE
        } catch (_: Exception) {
        }
        // Hide visible preview only
        lastBitmap = null
        laidOutPreviewWidth = 0
        laidOutPreviewHeight = 0
        capturing = false
        imageView?.setImageDrawable(null)
        imageView?.visibility = GONE

        shutterButton?.visibility = INVISIBLE

        settingsButton?.visibility = INVISIBLE

        previewCardView?.visibility = GONE

        connectBtn?.visibility = VISIBLE

        connectBtn?.setOnClickListener {

            Toast.makeText(context, R.string.usb_camera_plug_in_camera, Toast.LENGTH_SHORT).show()

        }
    }

    private fun showResolutionChoiceDialog() {

        val supportedSizes =
            controller.getUsbApi().supportedSizes?.map { android.util.Size(it.width, it.height) }
                ?.distinct()
        val initialMaxIndexSelected =
            supportedSizes?.maxByOrNull { it.height * it.width }?.let { supportedSizes.indexOf(it) }
                ?: 0
        val settingsView =
            UsbCameraTraitSettingsView(context, supportedSizes ?: listOf(), initialMaxIndexSelected)

        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.trait_usb_photo_settings_title)
            .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                settingsView.commitChanges()
                onSettingsChanged()
                dialog.dismiss()
            }
            .setView(settingsView)
            .show()
    }

    private fun setCameraPreviewSize(camera: UVCCamera?, size: Size) {
        val sink = previewUvc()
        val texture = sink.surfaceTexture
        if (texture == null) {
            sink.post {
                if (controller.getUsbApi().isConnected()) {
                    setCameraPreviewSize(camera ?: controller.getUsbApi().camera, size)
                }
            }
            return
        }
        releasePreviewSurface()
        surface = Surface(texture)
        if (needsPreviewRebind || sink !== boundStreamSink) {
            try {
                // Previous TextureView was destroyed or stream sink changed (preview on/off).
                camera?.stopPreview()
            } catch (_: Exception) {
            }
            needsPreviewRebind = false
        }
        camera?.setPreviewSize(size.width, size.height)
        camera?.setPreviewDisplay(surface)
        camera?.startPreview()
        boundStreamSink = sink
    }

    private fun updatePreviewSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == laidOutPreviewWidth && height == laidOutPreviewHeight) return
        laidOutPreviewWidth = width
        laidOutPreviewHeight = height

        // Apply on the current sink immediately so WRAP_CONTENT can measure this frame.
        try {
            previewUvc().aspectRatio = width / height.toDouble()
        } catch (_: Exception) {
            ui.launch {
                previewUvc().aspectRatio = width / height.toDouble()
            }
        }
    }

    private fun startCaptureUi() {

        ui.launch {

            shutterButton?.visibility = VISIBLE
            connectBtn?.visibility = INVISIBLE
            settingsButton?.visibility = VISIBLE
            val showPreview = prefs.getBoolean(GeneralKeys.USB_CAMERA_PREVIEW, true)
            previewCardView?.visibility = if (showPreview) VISIBLE else GONE
            imageView?.visibility = GONE
            // Never GONE the stream sink captureStill needs an active preview surface.
            traitUvcView?.visibility = if (showPreview) VISIBLE else GONE
            setupPreviewAndCaptureButtons()

            shutterButton?.setOnClickListener {

                if (capturing) return@setOnClickListener
                shutterButton?.isEnabled = false
                capturing = true

                val captureSize = selectedCaptureSize()
                val previewSize = choosePreviewStreamSize(
                    controller.getUsbApi().supportedSizes?.distinct() ?: listOf(captureSize)
                )

                ui.launch {
                    val bmp = withContext(Dispatchers.IO) {
                        controller.getUsbApi().captureStill(captureSize)
                    }

                    if (bmp != null) {
                        lastBitmap = bmp
                        controller.getSoundHelper().playShutter()
                        saveBitmapToStorage(bmp, currentRange, currentTrait)
                    }

                    if (controller.getUsbApi().isConnected()) {
                        setCameraPreviewSize(controller.getUsbApi().camera, previewSize)
                    }

                    capturing = false
                    shutterButton?.isEnabled = true
                }
            }
        }
    }

    private fun choosePreviewStreamSize(sizes: List<Size>): Size {
        if (sizes.isEmpty()) {
            return Size(
                0, 0, 0,
                UVCCamera.DEFAULT_PREVIEW_WIDTH,
                UVCCamera.DEFAULT_PREVIEW_HEIGHT
            )
        }
        return sizes.filter { it.width * it.height <= MAX_PREVIEW_PIXELS }
            .maxByOrNull { it.height * it.width }
            ?: sizes.minByOrNull { it.height * it.width }
            ?: sizes.first()
    }

    private fun selectedCaptureSize(): Size {
        val sizes = controller.getUsbApi().supportedSizes?.distinct()
            ?: controller.getUsbApi().camera?.supportedSizeList?.distinct()
            ?: emptyList()
        if (sizes.isEmpty()) {
            return Size(
                0, 0, 0,
                UVCCamera.DEFAULT_PREVIEW_WIDTH,
                UVCCamera.DEFAULT_PREVIEW_HEIGHT
            )
        }
        val maxIndex = sizes.maxByOrNull { it.height * it.width }?.let { sizes.indexOf(it) } ?: 0
        val resIndex = prefs.getInt(GeneralKeys.USB_CAMERA_RESOLUTION_INDEX, maxIndex)
            .coerceIn(0, sizes.lastIndex)
        return sizes[resIndex]
    }

    private fun setupPreviewAndCaptureButtons() {

        settingsButton?.visibility = VISIBLE
        shutterButton?.visibility = VISIBLE

    }
}