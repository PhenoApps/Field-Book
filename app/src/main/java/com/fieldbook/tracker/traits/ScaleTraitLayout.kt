package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.devices.spectrometers.Device
import com.fieldbook.tracker.dialogs.SimpleListDialog
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.connectivity.ScaleGattManager
import com.fieldbook.tracker.views.GattDeviceSettingsView
import com.github.mikephil.charting.charts.LineChart
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.rabross.segmenteddisplay.Led
import com.rabross.segmenteddisplay.SingleColorLed
import com.rabross.segmenteddisplay.delimiter.Decoder
import com.rabross.segmenteddisplay.fourteen.BinaryDecoder
import com.rabross.segmenteddisplay.fourteen.SegmentDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.EasyPermissions

class ScaleTraitLayout : BaseTraitLayout {

    companion object {
        const val TAG = "ScaleTraitLayout"
        const val type = "scale"
    }

    private var composeView: ComposeView? = null
    private var connectButton: FloatingActionButton? = null
    private var captureButton: FloatingActionButton? = null
    private var lineChart: LineChart? = null
    private var settingsButton: FloatingActionButton? = null

    private var deviceList: List<Device> = emptyList()
    private var lastWeightString: String = String()
    private var numSegmentsForWeight = 11

    private val scope = CoroutineScope(Dispatchers.Main)

    private val hapticFeedback by lazy {
        controller.getVibrator()
    }

    private val soundFeedback by lazy {
        controller.getSoundHelper()
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    override fun setNaTraitsText() {
    }

    override fun type(): String {
        return Formats.SCALE.getDatabaseName()
    }

    override fun init(act: Activity) {
        composeView = act.findViewById(R.id.compose_view)
        connectButton = act.findViewById(R.id.connect_btn)
        captureButton = act.findViewById(R.id.capture_btn)
        lineChart = act.findViewById(R.id.line_chart)
        settingsButton = act.findViewById(R.id.settings_btn)
    }

    override fun layoutId(): Int {
        return R.layout.trait_scale
    }

    override fun loadLayout() {
        super.loadLayout()

        val device = (context as CollectActivity).scaleGattManager.device

        if (!establishConnection() || device == null) {
            setupConnectUi()
        } else {
            enableCapture(Device(device))
        }

        settingsButton?.setOnClickListener {
            if (!isLocked) {
                showSettings()
            }
        }
    }

    private fun showSettings() {

        var dialog: AlertDialog? = null

        val scaleManager = (context as? CollectActivity)?.scaleGattManager

        scaleManager?.device?.let { device ->

            val settingsView = GattDeviceSettingsView(context, device) {

                if (isLocked) {
                    return@GattDeviceSettingsView
                }

                prefs.edit(commit = true) {
                    remove(GeneralKeys.SCALE_ADDRESS)
                }

                scaleManager.disconnect()

                setupConnectUi()

                dialog?.dismiss()

            }

            dialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(context.getString(R.string.device_settings_dialog_title))
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                }
                .setView(settingsView)
                .create()

            dialog?.show()
        }

    }

    private fun setupConnectUi() {
        (context as CollectActivity).getNixSensorHelper().connectedDevice = null
        connectButton?.visibility = VISIBLE
        captureButton?.visibility = GONE
        settingsButton?.visibility = GONE
        startDeviceSearch()
        setupConnectButton()
        composeView?.setContent {

        }
    }

    private fun establishConnection(): Boolean {
        return (context as CollectActivity).scaleGattManager.isConnected
    }

    private fun setupConnectButton() {

        connectButton?.setOnClickListener {
            if (isLocked) return@setOnClickListener
            startDeviceSearch()
            showDeviceListDialog()
        }
    }

