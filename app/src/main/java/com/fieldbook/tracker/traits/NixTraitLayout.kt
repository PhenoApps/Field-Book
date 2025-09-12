package com.fieldbook.tracker.traits

import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.basicTimeFormatter
import com.fieldbook.tracker.database.saver.NixSpectralSaver
import com.fieldbook.tracker.devices.spectrometers.Device
import com.fieldbook.tracker.devices.spectrometers.SpectralFrame
import com.fieldbook.tracker.devices.spectrometers.Spectrometer.ResultCallback
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.SpectralTraitLayout.State.Color
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.NixSensorHelper
import com.fieldbook.tracker.views.NixTraitSettingsView
import com.nixsensor.universalsdk.CommandStatus
import com.nixsensor.universalsdk.DeviceState
import com.nixsensor.universalsdk.IDeviceCompat
import com.nixsensor.universalsdk.IDeviceScanner
import com.nixsensor.universalsdk.IMeasurementData
import com.nixsensor.universalsdk.OnDeviceResultListener
import com.nixsensor.universalsdk.ReferenceWhite
import com.nixsensor.universalsdk.ScanMode
import com.serenegiant.bluetooth.BluetoothManager
import kotlinx.coroutines.launch
import org.threeten.bp.OffsetDateTime


/**
 * https://nixsensor.github.io/nix-universal-sdk-android-doc/device-operations/
 */
class NixTraitLayout : SpectralTraitLayout {

    companion object {
        const val TAG = "NixTraitLayout"
        const val SEARCH_RECURSION_LIMIT = 42
    }


    private var recursionCount = 0
    private val nixSaver = NixSpectralSaver(database)

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    private fun IDeviceCompat.toDevice() = Device(this).also {
        it.displayableName = this.name
    }

    override fun type(): String {
        return Formats.NIX.getDatabaseName()
    }

