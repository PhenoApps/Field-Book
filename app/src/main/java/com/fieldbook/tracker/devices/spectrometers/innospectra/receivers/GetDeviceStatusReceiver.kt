package com.fieldbook.tracker.devices.spectrometers.innospectra.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ISCSDK.ISCNIRScanSDK
import com.fieldbook.tracker.devices.spectrometers.innospectra.models.DeviceStatus
import com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces.NanoEventListener

class GetDeviceStatusReceiver(private val listener: NanoEventListener) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        Log.d("Nano", "Device Status Received")

        val battery = intent.getIntExtra(ISCNIRScanSDK.EXTRA_BATT, 0).toString()
        val totalLampTime = intent.getLongExtra(ISCNIRScanSDK.EXTRA_LAMPTIME, 0).toString()
        val temperature = intent.getFloatExtra(ISCNIRScanSDK.EXTRA_TEMP, 0f).toString()
        val humidity = intent.getFloatExtra(ISCNIRScanSDK.EXTRA_HUMID, 0f).toString()

        listener.onGetDeviceStatus(
            DeviceStatus(
                battery, totalLampTime, temperature, humidity
            )
        )
    }
}