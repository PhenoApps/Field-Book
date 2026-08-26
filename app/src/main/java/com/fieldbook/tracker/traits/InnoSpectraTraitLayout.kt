package com.fieldbook.tracker.traits

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.AttributeSet
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.ISCSDK.ISCNIRScanSDK
import com.ISCSDK.ISCNIRScanSDK.NanoDevice
import com.ISCSDK.ISCNIRScanSDK.SharedPreferencesKeys
import com.ISCSDK.ISCNIRScanSDK.getStringPref
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.internalTimeFormatter
import com.fieldbook.tracker.devices.spectrometers.Device
import com.fieldbook.tracker.devices.spectrometers.SpectralFrame
import com.fieldbook.tracker.devices.spectrometers.Spectrometer
import com.fieldbook.tracker.devices.spectrometers.innospectra.InnoSpectraBase
import com.fieldbook.tracker.devices.spectrometers.innospectra.InnoSpectraViewModel
import com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces.NanoEventListener
import com.fieldbook.tracker.devices.spectrometers.innospectra.models.Frame
import com.fieldbook.tracker.database.saver.SpectralSaver
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.formats.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.threeten.bp.OffsetDateTime
import java.util.UUID
import kotlin.jvm.java

class InnoSpectraTraitLayout : SpectralTraitLayout {

    companion object {
        const val TAG = "SpectralTraitLayout"

        //how long a device scan may run before it stops itself
        private const val SCAN_PERIOD_MS = 30000L

        //how long to wait for the device to report its scan config after connecting
        private const val DEVICE_INFO_TIMEOUT_MS = 30000L

        //how long a measurement may run before the capture UI is restored anyway
        private const val CAPTURE_TIMEOUT_MS = 20000L

        private const val DEVICE_INFO_POLL_MS = 500L
        private const val DEVICE_SEARCH_POLL_MS = 500L

        private const val DEFAULT_DEVICE_FILTER = "NIR"
    }

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var connectedNanoDevice: NanoDevice? = null
    private var deviceList: MutableList<Pair<BluetoothDevice, NanoDevice>> = mutableListOf()
    private var nanoReceiver: InnoSpectraBase? = null

    //notices the link dropping, registered and unregistered with nanoReceiver
    private var gattDisconnectReceiver: BroadcastReceiver? = null

    private var isStarting = false

    //held so the scan can actually be stopped again; non-null means a scan is running.
    //volatile because the scan is started on the main thread but stopDeviceScan() is also reached
    //from the background auto-reconnect path in scheduleDeviceSearch -> connectDevice
    @Volatile
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    @Volatile
    private var scanCallback: ScanCallback? = null

    //name filter for the running scan, read once per scan rather than per advertisement
    @Volatile
    private var scanNameFilter: String = DEFAULT_DEVICE_FILTER

    private var deviceSearchJob: kotlinx.coroutines.Job? = null
    private var deviceInfoCheckJob: kotlinx.coroutines.Job? = null
    private var captureTimeoutJob: kotlinx.coroutines.Job? = null

    @Volatile
    private var scanTimeoutJob: kotlinx.coroutines.Job? = null

    private var connection: ServiceConnection? = null

    private val nanoSaver = SpectralSaver(database)

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    override fun type(): String {
        return Formats.INNO_SPECTRA_SENSOR.getDatabaseName()
    }

    private var lastProcessedFrameHash: String = "-1"
    private var hardwareButtonObserver: Observer<Frame?>? = null

