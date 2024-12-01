package com.fieldbook.tracker.devices.camera

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import android.widget.ImageView
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.widget.CameraViewInterface
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.internal.toImmutableList
import javax.inject.Inject


class UsbCameraApi @Inject constructor(@ActivityContext private val context: Context) {

    interface Callbacks {
        fun onConnected(camera: UVCCamera?, sizes: List<Size>)
        fun onDisconnected()
        fun getUsbCameraInterface(): CameraViewInterface?
        fun getPreviewView(): ImageView?
    }

    companion object {
        const val TAG = "UsbCameraApi"
    }

    val background = CoroutineScope(Dispatchers.IO)
    val ui = CoroutineScope(Dispatchers.Main)

    var camera: UVCCamera? = null
    var supportedSizes: List<Size>? = null

    private var callbacks: Callbacks? = null
    private var monitor: USBMonitor? = null

    private val listener = object : USBMonitor.OnDeviceConnectListener {

        override fun onAttach(device: UsbDevice?) {

            monitor?.requestPermission(device)

        }

        override fun onDetach(device: UsbDevice?) {

            camera = null

        }

        override fun onConnect(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean
        ) {

            onConnectCamera(callbacks, ctrlBlock)

        }

        override fun onDisconnect(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?
        ) {

            callbacks?.onDisconnected()

        }

        override fun onCancel(device: UsbDevice?) {}

    }

    private fun onConnectCamera(callbacks: Callbacks?, ctrlBlock: USBMonitor.UsbControlBlock?) {

        try {

            camera = UVCCamera()

            camera?.open(ctrlBlock)

            supportedSizes = camera?.supportedSizeList ?: listOf()

            initializePreview(callbacks, camera)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initializePreview(callbacks: Callbacks?, camera: UVCCamera?) {
        val supportedSizes = camera?.supportedSizeList?.toImmutableList()
            ?.distinctBy { it.width to it.height } ?: listOf()

        callbacks?.onConnected(camera, supportedSizes)
    }

    private fun destroyCamera() {
        try {
            camera?.destroy()
            camera = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun attach(callbacks: Callbacks) {

        this.callbacks = callbacks

        if (camera == null) {

            monitor = USBMonitor(context, listener)

            monitor?.register()

        } else {

            initializePreview(callbacks, camera)
        }
    }

    fun onStart() {
        if (camera != null) {
            monitor?.register()
        }
    }

    fun onStop() {
        if (camera != null) {
            monitor?.unregister()
        }
    }

    fun onDestroy() {
        if (camera != null) {
            try {
                monitor?.unregister()
                //camera?.close()
                camera = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
            //destroyCamera()
        }
    }

    fun isConnected(): Boolean = camera != null

    fun getSizes(): List<Size>? = camera?.supportedSizeList
}