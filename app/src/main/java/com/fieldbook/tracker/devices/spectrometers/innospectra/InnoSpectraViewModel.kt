package com.fieldbook.tracker.devices.spectrometers.innospectra

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.ISCSDK.ISCNIRScanSDK
import com.ISCSDK.ISCNIRScanSDK.*
import com.fieldbook.tracker.R
import com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces.NanoEventListener
import com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces.InnoSpectraViewModel
import com.fieldbook.tracker.devices.spectrometers.innospectra.models.Config
import com.fieldbook.tracker.devices.spectrometers.innospectra.models.DeviceInfo
import com.fieldbook.tracker.devices.spectrometers.innospectra.models.DeviceStatus
import com.fieldbook.tracker.devices.spectrometers.innospectra.models.Frame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.fieldbook.tracker.devices.spectrometers.innospectra.receivers.DeviceInfoReceiver
import com.fieldbook.tracker.preferences.GeneralKeys
import org.phenoapps.interfaces.spectrometers.Spectrometer.Companion.DEVICE_TYPE_NANO
import java.lang.IllegalArgumentException
import javax.inject.Inject
import kotlin.collections.ArrayList

@HiltViewModel
class InnoSpectraViewModel @Inject constructor() : ViewModel(), InnoSpectraViewModel,
    NanoEventListener {

    private var mBluetoothManager: BluetoothManager? = null

    private var mBluetoothDevice: BluetoothDevice? = null

    //service connection to the bluetooth Gatt service, built when connect is called with a context
    private var mServiceConnection: ServiceConnection? = null

    //nano sdk that is set when service is established
    private var mNanoSdk: NanoConnection? = null

    data class NanoConnection(val sdk: ISCNIRScanSDK, val device: NanoDevice)

    //track if device is currently connected
    private var mConnected = false

    private var mConnectionStarting = false

    //track if we have the ref data ready (need this before scanning)
    private var mRefDataReady = false

    //wrapper class for handling IS receivers
    private var mNanoReceiver: InnoSpectraBase? = null

    //live data that receivers will produce and listeners will consume
    private var mSpectralData: Frame? = null

    private var OnDeviceButtonClicked: (() -> Unit)? = null

    private var mIsScanning = false

    private var mUiScan = false

    private var mDeviceInfo: DeviceInfo? = null

    private var mDeviceStatus: DeviceStatus? = null

    private var mConfigs = ArrayList<ScanConfiguration>()

    private var mConfigSize: Int? = null

    private var mActiveIndex: Int? = null

    private var mConfigSaved: Boolean? = null

    private fun buildServiceConnection(context: Context): ServiceConnection =
        object : ServiceConnection {

            override fun onServiceConnected(name: ComponentName, service: IBinder) {

                (service as? LocalBinder)?.service?.let { sdk ->

                    sdk.initialize()

                    mBluetoothManager =
                        context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                    val adapter = mBluetoothManager?.adapter
                    val scanner = adapter?.bluetoothLeScanner
                    val connect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                    val scan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }

                    val fine = ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    val coarse = ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (fine && coarse && connect && scan) {

                        scanner?.startScan(object : ScanCallback() {

                            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                                super.onScanResult(callbackType, result)

                                if (mBluetoothDevice != null) {
                                    return
                                }

                                try {

                                    result?.device?.let { device ->

                                        val nanoName = getStringPref(
                                            context,
                                            SharedPreferencesKeys.DeviceFilter,
                                            "NIR"
                                        )

                                        result.scanRecord?.deviceName?.let { name ->

                                            if (name.contains(nanoName)) {

                                                result.scanRecord?.let { record ->

                                                    mBluetoothDevice = device

                                                    val nanoDevice = NanoDevice(
                                                        device,
                                                        result.rssi,
                                                        record.bytes,
                                                        nanoName
                                                    )

                                                    mConnectionStarting = true

                                                    if (sdk.connect(nanoDevice.nanoMac)) {

                                                        scanner.stopScan(this)

                                                        mNanoSdk = NanoConnection(sdk, nanoDevice)

                                                        val configIndex =
                                                            PreferenceManager.getDefaultSharedPreferences(
                                                                context
                                                            )
                                                                .getInt(
                                                                   GeneralKeys.INNOSPECTRA_NANO_CONFIG_INDEX,
                                                                    0
                                                                )

                                                        setActiveConfig(configIndex)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                } catch (e: SecurityException) {

                                    e.printStackTrace()

                                }
                            }
                        })
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mConnected = false
            }
        }

    fun setBluetoothDevice(context: Context, device: BluetoothDevice) {
        mBluetoothManager =
            context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothDevice = device
    }

    override suspend fun connect(context: Context) {

        if (!mConnectionStarting) {

            buildServiceConnection(context).also { conn ->

                mServiceConnection = conn

                val gattService = Intent(context, ISCNIRScanSDK::class.java)

                context.bindService(gattService, conn, BIND_AUTO_CREATE)

                mNanoReceiver = InnoSpectraBase(this).also {
                    it.register(context)
                }
            }
        }
    }

    override fun disconnect(context: Context): Int {

        try {
            mServiceConnection?.let { conn ->

                context.unbindService(conn)

            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }

        mNanoSdk?.sdk?.disconnect()
        mNanoSdk?.sdk?.close()
        mNanoReceiver?.unregister(context)

        refreshConfigs()
        mConnected = false
        mRefDataReady = false
        mBluetoothDevice = null
        mConnectionStarting = false

        return 1
    }

    private fun isGattStateConnected() = if (mBluetoothDevice != null) {

        try {

            mBluetoothManager?.getConnectionState(
                mBluetoothDevice,
                BluetoothProfile.GATT_SERVER
            ) == BluetoothProfile.STATE_CONNECTED

        } catch (e: SecurityException) {

            e.printStackTrace()

            false

        }

    } else false

    override fun isConnected() = if (mBluetoothDevice != null && mConnected) {

        isGattStateConnected()

    } else false

    private fun isRefDataReady() = mRefDataReady

    override fun reset(context: Context?) {

        context?.let { ctx ->

            disconnect(ctx)

            viewModelScope.launch {

                connect(ctx)

            }
        }
    }

    override fun getDeviceError(): String {
        return "None"
    }

    override fun getDeviceInfo() =
        mDeviceInfo ?: DeviceInfo("?", "?", "?", "?", "?", DEVICE_TYPE_NANO)

    override fun setEventListener(onClick: () -> Unit): LiveData<String> = liveData {

        OnDeviceButtonClicked = onClick

        emit("DONE")

    }

    override fun scan(context: Context, manual: Boolean?): LiveData<List<Frame>?> = liveData {

        if (isConnected() && isRefDataReady()) {

            mUiScan = !(manual ?: false)

            //pressing Nano button automatically starts a scan, no need to call jni
            if (manual != true) {
                //jni call
                StartScan()
            }

            //wait for spectral data from the receiver
            while (mSpectralData == null) {

                delay(500)

            }

            mSpectralData?.let { frame ->

                mIsScanning = false

                ControlPhysicalButton(PhysicalButton.Unlock)

                //reset data to null for next scan
                mSpectralData = null

                mUiScan = false

                emit(listOf(frame))
            }

        } else emit(null)
    }

    override fun onNotifyReceived() {

        if (isGattStateConnected()) {

            // ControlPhysicalButton(PhysicalButton.Lock)

            //jni call
            SetCurrentTime()

            GetDeviceInfo()

        }
    }

    override fun onRefDataReady() {

        if (isGattStateConnected()) {

            mRefDataReady = true

            //ControlPhysicalButton(PhysicalButton.Unlock)

            GetDeviceInfo()
        }
    }

    override fun onScanStarted() {

        if (isGattStateConnected() && !mIsScanning) {

            mIsScanning = true

            if (!mUiScan) {

                ControlPhysicalButton(PhysicalButton.Lock)

                OnDeviceButtonClicked?.invoke()

            }
        }
    }

    override fun onScanDataReady(spectral: Frame) {

        if (isGattStateConnected()) {

            mSpectralData = spectral

        }
    }

    override fun onGetUuid(uuid: String) {

        mDeviceInfo?.let { info ->
//            mDeviceInfo = Spectrometer.DeviceInfo(
//                info.softwareVersion,
//                info.hardwareVersion,
//                uuid,
//                info.alias,
//                info.opMode,
//                info.deviceType
//            )
        }

        GetDeviceStatus()

    }

    override fun onGetDeviceInfo(info: DeviceInfoReceiver.NanoDeviceInfo) {

        mDeviceInfo = with(info) {
            DeviceInfo(
                this.spec,
                this.hardware,
                this.model,
                "",
                "",
                DEVICE_TYPE_NANO,
                serialNumber = serial,
            )
        }

        mConnected = true

        GetUUID()
    }

    override fun onGetConfig(config: ScanConfiguration) {

        if (mConfigs.isEmpty() || !mConfigs.any { config.scanConfigIndex == it.scanConfigIndex }) {

            mConfigs.add(config)

            if (mConfigs.size == mConfigSize) {

                GetActiveConfig()
            }
        }
    }

    fun hasActiveScan() = mActiveIndex != null

    override fun onGetActiveConfig(index: Int) {

        mActiveIndex = index
    }

    override fun onGetDeviceStatus(status: DeviceStatus) {

        mDeviceStatus = status

        GetScanConfig()
    }

    override fun onGetConfigSize(size: Int) {

        mConfigSize = size
    }

    override fun onConfigSaveStatus(status: Boolean) {

        mConfigSaved = status

        GetScanConfig()
        //requestStoredConfigurationList()
    }

    fun forceRefreshConfigs() {
        GetScanConfig()
    }

    fun getConfigSaved() = mConfigSaved

    fun resetConfigSaved() {

        mConfigSaved = null

    }

    /**
     * Save the config byte array as a string in user preferences.
     */
    fun addConfig(config: Config) {

        val nameSize = config.name.length
        val bytes = config.name.toByteArray()
        for (i in 0..nameSize) {
            ScanConfigInfo.configName[i] = if (i == nameSize) 0
            else bytes[i]
        }
        ScanConfigInfo.write_scanType = 2
        val serialNum = "12345678"
        val serialBytes = serialNum.toByteArray()
        val serialNumSize = serialNum.length
        for (i in 0 until serialNumSize) {
            ScanConfigInfo.scanConfigSerialNumber[i] = serialBytes[i]
        }
        ScanConfigInfo.write_scanConfigIndex = 255
        ScanConfigInfo.write_numSections = (config.sections?.size ?: 1).toByte()
        ScanConfigInfo.write_numRepeat = config.repeats

        config.sections?.forEachIndexed { i, section ->
            ScanConfigInfo.sectionScanType[i] = section.methodIndex.toByte()
            ScanConfigInfo.sectionWavelengthStartNm[i] = section.start.toInt()
            ScanConfigInfo.sectionWavelengthEndNm[i] = section.end.toInt()
            ScanConfigInfo.sectionNumPatterns[i] = section.resolution
            //width index starts from 2 for whatever reason (2-52)
            ScanConfigInfo.sectionWidthPx[i] = (section.widthIndex + 2).toByte()
            ScanConfigInfo.sectionExposureTime[i] = section.exposureIndex
        }

        ScanConfig(WriteScanConfiguration(ScanConfigInfo()), ScanConfig.SAVE)
    }

    fun readScanConfig(spec: ByteArray): ScanConfiguration {
        return GetScanConfiguration(spec)
    }

    //return sdk bytes representation of a config
    fun getConfigBytes(config: ScanConfiguration): ByteArray {

        val nameSize = config.configName.length
        val bytes = config.configName.toByteArray()
        for (i in 0..nameSize) {
            ScanConfigInfo.configName[i] = if (i == nameSize) 0
            else bytes[i]
        }
        ScanConfigInfo.write_scanType = 2
        val serialNum = config.scanConfigSerialNumber
        val serialBytes = serialNum.toByteArray()
        val serialNumSize = serialNum.length
        for (i in 0 until serialNumSize) {
            ScanConfigInfo.scanConfigSerialNumber[i] = serialBytes[i]
        }
        ScanConfigInfo.write_scanConfigIndex = 255
        ScanConfigInfo.write_numSections = config.slewNumSections
        ScanConfigInfo.write_numRepeat = config.numRepeats

        for (i in 0 until config.slewNumSections) {
            ScanConfigInfo.sectionScanType[i] = config.sectionScanType[i]
            ScanConfigInfo.sectionWavelengthStartNm[i] = config.sectionWavelengthStartNm[i]
            ScanConfigInfo.sectionWavelengthEndNm[i] = config.sectionWavelengthEndNm[i]
            ScanConfigInfo.sectionNumPatterns[i] = config.sectionNumPatterns[i]
            ScanConfigInfo.sectionWidthPx[i] = config.sectionWidthPx[i]
            ScanConfigInfo.sectionExposureTime[i] = config.sectionExposureTime[i]
        }

        return WriteScanConfiguration(ScanConfigInfo())
    }

    fun refreshConfigs() {

        mActiveIndex = null
        mConfigSize = null
        mConfigs.clear()

    }

    fun getScanConfigs(): List<ScanConfiguration> {

        return mConfigs.toList()
    }

    fun getActiveConfig(): Int? {

        return mActiveIndex
    }

    fun setActiveConfig(index: Int) {

        SetActiveConfig(byteArrayOf(index.toByte(), (index / 256).toByte()))

    }

    fun setActiveConfig(data: ByteArray) {

        ScanConfig(data, ScanConfig.SET)

    }

    fun getScanConfigSize(): Int {

        return mConfigSize ?: -1
    }

    fun getDeviceStatusObject() = mDeviceStatus

    fun getDeviceStatus(context: Context): String {

        val temperatureHeader = context.getString(R.string.header_temperature)
        val humidityHeader = context.getString(R.string.header_humidity)
        val totalLampHeader = context.getString(R.string.header_total_lamp_time)
        val batteryHeader = context.getString(R.string.header_battery)

        if (mDeviceStatus == null) return ""

        return """
            $temperatureHeader: ${mDeviceStatus?.temperature}
            $humidityHeader: ${mDeviceStatus?.humidity}
            $totalLampHeader: ${mDeviceStatus?.totalLampTime}
            $batteryHeader: ${mDeviceStatus?.battery}
        """.trimIndent()
    }

    fun getDeviceInfo(context: Context): String {

        val softwareHeader = context.getString(R.string.view_trait_nix_settings_software_version)
        val hardwareHeader = context.getString(R.string.header_hardware_version)
        val deviceIdHeader = context.getString(R.string.header_device_id)
        val serialHeader = context.getString(R.string.header_serial_id)

        if (mDeviceInfo == null) return ""

        return """
            $softwareHeader: ${mDeviceInfo?.softwareVersion}
            $hardwareHeader: ${mDeviceInfo?.hardwareVersion}
            $deviceIdHeader: ${mDeviceInfo?.deviceId}
            $serialHeader:   ${mDeviceInfo?.serialNumber}
        """.trimIndent()
    }

}