    private fun observeHardwareButtonScans() {
        val activity = (context as? CollectActivity) ?: return
        val viewModel = activity.innoSpectraViewModel ?: return

        // Only set up observer once
        if (hardwareButtonObserver != null) {
            return
        }

        // Create observer that only saves new frames from hardware button
        hardwareButtonObserver = Observer { frame ->
            if (frame != null && !isLocked && !viewModel.isUiScan()) {
                //avoid duplication
                val frameId = UUID.randomUUID().toString()

                // Only save if this is a new frame (different hash from last processed)
                if (frameId != lastProcessedFrameHash) {
                    val entryId = currentRange?.uniqueId
                    val traitId = currentTrait?.id

                    if (entryId != null && traitId != null) {
                        saveSpectralFrame(frame, entryId, traitId)
                        lastProcessedFrameHash = frameId

                        captureTimeoutJob?.cancel()
                        captureTimeoutJob = null

                        // Restore the capture UI. This is the completion point for both hardware
                        // and UI triggered scans, so it has to re-enable the button as well as
                        // drop the overlay.
                        connectedNanoDevice?.let { nano ->
                            enableCapture(nano.toDevice())
                        } ?: toggleProgressBar(false)
                    }
                }
            } else if (frame == null) {
                lastProcessedFrameHash = "-1"
            }
        }

        // Register observer only once
        hardwareButtonObserver?.let { observer ->
            viewModel.getSpectralData().observe(activity, observer)
        }
    }

    private var connectionProgressObserver: Observer<InnoSpectraViewModel.ConnectionProgress?>? = null

    /**
     * Drives the determinate arc from the connection handshake.
     *
     * Registered once, like the hardware scan observer, so a reconnect cannot stack a second one.
     */
    private fun observeConnectionProgress() {

        val activity = (context as? CollectActivity) ?: return
        val viewModel = activity.innoSpectraViewModel ?: return

        if (connectionProgressObserver != null) return

        connectionProgressObserver = Observer { progress ->

            if (progress == null) return@Observer

            val label = context.getString(labelFor(progress.stage))

            //a stage with no reading of its own leaves the indicator spinning
            progress.percent?.let { percent ->
                setProgressPercent(percent, label)
            } ?: setProgressLabel(label)
        }

        connectionProgressObserver?.let { observer ->
            viewModel.getConnectionProgress().observe(activity, observer)
        }
    }

    private fun labelFor(stage: InnoSpectraViewModel.ConnectionStage) = when (stage) {
        InnoSpectraViewModel.ConnectionStage.CONNECTING -> R.string.inno_spectra_connect_connecting
        InnoSpectraViewModel.ConnectionStage.HANDSHAKE -> R.string.inno_spectra_connect_handshake
        InnoSpectraViewModel.ConnectionStage.DEVICE_INFO -> R.string.inno_spectra_connect_device_info
        InnoSpectraViewModel.ConnectionStage.DEVICE_UUID -> R.string.inno_spectra_connect_device_uuid
        InnoSpectraViewModel.ConnectionStage.DEVICE_STATUS -> R.string.inno_spectra_connect_device_status
        InnoSpectraViewModel.ConnectionStage.SCAN_CONFIGS -> R.string.inno_spectra_connect_scan_configs
        InnoSpectraViewModel.ConnectionStage.READY -> R.string.inno_spectra_connect_ready
    }

    private fun setupScanStartedListener() {
        val activity = (context as? CollectActivity) ?: return
        val viewModel = activity.innoSpectraViewModel ?: return

        viewModel.setScanStartedListener {
            // Show progress bar when any scan starts (hardware or software)
            toggleProgressBar(true)
        }
    }

    private fun NanoDevice.toDevice() = Device(this).also {
        it.displayableName = this.nanoName
    }

    override fun colorUiMode() { /* only spectral line graphing mode */ }

    override fun loadLayout() {
        super.loadLayout()
        spectralUiMode()

        // Observe spectral data from hardware button scans and auto-save them
        observeHardwareButtonScans()

        // Drive the determinate arc from the connection handshake
        observeConnectionProgress()

        // Set up listener to show progress bar when any scan starts
        setupScanStartedListener()
    }

