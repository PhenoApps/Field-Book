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

    private var callbacks: Callbacks? = null
    private var monitor: USBMonitor? = null

    private fun getListener(callbacks: Callbacks?) = object : USBMonitor.OnDeviceConnectListener {

        override fun onAttach(device: UsbDevice?) {

            monitor?.requestPermission(device)

        }

        override fun onDetach(device: UsbDevice?) {}

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

            camera?.close()

            callbacks?.onDisconnected()
        }

        override fun onCancel(device: UsbDevice?) {}

    }

    private fun onConnectCamera(callbacks: Callbacks?, ctrlBlock: USBMonitor.UsbControlBlock?) {

        try {

            camera = UVCCamera()

            camera?.open(ctrlBlock)

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

    fun attachToTraitLayout(callbacks: Callbacks) {

        this.callbacks = callbacks

        if (camera == null) {

            monitor = USBMonitor(context, getListener(callbacks))

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
            monitor?.destroy()
        }
    }

    fun isConnected(): Boolean = camera != null

    fun getSizes(): List<Size>? = camera?.supportedSizeList
}