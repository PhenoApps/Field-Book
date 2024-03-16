package com.fieldbook.tracker.devices.ptpip

import android.graphics.Bitmap
import com.fieldbook.tracker.objects.RangeObject

interface PtpSessionCallback {
    fun onSessionStart()
    fun onSessionStop()
    fun onPreview(bmp: Bitmap)
    fun onBitmapCaptured(bmp: Bitmap, obsUnit: RangeObject) = Unit
    fun onJpegCaptured(data: ByteArray, obsUnit: RangeObject) = Unit
}