    /**
     * Releases the BLE scan, the SDK receivers and the service binding.
     *
     * None of these are tied to the view lifecycle: the scan is registered with the Bluetooth
     * stack, the receivers with an application scoped LocalBroadcastManager, and the binding with
     * the activity manager. Left alone they survive the activity that created them.
     *
     * The device is disconnected rather than handed on, because the ViewModel holding the
     * connection state is activity scoped: a new CollectActivity gets a fresh one that reports
     * disconnected regardless, so keeping the GATT link open would only orphan it.
     */
    override fun onDestroy() {
        endConnection()
        super.onDestroy()
    }

    override fun setupConnectUi() {
        // Cancel any ongoing device search
        deviceSearchJob?.cancel()
        deviceSearchJob = null

        deviceInfoCheckJob?.cancel()
        deviceInfoCheckJob = null

        // Reset connection state
        isStarting = false

        // Set up UI for disconnected state
        background.launch(Dispatchers.Main) {
            connectButton?.visibility = VISIBLE
            captureButton?.visibility = GONE
            disconnectButton?.visibility = GONE
            settingsButton?.visibility = GONE
            toggleProgressBar(false)
            startDeviceSearch()
            //arriving at the trait disconnected still reconnects on its own
            attemptAutoReconnect()
            setupConnectButton()
        }
    }

    override fun establishConnection(): Boolean {
        val connected = (context as CollectActivity).innoSpectraViewModel?.isConnected() == true

        if (connected) {
            connectedNanoDevice?.let { nano ->
                enableCapture(nano.toDevice())
            }
        }

        return connected
    }

    /**
     * Starts scanning only.
     *
     * Auto-reconnect deliberately does not live here. This is also what the connect button calls,
     * and SpectralTraitLayout follows it immediately with the device picker, so doing both meant
     * pressing connect raced a background reconnect against the user's own selection: the spinner
     * and the picker came up together, and whichever won left the other stale. Arriving at the
     * trait disconnected still auto-reconnects, from setupConnectUi().
     */
    override fun startDeviceSearch() {
        setupDeviceSearch()
    }

    /**
     * Reconnects to the saved device once it turns up in the scan results.
     */
    private fun attemptAutoReconnect() {

        val deviceId = controller.getPreferences().getString(GeneralKeys.INNOSPECTRA_NANO_DEVICE_ID, "") ?: ""
        val deviceName = controller.getPreferences().getString(GeneralKeys.INNOSPECTRA_NANO_DEVICE_NAME, "") ?: ""

        if (deviceId.isBlank() || deviceName.isBlank()) return

        scheduleDeviceSearch(deviceName, deviceId) { data ->

            val device = data.first as BluetoothDevice
            val nanoDevice = data.second as NanoDevice

            // wrap as Device so it can be handled by connectDevice(Device)
            connectDevice(Device(device to nanoDevice))
        }
    }

    private fun setupDeviceSearch() {

        background.launch {
            scanForDevices()
        }

        //schedule a checker to see when device info is found
        scheduleDeviceInfoCheck {

            withContext(Dispatchers.Main) {

                // create a Device wrapper from connected pieces if available
                bluetoothDevice?.let { bt ->
                    connectedNanoDevice?.let { nano ->
                        enableCapture(Device(bt to nano))
                    } ?: run {
                        // fallback: try to enable without a concrete device (graceful)
                        // If enableCapture requires a device, we skip until device connected
                        Log.d(TAG, "Connected nano device not yet available")
                    }
                }

            }
        }
    }

    override fun capture(
        device: Device,
        entryId: String,
        traitId: String,
        callback: Spectrometer.ResultCallback
    ) {

        if (isLocked) {
            // If locked, immediately callback with failure
            enableCapture(device)
            callback.onResult(false)
            return
        }

        val viewModel = (context as CollectActivity).innoSpectraViewModel

        if (viewModel == null || !viewModel.isReadyToScan()) {
            Log.w(TAG, "capture: device is not ready to scan")
            enableCapture(device)
            callback.onResult(false)
            return
        }

        // scan() starts the measurement. The frame it produces is handled by
        // observeHardwareButtonScans(), which is the single place a scan is saved no matter
        // whether the UI or the hardware button triggered it.
        //
        // This used to observe the returned LiveData as well, but scan() publishes a null to
        // reset itself before starting, and that null arrived immediately and was read as a
        // failed capture. The result was a visible flash: the overlay came up on the button
        // press, was torn straight back down by that phantom failure, then came up again when
        // the scan actually started.
        viewModel.scan(context, false)

        callback.onResult(true)

        startCaptureTimeout(device)
    }

