package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.fieldbook.tracker.R
import com.fieldbook.tracker.devices.multispeq.MultispeqListener
import com.fieldbook.tracker.devices.multispeq.MultispeqManager
import com.fieldbook.tracker.devices.multispeq.MultispeqProtocol
import com.fieldbook.tracker.devices.multispeq.MultispeqProtocolLoader
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONException
import org.json.JSONObject

/**
 * Field Book trait layout for MultispeQ hardware devices.
 *
 * Flow:
 * 1. User selects a protocol from the spinner (populated from bundled JSON).
 * 2. User taps "Connect" → dialog lists bonded MultispeQ devices.
 * 3. On connection the UI shows a placeholder; [requestDeviceInfo] is called which sends "1"
 *    and updates battery, firmware, and device ID when the response arrives.
 * 4. User taps the leaf-icon FAB to measure → command sent, progress shown.
 * 5. Raw JSON response displayed; observation saved automatically.
 *
 * The [MultispeqManager] is created once and kept alive across entry navigation.
 * On each [init] call the views are re-bound to the freshly-inflated layout, and
 * the UI is restored to match the current connection state.
 */
class MultispeqTraitLayout : BaseTraitLayout, MultispeqListener {

    companion object {
        const val type = "multispeq"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Persists across init() calls (entry navigation re-inflates views but reuses this object)
    private var multispeqManager: MultispeqManager? = null
    private var protocols: List<MultispeqProtocol> = emptyList()
    private var connectedDeviceName: String = ""
    /** "Name (deviceId)" — set once device info arrives; empty until then. */
    private var connectedDisplayName: String = ""
    /** Full device-info line text — restored on view re-inflation. */
    private var connectedDeviceInfoText: String = ""
    /** Firmware string from device info; kept so battery-only updates don't lose it. */
    private var connectedFirmware: String = "--"

    // Re-bound on every init() because inflateTrait() creates new view instances
    private lateinit var protocolSpinner: Spinner
    private lateinit var connectButton: FloatingActionButton
    private lateinit var deviceNameText: TextView
    private lateinit var deviceInfoText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var measureButton: FloatingActionButton
    private lateinit var disconnectButton: Button
    private lateinit var powerOffButton: Button
    private lateinit var resultScroll: ScrollView
    private lateinit var resultText: TextView

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    override fun type(): String = type

    override fun layoutId(): Int = R.layout.trait_multispeq

    override fun init(act: Activity) {
        // Re-bind views — inflateTrait() produces a fresh view tree on every navigation
        protocolSpinner = act.findViewById(R.id.multispeq_protocol_spinner)
        connectButton = act.findViewById(R.id.multispeq_connect_button)
        deviceNameText = act.findViewById(R.id.multispeq_device_name)
        deviceInfoText = act.findViewById(R.id.multispeq_device_info)
        progressBar = act.findViewById(R.id.multispeq_progress)
        measureButton = act.findViewById(R.id.multispeq_measure_button)
        disconnectButton = act.findViewById(R.id.multispeq_disconnect_button)
        powerOffButton = act.findViewById(R.id.multispeq_power_off_button)
        resultScroll = act.findViewById(R.id.multispeq_result_scroll)
        resultText = act.findViewById(R.id.multispeq_result_text)

        // Keep the existing manager (and its live Bluetooth connection) across navigations
        if (multispeqManager == null) {
            multispeqManager = MultispeqManager(this)
        }

        loadProtocols()

        // Restore UI to reflect the current connection state
        if (connectedDeviceName.isNotEmpty()) {
            restoreConnectedUi()
        } else {
            setupConnectButton()
        }
    }

    // -------------------------------------------------------------------------
    // Protocol loading
    // -------------------------------------------------------------------------

    private fun loadProtocols() {
        protocols = MultispeqProtocolLoader.load(context)

        val names = if (protocols.isEmpty()) {
            listOf(context.getString(R.string.multispeq_no_protocols))
        } else {
            protocols.map { it.name }
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        protocolSpinner.adapter = adapter
    }

    // -------------------------------------------------------------------------
    // UI setup
    // -------------------------------------------------------------------------

    private fun setupConnectButton() {
        connectButton.visibility = View.VISIBLE
        connectButton.setOnClickListener { showDevicePickerDialog() }
    }

    /** Called on first connection — shows placeholder text and registers button listeners. */
    private fun showConnectedUi(deviceName: String) {
        val nameDisplay = connectedDisplayName.ifBlank { deviceName }
        deviceNameText.text = context.getString(R.string.multispeq_connected, nameDisplay)
        deviceNameText.visibility = View.VISIBLE

        val infoText = connectedDeviceInfoText.ifBlank {
            context.getString(R.string.multispeq_device_info_loading)
        }
        deviceInfoText.text = infoText
        deviceInfoText.visibility = View.VISIBLE

        connectButton.visibility = View.GONE
        measureButton.visibility = View.VISIBLE
        disconnectButton.visibility = View.VISIBLE
        powerOffButton.visibility = View.VISIBLE

        wireConnectedButtons()
    }

    /**
     * Called by [init] when the view is re-inflated but a connection is already live.
     * Restores all stored display text and re-registers button listeners.
     */
    private fun restoreConnectedUi() {
        val nameDisplay = connectedDisplayName.ifBlank { connectedDeviceName }
        deviceNameText.text = context.getString(R.string.multispeq_connected, nameDisplay)
        deviceNameText.visibility = View.VISIBLE

        val infoText = connectedDeviceInfoText.ifBlank {
            context.getString(R.string.multispeq_device_info_loading)
        }
        deviceInfoText.text = infoText
        deviceInfoText.visibility = View.VISIBLE

        connectButton.visibility = View.GONE
        measureButton.visibility = View.VISIBLE
        disconnectButton.visibility = View.VISIBLE
        powerOffButton.visibility = View.VISIBLE
        progressBar.visibility = View.GONE

        wireConnectedButtons()
    }

    private fun wireConnectedButtons() {
        measureButton.setOnClickListener { runMeasurement() }
        disconnectButton.setOnClickListener {
            multispeqManager?.disconnect()
            resetToConnectState()
        }
        powerOffButton.setOnClickListener {
            multispeqManager?.sendCommand("sleep")
        }
    }

    private fun resetToConnectState() {
        connectedDeviceName = ""
        connectedDisplayName = ""
        connectedDeviceInfoText = ""
        connectedFirmware = "--"
        deviceNameText.visibility = View.GONE
        deviceInfoText.visibility = View.GONE
        connectButton.visibility = View.VISIBLE
        measureButton.visibility = View.GONE
        disconnectButton.visibility = View.GONE
        powerOffButton.visibility = View.GONE
        progressBar.visibility = View.GONE
        resultScroll.visibility = View.GONE
        setupConnectButton()
    }

    private fun showDevicePickerDialog() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(context, context.getString(R.string.permission_ask_bluetooth), Toast.LENGTH_SHORT).show()
            return
        }

        val devices = multispeqManager?.getMultispeqDevices() ?: emptyList()
        if (devices.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.multispeq_no_devices_found), Toast.LENGTH_LONG).show()
            return
        }

        val names = devices.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(context.getString(R.string.multispeq_choose_device_title))
            .setSingleChoiceItems(names, -1) { dialog, which ->
                dialog.dismiss()
                connectToDevice(devices[which])
            }
            .show()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        connectedDeviceName = device.name ?: device.address
        deviceNameText.text = context.getString(R.string.multispeq_connecting)
        deviceNameText.visibility = View.VISIBLE
        connectButton.visibility = View.GONE
        multispeqManager?.connect(device)
    }

    // -------------------------------------------------------------------------
    // Measurement
    // -------------------------------------------------------------------------

    private fun runMeasurement() {
        if (protocols.isEmpty()) return

        val selectedIndex = protocolSpinner.selectedItemPosition
        if (selectedIndex < 0 || selectedIndex >= protocols.size) return

        val protocol = protocols[selectedIndex]

        measureButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        resultScroll.visibility = View.GONE
        deviceNameText.text = context.getString(R.string.multispeq_measuring)

        multispeqManager?.sendCommand(protocol.commandJson)
    }

    // -------------------------------------------------------------------------
    // MultispeqListener callbacks (background thread → post to main)
    // -------------------------------------------------------------------------

    override fun onConnected(device: BluetoothDevice) {
        mainHandler.post {
            showConnectedUi(connectedDeviceName)
            // Request device info NOW — after the UI is ready, eliminating the race condition
            // where onDeviceInfo could arrive before showConnectedUi runs.
            multispeqManager?.requestDeviceInfo()
        }
    }

    override fun onDeviceInfo(deviceId: String, firmware: String, battery: Int) {
        mainHandler.post {
            connectedDisplayName = "$connectedDeviceName ($deviceId)"
            connectedFirmware = firmware.ifBlank { "--" }
            deviceNameText.text = context.getString(R.string.multispeq_connected, connectedDisplayName)

            val batteryStr = if (battery >= 0) "$battery%" else "--"
            connectedDeviceInfoText = context.getString(
                R.string.multispeq_device_info,
                batteryStr,
                connectedFirmware
            )
            deviceInfoText.text = connectedDeviceInfoText
        }
    }

    override fun onMeasurementResult(rawJson: String) {
        mainHandler.post {
            progressBar.visibility = View.GONE
            measureButton.isEnabled = true

            val nameDisplay = connectedDisplayName.ifBlank { connectedDeviceName }
            deviceNameText.text = context.getString(R.string.multispeq_connected, nameDisplay)

            // Update battery and firmware from every scan result
            try {
                val obj = JSONObject(rawJson)
                val liveBattery = obj.optInt("device_battery", -1)
                val liveFirmware = obj.optString("device_firmware", "").ifBlank { null }
                if (liveFirmware != null) connectedFirmware = liveFirmware
                if (liveBattery >= 0) {
                    connectedDeviceInfoText = context.getString(
                        R.string.multispeq_device_info,
                        "$liveBattery%",
                        connectedFirmware
                    )
                    deviceInfoText.text = connectedDeviceInfoText
                }
            } catch (_: JSONException) { /* non-JSON result — leave as-is */ }

            val display = try {
                JSONObject(rawJson).toString(2)
            } catch (e: JSONException) {
                rawJson
            }

            resultText.text = display
            resultScroll.visibility = View.VISIBLE

            updateObservation(currentTrait, rawJson)
            collectInputView.text = rawJson
        }
    }

    override fun onError(message: String) {
        mainHandler.post {
            progressBar.visibility = View.GONE
            measureButton.isEnabled = true
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDisconnected() {
        mainHandler.post {
            resetToConnectState()
        }
    }

    // -------------------------------------------------------------------------
    // BaseTraitLayout overrides
    // -------------------------------------------------------------------------

    override fun setNaTraitsText() {
    }

    override fun loadLayout() {
        super.loadLayout()
        this.visibility = View.VISIBLE
        collectInputView.visibility = View.VISIBLE
    }

    override fun validate(data: String?): Boolean {
        if (data.isNullOrBlank()) return false
        return try {
            JSONObject(data)
            true
        } catch (e: JSONException) {
            data.isNotBlank()
        }
    }
}
