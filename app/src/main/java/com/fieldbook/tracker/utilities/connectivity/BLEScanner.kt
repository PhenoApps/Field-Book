package com.fieldbook.tracker.utilities.connectivity

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

class BLEScanner @Inject constructor(@ActivityContext private val context: Context) {

    companion object {
        private val TAG = BLEScanner::class.simpleName
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val scanResults = mutableListOf<BluetoothDevice>()
    var onDeviceFound: ((BluetoothDevice) -> Unit)? = null

    private var scanning = false

    /**
     * Resolved per use rather than cached: getBluetoothLeScanner() returns null while the adapter
     * is off, so a scanner captured at construction is either permanently null (BT off at startup,
     * scanning never works even after the user enables it) or a stale handle that throws
     * IllegalStateException once the adapter leaves STATE_ON.
     */
    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.takeIf { it.isEnabled }?.bluetoothLeScanner

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

        if (scanning) return

        try {

            scanner?.let { s ->
                s.startScan(scanCallback)
                scanning = true
            } ?: Log.w(TAG, "Not starting BLE scan, adapter is unavailable or turned off.")

        } catch (e: Exception) {

            //adapter can be torn down between the state check and the call
            Log.w(TAG, "Unable to start BLE scan.", e)
        }
    }

    fun stopScanning() {

        if (!scanning) return

        scanning = false

        try {

            //a null scanner means the adapter is already off, which stopped the scan for us
            scanner?.stopScan(scanCallback)

        } catch (e: Exception) {

            //stopScan throws IllegalStateException("BT Adapter is not turned ON") if the user
            //disables bluetooth while a scan is active, which is fatal from onDestroy
            Log.w(TAG, "Unable to stop BLE scan.", e)
        }
    }

    fun getDiscoveredDevices(): MutableList<BluetoothDevice> = scanResults
}