    /**
     * Restores the UI if a measurement never reports back.
     *
     * The busy overlay swallows touches, so a scan that silently never completes would otherwise
     * leave the trait unusable until the user navigates away.
     */
    private fun startCaptureTimeout(device: Device) {

        captureTimeoutJob?.cancel()

        captureTimeoutJob = background.launch {

            delay(CAPTURE_TIMEOUT_MS)

            withContext(Dispatchers.Main) {

                Log.w(TAG, "capture: no frame received within timeout, restoring the capture UI")

                enableCapture(device)

                //drop the placeholder row that was added when the capture started
                submitList()
            }
        }
    }


    private fun saveSpectralFrame(frame: Frame, entryId: String, traitId: String) {

        val values = frame.rawData.joinToString(" ") { it.toString() }
        val wavelengths = frame.data.joinToString(" ") { it.toString() }
        val timestamp = OffsetDateTime.now().format(internalTimeFormatter)

        // Build SpectralFrame
        val spectralFrame = SpectralFrame(
            values = values,
            wavelengths = wavelengths,
            color = "",
            timestamp = timestamp,
            entryId = entryId,
            traitId = traitId
        )

        val deviceAddress = connectedNanoDevice?.nanoMac ?: return

        // Write to file and database
        writeSpectralDataToFile("inno_spectra_$deviceAddress", spectralFrame, true)?.let { spectralUri ->
            writeSpectralDataToDatabase(spectralFrame, "", spectralUri, entryId, traitId)
        }
    }

    private fun scheduleDeviceInfoCheck(onInfoReceived: suspend () -> Unit) {

        // Cancel any existing check first
        deviceInfoCheckJob?.cancel()

        deviceInfoCheckJob = background.launch {

            // Bounded: if the config never arrives the device is not talking to us, and an
            // unbounded loop here would keep polling for the life of the process.
            val config = withTimeoutOrNull(DEVICE_INFO_TIMEOUT_MS) {

                var activeConfig = (context as CollectActivity).innoSpectraViewModel?.getActiveConfig()

                while (activeConfig == null) {
                    delay(DEVICE_INFO_POLL_MS)
                    activeConfig = (context as CollectActivity).innoSpectraViewModel?.getActiveConfig()
                }

                activeConfig
            }

            if (config != null) {
                onInfoReceived()
            } else {
                Log.w(TAG, "scheduleDeviceInfoCheck: timed out waiting for device config")
            }
        }
    }

    /**
     * Handles the SDK's GATT disconnect broadcast.
     *
     * This replaces a `while (true)` loop that asked the ViewModel for the connection state every
     * 250ms, which meant a Bluetooth binder call four times a second for as long as the process
     * lived. The SDK already tells us when the link drops, so there is nothing to poll for.
     *
     * Deliberately does not try to reconnect. Reconnecting means scanning, and a standing scan is
     * what made the app unresponsive in the first place. Returning to the connect UI and letting
     * the collector re-select the device is the intended behaviour after a mid-session drop;
     * arriving at the trait with a saved device still connects on its own via setupConnectUi().
     */
    private fun onGattDisconnected() {

        if (isLocked) return

        Log.d(TAG, "onGattDisconnected: device reported disconnect, tearing down")

        isStarting = false
        endConnection()

        connectButton?.visibility = VISIBLE
        captureButton?.visibility = GONE
        toggleProgressBar(false)
        settingsButton?.visibility = GONE
    }

