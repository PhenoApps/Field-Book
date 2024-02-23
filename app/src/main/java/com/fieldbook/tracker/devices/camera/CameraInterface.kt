package com.fieldbook.tracker.devices.camera

import android.graphics.Bitmap

sealed interface Device {
    fun connect()
    fun disconnect()
}

interface CameraInterface: Device {

    fun startSingleShotCapture()

    fun onPreview(bmp: Bitmap)

    fun onBitmapCaptured(bmp: Bitmap)

}

//object DeviceAdapterFactory {
//
//    fun create(): Device {
//
//
//    }
//
//}