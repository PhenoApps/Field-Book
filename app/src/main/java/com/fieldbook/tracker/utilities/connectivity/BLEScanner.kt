package com.fieldbook.tracker.utilities.connectivity

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

class BLEScanner @Inject constructor(@ActivityContext private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val scanner = bluetoothAdapter?.bluetoothLeScanner
    private val scanResults = mutableListOf<BluetoothDevice>()
    var onDeviceFound: ((BluetoothDevice) -> Unit)? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (!scanResults.any { it.address == device.address }) {
                    scanResults.add(device)
                    onDeviceFound?.invoke(device)
                }
            }
        }
    }

    fun startScanning() {
        scanner?.startScan(scanCallback)
    }

    fun stopScanning() {
        scanner?.stopScan(scanCallback)
    }

    fun getDiscoveredDevices(): MutableList<BluetoothDevice> = scanResults
}
