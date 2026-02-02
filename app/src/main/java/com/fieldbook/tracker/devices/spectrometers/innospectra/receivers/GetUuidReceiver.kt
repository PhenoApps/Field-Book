package com.fieldbook.tracker.devices.spectrometers.innospectra.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ISCSDK.ISCNIRScanSDK
import com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces.NanoEventListener

class GetUuidReceiver(private val listener: NanoEventListener) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        Log.d("Nano", "Device UUID Received")

        var uuid = ""

        val buf = intent.getByteArrayExtra(ISCNIRScanSDK.EXTRA_DEVICE_UUID)
        for (i in buf!!.indices) {
            uuid += Integer.toHexString(0xff and buf[i].toInt())
            if (i != buf.size - 1) {
                uuid += ":"
            }
        }

        listener.onGetUuid(buf.toString())
    }
}