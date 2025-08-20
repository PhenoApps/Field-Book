package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.spectral.LineGraphSelectableAdapter
import com.fieldbook.tracker.database.basicTimeFormatter
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.database.models.spectral.SpectralFact
import com.fieldbook.tracker.database.saver.SpectralSaver
import com.fieldbook.tracker.devices.spectrometers.Device
import com.fieldbook.tracker.devices.spectrometers.SpectralFrame
import com.fieldbook.tracker.dialogs.SimpleListDialog
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.SpectralTraitLayout.Companion.TAG
import com.fieldbook.tracker.traits.SpectralTraitLayout.FrameEntry
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.connectivity.GreenSeekerGattManager
import com.fieldbook.tracker.views.GattDeviceSettingsView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.OffsetDateTime
import pub.devrel.easypermissions.EasyPermissions
import kotlin.math.min

class GreenSeekerTraitLayout : BaseTraitLayout, LineGraphSelectableAdapter.Listener {

    companion object {
        const val type = "green_seeker"
        const val deviceType = "GreenSeeker"
    }

    private var recyclerView: RecyclerView? = null
    private var connectButton: FloatingActionButton? = null
    private var captureButton: FloatingActionButton? = null
    private var disconnectButton: FloatingActionButton? = null
    private var lineChart: LineChart? = null
    private var settingsButton: FloatingActionButton? = null

    private var deviceList: List<Device> = emptyList()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val spectralSaver = SpectralSaver(database)

    private val spectralDataList = mutableListOf<SpectralFact?>()

    private var selected: Int = 0

    private val hapticFeedback by lazy {
        controller.getVibrator()
    }

    private val soundFeedback by lazy {
        controller.getSoundHelper()
    }

    private var lastCapturedEntryId: String = String()

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    override fun setNaTraitsText() {
    }

    override fun type(): String {
        return Formats.GREEN_SEEKER.getDatabaseName()
    }

    override fun init(act: Activity) {
        recyclerView = act.findViewById(R.id.recycler_view)
        connectButton = act.findViewById(R.id.connect_btn)
        captureButton = act.findViewById(R.id.capture_btn)
        disconnectButton = act.findViewById(R.id.disconnect_btn)
        lineChart = act.findViewById(R.id.line_chart)
        settingsButton = act.findViewById(R.id.settings_btn)

        recyclerView?.adapter = LineGraphSelectableAdapter(this)

    }

    override fun loadLayout() {
        super.loadLayout()

        loadSpectralFactsList(firstLoad = true)

        if (!establishConnection()) {
            setupConnectUi()
        } else {
            (context as CollectActivity).greenSeekerGattManager.device?.let { greenseeker ->
                enableCapture(Device(greenseeker).apply {
                    displayableName = greenseeker.name ?: deviceType
                })
            }
        }

        settingsButton?.setOnClickListener {
            if (!isLocked) {
                showSettings()
            }
        }

        controller.getInputView().visibility = View.VISIBLE

        controller.updateNumberOfObservations()
    }

    override fun layoutId(): Int {
        return R.layout.trait_green_seeker
    }

