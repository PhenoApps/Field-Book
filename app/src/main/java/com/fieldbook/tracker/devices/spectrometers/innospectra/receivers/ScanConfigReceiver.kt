package com.fieldbook.tracker.devices.spectrometers.innospectra.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ISCSDK.ISCNIRScanSDK
import com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces.NanoEventListener

class ScanConfigReceiver(private val listener: NanoEventListener) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent) {

        Log.d("Nano", "Scan config received")

        val confBytes = intent.getByteArrayExtra(ISCNIRScanSDK.EXTRA_DATA)
        val conf = ISCNIRScanSDK.GetScanConfiguration(confBytes)

        listener.onGetConfig(conf)
    }
}