    override fun loadLayout() {
        super.loadLayout()

        //check if device is connected to a network, if not show an error message
        //the nix requires internet access to verify license
        if (!isNetworkConnected()) {
            if ((context as CollectActivity).numNixInternetWarnings < 1) {
                (context as CollectActivity).numNixInternetWarnings++
                Toast.makeText(
                    context,
                    R.string.nix_error_no_network,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    //https://stackoverflow.com/questions/4238921/detect-whether-there-is-an-internet-connection-available-on-android
    private fun isNetworkConnected(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        val activeNetworkInfo = connectivityManager?.getActiveNetworkInfo()
        return activeNetworkInfo != null && activeNetworkInfo.isConnected()
    }

    override fun establishConnection(): Boolean {

        var connected = false

        val nixDeviceId = controller.getPreferences().getString(GeneralKeys.NIX_ADDRESS, "") ?: ""
        val nixDeviceName = controller.getPreferences().getString(GeneralKeys.NIX_NAME, "") ?: ""
        val nix = (context as CollectActivity).getNixSensorHelper()

        if (nix.connectedDevice != null) {

            nix.connectedDevice?.let { nixDevice ->
                toggleProgressBar(true)
                enableCapture(nixDevice.toDevice())
            }

            connected = true

        } else if (nixDeviceId.isNotBlank() && nixDeviceName.isNotBlank()) {

            val device = nix.getDeviceById(nixDeviceId, nixDeviceName)

            connectDevice(device.toDevice())

            connected = true

        } else {

            connected = false

        }

        return connected
    }

    override fun startDeviceSearch() {
        checkPermissionAndStartSearch()
    }

    override fun writeSpectralDataToDatabase(
        frame: SpectralFrame,
        color: String,
        uri: String,
        entryId: String,
        traitId: String
    ) {

        val deviceAddress = controller.getPreferences().getString(GeneralKeys.NIX_ADDRESS, "") ?: ""
        val deviceName = controller.getPreferences().getString(GeneralKeys.NIX_NAME, "") ?: ""
        val studyId = collectActivity.studyId
        val person = (context as? CollectActivity)?.person
        val location = (context as? CollectActivity)?.locationByPreferences
        val comment = null
        val createdAt = OffsetDateTime.now().format(basicTimeFormatter)

        background.launch {

            nixSaver.saveData(
                NixSpectralSaver.RequiredData(
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
                    traitId = traitId.toString()
                )
            ).onSuccess { fact ->
                submitNewSpectralFact(fact)
            }.onFailure { exception ->
                Log.e(TAG, "Failed to save spectral data", exception)
            }
        }
    }

    private fun checkPermissionAndStartSearch() {

        //check if bluetooth adapter is disabled
        val bluetoothAdapter = BluetoothManager.isEnabled()
        if (!bluetoothAdapter) {
            Toast.makeText(
                context,
                R.string.nix_error_bluetooth_disabled,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (IDeviceScanner.isBluetoothPermissionGranted(context)) {

            startSearch()

        } else {

            with((context as CollectActivity)) {

                getSecurityChecker().withPermission(
                    IDeviceScanner.requiredBluetoothPermissions
                ) {
                    startSearch()
                }
            }
        }
    }

    private fun startSearch() {

        if (IDeviceScanner.isBluetoothPermissionGranted(context)) {

            controller.getNixSensorHelper().search {}

        } else {

            recursionCount++

            if (recursionCount > SEARCH_RECURSION_LIMIT) {
                Toast.makeText(
                    context,
                    R.string.nix_error_bluetooth_permissions,
                    Toast.LENGTH_SHORT
                ).show()
                return
            } else {

                Handler(Looper.getMainLooper()).postDelayed(
                    {
                        checkPermissionAndStartSearch()
                    }, 1000
                )
            }
        }
    }

    override fun showSettings() {

        var dialog: AlertDialog? = null

        controller.getNixSensorHelper().connectedDevice?.let { nixDevice ->

            val settingsView = NixTraitSettingsView(context, nixDevice) {

                if (isLocked) {
                    return@NixTraitSettingsView
                }

                controller.getNixSensorHelper().connectedDevice?.let { device ->
                    disconnectAndEraseDevice(device.toDevice())
                }

                setupConnectUi()

                dialog?.dismiss()

            }

            dialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(context.getString(R.string.nix_trait_settings_title, nixDevice.name))
                .setView(settingsView)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    settingsView.commitChanges()
                    onSettingsChanged()
                    dialog.dismiss()
                }
                .setView(settingsView)
                .create()

            dialog?.show()
        }

    }

    override fun getDeviceList(): List<Device> {
        val nix = (context as CollectActivity).getNixSensorHelper()
        val devices = nix.getDeviceList()
        return devices.map { nixDevice ->
            Device(nixDevice).also {
                it.displayableName = nixDevice.name
            }
        }
    }

    override fun connectDevice(device: Device) {

        getDevice(device)?.let { nixDevice ->

            toggleProgressBar(true)
            connectButton?.isEnabled = false

            val nix = (context as CollectActivity).getNixSensorHelper()

            nix.stopScan()

            nix.connect(nixDevice) { connected ->
                if (connected) {
                    connectButton?.isEnabled = true
                    saveDevice(device)
                    enableCapture(device)
                    ensureSpectralCompat(nixDevice)
                } else {
                    setupConnectUi()
                }
            }
        }
    }

    override fun isSpectralCompatible(device: Device) = getDevice(device)?.providesSpectral == true

    override fun spectralUiMode() {

        controller.getNixSensorHelper().connectedDevice?.let { nixDevice ->

            if (isSpectralCompatible(nixDevice.toDevice())) {

                super.spectralUiMode()

            }
        }
    }

    private fun ensureSpectralCompat(nixDevice: IDeviceCompat) {

        if (!nixDevice.providesSpectral) {

            prefs.edit {
                putInt(GeneralKeys.SPECTRAL_MODE, Color.id)
            }

            colorUiMode()
        }
    }

    private fun getDevice(device: Device): IDeviceCompat? = (device.deviceImplementation as? IDeviceCompat)
            ?: (device.deviceImplementation as? NixSensorHelper.NixDevice)?.device

    override fun disconnectAndEraseDevice(device: Device) {
        getDevice(device)?.let { nixDevice ->
            nixDevice.disconnect()
            controller.getPreferences().edit {
                remove(GeneralKeys.NIX_ADDRESS)
                remove(GeneralKeys.NIX_NAME)
            }
        }
    }

    override fun saveDevice(device: Device) {

        getDevice(device)?.let { nixDevice ->
            (context as CollectActivity).getNixSensorHelper().saveDevice(nixDevice)
            controller.getPreferences().edit {
                putString(GeneralKeys.NIX_ADDRESS, nixDevice.id)
                putString(GeneralKeys.NIX_NAME, nixDevice.name)
            }
        }
    }

    override fun capture(device: Device, entryId: String, traitId: String, callback: ResultCallback) {

        getDevice(device)?.let { nixDevice ->

            Log.d(TAG, "Device state: ${nixDevice.state}")

            if (!isDeviceCompatible(nixDevice)) {
                Toast.makeText(context, R.string.nix_device_incompatible, Toast.LENGTH_SHORT).show()
                enableCapture(device)
                return@let
            }

            if (nixDevice.state != DeviceState.IDLE) {
                Toast.makeText(context, R.string.nix_device_connecting, Toast.LENGTH_SHORT).show()
                enableCapture(device)
                return@let
            }

            callback.onResult(true)

            nixDevice.measure(object : OnDeviceResultListener {
                override fun onDeviceResult(
                    status: CommandStatus,
                    measurements: Map<ScanMode, IMeasurementData>?
                ) {

                    captureButton?.isEnabled = true
                    toggleProgressBar(false)

                    when (status) {
                        CommandStatus.SUCCESS -> {
                            // Successful operation
                            handleMeasurementData(measurements, entryId, traitId)
                        }

                        CommandStatus.ERROR_NOT_READY -> {
                            // Did not complete because the device was busy
                            Log.d(TAG, "Device not ready")
                            enableCapture(device)
                            Toast.makeText(context, R.string.nix_error_not_ready, Toast.LENGTH_SHORT).show()

                        }

                        CommandStatus.ERROR_NOT_SUPPORTED -> {
                            // Did not complete because an unsupported scan mode was
                            // specified
                            Log.d(TAG, "Device not supported")
                            enableCapture(device)
                            Toast.makeText(context, R.string.nix_error_scan_not_supported, Toast.LENGTH_SHORT).show()

                        }

                        CommandStatus.ERROR_LOW_POWER -> {
                            // Did not complete because the battery level is too low
                            Log.d(TAG, "Device low power")
                            enableCapture(device)
                            Toast.makeText(context, R.string.nix_error_low_power, Toast.LENGTH_SHORT).show()

                        }

                        CommandStatus.ERROR_TIMEOUT -> {
                            // Timeout when waiting for result
                            Log.d(TAG, "Device timeout")
                            enableCapture(device)
                            Toast.makeText(context, R.string.nix_error_timeout, Toast.LENGTH_SHORT).show()

                        }

                        CommandStatus.ERROR_AMBIENT_LIGHT -> {
                            // Did not complete because of ambient light leakage
                            Log.d(TAG, "Device ambient light")
                            enableCapture(device)
                            Toast.makeText(context, R.string.nix_error_ambient_light, Toast.LENGTH_SHORT).show()

                        }

                        CommandStatus.ERROR_LICENSE -> {
                            // Scan did not complete because of a license issue
                            // Check LicenseManager.state
                            Log.d(TAG, "Device License error")
                            enableCapture(device)
                            Toast.makeText(context, R.string.nix_error_license, Toast.LENGTH_SHORT).show()

                        }

                        else -> {
                            // Did not complete because of other internal error
                            Log.d(TAG, "Unknown error")
                            enableCapture(device)
                            Toast.makeText(context, R.string.nix_error_unknown, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }

    private fun isDeviceCompatible(device: IDeviceCompat): Boolean {

        if (spectralDataList.isEmpty()) return true

        val deviceId = spectralDataList[0]?.deviceId

        if (deviceId == null) return true

        val deviceModel = controller.getSpectralViewModel().getDevice(
            id = deviceId,
        )

        if (deviceModel == null) return true

        return deviceModel.name == device.name

    }

    private fun handleMeasurementData(
        measurements: Map<ScanMode, IMeasurementData>?,
        entryId: String,
        traitId: String
    ) {
        measurements?.let { m ->
            if (m.isNotEmpty()) {

                val scan = m[ScanMode.M0] ?: m.values.firstOrNull()

                //get spectral data and graph it
                scan?.let { data ->

                    val color = if (data.providesColor(ReferenceWhite.D50_10)) {
                        data.toColorData(ReferenceWhite.D50_2)?.hexCode ?: ""
                    } else {
                        ""
                    }

                    val frame = SpectralFrame(
                        color = color,
                        timestamp = OffsetDateTime.now().format(
                            basicTimeFormatter
                        ),
                        entryId = entryId,
                        traitId = traitId
                    )

                    if (data.providesSpectral) {

                        frame.wavelengths = data.spectralData!!.lambda
                            .mapIndexed { i, w -> w }
                            .joinToString(" ") { fl -> fl.toString() }

                        frame.values = data.spectralData!!.value.joinToString(" ") { it.toString() }

                    }

                    writeSpectralDataToFile(data.deviceType.toString(), frame, data.providesSpectral)?.let { spectralUri ->

                        writeSpectralDataToDatabase(frame, color, spectralUri, entryId, traitId)

                    }
                }
            }
        }
    }
}