    private fun scheduleDeviceSearch(deviceName: String, macAddress: String, onDeviceFound: suspend (Pair<*, *>) -> Unit) {
        // Cancel any existing search job to prevent duplicates
        deviceSearchJob?.cancel()

        deviceSearchJob = background.launch {

            // Bounded by the scan period: once the scan stops, deviceList cannot grow, so polling
            // past that point would never succeed.
            val device = withTimeoutOrNull(SCAN_PERIOD_MS) {

                var found: Pair<BluetoothDevice, NanoDevice>? = null

                while (found == null) {

                    // Check if already starting to prevent race conditions
                    if (isStarting) {
                        Log.d(TAG, "scheduleDeviceSearch: connection already starting, stopping search")
                        break
                    }

                    found = deviceList.firstOrNull { deviceData ->
                        deviceData.second.nanoMac == macAddress && deviceData.second.nanoName == deviceName
                    }

                    if (found == null) delay(DEVICE_SEARCH_POLL_MS)
                }

                found
            }

            if (device != null) {
                onDeviceFound(device)
            } else {
                Log.d(TAG, "scheduleDeviceSearch: $deviceName not found within the scan period")
            }
        }
    }

    override fun showSettings() {
        var dialog: android.app.AlertDialog? = null

        // if we have a connected nano device, show device info in settings
        connectedNanoDevice?.let { nano ->

            // Get device info and status from ViewModel
            val viewModel = (context as CollectActivity).innoSpectraViewModel
            val deviceInfo = viewModel?.getDeviceInfo()
            val deviceStatus = viewModel?.getDeviceStatusObject()

            val firmwareVersion = deviceInfo?.softwareVersion ?: context.getString(R.string.unknown)
            val batteryLevel = deviceStatus?.battery ?: context.getString(R.string.unknown)

            // create settings view using the new InnoSpectra settings view
            val settingsView = com.fieldbook.tracker.views.InnoSpectraSettingsView(context, nano, firmwareVersion, batteryLevel) {
                // onDisconnect callback
                if (isLocked) {
                    return@InnoSpectraSettingsView
                }

                // perform disconnect and erase saved device
                disconnectAndEraseDevice(Device(nano))

                setupConnectUi()

                dialog?.dismiss()
            }

            dialog = android.app.AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(context.getString(R.string.traits_format_inno_spectra))
                .setView(settingsView)
                .setPositiveButton(android.R.string.ok) { d, _ ->
                    onSettingsChanged()
                    d.dismiss()
                }
                .create()

            dialog?.show()
        }
    }

    override fun connectDevice(device: Device) {

        // deviceImplementation is expected to be Pair<BluetoothDevice, NanoDevice>
        val deviceData = device.deviceImplementation as? Pair<*, *>

        val bt = deviceData?.first as? BluetoothDevice
        val nano = deviceData?.second as? NanoDevice

        if (bt == null || nano == null) {
            Log.e(TAG, "connectDevice: invalid device payload")
            return
        }

        // If already in the process of connecting, prevent duplicate attempts
        if (isStarting) {
            Log.w(TAG, "connectDevice: connection already in progress, ignoring duplicate attempt")
            return
        }

        // We have the device we were looking for, so stop scanning. Leaving the scan running
        // competes with the GATT connection we are about to open.
        stopDeviceScan()

        // persist selection
        controller.getPreferences().edit {
            putString(GeneralKeys.INNOSPECTRA_NANO_DEVICE_ID, nano.nanoMac)
            putString(GeneralKeys.INNOSPECTRA_NANO_DEVICE_NAME, nano.nanoName)
        }

        // save references for later use
        bluetoothDevice = bt
        connectedNanoDevice = nano

        background.launch {
            withContext(Dispatchers.Main) {
                controller.getSecurityChecker().withNearby {
                    nano.let {
                        //the handshake reports each step, so this one is determinate
                        toggleProgressBar(
                            true,
                            determinate = true,
                            label = context.getString(R.string.inno_spectra_connect_connecting)
                        )

                        connectButton?.visibility = GONE

                        (context as CollectActivity).innoSpectraViewModel?.let { vm ->
                            vm.setBluetoothDevice(context, bt)
                            vm.startConnectionProgress()
                        }

                        beginConnection(it)
                    }
                }
            }
        }
    }

