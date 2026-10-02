package com.fieldbook.tracker.devices.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.view.Surface
import android.widget.ImageView
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.widget.CameraViewInterface
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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

    private val lock = Any()

    /** Written on USB monitor / release threads; read from main keep volatile. */
    @Volatile
    var camera: UVCCamera? = null
        private set

    @Volatile
    var supportedSizes: List<Size>? = null
        private set

    private var callbacks: Callbacks? = null
    private var monitor: USBMonitor? = null

    /** True from start of release until join/FD close finishes (covers async soft-release). */
    @Volatile
    private var releasing = false

    private var softReleaseDone: CountDownLatch? = null

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
            // onDisconnect usually already soft-released; this covers detach-only paths.
            softReleaseOnUnplug(null)
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
            // setUsbGone is flags-only (no join) so this returns before FDs close.
            softReleaseOnUnplug(ctrlBlock)
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
            synchronized(lock) {
                camera = cam
                supportedSizes =
                    cam.supportedSizeList?.distinctBy { it.width to it.height } ?: listOf()
            }
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
        synchronized(lock) {
            this.supportedSizes = sizes
        }
        callbacks?.onConnected(camera, sizes)
    }

    /**
     * Hot-unplug teardown: stop native I/O, join threads, then close Java USB FDs.
     * Closing FDs before join races libusb and can abort; never closing them leaks
     * two UsbDeviceConnection descriptors per unplug (monitor + camera clone).
     *
     * [monitorCtrlBlock] is the monitor-owned block from onDisconnect (closeConnection
     * was false so its FD is still open). Native destroy stays deferred, uvc_exit
     * after unplug is still unsafe in the prebuilt JNI.
     */
    private fun softReleaseOnUnplug(monitorCtrlBlock: USBMonitor.UsbControlBlock?) {
        val cam: UVCCamera
        val latch: CountDownLatch
        synchronized(lock) {
            if (releasing) {
                // Release already in flight; still try to close monitor FD after it finishes.
                val inFlight = softReleaseDone
                background.launch {
                    inFlight?.await(2, TimeUnit.SECONDS)
                    closeControlBlockFd(monitorCtrlBlock)
                }
                return
            }
            val current = camera
            if (current == null) {
                closeControlBlockFd(monitorCtrlBlock)
                return
            }
            cam = current
            releasing = true
            camera = null
            supportedSizes = null
            latch = CountDownLatch(1)
            softReleaseDone = latch
            softClosedCameras.add(cam)
        }
        try {
            cam.setUsbGone()
            // Join off the USB callback thread so USBMonitor is not wedged.
            background.launch {
                try {
                    cam.joinAfterUsbGone()
                } catch (_: Exception) {
                }
                try {
                    cam.releaseUsbConnection()
                } catch (_: Exception) {
                }
                closeControlBlockFd(monitorCtrlBlock)
                synchronized(lock) {
                    releasing = false
                    if (softReleaseDone === latch) {
                        softReleaseDone = null
                    }
                }
                latch.countDown()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            synchronized(lock) {
                releasing = false
                softReleaseDone = null
            }
            latch.countDown()
        }
    }

    private fun closeControlBlockFd(ctrlBlock: USBMonitor.UsbControlBlock?) {
        if (ctrlBlock == null) return
        try {
            // Second close with closeConnection=true FD was left open on detach.
            ctrlBlock.close(false, true)
        } catch (_: Exception) {
        }
    }

    private fun releaseCamera() {
        val cam: UVCCamera
        synchronized(lock) {
            if (releasing) return
            cam = camera ?: return
            releasing = true
            camera = null
            supportedSizes = null
        }
        try {
            // close() calls stopPreview() once; do not stopPreview here or we
            // risk a second native join on already-reaped threads.
            cam.close()
            closedCameras.add(cam)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            synchronized(lock) {
                releasing = false
            }
        }
    }

    fun attach(callbacks: Callbacks) {
        this.callbacks = callbacks
        if (monitor == null) {
            monitor = USBMonitor(context, listener)
        }
        monitor?.register()
        val cam = camera
        if (cam != null) {
            initializePreview(callbacks, cam)
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
        // Finish any async soft-unplug (join + FD close) before tearing down the monitor.
        try {
            softReleaseDone?.await(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
        releaseCamera()
        // Soft-unplug cams already closed their Java FDs after join; skip nativeDestroy —
        // uvc_exit after hot-unplug is still unsafe in the prebuilt JNI.
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

    /**
     * Grab one still frame at [size] via UVC frame callback (camera resolution),
     * not the TextureView preview bitmap.
     * Call from a background thread. May temporarily change the stream size;
     * the caller should restore the live-preview size afterward.
     */
    fun captureStill(size: Size): Bitmap? {
        val cam = camera ?: return null
        if (size.width <= 0 || size.height <= 0) return null

        val latch = CountDownLatch(1)
        val captured = AtomicReference<Bitmap?>(null)
        val accepted = AtomicBoolean(false)

        try {
            val current = try {
                cam.previewSize
            } catch (_: Exception) {
                null
            }
            val needResize = current == null ||
                    current.width != size.width ||
                    current.height != size.height

            if (needResize) {
                try {
                    cam.stopPreview()
                } catch (_: Exception) {
                }
                cam.setPreviewSize(size.width, size.height)
                val view = callbacks?.getUsbCameraInterface()
                val display = view?.surface
                    ?: view?.surfaceTexture?.let { Surface(it) }
                if (display != null) {
                    cam.setPreviewDisplay(display)
                }
                cam.startPreview()
            }

            val callback = IFrameCallback { frame ->
                if (!accepted.compareAndSet(false, true)) {
                    return@IFrameCallback
                }
                try {
                    frame.rewind()
                    // RGBX bytes match ARGB_8888's in-memory RGBA order; full 8-bit color.
                    val bmp = Bitmap.createBitmap(
                        size.width,
                        size.height,
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.copyPixelsFromBuffer(frame)
                    captured.set(bmp)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }

            cam.setFrameCallback(callback, UVCCamera.PIXEL_FORMAT_RGBX)
            if (!latch.await(3, TimeUnit.SECONDS)) {
                // Timed out waiting for a frame at the requested size.
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                cam.setFrameCallback(null, 0)
            } catch (_: Exception) {
            }
        }

        return captured.get()
    }
}