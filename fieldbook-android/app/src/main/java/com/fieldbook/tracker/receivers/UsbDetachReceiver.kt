package com.fieldbook.tracker.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager

class UsbDetachReceiver(private val f: (() -> Unit)? = null): BroadcastReceiver() {

    companion object {
        const val TAG = "UsbDetachBR"
    }

    override fun onReceive(context: Context, intent: Intent) {

        if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {

            synchronized(this) {

                f?.invoke()
            }
        }
    }
}