    private fun showSettings() {

        var dialog: AlertDialog? = null

        val greenSeekerManager = (context as? CollectActivity)?.greenSeekerGattManager

        greenSeekerManager?.device?.let { device ->

            val settingsView = GattDeviceSettingsView(context, device) {

                if (isLocked) {
                    return@GattDeviceSettingsView
                }

                prefs.edit(commit = true) {
                    remove(GeneralKeys.GREEN_SEEKER_ADDRESS)
                }

                greenSeekerManager.disconnect()

                setupConnectUi()

                dialog?.dismiss()

            }

            dialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(context.getString(R.string.device_settings_dialog_title))
                .setView(settingsView)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                }
                .setView(settingsView)
                .create()

            dialog?.show()
        }

    }

    private fun setupConnectUi() {
        connectButton?.visibility = VISIBLE
        disconnectButton?.visibility = GONE
        settingsButton?.visibility = GONE
        startDeviceSearch()
        setupConnectButton()
    }

    private fun loadSpectralFactsList(firstLoad: Boolean = false) {

        spectralDataList.clear()

        val studyId = collectActivity.studyId
        val plot = currentRange.uniqueId
        val traitDbId = currentTrait.id

        //query facts table with spectral uri to get all saved data
        scope.launch(Dispatchers.IO) {

            val observations = database.getAllObservations(studyId, plot, traitDbId)

            val facts = controller.getSpectralViewModel()
                .getSpectralFacts(observations.map { it.internal_id_observation })

            spectralDataList.addAll(facts)

            withContext(Dispatchers.Main) {

                submitList()

                if (firstLoad) {

                    val index = spectralDataList.size - 1

                    selected = index

                    recyclerView?.postDelayed({
                        recyclerView?.scrollToPosition(index)
                    }, 500L)
                }
            }
        }
    }

    private fun submitList(submitPlaceholder: Boolean = false) {

        scope.launch(Dispatchers.Main) {

            val frames = spectralDataList.filterNotNull()
                .map {
                    val observation = database.getObservationById(it.observationId.toString())
                    it.toSpectralFrame(
                        observation!!.observation_unit_id,
                        observation.observation_variable_db_id.toString()
                    )
                }
                .filter {
                    it.traitId == currentTrait.id && it.entryId == currentRange.uniqueId
                }.toMutableList()

            if (submitPlaceholder) {
                frames.add(SpectralFrame.placeholder())
            } else {
                frames.removeIf { it.traitId.isEmpty() }
            }

            submitSpectralList(frames, submitPlaceholder)

            controller.updateNumberOfObservations()

        }
    }

    private fun submitSpectralList(frames: List<SpectralFrame>, submitPlaceholder: Boolean = false) {

        if (frames.isEmpty() && !submitPlaceholder) {
            lineChart?.visibility = GONE
        } else {
            lineChart?.visibility = VISIBLE
        }

        val fs = spectralDataList.filterNotNull()
            .map {
                val observation = database.getObservationById(it.observationId.toString())
                it.toSpectralFrame(
                    observation!!.observation_unit_id,
                    observation.observation_variable_db_id.toString()
                )
            }
            .filter {
                it.traitId == currentTrait.id && it.entryId == currentRange.uniqueId
            }

        if (fs.isEmpty() && !submitPlaceholder) {
            lineChart?.visibility = GONE
        } else {
            lineChart?.visibility = VISIBLE
        }

        if (selected < fs.size && selected >= 0) {

            val average = fs[selected].color

            controller.getInputView().text = average
        }

        val entries = ArrayList(fs.filter { it.wavelengths.isNotEmpty() && it.values.isNotEmpty() }
            .mapIndexed { index, frame ->
                val color =
                    if (index == selected) getColor(R.attr.fb_graph_item_selected_color) else Color.BLACK
                val ws = frame.wavelengths.split(" ").map { it.toFloat() }
                val vs = frame.values.split(" ").map { it.toFloat() }

                FrameEntry(ws.mapIndexed { i, l ->
                    Entry(l, vs[i])
                }, color)
            })

        renderNormal(
            lineChart!!,
            entries
        )

        lineChart!!.invalidate()

        submitLinesList(if (submitPlaceholder) fs + SpectralFrame.placeholder() else fs)

    }

    private fun submitLinesList(frames: List<SpectralFrame>) {
        val lineData = frames
            .mapIndexed { index, data ->
                if (data.traitId.isEmpty()) LineGraphSelectableAdapter.LineColorData.placeholder()
                else LineGraphSelectableAdapter.LineColorData(
                    index,
                    if (index == selected) getColor(R.attr.fb_graph_item_selected_color) else getColor(
                        R.attr.fb_graph_item_unselected_color
                    ),
                    data.timestamp
                )
            }

        controller.getInputView().visibility = View.VISIBLE

        (recyclerView?.adapter as? LineGraphSelectableAdapter)?.submitList(lineData)
    }

    private fun getColor(resId: Int): Int {
        val colorResValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(resId, colorResValue, true)
        return colorResValue.data
    }

    private fun establishConnection(): Boolean {
        return (context as CollectActivity).greenSeekerGattManager.isConnected
    }

    private fun setupConnectButton() {

        connectButton?.setOnClickListener {
            if (isLocked) return@setOnClickListener
            startDeviceSearch()
            showDeviceListDialog()
        }
    }

    private fun startDeviceSearch() {
        var found = false

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

//            adapter.bondedDevices.forEach { bonded ->
//                deviceList = deviceList + Device(bonded).apply {
//                    displayableName = bonded.name ?: deviceType
//                }
//            }
            //adapter.bondedDevices.forEach { device ->

            val address = prefs.getString(GeneralKeys.GREEN_SEEKER_ADDRESS, null)
            val scanner = (context as CollectActivity).bleScanner.apply {
                onDeviceFound = { device ->
                    if (!found) {
                        if (device.name != null && device.name.isNotEmpty()) {
                            //Log.d("BLEScanner", "Discovered device: ${device.name}")
                            if ("GreenSeeker" in device.name) {
                                if (address != null && device.address == address) {
                                    found = true
                                    stopScanning()
                                    connectDevice(Device(device))
                                }
                                //Log.d("BLEScanner", "Adding GreenSeeker device: ${device.name}")
                                deviceList = deviceList + Device(device).apply {
                                    displayableName = device.name
                                }
                            }
                        }
                    }
                }
            }

            scanner.startScanning()
            //}
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
                Toast.makeText(context, context.getString(R.string.nix_device_connecting), Toast.LENGTH_SHORT).show()
                (context as CollectActivity).bleScanner.stopScanning()
                prefs.edit(commit = true) {
                    putString(GeneralKeys.GREEN_SEEKER_ADDRESS, (device.deviceImplementation as BluetoothDevice).address)
                }
                connectDevice(device)
            }
        } else {
            Toast.makeText(context, R.string.no_devices_found, Toast.LENGTH_SHORT).show()
            startDeviceSearch()
        }
    }

    private var isCapturing = false
    private fun connectDevice(device: Device) {
        Log.d(TAG, "Connecting to device: ${device.displayableName}")

        (context as CollectActivity).greenSeekerGattManager.apply {
            connect(device.deviceImplementation as BluetoothDevice,
                listener = object : GreenSeekerGattManager.GreenSeekerListener {
                    override fun onTriggerDown() {
                        scope.launch {
                            if (!isCapturing) {
                                lastCapturedEntryId = currentRange.uniqueId
                                enableCapture(device)
                                submitList(true)
                                hapticFeedback.vibrate()
                            }
                            isCapturing = true
                        }
                    }

                    override fun onTriggerUp() {
                        scope.launch {
                            hapticFeedback.vibrate()
                            saveNdviValues()
                            ndviValues.clear()
                            isCapturing = false
                        }
                    }

                    override fun onDeviceConnected() {
                        scope.launch {
                            enableCapture(device)
                        }
                    }

                    override fun onDeviceDisconnected() {
                        scope.launch {
                            setupConnectUi()
                        }
                    }

                    override fun onDataReceived(ndviValue: Int) {
                        ndviValues.add(ndviValue)
                    }

                    override fun onAverageReceived(ndviValue: Int) {
//                        scope.launch {
//                            controller.getInputView().text = ndviValue.toString()
//                        }
                    }

                    override fun onError(message: String) {
                    }

                })
        }
     //toggleProgressBar(false)
        //Toast.makeText(context, R.string.device_connected, Toast.LENGTH_SHORT).show()
    }

    private var ndviValues = mutableListOf<Int>()

    private fun saveNdviValues() {

        //use spectral tables to save values
        if (ndviValues.isEmpty()) {
            Toast.makeText(context, R.string.no_data_captured, Toast.LENGTH_SHORT).show()
            submitList()
            return
        }

        val entryId = currentRange.uniqueId
        val traitId = currentTrait.id

        val avgString = (ndviValues.average() / 100.0).toString()
        val average = avgString.substring(0, min(5, avgString.length))

        scope.launch(Dispatchers.Main) {
            controller.getInputView().text = average
        }

        val frame = SpectralFrame(
            color = average,
            timestamp = OffsetDateTime.now().format(
                basicTimeFormatter
            ),
            entryId = entryId,
            traitId = traitId
        ).also {

            it.wavelengths = ndviValues.mapIndexed { index, fl -> index.toFloat() }.joinToString(" ") { it.toString() }

            it.values = ndviValues.joinToString(" ") { (it / 100.0).toString() }

        }

        writeGreenSeekerDataFile(frame)?.let { spectralUri ->

            writeGreenSeekerDataToDatabase(frame, average, spectralUri, entryId, traitId)

        }
    }

    private fun getSpectralUri(deviceType: String): Uri? {

        val sanitizedTraitName = FileUtil.sanitizeFileName(currentTrait.name)
        val sanitizedDeviceName = FileUtil.sanitizeFileName(deviceType)

        DocumentTreeUtil.getFieldMediaDirectory(context, sanitizedTraitName)?.let { mediaDir ->

            val spectralFileName = "${sanitizedDeviceName}_spectral_file.csv"

            val spectralFile = mediaDir.findFile(spectralFileName)
                ?: mediaDir.createFile("text/csv", spectralFileName)

            return spectralFile?.uri
        }

        return null
    }

    private fun writeGreenSeekerDataFile(frame: SpectralFrame): String? {

        getSpectralUri(deviceType)?.let { uri ->

            return uri.toString()
        }

        return null
    }

    private fun writeGreenSeekerDataToDatabase(
        frame: SpectralFrame,
        average: String,
        uri: String,
        entryId: String,
        traitId: String
    ) {

        val deviceAddress = controller.getPreferences().getString(GeneralKeys.GREEN_SEEKER_ADDRESS, "") ?: ""
        val deviceName = "GreenSeeker"
        val studyId = collectActivity.studyId
        val person = (context as? CollectActivity)?.person
        val location = (context as? CollectActivity)?.locationByPreferences
        val comment = null
        val createdAt = OffsetDateTime.now().format(basicTimeFormatter)

        scope.launch(Dispatchers.IO) {

            spectralSaver.saveData(
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
                    color = average,
                    uri = uri,
                    entryId = entryId,
                    traitId = traitId
                )
            ).onSuccess { fact ->
                submitNewSpectralFact(fact)
            }.onFailure { exception ->
                Log.e(NixTraitLayout.TAG, "Failed to save spectral data", exception)
            }
        }
    }

    private suspend fun submitNewSpectralFact(fact: SpectralFact) {

        withContext(Dispatchers.Main) {

            collectInputView.visibility = GONE

            spectralDataList.add(fact)

            val index = spectralDataList.indexOf(fact)

            if (currentRange.uniqueId == lastCapturedEntryId) {

                selected = index

            }

            submitList()

            recyclerView?.postDelayed({
                recyclerView?.scrollToPosition(index)
            }, 500L)

            soundFeedback.playCelebrate()
        }
    }

    /**
     * Color the graph if it is chosen and plot the series into the graph view.
     */
    private fun renderNormal(graph: LineChart, entries: ArrayList<FrameEntry>) {

        graph.clear()

        graph.legend.isEnabled = false
        graph.description.isEnabled = false
        graph.setTouchEnabled(false)
        graph.setDrawGridBackground(false)
        graph.setDrawBorders(true)
        graph.setBackgroundColor(Color.WHITE)
        graph.setDrawMarkers(false)
        graph.xAxis.position = XAxis.XAxisPosition.BOTTOM
        graph.xAxis.granularity = 1f
        graph.axisLeft.granularity = 0.01f
        graph.axisRight.isEnabled = false

        graph.data = LineData(
            *entries
                .mapIndexed { index, data ->
                    LineDataSet(data.data, index.toString()).apply {
                        val isSelected = index == selected
                        lineWidth = if (isSelected) 5f else 2.5f
                        if (!isSelected) {
                            enableDashedLine(1f, 2f, 1f)
                        }
                        color = data.color
                        mode = LineDataSet.Mode.LINEAR
                        val isSingleValue = data.data.size < 2
                        setDrawValues(isSingleValue)
                        setDrawCircles(isSingleValue)
                        setDrawFilled(isSingleValue)
                        if (isSingleValue) {
                            setCircleColor(data.color)
                            circleRadius = 5f
                        }
                    }
                }
                .toTypedArray())

        graph.notifyDataSetChanged()

    }

    private fun enableCapture(device: Device) {
        connectButton?.visibility = INVISIBLE
        settingsButton?.visibility = VISIBLE
        lineChart?.visibility = VISIBLE
        recyclerView?.visibility = VISIBLE
    }

    override fun deleteTraitListener() {
        super.deleteTraitListener()

        if (isLocked) return

        scope.launch(Dispatchers.IO) {

            runCatching {

                controller.getInputView().clear()

                if (currentObservation != null && currentObservation.value == "NA") {

                    deleteNaObservation(currentObservation)

                } else {

                    deleteSpectralFact(selected)

                }

            }.onSuccess {

                Log.d(TAG, "Deleted spectral data")

                withContext(Dispatchers.Main) {

                    loadSpectralFactsList()
                }

            }.onFailure {

                Log.e(TAG, "Failed to delete spectral data", it)

            }
        }
    }

    private fun deleteObservation(model: ObservationModel) {

        scope.launch(Dispatchers.IO) {

            database.deleteObservation(model.internal_id_observation.toString())

        }
    }

    private fun deleteNaObservation(model: ObservationModel) {

        scope.launch(Dispatchers.IO) {

            deleteObservation(model)

            withContext(Dispatchers.Main) {

                collectInputView?.visibility = GONE

            }
        }
    }

    private fun deleteSpectralFact(selected: Int) {

        val studyId = collectActivity.studyId
        val plot = (context as? CollectActivity)?.observationUnit
        val traitDbId = currentTrait.id

        with(controller.getSpectralViewModel()) {

            spectralDataList[selected]?.let { fact ->

                deleteSpectralFact(fact)

                deleteSpectralObservation(studyId, plot.toString(), traitDbId, fact.id.toString())

                spectralDataList.remove(fact)

                this@GreenSeekerTraitLayout.selected = 0
            }
        }
    }

    fun setNa() {

        if (isLocked) return

        //check if observations exist already
        val studyId = collectActivity.studyId
        val plot = (context as? CollectActivity)?.observationUnit
        val traitDbId = currentTrait.id
        val observations = database.getAllObservations(studyId, plot, traitDbId)

        if (observations.isEmpty()) {

            scope.launch(Dispatchers.IO) {

                insertAndSetNa()
            }

        } else {

            if (observations.size == 1 && observations[0].value == "NA") {

                //already set to NA, do nothing
                return

            } else {

                askUserReplaceObservationsWithNa(observations)

            }
        }
    }

    private fun askUserReplaceObservationsWithNa(observations: Array<ObservationModel>) {
        AlertDialog.Builder(context)
            .setTitle(R.string.replace_observations)
            .setMessage(R.string.replace_observations_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteObservationsAndInsertNa(observations)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteObservationsAndInsertNa(observations: Array<ObservationModel>) {

        scope.launch(Dispatchers.IO) {

            observations.forEach {
                database.deleteObservation(it.internal_id_observation.toString())
            }

            spectralDataList.clear()

            insertAndSetNa()
        }
    }

    private suspend fun insertAndSetNa() {

        database.insertObservation(
            currentRange.uniqueId,
            currentTrait.id,
            "NA",
            (context as? CollectActivity)?.person,
            (context as? CollectActivity)?.locationByPreferences,
            "",
            collectActivity.studyId,
            null,
            null,
            "1"
        )

        withContext(Dispatchers.Main) {

            collectInputView.visibility = VISIBLE
            collectInputView.text = "NA"

            lineChart?.visibility = GONE
            recyclerView?.visibility = GONE

            submitList()
        }
    }

    override fun refreshLayout(onNew: Boolean) {
        super.refreshLayout(false)
        loadSpectralFactsList()
    }

    override fun refreshLock() {
        super.refreshLock()
        loadLayout()
    }

    override fun onItemSelected(position: Int, onSelect: (() -> Unit)?) {
        selected = position
        submitList()
    }

    override fun onItemLongClick(position: Int, onSelect: (() -> Unit)?) {
        //show Alert dialog with edit text
        val item = recyclerView?.adapter?.getItemId(position)
        if (item != null) {

            //create alert dialog
            val builder = AlertDialog.Builder(context)
            builder.setTitle(R.string.spectral_trait_insert_note_message)
            val input = EditText(context)
            builder.setView(input)
            input.setText(spectralDataList[position]?.comment ?: "")
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                input.text.toString()
                runCatching {
                    val fact = spectralDataList[position]
                    fact?.let {
                        it.comment = input.text.toString()
                        updateDatabaseNote(it)
                    }
                }.onFailure {
                    Log.e(TAG, "Failed to update spectral data", it)
                }
            }
            builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            builder.show()
        }
    }

    private fun updateDatabaseNote(fact: SpectralFact) {

        scope.launch(Dispatchers.IO) {

            controller.getSpectralViewModel().updateFact(fact)

            withContext(Dispatchers.Main) {
                soundFeedback.playCelebrate()
            }
        }
    }
}