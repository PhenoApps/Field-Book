package com.fieldbook.tracker.utilities

import android.content.Context
import android.os.Handler
import android.os.Looper
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
import javax.inject.Inject

class NixSensorHelper @Inject constructor(@ActivityContext val context: Context) {

    companion object {
        private const val TAG = "Nix"

        //how long a scan may run before it stops itself, so an unattended search does not leave
        //the radio scanning for the rest of the session
        private const val SCAN_PERIOD_MS = 30000L

        //how long a connect attempt may go unanswered before it is abandoned and the UI released
        private const val CONNECT_TIMEOUT_MS = 15000L

        //how long to wait for a device to advertise again after a disconnect before giving up on
        //connecting to it
        private const val SIGHTING_TIMEOUT_MS = 15000L
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

    private val scanTimeoutHandler = Handler(Looper.getMainLooper())
    private val scanTimeoutRunnable = Runnable {
        Log.d(TAG, "Scan period elapsed, stopping scan")
        stopScan()
    }

    private val connectHandler = Handler(Looper.getMainLooper())

    //ids of devices disconnected since the scanner last saw them advertise: they are not
    //reachable again until they are
    private val needsSighting = mutableSetOf<String>()

    private var connectTimeout: Runnable? = null
    private var connectingDevice: IDeviceCompat? = null

    //a connect held until the device it is for advertises
    private data class SightingWait(
        val id: String,
        val onSighted: (IDeviceCompat?) -> Unit,
        val timeout: Runnable
    )

    private var sightingWait: SightingWait? = null

    //retires the open connect attempt so a deliberate disconnect does not surface as a failure
    private var retireAttempt: (() -> Unit)? = null

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
        } catch (e: Exception) {
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

        //via stopScan so a timeout armed for the outgoing scanner cannot fire against the new one
        stopScan()

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

            scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable)
            scanTimeoutHandler.postDelayed(scanTimeoutRunnable, SCAN_PERIOD_MS)

