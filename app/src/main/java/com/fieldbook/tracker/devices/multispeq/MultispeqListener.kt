package com.fieldbook.tracker.devices.multispeq

import android.bluetooth.BluetoothDevice

/** Callbacks delivered from [MultispeqManager] to the trait layout. */
interface MultispeqListener {
    fun onConnected(device: BluetoothDevice)
    /** Delivered once, immediately after [onConnected], with data from the "1" (device info) command. */
    fun onDeviceInfo(deviceId: String, firmware: String, battery: Int)
    fun onMeasurementResult(rawJson: String)
    fun onError(message: String)
    fun onDisconnected()
}
