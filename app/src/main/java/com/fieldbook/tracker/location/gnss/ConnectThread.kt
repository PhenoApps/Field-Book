package com.fieldbook.tracker.location.gnss

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.util.Log
import java.io.IOException

class ConnectThread(device: BluetoothDevice, private val handler: Handler) : Thread() {

    private val mmSocket: BluetoothSocket?
    private var mConnectedThread: ConnectedThread? = null

    private companion object {
        const val TAG = "ConnectThreadFB"
    }

    init {
        // Use a temporary object that is later assigned to mmSocket
        // because mmSocket is final.
        var tmp: BluetoothSocket? = null

        try {
            tmp = device.createRfcommSocketToServiceRecord(device.uuids[0].uuid)
        } catch (e: IOException) {
            Log.e(TAG, "Socket's create() method failed", e)
        }

        mmSocket = tmp
    }

    override fun run() {

        var success = true

        //attempt connection until thread is cancelled,
        // sometimes connection to socket fails after closing in previous activity
        try {

            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            mmSocket?.connect()

        } catch (connectException: IOException) {

            connectException.printStackTrace()

            val readMsg = handler.obtainMessage(
                GNSSResponseReceiver.MESSAGE_OUTPUT_FAIL, 0, -1, "fail")

            readMsg.sendToTarget()

            cancel()

            success = false
        }

        if (success) {
            mmSocket?.let { sock ->
                mConnectedThread = ConnectedThread(sock, handler)
                mConnectedThread?.start()
            }
        }
    }

    // Closes the client socket and causes the thread to finish.
    fun cancel() = try {

        mConnectedThread?.cancel()

        mmSocket?.close()

        this.handler.removeCallbacksAndMessages(null)

    } catch (e: IOException) {

        Log.e(TAG, "Could not close the client socket", e)

    }
}