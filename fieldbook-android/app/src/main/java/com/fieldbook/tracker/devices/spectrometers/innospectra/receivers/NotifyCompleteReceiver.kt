package com.fieldbook.tracker.devices.spectrometers.innospectra.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces.NanoEventListener

class NotifyCompleteReceiver(private val listener: NanoEventListener) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        Log.d("Nano", "Notify Complete.")

        listener.onNotifyReceived()

    }
}