    private fun beginConnection(device: NanoDevice) {

        // Ensure previous service connection is cleaned up before starting new one
        try {
            if (connection != null) {
                context?.unbindService(connection!!)
                Log.d(TAG, "beginConnection: cleaned up previous service connection")
            }
        } catch (e: Exception) {
            Log.d(TAG, "beginConnection: no previous connection to clean up - ${e.message}")
        }

        connection = buildInnoSpectraServiceConnection { sdk ->

            if (sdk.connect(device.nanoMac)) {
                Log.d(TAG, "SDK connection initiated for device: ${device.nanoMac}")

                val configIndex = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(GeneralKeys.INNOSPECTRA_NANO_CONFIG_INDEX, 0)

                ISCNIRScanSDK.SetActiveConfig(byteArrayOf(configIndex.toByte(), (configIndex/256).toByte()))
            } else {
                Log.e(TAG, "SDK failed to initiate connection for device: ${device.nanoMac}")
                // Reset state on connection failure
                isStarting = false
                background.launch(Dispatchers.Main) {
                    setupConnectUi()
                }
            }
        }

        if (!isStarting) {
            isStarting = true
            Log.d(TAG, "beginConnection: binding to GATT service for device ${device.nanoMac}")

            try {
                val gattService = Intent(context, ISCNIRScanSDK::class.java)
                context.bindService(gattService, connection!!, Context.BIND_AUTO_CREATE)

                ((context as CollectActivity).innoSpectraViewModel as? NanoEventListener)?.let { listener ->
                    // Drop any previously registered set before creating a new one. This is the
                    // only place receivers are registered, so unregistering here is what keeps a
                    // reconnect from stacking a second set on top of the first.
                    unregisterNanoReceiver()
                    nanoReceiver = InnoSpectraBase(listener).also {
                        it.register(context)
                        Log.d(TAG, "beginConnection: registered InnoSpectraBase receiver")
                    }
                    registerGattDisconnectReceiver()
                }
            } catch (e: Exception) {
                Log.e(TAG, "beginConnection: failed to bind service - ${e.message}")
                isStarting = false
                background.launch(Dispatchers.Main) {
                    setupConnectUi()
                }
            }
        } else {
            Log.w(TAG, "beginConnection: connection already starting, ignoring duplicate attempt")
        }
    }

    /**
     * Unregisters the active [InnoSpectraBase] receiver set and the GATT disconnect receiver,
     * if they are registered.
     *
     * The receivers are registered against LocalBroadcastManager, which is application scoped, so
     * nothing removes them implicitly when this view or the activity goes away. A stale set means
     * every SDK broadcast is handled once per set, and because several of the handlers respond by
     * issuing a BLE command that itself broadcasts a reply, duplicate sets compound on each round
     * instead of costing a constant amount.
     */
    private fun unregisterNanoReceiver() {

        try {
            nanoReceiver?.unregister(context)
        } catch (e: Exception) {
            Log.d(TAG, "Error unregistering nanoReceiver: ${e.message}")
        }
        nanoReceiver = null

        try {
            gattDisconnectReceiver?.let {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(it)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error unregistering gattDisconnectReceiver: ${e.message}")
        }
        gattDisconnectReceiver = null
    }

    /**
     * Registers the receiver that notices the device dropping its connection.
     *
     * Kept on the same lifetime as [nanoReceiver] so the two cannot get out of step.
     */
    private fun registerGattDisconnectReceiver() {

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                onGattDisconnected()
            }
        }

