package com.fieldbook.tracker.devices.camera

import android.content.Context
import android.hardware.usb.UsbDevice
import android.widget.ImageView
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.widget.CameraViewInterface
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
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
    @Volatile private var releasing = false
    // Defer nativeDestroy/uvc_exit until onDestroy — avoids FORTIFY during plug cycles.
    private val closedCameras = CopyOnWriteArrayList<UVCCamera>()
    // Hot-unplug: never nativeRelease/uvc_exit these — usbfs already gone.
    private val softClosedCameras = CopyOnWriteArrayList<UVCCamera>()

    private val listener = object : USBMonitor.OnDeviceConnectListener {

        override fun onAttach(device: UsbDevice?) {
            monitor?.requestPermission(device)
        }

        override fun onDetach(device: UsbDevice?) {
            val hadCamera = camera != null
            // Clear camera before UI callback so isConnected() is false in onDisconnected.
            softReleaseOnUnplug()
            if (hadCamera) {
                callbacks?.onDisconnected()
            }
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
            val hadCamera = camera != null
            // Clear camera before UI callback so isConnected() is false in onDisconnected.
            // setUsbGone is flags-only (no join) so this returns before the FD closes.
            softReleaseOnUnplug()
            if (hadCamera) {
                callbacks?.onDisconnected()
            }
        }

        override fun onCancel(device: UsbDevice?) {}
    }

    private fun onConnectCamera(callbacks: Callbacks?, ctrlBlock: USBMonitor.UsbControlBlock?) {
        try {
            releaseCamera()
            val cam = UVCCamera()
            cam.open(ctrlBlock)
            camera = cam
            supportedSizes = cam.supportedSizeList?.distinctBy { it.width to it.height } ?: listOf()
            initializePreview(callbacks, cam)
        } catch (e: Exception) {
            e.printStackTrace()
            releaseCamera()
            callbacks?.onDisconnected()
        }
    }

    private fun initializePreview(callbacks: Callbacks?, camera: UVCCamera?) {
        val sizes = camera?.supportedSizeList
            ?.distinctBy { it.width to it.height } ?: listOf()
        this.supportedSizes = sizes
        callbacks?.onConnected(camera, sizes)
    }

    private fun softReleaseOnUnplug() {
        if (releasing) return
        val cam = camera ?: return
        releasing = true
        camera = null
        supportedSizes = null
        try {
            // USB FD may already be invalid. Flag threads out of libusb, skip close()/nativeRelease.
            cam.setUsbGone()
            softClosedCameras.add(cam)
            // Join preview/capture off the USB callback thread so USBMonitor is not wedged.
            background.launch {
                try {
                    cam.joinAfterUsbGone()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            releasing = false
        }
    }

    private fun releaseCamera() {
        if (releasing) return
        val cam = camera ?: return
        releasing = true
        camera = null
        supportedSizes = null
        try {
            // close() calls stopPreview() once; do not stopPreview here or we
            // risk a second native join on already-reaped threads.
            cam.close()
            closedCameras.add(cam)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            releasing = false
        }
    }

    fun attach(callbacks: Callbacks) {
        this.callbacks = callbacks
        if (monitor == null) {
            monitor = USBMonitor(context, listener)
        }
        monitor?.register()
        if (camera != null) {
            initializePreview(callbacks, camera)
        }
    }

    fun onStart() {
        monitor?.register()
    }

    fun onStop() {
        // Stay registered while a session is open so unplug is delivered.
        if (camera != null) return
        try {
            monitor?.unregister()
        } catch (_: Exception) {
        }
    }

    fun onDestroy() {
        releaseCamera()
        // Soft-unplug cams: drop refs only. destroy()/close() still touches libusb.
        softClosedCameras.clear()
        for (cam in closedCameras) {
            try {
                cam.destroy()
            } catch (_: Exception) {
            }
        }
        closedCameras.clear()
        try {
            monitor?.unregister()
        } catch (_: Exception) {
        }
        try {
            monitor?.destroy()
        } catch (_: Exception) {
        }
        monitor = null
        callbacks = null
    }

    fun isConnected(): Boolean = camera != null

    fun getSizes(): List<Size>? = camera?.supportedSizeList
}