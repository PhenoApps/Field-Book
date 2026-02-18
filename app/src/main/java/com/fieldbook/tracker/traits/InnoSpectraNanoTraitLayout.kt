package com.fieldbook.tracker.traits

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.AttributeSet
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.ISCSDK.ISCNIRScanSDK
import com.ISCSDK.ISCNIRScanSDK.NanoDevice
import com.ISCSDK.ISCNIRScanSDK.SharedPreferencesKeys
import com.ISCSDK.ISCNIRScanSDK.getStringPref
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.basicTimeFormatter
import com.fieldbook.tracker.database.internalTimeFormatter
import com.fieldbook.tracker.devices.spectrometers.Device
import com.fieldbook.tracker.devices.spectrometers.SpectralFrame
import com.fieldbook.tracker.devices.spectrometers.Spectrometer
import com.fieldbook.tracker.devices.spectrometers.innospectra.InnoSpectraBase
import com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces.NanoEventListener
import com.fieldbook.tracker.database.saver.SpectralSaver
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.fieldbook.tracker.utilities.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.OffsetDateTime

class InnoSpectraNanoTraitLayout : SpectralTraitLayout {

    companion object {
        const val TAG = "SpectralTraitLayout"
    }

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var connectedNanoDevice: NanoDevice? = null
    private var deviceList: MutableList<Pair<BluetoothDevice, NanoDevice>> = mutableListOf()
    private var nanoReceiver: InnoSpectraBase? = null
    private var isStarting = false

    private var connection: ServiceConnection? = null

    private val nanoSaver = SpectralSaver(database)

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    private fun <T> LiveData<T>.observeOnce(lifecycleOwner: LifecycleOwner, observer: Observer<T?>) {
        observe(lifecycleOwner, object : Observer<T?> {
            override fun onChanged(value: T?) {
                observer.onChanged(value)
                removeObserver(this)
            }
        })
    }

    override fun type(): String {
        return Formats.INNO_SPECTRA_NANO_SENSOR.getDatabaseName()
    }

    private fun NanoDevice.toDevice() = Device(this).also {
        it.displayableName = this.nanoName
    }

    override fun colorUiMode() { /* only spectral line graphing mode */ }