            scanner.start(object : IDeviceScanner.OnDeviceFoundListener {
                override fun onScanResult(sender: IDeviceScanner, device: IDeviceCompat) {
                    //log device details
                    val logMsg = "Device found: ${device.name} ID: ${device.id} Type: ${device.type} ScanCount: ${device.scanCount} RSSI: ${device.rssi} Battery: ${device.batteryLevel}"
                    Log.d(TAG, logMsg)

                    val found = NixDevice(
                        id = device.id,
                        name = device.name,
                        device = device,
                        description = "${device.type} (${device.id})"
                    )

                    val known = deviceList.indexOfFirst { it.id == device.id }

                    //the newest handle wins: search() builds a fresh DeviceScanner each time, and
                    //a device object left over from a scanner that has since been stopped never
                    //calls back when connected through
                    if (known < 0) {
                        deviceList.add(found)
                    } else if (deviceList[known].device !== connectedDevice) {
                        deviceList[known] = found
                    }

                    needsSighting.remove(device.id)

                    //a connect waiting on this device now has a handle it can use, and scan
                    //callbacks do not arrive on the main thread
                    sightingWait?.takeIf { it.id == device.id }?.let { waiting ->

                        sightingWait = null

                        connectHandler.removeCallbacks(waiting.timeout)

                        connectHandler.post { waiting.onSighted(found.device) }
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

            stopScan()

            onResult(false)
        }
    }

    fun stopScan() {
        scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable)
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

    /**
     * Opens a connection to [device] and reports the device that connected through [onConnected],
     * or null if the attempt did not get there.
     *
     * A device that has been disconnected since the scanner last saw it is waited on until it
     * advertises again. It only accepts a connection while it is broadcasting, and a Nix takes a
     * few seconds to get back to that after a link drops, so a quick disconnect and reconnect
     * failed outright: the connect went nowhere and nothing called back. The sighting also yields
     * the handle to connect through, the one the live scanner just produced rather than one left
     * over from a scanner that has since been stopped.
     *
     * The attempt itself is then bounded by [CONNECT_TIMEOUT_MS], so one the SDK never answers
     * still hands the UI back instead of hanging on it.
     *
     * [onConnected] is not single shot. It reports the outcome of the attempt, and then any later
     * drop of an established connection, which is how the trait finds its way back to connect UI.
     */
    fun connect(device: IDeviceCompat, onConnected: (IDeviceCompat?) -> Unit) {

        cancelPendingConnect()

        //whatever the previous attempt does from here is no longer this one's concern: without
        //retiring it, its late callback drives the trait UI while this attempt is still running
        retireAttempt?.invoke()
        retireAttempt = null

        //an attempt the SDK never answered still holds the radio, so cancel it before opening
        //another one
        connectingDevice?.let { stale ->

            Log.d(TAG, "Cancelling an unanswered connect to ${stale.name}")

            runCatching { stale.disconnect() }
        }

        connectingDevice = null

        //USB devices never advertise, and a device the scanner has seen since its last disconnect
        //is reachable as it stands: both connect through the handle in hand
        if (device.interfaceType == IDeviceCompat.InterfaceType.USB_CDC ||
            device.id !in needsSighting) {

            beginConnect(handleFor(device), onConnected)

        } else {

            Log.d(TAG, "Waiting for ${device.name} to advertise before connecting")

            awaitSighting(device.id) { sighted ->

                if (sighted == null) {

                    Log.w(TAG, "${device.name} never advertised, abandoning connect")

                    onConnected(null)

                } else {

                    beginConnect(sighted, onConnected)
                }
            }
        }
    }

    /**
     * Calls [onSighted] with the scanner's handle for [id] the next time it advertises, or with
     * null once [SIGHTING_TIMEOUT_MS] has passed without it.
     */
    private fun awaitSighting(id: String, onSighted: (IDeviceCompat?) -> Unit) {

        val timeout = Runnable {
            sightingWait = null
            onSighted(null)
        }

        sightingWait = SightingWait(id, onSighted, timeout)

        //nothing will be sighted unless something is scanning, and a scan already running has to
        //outlast this wait rather than stop itself partway through it
        if (scanner.state == IDeviceScanner.DeviceScannerState.IDLE) {

            search {}

        } else {

            scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable)
            scanTimeoutHandler.postDelayed(scanTimeoutRunnable, SCAN_PERIOD_MS)
        }

        connectHandler.postDelayed(timeout, SIGHTING_TIMEOUT_MS)
    }

    //the scanner's current handle for this device, which is the one worth connecting through
    private fun handleFor(device: IDeviceCompat) =
        deviceList.firstOrNull { it.id == device.id }?.device ?: device

    private fun beginConnect(device: IDeviceCompat, onConnected: (IDeviceCompat?) -> Unit) {

        //scanning from here on only competes with the connection being opened
        stopScan()

        connectingDevice = device

        //null while the attempt is open, then the result it was reported with, so that whichever
        //of the SDK or the timeout arrives first is the one that owns the outcome
        var outcome: Boolean? = null

        //lets a deliberate disconnect close the attempt out without reporting it as a failure
        retireAttempt = { outcome = false }

        val timeout = Runnable {

            if (outcome == null) {

                outcome = false

                connectTimeout = null

                Log.w(TAG, "No answer from ${device.name} in ${CONNECT_TIMEOUT_MS}ms, giving up")

                //also cancels an attempt still in progress, per IDeviceCompat.disconnect
                runCatching { device.disconnect() }

                noteDisconnected(device)

                onConnected(null)
            }
        }

        connectTimeout = timeout

        connectHandler.postDelayed(timeout, CONNECT_TIMEOUT_MS)

        device.connect(object : IDeviceCompat.OnDeviceStateChangeListener {

            override fun onConnected(sender: IDeviceCompat) {

                Log.d(TAG, "Device connected")

                if (outcome == false) {

                    //the attempt was already given up on, so nothing owns this link
                    Log.w(TAG, "Connected after giving up on ${sender.name}, dropping the link")

                    runCatching { sender.disconnect() }

                    return
                }

                outcome = true

                clearConnectTimeout()

                connectingDevice = null

                connectedDevice = sender

                onConnected(sender)
            }

            override fun onDisconnected(sender: IDeviceCompat, status: DeviceStatus) {

                Log.d(TAG, "Device disconnected: $status")

                noteDisconnected(sender)

                //a failed attempt and a dropped connection both leave the trait disconnected, but
                //an attempt already abandoned by the timeout was reported once already
                if (outcome != false) {

                    outcome = false

                    clearConnectTimeout()

                    onConnected(null)
                }
            }

            override fun onBatteryStateChanged(sender: IDeviceCompat, newState: Int) {
                super.onBatteryStateChanged(sender, newState)
            }

            override fun onExtPowerStateChanged(sender: IDeviceCompat, newState: Boolean) {
                super.onExtPowerStateChanged(sender, newState)
            }
        })
    }

    /**
     * Drops the link to [device], or to whatever is connected when none is given.
     */
    fun disconnect(device: IDeviceCompat? = null) {

        cancelPendingConnect()

        stopScan()

        //the user asked for this one, so the open attempt is retired rather than failed
        retireAttempt?.invoke()
        retireAttempt = null

        val target = device ?: connectedDevice

        runCatching { target?.disconnect() }

        //an attempt still open for some other device would otherwise outlive this disconnect
        connectingDevice?.let { runCatching { it.disconnect() } }

        target?.let { requireSighting(it) }

        connectingDevice = null

        connectedDevice = null
    }

    private fun noteDisconnected(device: IDeviceCompat) {

        requireSighting(device)

        if (connectingDevice === device) connectingDevice = null

        if (connectedDevice === device) connectedDevice = null
    }

    /**
     * Marks [device] as out of reach until the scanner sees it advertise again.
     *
     * A Nix takes a few seconds to start advertising after a link drops, and a connect opened
     * before then fails outright with nothing to show for it, which is what a quick disconnect
     * and reconnect ran into. The next connect waits for the device to announce itself instead.
     */
    private fun requireSighting(device: IDeviceCompat) {
        needsSighting.add(device.id)
    }

    /**
     * Drops the wait for a device to advertise and the timeout watching over an attempt already
     * open. Called when the trait goes away, so neither fires into a view that is already gone.
     */
    fun cancelPendingConnect() {

        sightingWait?.let { connectHandler.removeCallbacks(it.timeout) }

        sightingWait = null

        clearConnectTimeout()
    }

    private fun clearConnectTimeout() {

        connectTimeout?.let { connectHandler.removeCallbacks(it) }

        connectTimeout = null
    }
}
