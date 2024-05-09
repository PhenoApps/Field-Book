package com.fieldbook.tracker.traits

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.View
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
import org.phenoapps.interfaces.usb.camera.CameraSurfaceListener

@AndroidEntryPoint
class UsbCameraTraitLayout : CameraTrait, UsbCameraApi.Callbacks {

    companion object {
        const val TAG = "UsbTrait"
        const val type = "usb camera"
    }

    private var surface: Surface? = null
    private var lastBitmap: Bitmap? = null

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

    override fun loadLayout() {
        super.loadLayout()
        setup()
    }

    override fun onConnected(camera: UVCCamera?, sizes: List<Size>) {

        sizes.maxByOrNull { it.height * it.width }?.let { size ->
            setCameraPreviewSize(camera, size)
            updatePreviewSize(size.width, size.height)
        }

        startCaptureUi()
    }

    override fun onDisconnected() {
        ui.launch {
            initUi()
        }
    }

    override fun getUsbCameraInterface(): CameraViewInterface {
        return controller.getUvcView()
    }

    override fun getPreviewView(): ImageView? {
        return imageView
    }

    override fun onSettingsChanged() {

        imageView?.visibility = if (prefs.getBoolean(GeneralKeys.USB_CAMERA_PREVIEW, true))
            View.VISIBLE else View.GONE

        val camera = controller.getUsbApi().camera
        val sizes = camera?.supportedSizeList ?: listOf()
        val resIndex = prefs.getInt(GeneralKeys.USB_CAMERA_RESOLUTION_INDEX, 0)

        camera?.autoFocus = prefs.getBoolean(GeneralKeys.USB_CAMERA_AUTO_FOCUS, true)
        camera?.autoWhiteBlance = prefs.getBoolean(GeneralKeys.USB_CAMERA_AUTO_WHITE_BALANCE, true)
        camera?.updateCameraParams()

        if (sizes.isNotEmpty() && resIndex in sizes.indices) {
            val size = sizes[resIndex] ?: Size(0,0,0,
                1920,
                1080
            )
            updatePreviewSize(size.width, size.height)
        }
    }

    private fun setup() {

        imageView?.visibility = View.VISIBLE

        (imageView?.layoutParams as ConstraintLayout.LayoutParams)
            .width = ConstraintLayout.LayoutParams.WRAP_CONTENT

        (captureBtn?.layoutParams as ConstraintLayout.LayoutParams)
            .topToBottom = imageView?.id ?: ConstraintLayout.LayoutParams.PARENT_ID

        if (controller.getUsbApi().isConnected()) {
            
            startCaptureUi()

        } else initUi()

        controller.getUsbApi().attach(this)

        settingsBtn?.setOnClickListener {

            showResolutionChoiceDialog()
        }

        onSettingsChanged()
    }

    private fun initUi() {

        captureBtn?.visibility = View.INVISIBLE

        settingsBtn?.visibility = View.INVISIBLE

        connectBtn?.visibility = View.VISIBLE

        connectBtn?.setOnClickListener {

            Toast.makeText(context, R.string.usb_camera_plug_in_camera, Toast.LENGTH_SHORT).show()

        }
    }

    private fun showResolutionChoiceDialog() {
        val supportedSizes = controller.getUsbApi().supportedSizes?.map { android.util.Size(it.width, it.height) }

        AlertDialog.Builder(context)
            .setTitle(R.string.trait_system_photo_settings_title)
            .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                onSettingsChanged()
                dialog.dismiss()
            }
            .setView(UsbCameraTraitSettingsView(context, supportedSizes ?: listOf()))
            .show()
    }

    private fun setCameraPreviewSize(camera: UVCCamera?, size: Size) {
        surface = Surface(controller.getUvcView().surfaceTexture)

        camera?.setPreviewSize(size.width, size.height)
        camera?.setPreviewDisplay(surface)
        camera?.startPreview()
    }

    private fun updatePreviewSize(width: Int, height: Int) {

        ui.launch {
            controller?.getUvcView()?.aspectRatio = width / height.toDouble()

            (controller?.getUvcView() as UVCCameraTextureView).layoutParams = ConstraintLayout.LayoutParams(
                width, height
            )

            ((controller?.getUvcView() as UVCCameraTextureView).layoutParams as ConstraintLayout.LayoutParams).let { params ->
                params.topToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                params.startToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
        }
    }

    private fun startCaptureUi() {

        ui.launch {

            captureBtn?.visibility = View.VISIBLE
            connectBtn?.visibility = View.INVISIBLE
            settingsBtn?.visibility = View.VISIBLE

            captureBtn?.setOnClickListener {

                captureBtn?.isEnabled = false

                lastBitmap?.let { bmp ->

                    saveBitmapToStorage(type(), bmp, currentRange)

                    activity?.runOnUiThread {

                        captureBtn?.isEnabled = true
                    }
                }
            }

            controller.getUvcView().surfaceTextureListener =
                object : CameraSurfaceListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                        controller.getUvcView().bitmap?.let { bmp ->
                            imageView?.setImageBitmap(bmp)
                            lastBitmap = bmp
                        }
                    }
                }

            val sizes = controller.getUsbApi().camera?.supportedSizeList ?: listOf()
            val resIndex = prefs.getInt(GeneralKeys.USB_CAMERA_RESOLUTION_INDEX, 0)
            if (sizes.isNotEmpty() && resIndex < sizes.size) {
                updatePreviewSize(sizes[resIndex].width, sizes[resIndex].height)
            }
        }
    }
}