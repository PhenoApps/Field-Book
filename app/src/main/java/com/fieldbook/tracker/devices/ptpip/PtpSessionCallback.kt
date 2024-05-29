package com.fieldbook.tracker.devices.ptpip

import android.graphics.Bitmap
import com.fieldbook.tracker.devices.camera.CanonApi
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.traits.AbstractCameraTrait

interface PtpSessionCallback {
    fun onSessionStart()
    fun onSessionStop()
    fun onPreview(bmp: Bitmap)
    fun onBitmapCaptured(bmp: Bitmap, obsUnit: RangeObject) = Unit
    fun onJpegCaptured(
        data: ByteArray,
        obsUnit: RangeObject,
        saveTime: String,
        saveState: AbstractCameraTrait.SaveState,
        offset: Int? = null
    ) = Unit
}