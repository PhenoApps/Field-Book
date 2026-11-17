package com.fieldbook.tracker.devices.camera

import android.graphics.SurfaceTexture
import android.view.TextureView

/**
 * Local copy of PhenoLib's CameraSurfaceListener so Field Book no longer depends on
 * org.phenoapps.interfaces.usb for USB/UVC camera preview wiring.
 */
interface CameraSurfaceListener : TextureView.SurfaceTextureListener {
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int)
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean = true
    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture)
}
