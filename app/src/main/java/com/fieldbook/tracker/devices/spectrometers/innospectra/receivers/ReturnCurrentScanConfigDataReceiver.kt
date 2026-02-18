package com.fieldbook.tracker.devices.spectrometers.innospectra.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ISCSDK.ISCNIRScanSDK

class ReturnCurrentScanConfigDataReceiver() : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent) {

        Log.d("Nano", "Current scan config data received")

        //val config = intent.getByteArrayExtra(ISCNIRScanSDK.EXTRA_CURRENT_CONFIG_DATA)

        ISCNIRScanSDK.StartScan()
    }
}