    private fun startDeviceSearch() {

        val savedDeviceAddress = prefs.getString(GeneralKeys.SCALE_ADDRESS, null)

        controller.getSecurityChecker().withPermission(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                )
            else arrayOf(
                android.Manifest.permission.BLUETOOTH,
            )
        ) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!EasyPermissions.hasPermissions(context,
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_SCAN)) {

                    return@withPermission
                }
            } else {
                if (!EasyPermissions.hasPermissions(context,
                        android.Manifest.permission.BLUETOOTH)) {

                    return@withPermission
                }
            }

            //get system bluetooth adapter
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter

            adapter.bondedDevices.forEach { device ->

                //Log.d("GATT", "Loading bonded device: ${device.name}")
                val bonded = Device(device)

                if (device.name !in deviceList.mapNotNull { it.displayableName }) {
                    deviceList = deviceList + bonded.apply {
                        displayableName = device.name
                    }
                }

                if (device.address == savedDeviceAddress) {
                    //Log.d("GATT", "Found saved device: ${device.name}")
                    connectDevice(Device(device))

                    return@forEach
                }
            }

            val scanner = (context as CollectActivity).bleScanner.apply {
                onDeviceFound = { device ->
                    if (device.name != null && device.name.isNotEmpty()) {

                        val newDevice = Device(device)

                        //Log.d("BLEScanner", "Discovered device: ${device.name}")

                        deviceList = deviceList + Device(device).apply {
                            displayableName = device.name
                        }

                        if (device.name !in deviceList.mapNotNull { it.displayableName }) {
                            deviceList = deviceList + newDevice.apply {
                                displayableName = device.name
                            }
                        }

                        if (device.address == savedDeviceAddress) {
                            stopScanning()
                            connectDevice(Device(device))
                        }
                    }
                }
            }

            scanner.startScanning()
        }
    }

    private fun showDeviceListDialog() {

        if (deviceList.isNotEmpty()) {

            SimpleListDialog.show(
                context,
                R.string.select_device,
                deviceList,
                deviceList.mapNotNull { it.displayableName }
            ) { device ->
                Toast.makeText(
                    context,
                    context.getString(R.string.nix_device_connecting),
                    Toast.LENGTH_SHORT
                ).show()
                (context as CollectActivity).bleScanner.stopScanning()
                prefs.edit(commit = true) {
                    putString(
                        GeneralKeys.SCALE_ADDRESS,
                        (device.deviceImplementation as BluetoothDevice).address
                    )
                }
                connectDevice(device)
            }
        } else {
            Toast.makeText(context, R.string.no_devices_found, Toast.LENGTH_SHORT).show()
            startDeviceSearch()
        }
    }

    private fun connectDevice(device: Device) {
        //Log.d(TAG, "Connecting to device: ${device.displayableName}")

        (context as CollectActivity).scaleGattManager.apply {

            connect(
                device.deviceImplementation as BluetoothDevice,
                listener = object : ScaleGattManager.ScaleListener {

                    override fun onDeviceConnected() {
                        //Log.d(TAG, "Device connected: ${device.displayableName}")

                        scope.launch {
                            enableCapture(device)
                        }
                    }

                    override fun onDeviceDisconnected() {
                        //Log.d(TAG, "Device disconnected: ${device.displayableName}")

                        scope.launch {
                            setupConnectUi()
                        }
                    }

                    override fun onDataReceived(weight: String, unit: String, isStable: Boolean) {
                        //Log.d(TAG, "Data received: $weight")
                        // Handle data received from the device

                        lastWeightString = weight.replace(" ", "") + " " + unit

                        scope.launch {
                            composeView?.setContent {
                                if ((context as CollectActivity).scaleGattManager.isConnected) {
                                    ScaleDisplay(lastWeightString, isStable)
                                }
                            }
                        }
                    }

                    override fun logUnknownScaleBytes(
                        serviceUuid: String,
                        charUuid: String,
                        value: String,
                    ) {

                        val sanitizedTraitName = FileUtil.sanitizeFileName(currentTrait?.name)

                        DocumentTreeUtil.getFieldMediaDirectory(context, sanitizedTraitName)?.let { mediaDir ->

                            val logFile = "${serviceUuid}_$charUuid.txt"

                            val file = mediaDir.findFile(logFile)
                                ?: mediaDir.createFile("text/txt", logFile)

                            file?.uri?.let { uri ->
                                context.contentResolver.openOutputStream(uri, "wa")?.bufferedWriter().use { writer ->
                                    writer?.write("$value\n")
                                    writer?.flush()
                                }
                            }
                        }
                    }

                    override fun onError(message: String) {
                        Log.e(TAG, "Error: $message")
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    @Composable
    fun ScaleDisplay(weight: String = "-1337.0 kg", isStable: Boolean) {

        val requiredSegments = weight.length

        if (requiredSegments > numSegmentsForWeight) {
            numSegmentsForWeight = requiredSegments
        }

        var offsetX = 0.dp
        var padWeight = weight.padStart(numSegmentsForWeight, ' ')

        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd) {
                    StableSegmentDisplay(isStable)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {

                for (i in 0 until numSegmentsForWeight) {

                    val c = if (i < padWeight.length) padWeight[i] else ' '

                    if (c.isDigit()) {
                        com.rabross.segmenteddisplay.seven.SegmentDisplay(
                            modifier = Modifier.weight(1f).offset(x = offsetX),
                            led = SingleColorLed(Color.Black, Color.DarkGray.copy(alpha = 0.05f)),
                            decoder = com.rabross.segmenteddisplay.seven.BinaryDecoder(
                                com.rabross.segmenteddisplay.seven.BinaryDecoder.mapToDisplay(c.digitToInt())
                            )
                        )
                    } else if (c == '.') {

                        offsetX = (-16).dp

                        // Display a dot for decimal point
                        DecimalDelimiter(
                            modifier = Modifier.weight(1f).offset(x = -(8).dp),
                        )

                    } else if (c == '-') {

                        NegativeSegmentDisplay(Modifier.weight(1f))

                    } else if (c == ' ') {

                        //space between unit and numbers
                        com.rabross.segmenteddisplay.seven.SegmentDisplay(
                            modifier = Modifier.weight(1f).offset(x = offsetX),
                            led = SingleColorLed(Color.Black, Color.DarkGray.copy(alpha = 0.05f)),
                        )

                    } else {
                        SegmentDisplay(
                            modifier = Modifier.weight(1f).offset(x = offsetX),
                            led = SingleColorLed(Color.Black, Color.DarkGray.copy(alpha = 0.05f)),
                            decoder = BinaryDecoder(
                                BinaryDecoder.mapToDisplay(c.uppercaseChar())
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun NegativeSegmentDisplay(modifier: Modifier) {
        // just toggle the middle segment
        com.rabross.segmenteddisplay.seven.SegmentDisplay(
            modifier = modifier,
            led = SingleColorLed(Color.Black, Color.DarkGray.copy(alpha = 0.1f)),
            decoder = object : Decoder,
                com.rabross.segmenteddisplay.seven.Decoder {
                override val a: Int
                    get() = 0
                override val b: Int
                    get() = 0 // Middle segment on for negative sign
                override val c: Int
                    get() = 0
                override val d: Int
                    get() = 0
                override val e: Int
                    get() = 0
                override val f: Int
                    get() = 0
                override val g: Int
                    get() = 1
            }
        )
    }

    @Composable
    fun DecimalDelimiter(
        modifier: Modifier = Modifier,
        segmentScale: Int = 3,
        led: Led = SingleColorLed(Color.Black, Color.Black),
        decoder: Decoder = com.rabross.segmenteddisplay.delimiter.BinaryDecoder()
    ) {
        Canvas(
            modifier = modifier
                .aspectRatio(
                    ((1f + segmentScale + 1f)) / (1f + segmentScale + 1f + segmentScale + 1f),
                    true
                )
                .size(100.dp)
        ) {
            val scaleWidth = 1 + segmentScale + 1
            val scaleHeight = 1 + segmentScale + 1 + segmentScale + 1

            val segmentWidthByWidth = size.width / scaleWidth
            val segmentWidthByHeight = size.height / scaleHeight

            val segmentWidth = if (scaleHeight * segmentWidthByWidth < size.height) segmentWidthByWidth else segmentWidthByHeight

            val totalWidth = segmentWidth * scaleWidth
            val totalHeight = segmentWidth * scaleHeight

            drawDelimiter(
                led.signal(decoder.a),
                segmentWidth / 2,
                Offset(0f, segmentWidth / 2),
                Size(totalWidth, totalHeight - segmentWidth)
            )
        }
    }

    @Composable
    fun StableSegmentDisplay(isStable: Boolean = true) {
        SegmentDisplay(
            modifier = Modifier.size(32.dp),
            led = SingleColorLed(Color.Black, Color.DarkGray.copy(alpha = 0.1f)),
            decoder = object : Decoder,
                com.rabross.segmenteddisplay.fourteen.Decoder {
                override val c: Int
                    get() = 0
                override val d: Int
                    get() = 0
                override val e: Int
                    get() = 0
                override val f: Int
                    get() = 0
                override val g1: Int
                    get() = if (isStable) 1 else 0
                override val g2: Int
                    get() = if (isStable) 1 else 0
                override val h: Int
                    get() = if (isStable) 1 else 0
                override val i: Int
                    get() = if (isStable) 1 else 0
                override val j: Int
                    get() = if (isStable) 1 else 0
                override val k: Int
                    get() = if (isStable) 1 else 0
                override val l: Int
                    get() = if (isStable) 1 else 0
                override val m: Int
                    get() = if (isStable) 1 else 0
                override val b: Int
                    get() = 0
                override val a: Int
                    get() = 0
            }
        )
    }

    private fun DrawScope.drawDelimiter(downDotColor: Color, radius: Float, offset: Offset, size: Size) {
        val downDotCenterOffset = Offset(size.width / 2 + offset.x, size.height)
        drawCircle(downDotColor, radius, downDotCenterOffset)
    }

    private fun enableCapture(device: Device) {
        connectButton?.visibility = INVISIBLE
        captureButton?.visibility = VISIBLE
        settingsButton?.visibility = VISIBLE
        captureButton?.isEnabled = true
        setupCaptureButton(device)
    }

    private fun setupCaptureButton(device: Device) {
        captureButton?.setOnClickListener {

            if (isLocked) return@setOnClickListener

            //insert observation
            (context as CollectActivity).updateObservation(currentTrait, lastWeightString, null)

            hapticFeedback.vibrate()

            soundFeedback.playCelebrate()

            controller.getInputView().apply {

                text = lastWeightString

                markObservationEdited()
            }

        }
    }

    override fun deleteTraitListener() {
        (context as CollectActivity).removeTrait()
        super.deleteTraitListener()
    }

    override fun refreshLayout(onNew: Boolean) {
        super.refreshLayout(false)
    }

    override fun refreshLock() {
        super.refreshLock()
        loadLayout()
    }
}