package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.spectral.ColorAdapter
import com.fieldbook.tracker.adapters.spectral.LineGraphSelectableAdapter
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.database.models.spectral.SpectralFact
import com.fieldbook.tracker.devices.spectrometers.Device
import com.fieldbook.tracker.devices.spectrometers.SpectralFrame
import com.fieldbook.tracker.devices.spectrometers.Spectrometer
import com.fieldbook.tracker.devices.spectrometers.Spectrometer.ResultCallback
import com.fieldbook.tracker.dialogs.SimpleListDialog
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.fieldbook.tracker.utilities.FileUtil
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

open class SpectralTraitLayout : BaseTraitLayout, Spectrometer,
    LineGraphSelectableAdapter.Listener, ColorAdapter.Listener {

    companion object {
        const val TAG = "SpectralTraitLayout"
    }

    /**
     * data model for rendering wavelengths and values onto a line graph
     */
    data class FrameEntry(val data: List<Entry>, val color: Int = Color.BLACK)

    /** state data pattern that switches UI from spectral or color collection **/
    sealed class State(val id: Int) {
        object Spectral : State(0)
        object Color : State(1)
    }

    //background worker used to save and query data off the main thread
    protected val background = CoroutineScope(Dispatchers.IO)

    protected val colorList = mutableListOf<String>()

    protected var connectButton: FloatingActionButton? = null
    protected var captureButton: FloatingActionButton? = null
    protected var disconnectButton: FloatingActionButton? = null
    protected var recycler: RecyclerView? = null
    protected var colorRecycler: RecyclerView? = null
    protected var lineChart: LineChart? = null
    protected var progressBar: ProgressBar? = null
    protected var settingsButton: FloatingActionButton? = null

    protected val spectralDataList = mutableListOf<SpectralFact?>()

    protected var selected: Int = 0
    protected var state: State = State.Spectral

    protected val hapticFeedback by lazy {
        controller.getVibrator()
    }

    protected val soundFeedback by lazy {
        controller.getSoundHelper()
    }

    private var lastCapturedEntryId: String = String()

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    override fun type(): String {
        return Formats.BASE_SPECTRAL.getDatabaseName()
    }

    override fun layoutId(): Int {
        return R.layout.traits_spectral
    }

    override fun init(act: Activity) {
        connectButton = act.findViewById(R.id.connect_btn)
        captureButton = act.findViewById(R.id.capture_btn)
        disconnectButton = act.findViewById(R.id.disconnect_btn)
        recycler = act.findViewById(R.id.recycler_view)
        colorRecycler = act.findViewById(R.id.color_recycler_view)
        lineChart = act.findViewById(R.id.line_chart)
        progressBar = act.findViewById(R.id.progress_bar)
        settingsButton = act.findViewById(R.id.settings_btn)

        recycler?.adapter = LineGraphSelectableAdapter(this)
        colorRecycler?.adapter = ColorAdapter(context, this)
    }

    override fun afterLoadNotExists(act: CollectActivity?) {
        super.afterLoadNotExists(act)
        collectInputView?.visibility = GONE
    }

    override fun afterLoadExists(act: CollectActivity?, value: String?) {
        super.afterLoadExists(act, value)

        if (value == "NA") {
            collectInputView.visibility = VISIBLE
        } else {
            collectInputView.visibility = GONE
        }
    }

    override fun loadLayout() {
        super.loadLayout()

        spectralDataList.clear()

        loadSpectralFactsList(firstLoad = true)

        if (!establishConnection()) {
            setupConnectUi()
        }

        settingsButton?.setOnClickListener {
            if (!isLocked) {
                showSettings()
            }
        }

        setUiMode()

        controller.updateNumberOfObservations()
    }

    protected fun setupConnectUi() {
        (context as CollectActivity).getNixSensorHelper().connectedDevice = null
        connectButton?.visibility = VISIBLE
        captureButton?.visibility = GONE
        disconnectButton?.visibility = GONE
        settingsButton?.visibility = GONE
        toggleProgressBar(false)
        startDeviceSearch()
        setupConnectButton()
    }

    private fun loadSpectralFactsList(firstLoad: Boolean = false) {

        spectralDataList.clear()

        val studyId = collectActivity.studyId
        val plot = currentRange.uniqueId
        val traitDbId = currentTrait.id

        //query facts table with spectral uri to get all saved data
        background.launch {

            val observations = database.getAllObservations(studyId, plot, traitDbId)

            val facts = controller.getSpectralViewModel()
                .getSpectralFacts(observations.map { it.internal_id_observation })
            spectralDataList.addAll(facts)

            withContext(Dispatchers.Main) {

                submitList()

                if (firstLoad) {

                    val index = spectralDataList.size - 1

                    selected = index

                    listOf(recycler, colorRecycler).forEachIndexed { i, r ->
                        r?.postDelayed({
                            r.scrollToPosition(index)
                        }, i*50L)
                    }
                }
            }
        }
    }

    override fun isSpectralCompatible(device: Device): Boolean = true

    override fun establishConnection(): Boolean {
        TODO("Not yet implemented")
    }

    override fun startDeviceSearch() {
        TODO("Not yet implemented")
    }

    override fun getDeviceList(): List<Device> {
        TODO("Not yet implemented")
    }

    override fun connectDevice(device: Device) {
        TODO("Not yet implemented")
    }

    override fun disconnectAndEraseDevice(device: Device) {
        TODO("Not yet implemented")
    }

    override fun saveDevice(device: Device) {
        TODO("Not yet implemented")
    }

    override fun capture(
        device: Device,
        entryId: String,
        traitId: String,
        callback: ResultCallback
    ) {
        TODO("Not yet implemented")
    }

    override fun writeSpectralDataToDatabase(
        frame: SpectralFrame,
        color: String,
        uri: String,
        entryId: String,
        traitId: String
    ) {
        TODO("Not yet implemented")
    }

    protected suspend fun submitNewSpectralFact(fact: SpectralFact) {

        withContext(Dispatchers.Main) {


            collectInputView.visibility = GONE

            spectralDataList.add(fact)

            val index = spectralDataList.indexOf(fact)

            if (currentRange.uniqueId == lastCapturedEntryId) {

                selected = index

            }

            submitList()

            listOf(recycler, colorRecycler).forEachIndexed { i, r ->
                r?.postDelayed({
                    r.scrollToPosition(index)
                }, i*50L)
            }

            soundFeedback.playCelebrate()
        }
    }

    private fun updateDatabaseNote(fact: SpectralFact) {

        background.launch {

            controller.getSpectralViewModel().updateFact(fact)

            withContext(Dispatchers.Main) {
                soundFeedback.playCelebrate()
            }
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

    protected fun writeSpectralDataToFile(deviceType: String, frame: SpectralFrame, providesSpectral: Boolean): String? {

        getSpectralUri(deviceType)?.let { uri ->

//            if (providesSpectral) {
//                bisectAndWriteSpectralData(uri, frame)
//            }

            return uri.toString()
        }

        return null
    }

    /**
     * Headers:
     * sample_name, device_id, comments, created_at, **wavelengths,
     */
    private fun bisectAndWriteSpectralData(uri: Uri, newFrame: SpectralFrame) {

        //data to insert, but need to check existing headers/values
        val wavelengths = if (newFrame.wavelengths.isNotEmpty()) newFrame.wavelengths.split(" ")
            .map { it.toFloat() } else listOf()
        val values = if (newFrame.values.isNotEmpty()) newFrame.values.split(" ")
            .map { it.toFloat() } else listOf()

        //read file, create map from wavelengths to values
        val existingData = mutableListOf<MutableMap<Float, Float>>() //mutableMapOf<Float, Float>()
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val header = reader.readLine()
                ?: //file is empty, create new file
                return@use

            val existingWavelengths = header.split(",").map { it.toFloat() }
            reader.lines().forEach {
                val rowValues = it.split(",")
                val rowMapping = mutableMapOf<Float, Float>()
                rowValues.mapIndexed { index, v ->
                    rowMapping[existingWavelengths[index]] = v.toFloatOrNull() ?: 0f
                }
                existingData.add(rowMapping)
            }
        }

        val newMappedData = mutableMapOf<Float, Float>()
        for (i in wavelengths.indices) {
            newMappedData[wavelengths[i]] = values[i]
        }

        existingData.add(newMappedData)

        //create new wavelengths header by bisecting new wavelengths with old
        val mergedWavelengths = existingData.flatMap { it.keys }.sorted().distinct()

        //write new data to file
        context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { writer ->

            val metadataHeader = "sample_name, device_id, comments, created_at"

            //write header
            writer.write(mergedWavelengths.joinToString(",") + "\n")

            for (row in existingData) {
                val rowValues =
                    mergedWavelengths.joinToString(",") { row.getOrDefault(it, 0f).toString() }
                writer.write(rowValues + "\n")
            }
        }
    }

    protected fun setupConnectButton() {

        background.launch {

            withContext(Dispatchers.Main) {

                progressBar?.visibility = GONE

                connectButton?.setOnClickListener {
                    if (isLocked) return@setOnClickListener
                    startDeviceSearch()
                    showDeviceListDialog()
                }
            }
        }
    }

    private fun showDeviceListDialog() {

        val devices = getDeviceList()

        if (devices.isNotEmpty()) {

            SimpleListDialog.show(
                context,
                R.string.select_device,
                devices,
                devices.mapNotNull { it.displayableName }
            ) { device ->
                Toast.makeText(context, context.getString(R.string.nix_device_connecting), Toast.LENGTH_SHORT).show()
                connectDevice(device)
            }
        } else {
            Toast.makeText(context, R.string.no_devices_found, Toast.LENGTH_SHORT).show()
            startDeviceSearch()
        }
    }

    protected fun enableCapture(device: Device) {
        connectButton?.visibility = INVISIBLE
        captureButton?.visibility = VISIBLE
        disconnectButton?.visibility = VISIBLE
        settingsButton?.visibility = VISIBLE
        toggleProgressBar(false)
        captureButton?.isEnabled = true
        setupCaptureButton(device)
    }

    private fun setupCaptureButton(device: Device) {
        captureButton?.setOnClickListener {

            if (isLocked) return@setOnClickListener

            val entryId = currentRange.uniqueId
            val traitId = currentTrait.id

            captureButton?.isEnabled = false

            toggleProgressBar(true)

            Log.d(TAG, "Capture button clicked")

            lastCapturedEntryId = entryId

            capture(device, entryId, traitId) { result ->

                if (result) {

                    submitList(submitPlaceholder = true)

                }
            }

            hapticFeedback.vibrate()

        }
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
        (recycler?.adapter as? LineGraphSelectableAdapter)?.submitList(lineData)
    }

    private fun getColor(resId: Int): Int {
        val colorResValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(resId, colorResValue, true)
        return colorResValue.data
    }

    private fun submitList(submitPlaceholder: Boolean = false) {

        background.launch(Dispatchers.Main) {

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

            when (state) {
                State.Spectral -> submitSpectralList(frames, submitPlaceholder)
                State.Color -> submitColorList(frames)
            }

            if (submitPlaceholder) {

                val index = frames.size - 1

                listOf(recycler, colorRecycler).forEachIndexed { i, r ->
                    r?.postDelayed({
                        r.scrollToPosition(index)
                    }, i*50L)
                }
            }

            controller.updateNumberOfObservations()

        }
    }

    private fun submitSpectralList(frames: List<SpectralFrame>, submitPlaceholder: Boolean = false) {

        if (frames.isEmpty() && !submitPlaceholder) {
            lineChart?.visibility = GONE
            recycler?.visibility = GONE
        } else {
            lineChart?.visibility = VISIBLE
            recycler?.visibility = VISIBLE
        }

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
            }

        if (frames.isEmpty() && !submitPlaceholder) {
            lineChart?.visibility = GONE
            recycler?.visibility = GONE
        } else {
            lineChart?.visibility = VISIBLE
            recycler?.visibility = VISIBLE
        }

        renderNormal(
            lineChart!!,
            ArrayList(frames.filter { it.wavelengths.isNotEmpty() && it.values.isNotEmpty() }
                .mapIndexed { index, frame ->
                    val color =
                        if (index == selected) getColor(R.attr.fb_graph_item_selected_color) else Color.BLACK
                    val ws = frame.wavelengths.split(" ").map { it.toFloat() }
                    val vs = frame.values.split(" ").map { it.toFloat() }

                    Triple(ws, vs, color)
                }
                .filter { (ws, vs, _) -> ws.size == vs.size }
                .map { (ws, vs, color) ->
                    FrameEntry(ws.mapIndexed { i, w ->
                        Entry(w, vs[i]) }, color)
                })
        )

        lineChart!!.invalidate()

        submitLinesList(if (submitPlaceholder) frames + SpectralFrame.placeholder() else frames)
    }

    private fun submitColorList(frames: List<SpectralFrame>) {

        if (frames.isEmpty()) {
            colorRecycler?.visibility = GONE
        } else {
            colorRecycler?.visibility = VISIBLE
        }

        (colorRecycler?.adapter as? ColorAdapter)?.submitList(frames.map { if (it.traitId.isEmpty()) "-1" else it.color })
    }

    private fun interpolate(
        wavelengths: List<Float>,
        values: List<Float>,
        minWave: Int,
        maxWave: Int,
        step: Int = 10,
        algorithm: (List<Float>, List<Float>, Float) -> Float
    ): Pair<List<Int>, List<Float>> {

        val newWavelengths = (minWave..maxWave step step).toList()

        // Interpolate values for each wavelength in the new range
        val newValues = newWavelengths.map { wave ->
            algorithm(wavelengths, values, wave.toFloat())
        }

        return Pair(newWavelengths, newValues)
    }

    fun linearInterpolation(
        wavelengths: List<Float>,
        values: List<Float>,
        target: Float
    ): Float {
        val index = wavelengths.indexOfFirst { it >= target }
        if (index == 0 || index == -1) return values.getOrElse(index) { 0f }

        val x0 = wavelengths[index - 1]
        val x1 = wavelengths[index]
        val y0 = values[index - 1]
        val y1 = values[index]

        return y0 + ((y1 - y0) / (x1 - x0)) * (target - x0)
    }

    protected fun toggleProgressBar(flag: Boolean) {

        background.launch(Dispatchers.Main) {

            //progressBar?.visibility = if (flag) VISIBLE else INVISIBLE

        }
    }

    override fun deleteTraitListener() {
        super.deleteTraitListener()

        if (isLocked) return

        background.launch {

            runCatching {

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

    override fun setNaTraitsText() {

    }

    private fun deleteObservation(model: ObservationModel) {

        background.launch {

            database.deleteObservation(model.internal_id_observation.toString())

        }
    }

    private fun deleteNaObservation(model: ObservationModel) {

        background.launch {

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

                this@SpectralTraitLayout.selected = 0
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

            background.launch {

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

        background.launch {

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
            null,
            "1"
        )

        withContext(Dispatchers.Main) {

            collectInputView.visibility = VISIBLE
            collectInputView.text = "NA"

            lineChart?.visibility = GONE
            recycler?.visibility = GONE

            submitList()
        }
    }

    override fun refreshLayout(onNew: Boolean?) {
        super.refreshLayout(onNew)
        loadSpectralFactsList()
    }

    override fun onItemSelected(position: Int, onSelect: (() -> Unit)?) {
        selected = position
        submitList()
    }

    override fun onItemLongClick(position: Int, onSelect: (() -> Unit)?) {
        //show Alert dialog with edit text
        val item = recycler?.adapter?.getItemId(position)
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

    /**
     * Color the graph if it is chosen and plot the series into the graph view.
     */
    private fun renderNormal(graph: LineChart, entries: ArrayList<FrameEntry>) {

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
                        setDrawValues(false)
                        setDrawCircles(false)
                        setDrawFilled(false)
                    }
                }
                .toTypedArray())

        graph.notifyDataSetChanged()

    }

    open fun showSettings() {}

    fun onSettingsChanged() {

        setUiMode()

        submitList()
    }

    private fun setUiMode() {
        when (prefs.getInt(GeneralKeys.SPECTRAL_MODE, State.Spectral.id)) {
            State.Spectral.id -> if (state == State.Color) spectralUiMode()
            else -> if (state == State.Spectral) colorUiMode()
        }
    }

    protected open fun spectralUiMode() {
        state = State.Spectral
        lineChart?.visibility = VISIBLE
        recycler?.visibility = VISIBLE
        colorRecycler?.visibility = GONE
    }

    protected open fun colorUiMode() {
        state = State.Color
        lineChart?.visibility = GONE
        recycler?.visibility = GONE
        colorRecycler?.visibility = VISIBLE
    }

    override fun onColorDeleted(position: Int, onDelete: (() -> Unit)?) {

        if (isLocked) return

        background.launch {

            runCatching {
                val observation = database.getObservationById(
                    spectralDataList[position]?.observationId.toString()
                )
                deleteObservation(observation!!)
                spectralDataList.removeAt(position)
                submitList()
            }.onFailure {
                Log.e(TAG, "Failed to delete color", it)
            }
        }
    }

    override fun onColorLongClicked(position: Int) {
        try {
            val obsId = spectralDataList[position]?.observationId
            (context as? CollectActivity)?.showObservationMetadataDialog(obsId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}