    override fun loadLayout() {
        super.loadLayout()
        spectralUiMode()
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

    override fun startDeviceSearch() {

        val deviceId = controller.getPreferences().getString(GeneralKeys.INNOSPECTRA_NANO_DEVICE_ID, "") ?: ""
        val deviceName = controller.getPreferences().getString(GeneralKeys.INNOSPECTRA_NANO_DEVICE_NAME, "") ?: ""

        if (deviceId.isNotBlank() && deviceName.isNotBlank()) {

            setupDeviceSearch()

            scheduleDeviceSearch(deviceName, deviceId) { data ->

                val device = data.first as BluetoothDevice
                val nanoDevice = data.second as NanoDevice

                // wrap as Device so it can be handled by connectDevice(Device)
                connectDevice(Device(device to nanoDevice))
            }

        }
        else {

            setupDeviceSearch()
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

        if (!isLocked) {

            val viewLifecycleOwner = (context as CollectActivity)

            (context as CollectActivity).innoSpectraViewModel?.scan(context, false)?.observeOnce(viewLifecycleOwner) {

                it?.let { frames ->
                    Log.d(TAG, "Frames: $frames")
                    for (f in frames) {
                        Log.d(TAG, "Frame: ${f.rawData}")
                        val values = f.rawData.joinToString(" ") { it.toString() }
                        val wavelengths = f.data.joinToString(" ") { it.toString() }
                        val timestamp = OffsetDateTime.now().format(basicTimeFormatter)

                        // build SpectralFrame with correct parameter order
                        val frame = SpectralFrame(
                            values = values,
                            wavelengths = wavelengths,
                            color = "", // InnoSpectra frame does not provide color here
                            timestamp = timestamp,
                            entryId = entryId,
                            traitId = traitId
                        )

                        // write spectral data to file and database via saver
                        writeSpectralDataToFile("inno_spectra", frame, true)?.let { spectralUri ->
                            writeSpectralDataToDatabase(frame, "", spectralUri, entryId, traitId)
                        }

                        enableCapture(device)

                        Log.d(TAG, "Spectral Data queued for save")
                    }
                }
            }
        }

        (context as CollectActivity).innoSpectraViewModel?.setEventListener {
            Log.d(TAG, "Event listener called")
        }
    }

    private fun scheduleDeviceInfoCheck(onInfoReceived: suspend () -> Unit) {
        background.launch {
            while (true) {
                val config = (context as CollectActivity).innoSpectraViewModel?.getActiveConfig()
                if (config != null) {
                    onInfoReceived()
                    break
                }
                delay(500L)
            }
        }
    }

    private fun scheduleDeviceConnection() {
        background.launch {
            while (true) {
                val connected = (context as CollectActivity).innoSpectraViewModel?.isConnected()
                if (connected == false) {
                    // when the view model reports disconnected, update UI
                    withContext(Dispatchers.Main) {
                        connectButton?.visibility = VISIBLE
                        captureButton?.visibility = GONE
                        disconnectButton?.visibility = GONE
                        progressBar?.visibility = GONE
                    }
                    break
                }
                delay(250L)
            }
        }
    }

    private fun scheduleDeviceSearch(deviceName: String, macAddress: String, onDeviceFound: suspend (Pair<*, *>) -> Unit) {
        background.launch {
            while (true) {
                val device = deviceList.firstOrNull { deviceData ->
                    deviceData.second.nanoMac == macAddress && deviceData.second.nanoName == deviceName
                }
                if (device != null) {
                    onDeviceFound(device)
                    break
                }
                delay(500L)
            }
        }
    }

    override fun showSettings() {
        var dialog: android.app.AlertDialog? = null

        // if we have a connected nano device, show device info in settings
        connectedNanoDevice?.let { nano ->

            // create settings view using the new InnoSpectra settings view
            val settingsView = com.fieldbook.tracker.views.InnoSpectraNanoSettingsView(context, nano) {
                // onDisconnect callback
                if (isLocked) {
                    return@InnoSpectraNanoSettingsView
                }

                // perform disconnect and erase saved device
                disconnectAndEraseDevice(Device(nano))

                setupConnectUi()

                dialog?.dismiss()
            }

            dialog = android.app.AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(context.getString(R.string.traits_format_inno_spectra_nano_sensor))
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
                        progressBar?.visibility = VISIBLE
                        connectButton?.visibility = GONE
                        (context as CollectActivity).innoSpectraViewModel?.setBluetoothDevice(context, bt)

                        beginConnection(it)
                    }
                }
            }
        }
    }

    private fun beginConnection(device: NanoDevice) {

        connection = buildInnoSpectraServiceConnection { sdk ->

            if (sdk.connect(device.nanoMac)) {

                val configIndex = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(GeneralKeys.INNOSPECTRA_NANO_CONFIG_INDEX, 0)

                ISCNIRScanSDK.SetActiveConfig(byteArrayOf(configIndex.toByte(), (configIndex/256).toByte()))
            }
        }

        if (!isStarting) {
            isStarting = true
            val gattService = Intent(context, ISCNIRScanSDK::class.java)
            context.bindService(gattService, connection!!, Context.BIND_AUTO_CREATE)

            ((context as CollectActivity).innoSpectraViewModel as? NanoEventListener)?.let { listener ->
                nanoReceiver = InnoSpectraBase(listener).also {
                    it.register(context)
                }
            }
        }
    }

    private fun endConnection() {
        try {
            connection?.let { context?.unbindService(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

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

    override fun enableCapture(device: Device) {
        super.enableCapture(device)
        scheduleDeviceConnection()
    }

    override fun disconnectAndEraseDevice(device: Device) {
        if (!isLocked) {
            isStarting = false
            endConnection()
            controller.getPreferences().edit {
                remove(GeneralKeys.INNOSPECTRA_NANO_DEVICE_ID)
                remove(GeneralKeys.INNOSPECTRA_NANO_DEVICE_NAME)
            }
            connectButton?.visibility = VISIBLE
            captureButton?.visibility = GONE
            disconnectButton?.visibility = GONE
            setupConnectButton()
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

        val nanoName = getStringPref(
            context,
            SharedPreferencesKeys.DeviceFilter,
            "NIR"
        )

        result.scanRecord?.deviceName?.let { name ->

            if (name.contains(nanoName)) {

                result.scanRecord?.let { record ->

                    val nanoDevice = NanoDevice(
                        device,
                        result.rssi,
                        record.bytes,
                        nanoName
                    )

                    if (!deviceList.map { it.second.nanoMac }.contains(nanoDevice.nanoMac)) {

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

        scanner.startScan(object : ScanCallback() {

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
        })
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
        val createdAt = OffsetDateTime.now().format(basicTimeFormatter)

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