        gattDisconnectReceiver = receiver

        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            IntentFilter(ISCNIRScanSDK.ACTION_GATT_DISCONNECTED)
        )
    }

    private fun endConnection() {
        // Cancel ongoing device search to prevent reconnection attempts
        deviceSearchJob?.cancel()
        deviceSearchJob = null

        deviceInfoCheckJob?.cancel()
        deviceInfoCheckJob = null

        captureTimeoutJob?.cancel()
        captureTimeoutJob = null

        // Stop any scan started while looking for this device
        stopDeviceScan()

        // Reset connection state
        isStarting = false

        // Unregister the broadcast receivers to prevent stale listeners
        unregisterNanoReceiver()

        try {
            connection?.let { context?.unbindService(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        connection = null
        bluetoothDevice = null
        connectedNanoDevice = null

        (context as CollectActivity).innoSpectraViewModel?.disconnect(context)
    }

    override fun saveDevice(device: Device) {
        // persist device info in preferences so it can be auto-reconnected
        val deviceData = device.deviceImplementation as? Pair<*, *>
        val nano = deviceData?.second as? NanoDevice
        nano?.let {
            controller.getPreferences().edit {
                putString(GeneralKeys.INNOSPECTRA_NANO_DEVICE_ID, it.nanoMac)
                putString(GeneralKeys.INNOSPECTRA_NANO_DEVICE_NAME, it.nanoName)
            }
        }
    }

    override fun disconnectAndEraseDevice(device: Device) {
        if (!isLocked) {
            isStarting = false
            endConnection()
            controller.getPreferences().edit {
                remove(GeneralKeys.INNOSPECTRA_NANO_DEVICE_ID)
                remove(GeneralKeys.INNOSPECTRA_NANO_DEVICE_NAME)
            }

            background.launch(Dispatchers.Main) {
                connectButton?.visibility = VISIBLE
                captureButton?.visibility = GONE
                disconnectButton?.visibility = GONE
                settingsButton?.visibility = GONE
                setupConnectButton()
            }
        }
    }

    private fun scanForDevices() {

        bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager?.adapter
        val scanner = adapter?.bluetoothLeScanner

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Request both SCAN and CONNECT on Android S+ because some secure helpers
            // (withNearby/withPermission) may access bonded devices which require
            // BLUETOOTH_CONNECT at runtime. Requesting both prevents SecurityException.
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        controller.getSecurityChecker().withPermission(permissions) {

            scanner?.let { s ->
                startScan(s)
            }
        }
    }

    private fun parseScanDeviceResult(result: ScanResult, device: BluetoothDevice) {

        //read once when the scan starts, this runs for every advertisement received
        val nanoName = scanNameFilter

        result.scanRecord?.deviceName?.let { name ->

            if (name.contains(nanoName)) {

                result.scanRecord?.let { record ->

                    val nanoDevice = NanoDevice(
                        device,
                        result.rssi,
                        record.bytes,
                        nanoName
                    )

                    if (deviceList.none { it.second.nanoMac == nanoDevice.nanoMac }) {

                        deviceList.add(device to nanoDevice)

                    }
                }
            }
        }
    }

    private fun startScan(scanner: BluetoothLeScanner) {

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

        // A scan is already running. Starting another would register a second callback with the
        // Bluetooth stack that nothing would ever stop, so extend the current one instead.
        scanCallback?.let { running ->
            Log.d(TAG, "startScan: scan already running, extending scan period")
            scheduleScanTimeout(running)
            return
        }

        // Constant for the life of the scan, so read it here instead of on every advertisement.
        scanNameFilter = getStringPref(
            context,
            SharedPreferencesKeys.DeviceFilter,
            DEFAULT_DEVICE_FILTER
        )

        val callback = object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)

                try {

                    result?.device?.let { device ->

                        parseScanDeviceResult(result, device)
                    }

                } catch (e: SecurityException) {

                    e.printStackTrace()

                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)

                Log.e(TAG, "startScan: scan failed with error code $errorCode")

                stopDeviceScan()
            }
        }

        bluetoothLeScanner = scanner
        scanCallback = callback

        try {
            scanner.startScan(callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "startScan: missing permission - ${e.message}")
            bluetoothLeScanner = null
            scanCallback = null
            return
        }

        scheduleScanTimeout(callback)
    }

    /**
     * Arms the timeout that stops the running scan.
     *
     * Without it a device that never appears leaves the radio scanning, and the callback
     * delivering onto the main thread, for the rest of the process. Re-arming on each request is
     * what lets a repeated search extend the window rather than start a second scan.
     */
    private fun scheduleScanTimeout(callback: ScanCallback) {

        scanTimeoutJob?.cancel()

        scanTimeoutJob = background.launch {

            delay(SCAN_PERIOD_MS)

            withContext(Dispatchers.Main) {

                //only stop the scan this timeout was armed for
                if (scanCallback === callback) {

                    Log.d(TAG, "scheduleScanTimeout: scan period elapsed, stopping scan")

                    stopDeviceScan()
                }
            }
        }
    }

    /**
     * Stops the running BLE scan, if there is one.
     *
     * BluetoothLeScanner holds a callback until it is explicitly stopped and delivers results on
     * the main looper, so an abandoned scan keeps doing UI thread work for the life of the
     * process and degrades the throughput of the connections that are already established.
     * Android also caps how many scanners a single app may register.
     */
    private fun stopDeviceScan() {

        scanTimeoutJob?.cancel()
        scanTimeoutJob = null

        val scanner = bluetoothLeScanner
        val callback = scanCallback

        bluetoothLeScanner = null
        scanCallback = null

        if (scanner == null || callback == null) return

        try {
            scanner.stopScan(callback)
            Log.d(TAG, "stopDeviceScan: scan stopped")
        } catch (e: SecurityException) {
            Log.d(TAG, "stopDeviceScan: missing permission - ${e.message}")
        } catch (e: IllegalStateException) {
            //adapter turned off underneath us
            Log.d(TAG, "stopDeviceScan: adapter unavailable - ${e.message}")
        }
    }

    private fun buildInnoSpectraServiceConnection(onSdkInitialized: (ISCNIRScanSDK) -> Unit): ServiceConnection =
        object : ServiceConnection {

            override fun onServiceConnected(name: ComponentName, service: IBinder) {

                (service as? ISCNIRScanSDK.LocalBinder)?.service?.let { sdk ->

                    sdk.initialize()

                    onSdkInitialized(sdk)

                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {

            }
        }

    override fun getDeviceList(): List<Device> {
        return deviceList.map { pair ->
            Device(pair).also {
                it.displayableName = "${pair.second.nanoName} (${pair.second.nanoMac})"
            }
        }
    }

    override fun writeSpectralDataToDatabase(
        frame: SpectralFrame,
        color: String,
        uri: String,
        entryId: String,
        traitId: String
    ) {

        val deviceAddress = controller.getPreferences().getString("nano_device_id", "") ?: ""
        val deviceName = controller.getPreferences().getString("nano_device_name", "") ?: ""
        val studyId = collectActivity.studyId
        val person = (context as? CollectActivity)?.person
        val location = (context as? CollectActivity)?.locationByPreferences
        val comment: String? = null
        val createdAt = OffsetDateTime.now().format(internalTimeFormatter)

        background.launch {

            nanoSaver.saveData(
                SpectralSaver.RequiredData(
                    viewModel = controller.getSpectralViewModel(),
                    deviceAddress = deviceAddress,
                    deviceName = deviceName,
                    studyId = studyId,
                    person = person.toString(),
                    location = location.toString(),
                    comment = comment,
                    createdAt = createdAt,
                    frame = frame,
                    color = color,
                    uri = uri,
                    entryId = entryId,
                    traitId = traitId
                )
            ).onSuccess { fact ->
                submitNewSpectralFact(fact)
            }.onFailure { exception ->
                Log.e(TAG, "Failed to save spectral data", exception)
            }
        }
    }
}