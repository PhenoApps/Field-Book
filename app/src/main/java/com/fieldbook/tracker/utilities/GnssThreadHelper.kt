package com.fieldbook.tracker.utilities

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import com.fieldbook.tracker.location.gnss.ConnectThread
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

class GnssThreadHelper @Inject constructor(@ActivityContext private val context: Context) {

    var connectThread: ConnectThread? = null

    var isAlive = false

    private var lastDevice: BluetoothDevice? = null
    private var lastHandler: Handler? = null

    fun start(value: BluetoothDevice, handler: Handler) {

        lastDevice = value

        lastHandler = handler

        isAlive = true

        connectThread = ConnectThread(value, handler)

        connectThread?.start()

    }

    fun stop() {

        lastDevice = null

        lastHandler = null

        isAlive = false

        connectThread?.cancel()

    }

    fun reestablish() {

        isAlive = true

        if (lastDevice != null && lastHandler != null) {

            start(lastDevice!!, lastHandler!!)

        }
    }
}