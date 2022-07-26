package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.serenegiant.SimpleUVCCameraTextureView
import org.phenoapps.androidlibrary.Utils
import org.phenoapps.receivers.UsbPermissionReceiver
import org.phenoapps.usb.camera.UsbCameraHelper
import org.phenoapps.usb.camera.UsbCameraInterface

class UsbCameraTraitLayout : BaseTraitLayout {

    companion object {
        const val type = "usb camera"
    }

    private var camInterface: UsbCameraInterface? = null

    private var mUsbPermissionReceiver: UsbPermissionReceiver? = null
    private var mUsbCameraHelper: UsbCameraHelper? = null
    private var textureView: SimpleUVCCameraTextureView? = null
    private var devicesBtn: Button? = null
    private var captureBtn: Button? = null
    private var expandBtn: ImageButton? = null
    private var collapseBtn: ImageButton? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
    }

    override fun setNaTraitsText() {}
    override fun type(): String {
        return type
    }

    override fun init(act: Activity?) {
        super.init(act)

        textureView = findViewById(R.id.trait_usb_camera_texture_view)
        devicesBtn = findViewById(R.id.trait_usb_camera_devices_btn)
        captureBtn = findViewById(R.id.trait_usb_camera_capture_btn)
        expandBtn = findViewById(R.id.trait_usb_camera_expand_btn)
        collapseBtn = findViewById(R.id.trait_usb_camera_collapse_btn)

        expandBtn?.setOnClickListener {
            mUsbCameraHelper?.switchDisplayMode()
            expandBtn?.visibility = View.GONE
            collapseBtn?.visibility = View.VISIBLE
        }

        collapseBtn?.setOnClickListener {
            mUsbCameraHelper?.switchDisplayMode()
            expandBtn?.visibility = View.VISIBLE
            collapseBtn?.visibility = View.GONE
        }

        devicesBtn?.setOnClickListener {

            context?.let { ctx ->

                val manager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager

                val permissionIntent = PendingIntent.getBroadcast(
                    ctx,
                    0,
                    Intent(UsbPermissionReceiver.ACTION_USB_PERMISSION),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )

                val devices = manager.deviceList.map { it.value }

                devices.forEach {
                    manager.requestPermission(it, permissionIntent)
                }
            }
        }

        captureBtn?.setOnClickListener {

            currentTrait.trait?.let { traitName ->

                val bmp = mUsbCameraHelper?.getBitmap()

                val usbPhotosDir = DocumentTreeUtil.getTraitMediaDir(context, traitName, "photos")

                if (usbPhotosDir != null) {

                    val plot = cRange.plot_id

                    val time = Utils.getDateTime()

                    usbPhotosDir.createFile("*/*", "${traitName}_${plot}_$time.png")?.let { file ->

                        context.contentResolver.openOutputStream(file.uri)?.let { output ->

                            bmp?.compress(Bitmap.CompressFormat.PNG, 100, output)
                        }
                    }
                }
            }
        }

        camInterface = (act as UsbCameraInterface)

    }

    override fun init() {}
    override fun loadLayout() {
        etCurVal.removeTextChangedListener(cvText)
        etCurVal.visibility = GONE
        etCurVal.isEnabled = false

        mUsbPermissionReceiver = UsbPermissionReceiver {

            mUsbCameraHelper = camInterface?.getCameraHelper()?.also {
                textureView?.let { v ->
                    it.init(v, 512, 1024)
                    expandBtn?.visibility = View.VISIBLE
                    devicesBtn?.visibility = View.GONE
                    captureBtn?.visibility = View.VISIBLE

                    try {

                        context.unregisterReceiver(mUsbPermissionReceiver)

                    } catch (ignore: Exception) {} //might not be registered if already paired
                }
            }
        }

        val filter = IntentFilter(UsbPermissionReceiver.ACTION_USB_PERMISSION)
        context.registerReceiver(mUsbPermissionReceiver, filter)
    }

    override fun deleteTraitListener() {}

}