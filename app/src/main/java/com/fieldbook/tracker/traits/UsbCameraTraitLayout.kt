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
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UsbCameraTraitLayout : CameraTrait, UsbCameraApi.Callbacks {

    companion object {
        const val TAG = "UsbTrait"
        const val type = "usb camera"
    }

    private var surface: Surface? = null
    private var lastBitmap: Bitmap? = null
    private var laidOutPreviewWidth: Int = 0
    private var laidOutPreviewHeight: Int = 0
    private var traitUvcView: UVCCameraTextureView? = null

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

    /** On-screen TextureView inside the preview card (not the off-screen activity one). */
    private fun previewUvc(): UVCCameraTextureView {
        return traitUvcView ?: controller.getUvcView()
    }


    override fun loadLayout() {
        super.loadLayout()
        traitUvcView = activity?.findViewById(R.id.trait_camera_uvc_tv)
            ?: findViewById(R.id.trait_camera_uvc_tv)
        setup()
    }

    override fun onConnected(camera: UVCCamera?, sizes: List<Size>) {
        val maxPixels = 1280 * 720
        val size = sizes.filter { it.width * it.height <= maxPixels }
            .maxByOrNull { it.height * it.width }
            ?: sizes.minByOrNull { it.height * it.width }
            ?: Size(0, 0, 0, UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT)
        // Layout first, then bind after the TextureView has a SurfaceTexture.
        updatePreviewSize(size.width, size.height)
        startCaptureUi()
        previewUvc().post {
            if (controller.getUsbApi().isConnected()) {
                setCameraPreviewSize(camera ?: controller.getUsbApi().camera, size)
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

        previewCardView?.visibility = if (prefs.getBoolean(GeneralKeys.USB_CAMERA_PREVIEW, true))
            VISIBLE else GONE

        val sizes = controller.getUsbApi().supportedSizes?.distinct()
        val maxSize = sizes?.maxByOrNull { it.height * it.width }?.let { sizes.indexOf(it) } ?: 0
        val resIndex = prefs.getInt(GeneralKeys.USB_CAMERA_RESOLUTION_INDEX, maxSize)

        val size = sizes?.get(resIndex) ?: Size(
            0, 0, 0,
            1920,
            1080
        )

        controller.getUsbApi().camera?.autoFocus =
            prefs.getBoolean(GeneralKeys.USB_CAMERA_AUTO_FOCUS, true)
        controller.getUsbApi().camera?.autoWhiteBlance =
            prefs.getBoolean(GeneralKeys.USB_CAMERA_AUTO_WHITE_BALANCE, true)
        controller.getUsbApi().camera?.updateCameraParams()

        updatePreviewSize(size.width, size.height)

    }

    private fun setup() {

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

    private fun initUi() {
        try {
            previewUvc().visibility = GONE
        } catch (_: Exception) {
        }
        // Hide visible preview only
        lastBitmap = null
        laidOutPreviewWidth = 0
        laidOutPreviewHeight = 0
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
        val texture = previewUvc().surfaceTexture
        if (texture == null) {
            previewUvc().post {
                if (controller.getUsbApi().isConnected()) {
                    setCameraPreviewSize(camera ?: controller.getUsbApi().camera, size)
                }
            }
            return
        }
        try {
            surface?.release()
        } catch (_: Exception) {
        }
        surface = Surface(texture)
        camera?.setPreviewSize(size.width, size.height)
        camera?.setPreviewDisplay(surface)
        camera?.startPreview()
    }

    private fun updatePreviewSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == laidOutPreviewWidth && height == laidOutPreviewHeight) return
        laidOutPreviewWidth = width
        laidOutPreviewHeight = height

        ui.launch {
            previewUvc().aspectRatio = width / height.toDouble()
        }
    }

    private fun startCaptureUi() {

        ui.launch {

            shutterButton?.visibility = VISIBLE
            connectBtn?.visibility = INVISIBLE
            settingsButton?.visibility = VISIBLE
            previewCardView?.visibility = VISIBLE
            imageView?.visibility = GONE
            previewUvc().visibility = VISIBLE
            setupPreviewAndCaptureButtons()

            shutterButton?.setOnClickListener {

                shutterButton?.isEnabled = false

                val bmp = try {
                    previewUvc().bitmap
                } catch (_: Exception) {
                    lastBitmap
                }

                if (bmp != null) {
                    lastBitmap = bmp
                    controller.getSoundHelper().playShutter()
                    saveBitmapToStorage(bmp, currentRange, currentTrait)
                }

                activity?.runOnUiThread {
                    shutterButton?.isEnabled = true
                }
            }
        }
    }

    private fun setupPreviewAndCaptureButtons() {

        settingsButton?.visibility = VISIBLE
        shutterButton?.visibility = VISIBLE

    }
}