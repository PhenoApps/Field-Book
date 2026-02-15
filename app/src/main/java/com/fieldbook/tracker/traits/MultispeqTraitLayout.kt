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
 * 3. On connection, device name shown + "Measure" and "Disconnect" buttons appear.
 * 4. User taps "Measure" → command sent, progress shown.
 * 5. Raw JSON response displayed; user taps "Accept" (Measure again) or collects data
 *    automatically by calling updateObservation() with the JSON string.
 */
class MultispeqTraitLayout : BaseTraitLayout, MultispeqListener {

    companion object {
        const val type = "multispeq"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var multispeqManager: MultispeqManager? = null
    private var protocols: List<MultispeqProtocol> = emptyList()

    private lateinit var protocolSpinner: Spinner
    private lateinit var connectButton: FloatingActionButton
    private lateinit var deviceNameText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var measureButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var resultScroll: ScrollView
    private lateinit var resultText: TextView

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    override fun type(): String = type

    override fun layoutId(): Int = R.layout.trait_multispeq

    override fun init(act: Activity) {
        protocolSpinner = act.findViewById(R.id.multispeq_protocol_spinner)
        connectButton = act.findViewById(R.id.multispeq_connect_button)
        deviceNameText = act.findViewById(R.id.multispeq_device_name)
        progressBar = act.findViewById(R.id.multispeq_progress)
        measureButton = act.findViewById(R.id.multispeq_measure_button)
        disconnectButton = act.findViewById(R.id.multispeq_disconnect_button)
        resultScroll = act.findViewById(R.id.multispeq_result_scroll)
        resultText = act.findViewById(R.id.multispeq_result_text)

        multispeqManager = MultispeqManager(this)

        loadProtocols()
        setupConnectButton()
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
        connectButton.setOnClickListener {
            showDevicePickerDialog()
        }
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
        deviceNameText.text = context.getString(R.string.multispeq_connecting)
        deviceNameText.visibility = View.VISIBLE
        connectButton.visibility = View.GONE
        multispeqManager?.connect(device)
    }

    private fun showConnectedUi(deviceName: String) {
        deviceNameText.text = context.getString(R.string.multispeq_connected, deviceName)
        deviceNameText.visibility = View.VISIBLE
        connectButton.visibility = View.GONE
        measureButton.visibility = View.VISIBLE
        disconnectButton.visibility = View.VISIBLE

        measureButton.setOnClickListener {
            runMeasurement()
        }

        disconnectButton.setOnClickListener {
            multispeqManager?.disconnect()
            resetToConnectState()
        }
    }

    private fun resetToConnectState() {
        deviceNameText.visibility = View.GONE
        connectButton.visibility = View.VISIBLE
        measureButton.visibility = View.GONE
        disconnectButton.visibility = View.GONE
        progressBar.visibility = View.GONE
        resultScroll.visibility = View.GONE
        setupConnectButton()
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
    // MultispeqListener callbacks (delivered on background thread → switch to main)
    // -------------------------------------------------------------------------

    override fun onConnected(device: BluetoothDevice) {
        mainHandler.post {
            showConnectedUi(device.name ?: device.address)
        }
    }

    override fun onMeasurementResult(rawJson: String) {
        mainHandler.post {
            progressBar.visibility = View.GONE
            measureButton.isEnabled = true

            // Restore device name in status text
            deviceNameText.text = deviceNameText.text.toString()
                .replace(context.getString(R.string.multispeq_measuring), "")
                .trim()

            // Pretty-print JSON if possible
            val display = try {
                JSONObject(rawJson).toString(2)
            } catch (e: JSONException) {
                rawJson
            }

            resultText.text = display
            resultScroll.visibility = View.VISIBLE

            // Save raw JSON as the observation value
            updateObservation(currentTrait, rawJson)
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
            // Accept plain string responses (e.g. device info)
            data.isNotBlank()
        }
    }
}
