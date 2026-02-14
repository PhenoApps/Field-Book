package com.fieldbook.tracker.devices.multispeq

import android.bluetooth.BluetoothDevice

/** Callbacks delivered from [MultispeqManager] to the trait layout. */
interface MultispeqListener {
    fun onConnected(device: BluetoothDevice)
    fun onMeasurementResult(rawJson: String)
    fun onError(message: String)
    fun onDisconnected()
}
