package com.fieldbook.tracker.devices.spectrometers.innospectra.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ISCSDK.ISCNIRScanSDK
import com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces.NanoEventListener

/**
 * Send broadcast  GET_INFO will  through DeviceInfoReceiver  to get the device info(ISCNIRScanSDK.GetDeviceInfo() should be called)
 */
class DeviceInfoReceiver(private val listener: NanoEventListener) : BroadcastReceiver() {

    data class NanoDeviceInfo(val manufacturer: String,
                              val model: String,
                              val serial: String,
                              val hardware: String,
                              val tiva: String,
                              val spec: String)

    override fun onReceive(context: Context, intent: Intent) {

        Log.d("Nano", "Device Info Received")

        val manufacturerName = intent.getStringExtra(ISCNIRScanSDK.EXTRA_MANUF_NAME) ?: ""
        val modelNum = intent.getStringExtra(ISCNIRScanSDK.EXTRA_MODEL_NUM) ?: ""
        val serialNum = intent.getStringExtra(ISCNIRScanSDK.EXTRA_SERIAL_NUM) ?: ""
        val hardwareRev = intent.getStringExtra(ISCNIRScanSDK.EXTRA_HW_REV) ?: ""
        val tivaRev = intent.getStringExtra(ISCNIRScanSDK.EXTRA_TIVA_REV) ?: ""
        val specRev = intent.getStringExtra(ISCNIRScanSDK.EXTRA_SPECTRUM_REV) ?: ""

        listener.onGetDeviceInfo(
            NanoDeviceInfo(
                manufacturerName,
                modelNum,
                serialNum,
                hardwareRev,
                tivaRev,
                specRev)
        )

    }
}