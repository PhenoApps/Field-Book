package com.fieldbook.tracker.utilities

import android.content.Context
import android.util.Log
import com.fieldbook.tracker.traits.SpectralTraitLayout
import com.fieldbook.tracker.traits.SpectralTraitLayout.Companion
import com.nixsensor.universalsdk.CommandStatus
import com.nixsensor.universalsdk.DeviceCompat
import com.nixsensor.universalsdk.DeviceScanner
import com.nixsensor.universalsdk.DeviceStatus
import com.nixsensor.universalsdk.IDeviceCompat
import com.nixsensor.universalsdk.IDeviceScanner
import com.nixsensor.universalsdk.IMeasurementData
import com.nixsensor.universalsdk.LicenseManager
import com.nixsensor.universalsdk.LicenseManagerState
import com.nixsensor.universalsdk.OnDeviceResultListener
import com.nixsensor.universalsdk.ScanMode
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.internal.toImmutableList
import java.io.IOException
import javax.inject.Inject

class NixSensorHelper @Inject constructor(@ActivityContext val context: Context) {

    companion object {
        private const val TAG = "Nix"
        private const val SCAN_PERIOD_MS = 30000L
    }

    private var licenseManagerState: LicenseManagerState = LicenseManagerState.INACTIVE
    private var deviceList: MutableList<IDeviceCompat> = mutableListOf()
    private var isSearching: Boolean = false

    var connectedDevice: IDeviceCompat? = null

    @Serializable
    data class NixLicense(
        val options: String,
        val signature: String
    )

    init {
        // Initialize the Nix Sensor SDK, save the active state
        activate()?.let { licenseManagerState = it }

        Log.d(TAG, "License manager state: $licenseManagerState")
    }

    private fun readNixLicense(): NixLicense? {
        return try {
            val jsonString = context.assets.open("nix/license.json").bufferedReader().use {
                it.readText()
            }
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

    fun search() {

        if (!isSearching) {

            isSearching = true

            if (licenseManagerState != LicenseManagerState.ACTIVE) {
                // License is not active, do not proceed
                Log.d(TAG, "License is not active")
                return
            }

            deviceList.clear()

            with (DeviceScanner(context)) {

                val usb = listUsbDevices()

                if (usb.isNotEmpty()) {
                    Log.d(TAG, "Found ${usb.size} USB devices")
                    usb.forEach {
                        Log.d(TAG, "USB device: ${it.name}")
                        if (!deviceList.map { it.id }.contains(it.id)) {
                            deviceList.add(it)
                        }
                    }
                } else {
                    Log.d(TAG, "No USB devices found")
                }

                setOnScannerStateChangeListener(object : IDeviceScanner.OnScannerStateChangeListener {

                    override fun onScannerStarted(sender: IDeviceScanner) {
                        Log.d(TAG, "Scanner started")
                    }

                    override fun onScannerStopped(sender: IDeviceScanner) {
                        Log.d(TAG, "Scanner stopped")
                        isSearching = false
                    }
                })

                start(object : IDeviceScanner.OnDeviceFoundListener {
                    override fun onScanResult(sender: IDeviceScanner, device: IDeviceCompat) {
                        //log device details
                        val logMsg = "Device found: ${device.name} ID: ${device.id} Type: ${device.type} ScanCount: ${device.scanCount} RSSI: ${device.rssi} Battery: ${device.batteryLevel}"
                        Log.d(TAG, logMsg)
                        if (!deviceList.map { it.id }.contains(device.id)) {
                            deviceList.add(device)
                        }
                    }

                    override fun onScanFailed(sender: IDeviceScanner, errorCode: Int) {
                        super.onScanFailed(sender, errorCode)
                        Log.d(TAG, "Scan failed with error code: $errorCode")
                    }

                })
            }
        }
    }

    fun getDeviceById(address: String, name: String): IDeviceCompat {
        return DeviceCompat(
            context,
            address,
            name
        )
    }

    fun getDeviceList() = deviceList.toImmutableList()

    fun measure(device: IDeviceCompat) {
        device.measure(object : OnDeviceResultListener {
            override fun onDeviceResult(
                status: CommandStatus,
                measurements: Map<ScanMode, IMeasurementData>?
            ) {
                for ((mode, data) in measurements!!) {
                    Log.d(TAG, "Mode: $mode")
                    Log.d(TAG, "Data: $data")
                }
            }
        })
    }

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
}