package com.fieldbook.tracker.devices.spectrometers.innospectra.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.ISCSDK.ISCNIRScanSDK
import com.fieldbook.tracker.R
import com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces.NanoEventListener

class WriteScanConfigStatusReceiver(private val listener: NanoEventListener) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent) {

        intent.getByteArrayExtra(ISCNIRScanSDK.RETURN_WRITE_SCAN_CONFIG_STATUS)?.let { status ->
            if (status[0].toInt() == 1) {
                if (status[1].toInt() == 1) {
                    listener.onConfigSaveStatus(true)
                } else {
                    Toast.makeText(context, R.string.inno_spectra_save_failed, Toast.LENGTH_SHORT).show()
                    listener.onConfigSaveStatus(false)
                }
            } else if (status[0].toInt() == -1) {
                listener.onConfigSaveStatus(false)
                Toast.makeText(context, R.string.inno_spectra_save_failed, Toast.LENGTH_SHORT).show()
            } else if (status[0].toInt() == -2) {
                listener.onConfigSaveStatus(false)
                Toast.makeText(context, R.string.inno_spectra_hardware_incompatible, Toast.LENGTH_SHORT).show()
            } else if (status[0].toInt() == -3) {
                listener.onConfigSaveStatus(false)
                Toast.makeText(context, R.string.inno_spectra_function_locked, Toast.LENGTH_SHORT).show()
            }
        }
    }
}