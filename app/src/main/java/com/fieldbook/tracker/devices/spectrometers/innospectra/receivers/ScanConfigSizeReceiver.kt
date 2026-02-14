package com.fieldbook.tracker.devices.spectrometers.innospectra.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ISCSDK.ISCNIRScanSDK
import com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces.NanoEventListener

class ScanConfigSizeReceiver(private val listener: NanoEventListener) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent) {

        val size = intent.getIntExtra(ISCNIRScanSDK.EXTRA_CONF_SIZE, 0)

        Log.d("Nano", "Scan config size received $size")

        listener.onGetConfigSize(size)
    }
}