package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.fieldbook.tracker.R
import com.fieldbook.tracker.devices.multispeq.MultispeqCalculator
import com.fieldbook.tracker.devices.multispeq.MultispeqListener
import com.fieldbook.tracker.devices.multispeq.MultispeqManager
import com.fieldbook.tracker.devices.multispeq.MultispeqParameter
import com.fieldbook.tracker.devices.multispeq.MultispeqProtocolLoader
import com.fieldbook.tracker.devices.multispeq.MultispeqProtocolType
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONException
import org.json.JSONObject

/**
 * Field Book trait layout for MultispeQ hardware devices.
 *
 * Flow:
 * 1. User configures which calculated parameters to capture via the trait editor
 *    (NewTraitDialog → MultispeqParametersParameter card).
 * 2. User taps "Connect" → dialog lists bonded MultispeQ devices.
 * 3. On connection the UI shows device name; [requestDeviceInfo] is called to
 *    fetch battery, firmware, and device ID.
 * 4. User taps the leaf-icon FAB to measure → the fastest protocol covering the
 *    selected parameters is sent ([MultispeqProtocolType.select]), progress shown.
 * 5. Raw JSON response is processed by [MultispeqCalculator]; only the selected
 *    parameters are displayed and saved as the observation.
 *
 * The [MultispeqManager] is kept alive across entry navigation. On each [init]
 * call views are re-bound to the freshly-inflated layout and UI is restored.
 */
class MultispeqTraitLayout : BaseTraitLayout, MultispeqListener {

    companion object {
        const val type = "multispeq"
        private const val TAG = "MultispeqTraitLayout"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Persists across init() calls
    private var multispeqManager: MultispeqManager? = null
    private var connectedDeviceName: String = ""
    private var connectedDisplayName: String = ""
    private var connectedDeviceInfoText: String = ""
    private var connectedFirmware: String = "--"

    /** Protocol of the measurement in flight; the response is parsed according to it. */
    private var pendingProtocol = MultispeqProtocolType.RIDES

    // Re-bound on every init()
    private lateinit var connectButton: FloatingActionButton
    private lateinit var deviceNameText: TextView
    private lateinit var deviceInfoText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var measureButton: FloatingActionButton
    private lateinit var disconnectButton: Button
    private lateinit var powerOffButton: Button
    private lateinit var resultScroll: android.widget.ScrollView
    private lateinit var resultContainer: LinearLayout

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    override fun type(): String = type

    override fun layoutId(): Int = R.layout.trait_multispeq

    override fun init(act: Activity) {
        connectButton   = act.findViewById(R.id.multispeq_connect_button)
        deviceNameText  = act.findViewById(R.id.multispeq_device_name)
        deviceInfoText  = act.findViewById(R.id.multispeq_device_info)
        progressBar     = act.findViewById(R.id.multispeq_progress)
        measureButton   = act.findViewById(R.id.multispeq_measure_button)
        disconnectButton = act.findViewById(R.id.multispeq_disconnect_button)
        powerOffButton  = act.findViewById(R.id.multispeq_power_off_button)
        resultScroll    = act.findViewById(R.id.multispeq_result_scroll)
        resultContainer = act.findViewById(R.id.multispeq_result_container)

        if (multispeqManager == null) {
            multispeqManager = MultispeqManager(this)
        }

        if (connectedDeviceName.isNotEmpty()) {
            restoreConnectedUi()
        } else {
            setupConnectButton()
        }
    }

    // -------------------------------------------------------------------------
    // Protocol selection
    // -------------------------------------------------------------------------

    private fun selectedParameters(): Set<MultispeqParameter> =
        MultispeqParameter.fromJsonString(currentTrait.categories)
            .ifEmpty { MultispeqParameter.defaultSelection() }

    // -------------------------------------------------------------------------
    // UI setup
    // -------------------------------------------------------------------------

    private fun setupConnectButton() {
        connectButton.visibility = View.VISIBLE
        connectButton.setOnClickListener { showDevicePickerDialog() }
    }

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
            multispeqManager?.sendSleep()
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
        resultContainer.removeAllViews()
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
        measureButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        resultScroll.visibility = View.GONE
        resultContainer.removeAllViews()
        deviceNameText.text = context.getString(R.string.multispeq_measuring)

        val protocolType = MultispeqProtocolType.select(selectedParameters())
        val protocol = try {
            MultispeqProtocolLoader.commandFor(context, protocolType)
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            measureButton.isEnabled = true
            Toast.makeText(context, "Failed to load protocol: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        pendingProtocol = protocolType
        multispeqManager?.sendCommand(protocol)
    }

    // -------------------------------------------------------------------------
    // MultispeqListener callbacks (background thread → post to main)
    // -------------------------------------------------------------------------

    override fun onConnected(device: BluetoothDevice) {
        mainHandler.post {
            showConnectedUi(connectedDeviceName)
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

            Log.d(TAG, "onMeasurementResult: received raw JSON (${rawJson.length} chars): ${rawJson.take(500)}")

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

            // Calculate and display selected parameters
            val selectedParameters = selectedParameters()
            Log.d(TAG, "Selected parameters (${selectedParameters.size}): ${selectedParameters.map { it.name }}")

            val results = MultispeqCalculator.calculate(rawJson, selectedParameters, pendingProtocol)
            Log.d(TAG, "Calculation results (${results.size} entries): ${results.map { (k, v) -> "${k.name}=$v" }}")

            val naCount = results.values.count { it == "N/A" }
            if (naCount > 0) {
                Log.w(TAG, "$naCount/${results.size} parameters returned N/A")
            }

            displayResults(results, selectedParameters)

            // Save observation as compact JSON
            val observationJson = buildObservationJson(results)
            Log.d(TAG, "Observation JSON to save: $observationJson")
            Log.d(TAG, "Calling updateObservation for trait '${currentTrait.name}' (id=${currentTrait.id})")
            updateObservation(currentTrait, observationJson)
            Log.d(TAG, "updateObservation returned; setting collectInputView text")
            collectInputView.text = observationJson
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
    // Result display
    // -------------------------------------------------------------------------

    private fun displayResults(
        results: Map<MultispeqParameter, String>,
        selectedParameters: Set<MultispeqParameter>
    ) {
        resultContainer.removeAllViews()

        // Group by parameter group; show group headers
        for (group in MultispeqParameter.Group.values()) {
            val groupParams = selectedParameters.filter { it.group == group }
                .filter { results.containsKey(it) }
            if (groupParams.isEmpty()) continue

            // Group header
            val headerView = TextView(context).apply {
                text = group.label
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(context.getColor(android.R.color.darker_gray))
                setPadding(0, dpToPx(8), 0, dpToPx(2))
            }
            resultContainer.addView(headerView)

            // Parameter rows
            for (param in groupParams) {
                val value = results[param] ?: continue
                val rowView = android.view.LayoutInflater.from(context)
                    .inflate(android.R.layout.simple_list_item_2, resultContainer, false)
                val text1 = rowView.findViewById<TextView>(android.R.id.text1)
                val text2 = rowView.findViewById<TextView>(android.R.id.text2)

                val labelText = if (param.unit.isNotEmpty())
                    "${param.displayName} (${param.unit})"
                else param.displayName
                text1.text = labelText
                text2.text = value
                resultContainer.addView(rowView)
            }
        }

        resultScroll.visibility = if (resultContainer.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun buildObservationJson(results: Map<MultispeqParameter, String>): String {
        val obj = JSONObject()
        results.entries
            .sortedBy { (param, _) -> param.ordinal }
            .forEach { (param, value) -> obj.put(param.displayName, value) }
        return obj.toString()
    }

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

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
