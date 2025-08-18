package com.fieldbook.tracker.utilities

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.fieldbook.tracker.BuildConfig
import com.fieldbook.tracker.R
import com.nixsensor.universalsdk.DeviceCompat
import com.nixsensor.universalsdk.DeviceScanner
import com.nixsensor.universalsdk.DeviceStatus
import com.nixsensor.universalsdk.IDeviceCompat
import com.nixsensor.universalsdk.IDeviceScanner
import com.nixsensor.universalsdk.LicenseManager
import com.nixsensor.universalsdk.LicenseManagerState
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.internal.toImmutableList
import java.io.IOException
import javax.inject.Inject

class NixSensorHelper @Inject constructor(@ActivityContext val context: Context) {

    companion object {
        private const val TAG = "Nix"
        //private const val SCAN_PERIOD_MS = 30000L
    }

    data class NixDevice(
        val id: String,
        val name: String,
        val device: IDeviceCompat,
        val description: String
    )

    private var licenseManagerState: LicenseManagerState = LicenseManagerState.INACTIVE
    private var deviceList: MutableList<NixDevice> = mutableListOf()
    private var scanner = DeviceScanner(context)

    var connectedDevice: IDeviceCompat? = null

    @Serializable
    data class NixLicense(
        val options: String,
        val signature: String
    )

    init {

        try {

            // Initialize the Nix Sensor SDK, save the active state
            activate()?.let { licenseManagerState = it }

        } catch (e: Exception) {

            Log.e(TAG, "Error initializing Nix Sensor SDK: ${e.message}")

            e.printStackTrace()
        }

        Log.d(TAG, "License manager state: $licenseManagerState")
    }

    private fun readNixLicense(): NixLicense? {
        return try {
            val jsonString = BuildConfig.NIX_LICENSE
            Json.decodeFromString<NixLicense>(jsonString)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Activate the Nix Sensor SDK
     * Should only be called once per session, otherwise active devices will have to reconnect.
     */
    private fun activate(): LicenseManagerState? {

        readNixLicense()?.let { data ->

            return LicenseManager.activate(context, data.options, data.signature)

        }

        return null
    }

    fun saveDevice(device: IDeviceCompat) {
        connectedDevice = device
    }

    fun search(onResult: (Boolean) -> Unit) {

        if (licenseManagerState != LicenseManagerState.ACTIVE) {

            // License is not active, do not proceed
            Log.d(TAG, "License is not active")

            Toast.makeText(context, R.string.nix_error_license, Toast.LENGTH_SHORT).show()

            return
        }

        scanner.stop()

        scanner = DeviceScanner(context)

        val usb = scanner.listUsbDevices()

        if (usb.isNotEmpty()) {
            Log.d(TAG, "Found ${usb.size} USB devices")
            usb.forEach {
                Log.d(TAG, "USB device: ${it.name}")
                if (!deviceList.map { it.id }.contains(it.id)) {
                    deviceList.add(NixDevice(
                        id = it.id,
                        name = it.name,
                        device = it,
                        description = context.getString(R.string.usb_device_description)
                    ))
                }
            }
        } else {
            Log.d(TAG, "No USB devices found")
        }

        scanner.setOnScannerStateChangeListener(object : IDeviceScanner.OnScannerStateChangeListener {

            override fun onScannerStarted(sender: IDeviceScanner) {
                Log.d(TAG, "Scanner started")
            }

            override fun onScannerStopped(sender: IDeviceScanner) {
                Log.d(TAG, "Scanner stopped")
            }
        })

        if (scanner.state == IDeviceScanner.DeviceScannerState.IDLE) {

            scanner.start(object : IDeviceScanner.OnDeviceFoundListener {
                override fun onScanResult(sender: IDeviceScanner, device: IDeviceCompat) {
                    //log device details
                    val logMsg = "Device found: ${device.name} ID: ${device.id} Type: ${device.type} ScanCount: ${device.scanCount} RSSI: ${device.rssi} Battery: ${device.batteryLevel}"
                    Log.d(TAG, logMsg)
                    if (!deviceList.map { it.id }.contains(device.id)) {
                        deviceList.add(NixDevice(
                            id = device.id,
                            name = device.name,
                            device = device,
                            description = "${device.type} (${device.id})"
                        ))
                    }
                }

                override fun onScanFailed(sender: IDeviceScanner, errorCode: Int) {
                    super.onScanFailed(sender, errorCode)
                    Log.d(TAG, "Scan failed with error code: $errorCode")
                }
            })

        } else if (scanner.state in
            setOf(IDeviceScanner.DeviceScannerState.ERROR_BLUETOOTH_DISABLED,
                IDeviceScanner.DeviceScannerState.ERROR_LICENSE,
                IDeviceScanner.DeviceScannerState.ERROR_INTERNAL,
                IDeviceScanner.DeviceScannerState.ERROR_INVALID_HARDWARE_ID,
                IDeviceScanner.DeviceScannerState.ERROR_BLUETOOTH_PERMISSIONS,
                IDeviceScanner.DeviceScannerState.ERROR_BLUETOOTH_UNAVAILABLE)) {

            Log.d(TAG, "Scanner is not idle, cannot start scanning ${scanner.state.name}")

            val errorMessage = when (scanner.state) {
                IDeviceScanner.DeviceScannerState.ERROR_BLUETOOTH_DISABLED -> context.getString(R.string.nix_error_bluetooth_disabled)
                IDeviceScanner.DeviceScannerState.ERROR_LICENSE -> context.getString(R.string.nix_error_license)
                IDeviceScanner.DeviceScannerState.ERROR_INTERNAL -> context.getString(R.string.nix_error_internal)
                IDeviceScanner.DeviceScannerState.ERROR_INVALID_HARDWARE_ID -> context.getString(R.string.nix_error_invalid_hardware_id)
                IDeviceScanner.DeviceScannerState.ERROR_BLUETOOTH_PERMISSIONS -> context.getString(R.string.nix_error_bluetooth_permissions)
                IDeviceScanner.DeviceScannerState.ERROR_BLUETOOTH_UNAVAILABLE -> context.getString(R.string.nix_error_bluetooth_unavailable)
                else -> context.getString(R.string.nix_error_unknown)
            }

            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()

            scanner.stop()

            onResult(false)
        }
    }

    fun stopScan() {
        scanner.stop()
    }

    fun getDeviceById(address: String, name: String): IDeviceCompat {
        return DeviceCompat(
            context,
            address,
            name
        )
    }

    fun getDeviceList() = deviceList.toImmutableList()

    fun connect(device: IDeviceCompat, onConnected: (Boolean) -> Unit) {

        this.connectedDevice = device

        this.connectedDevice?.connect(object : IDeviceCompat.OnDeviceStateChangeListener {
            override fun onConnected(sender: IDeviceCompat) {
                onConnected(true)
                Log.d(TAG, "Device connected")
            }

            override fun onDisconnected(sender: IDeviceCompat, status: DeviceStatus) {
                onConnected(false)
                Log.d(TAG, "Device disconnected")
            }

            override fun onBatteryStateChanged(sender: IDeviceCompat, newState: Int) {
                super.onBatteryStateChanged(sender, newState)
            }

            override fun onExtPowerStateChanged(sender: IDeviceCompat, newState: Boolean) {
                super.onExtPowerStateChanged(sender, newState)
            }
        })
    }

    fun disconnect() {
        stopScan()
        connectedDevice?.disconnect()
        connectedDevice = null
    }
}