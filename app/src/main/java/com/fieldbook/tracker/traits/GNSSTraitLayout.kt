package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteException
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.Group
import androidx.core.view.isVisible
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.location.GPSTracker
import com.fieldbook.tracker.location.gnss.GNSSResponseReceiver
import com.fieldbook.tracker.location.gnss.GNSSResponseReceiver.Companion.ACTION_BROADCAST_GNSS_TRAIT
import com.fieldbook.tracker.location.gnss.NmeaParser
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.GeoJsonUtil
import com.fieldbook.tracker.utilities.GeodeticUtils
import com.fieldbook.tracker.utilities.GeodeticUtils.Companion.truncateFixQuality
import com.fieldbook.tracker.utilities.GnssThreadHelper
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A FieldBook trait for acquiring GPS coordinates from an external, bluetooth-connected device.
 * Users should have external devices already paired through their operating system.
 * First step is to click the connect button, this shows a list of paired devices.
 * Second step is to choose the device to listen to, this will automatically establish a connection
 * and begin listening for NMEA messages.
 *
 * This can be used in conjunction with GeoNav (external only). User may have to manually disconnect/reconnect if
 * the socket cannot be established.
 */
class GNSSTraitLayout : BaseTraitLayout, GPSTracker.GPSTrackerListener {

    companion object {
        const val CONNECTION_STATUS_INTERVAL = 5000L
    }

    private var mActivity: Activity? = null

    //used for communication between threads and ui thread
    private lateinit var mLocalBroadcastManager: LocalBroadcastManager

    private var mLastDevice: BluetoothDevice? = null

    private var mProgressDialog: AlertDialog? = null

    private var precision: String? = null

    private var currentFixQuality = false

    //flag to track when collect button is disabled
    private var isCollectEnabled = false

    //flag to track if this is the first time user is collecting a location before leaving the entry
    private var isFirstCollect = true

    private lateinit var chipGroup: ChipGroup
    private lateinit var averageSwitch: SwitchCompat
    private lateinit var utcTextView: TextView
    private lateinit var satTextView: TextView
    private lateinit var altTextView: TextView
    private lateinit var accTextView: TextView
    private lateinit var latTextView: TextView
    private lateinit var lngTextView: TextView
    private lateinit var hdopTextView: TextView
    private lateinit var precisionSp: Spinner
    private lateinit var connectGroup: Group
    private lateinit var connectButton: FloatingActionButton
    private lateinit var collectButton: ImageButton
    private lateinit var disconnectButton: ImageButton
    private lateinit var progressBar: ProgressBar

    private var lastUtc = String()
    private var currentUtc = String()

    private val mAverageResponseHandler = Handler(Looper.getMainLooper()) {

        val info = it.obj as AverageInfo

        if (!(context as CollectActivity).isDestroyed)
            averagePoints(info)

        mProgressDialog?.dismiss()

        true
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    data class AverageInfo(var unit: ObservationUnitModel, var location: Location?,
                           var points: List<Pair<Double, Double>>,
                           val latLength: Int,
                           val lngLength: Int,
                           val precision: String)

    private fun getThreadHelper(): GnssThreadHelper {
        return controller.getGnssThreadHelper()
    }

    private fun startAverageTimer(info: AverageInfo, period: Long) {

        val runnable = Runnable {

            val pointsToAverage = arrayListOf<Pair<Double, Double>>()
            val startTime = System.nanoTime()
            do {

                val a = latTextView.text.toString().toDoubleOrNull() ?: 0.0
                val b = lngTextView.text.toString().toDoubleOrNull() ?: 0.0

                pointsToAverage.add(a to b)

            } while ((System.nanoTime() - startTime) * 1e-9 < period * 1e-3)

            mAverageResponseHandler.sendMessage(Message().apply {
                obj = info.apply {
                    points = pointsToAverage
                }
            })
        }

        if (controller.getAverageHandler() != null)
            controller.getAverageHandler()?.post(runnable)
        else mProgressDialog?.dismiss()

    }

    override fun setNaTraitsText() {}

    //the type of this trait is "GNSS" Global Navigation Satellite System
    override fun type(): String {
        return "gnss"
    }

    override fun layoutId(): Int {
        return R.layout.trait_gnss
    }

    private val receiver = object : GNSSResponseReceiver() {

        override fun onNmeaMessageReceived(nmea: String?) {

            (mActivity as? CollectActivity)?.let { act ->

                nmea?.let { message ->

                    act.logNmeaMessage(message)

                }
            }
        }

        override fun onGNSSParsed(parser: NmeaParser) {

            currentUtc = parser.utc

            checkBeforeUpdate(parser.getSimpleFix()) {

                //populate ui
                accTextView.text = parser.fix
                latTextView.text = truncateFixQuality(parser.latitude)
                lngTextView.text = truncateFixQuality(parser.longitude)
                utcTextView.text = parser.utc
                hdopTextView.text = parser.hdop

                if (parser.satellites.isEmpty()) {
                    satTextView.text = "${parser.gsv.size}"
                } else {
                    val maxSats = maxOf(parser.satellites.toInt(), parser.gsv.size)
                    satTextView.text = "${parser.gsv.size}/$maxSats"
                }
                altTextView.text = truncateFixQuality(parser.altitude)

                resolveUiStatus()
            }
        }
    }

    private fun unregisterGnssReceiver() {
        try {
            mLocalBroadcastManager.unregisterReceiver(receiver)
        } catch (ignore: Exception) {
        }
    }

    private fun initialize() {

        mProgressDialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.gnss_trait_averaging_dialog_title)
            .setMessage(R.string.gnss_trait_averaging_dialog_message)
            .setView(R.layout.dialog_gnss_trait_averaging)
            .setCancelable(false)
            .create()

        precision = prefs.getString(GeneralKeys.GNSS_LAST_CHOSEN_PRECISION, "Any") ?: "Any"

        //initialize lbm and create a filter to broadcast nmea strings
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(context)

        setupChooseBluetoothDevice()

        checkIfFirstConnect()
    }

    //updates the average warning flag everytime the layout is loaded
    //if a geo coordinate already exists, we warn the user before they try to update it
    private fun checkIfFirstConnect() {

        try {

            val studyDbId = (context as CollectActivity).studyId

            val units = database.getAllObservationUnits(studyDbId.toInt())
                .filter { it.observation_unit_db_id == currentRange.uniqueId }

            if (units.isNotEmpty()) {

                val unit = units.first()

                //the saved geo coordinate location
                val location = GeodeticUtils.parseGeoCoordinate(unit.geo_coordinates)

                isFirstCollect = location == null
            }

        } catch (e: SQLiteException) {

            isFirstCollect = true

        } catch (e: NoSuchElementException) {

            isFirstCollect = true

        }
    }

    /**
     * Persists switch state and interval choice.
     */
    private fun setupAveragingUi() {

        val checked = prefs.getBoolean(PreferenceKeys.GEONAV_AVERAGING, false)

        averageSwitch.setOnCheckedChangeListener { _, isChecked ->

            prefs.edit().putBoolean(PreferenceKeys.GEONAV_AVERAGING, isChecked).apply()

            chipGroup.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE
        }

        averageSwitch.isChecked = checked

        chipGroup.visibility = if (checked) View.VISIBLE else View.INVISIBLE

        val chipId = prefs.getInt(PreferenceKeys.GEONAV_AVERAGING_INTERVAL, R.id.gnss_trait_5s_chip)
        chipGroup.check(chipId)

        chipGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putInt(PreferenceKeys.GEONAV_AVERAGING_INTERVAL, checkedId).apply()
        }
    }

    /**
     * This function is called to initialize the UI. All trait layouts are set to "gone" by default.
     */
    override fun init(act: Activity) {

        mActivity = act

        chipGroup = act.findViewById(R.id.gnss_trait_averaging_chip_group)
        averageSwitch = act.findViewById(R.id.gnss_trait_averaging_switch)
        connectButton = act.findViewById(R.id.gnss_connect_button)
        utcTextView = act.findViewById(R.id.utcTextView)
        satTextView = act.findViewById(R.id.satTextView)
        altTextView = act.findViewById(R.id.altTextView)
        accTextView = act.findViewById(R.id.accTextView)
        latTextView = act.findViewById(R.id.latTextView)
        lngTextView = act.findViewById(R.id.lngTextView)
        hdopTextView = act.findViewById(R.id.hdopTextView)
        connectGroup = act.findViewById(R.id.gnss_group)
        collectButton = act.findViewById(R.id.gnss_collect_button)
        disconnectButton = act.findViewById(R.id.disconnect_button)
        precisionSp = act.findViewById(R.id.precisionSpinner)
        progressBar = act.findViewById(R.id.trait_gnss_pb)

        connectButton.requestFocus()

        initialize()

    }

    private fun setupChooseBluetoothDevice() {

        //setup connect button
        connectButton.visibility = View.VISIBLE

        // Get Location
        connectButton.setOnClickListener {
            if (mActivity != null) {
                controller.getSecurityChecker().connectWith {
                    findPairedDevice()
                }
            } else {
                Toast.makeText(context, R.string.permission_ask_bluetooth, Toast.LENGTH_SHORT).show()
            }
        }

        val deviceName = prefs.getString(GeneralKeys.GNSS_LAST_PAIRED_DEVICE_NAME, null)

        if (deviceName != null) {

            controller.getSecurityChecker().connectWith { devices ->

                val names = devices.map { it.name } + context.getString(R.string.pref_behavior_geonav_internal_gps_choice)
                names.find { it == deviceName }?.let {
                    connectDevice(deviceName)
                }
            }
        }
    }

    /**
     * This function is called to make a database update statement.
     * First the selected studyDbId is found in the preferences, and the static ObservationUnitDao
     * is used to find the relevant Obs. Unit. and update the row with the NMEA data.
     */
    private fun submitGnss(latitude: String, longitude: String, elevation: String, precision: String) {

        if (latitude.isNotBlank() && longitude.isNotBlank()) {

            val studyDbId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

            //save fix length to truncate the average later if needed
            val latLength = latitude.length
            val lngLength = longitude.length

            //temporary variables to also update obs if obs units are averaged
            val newLat = latitude.toDouble()
            val newLng = longitude.toDouble()

            //geo json object : elevation (stored in obs. units, used in navigation)
            //geo json has properties map for additional info
            val geoJson = GeoJsonUtil.GeoJSON(
                geometry = GeoJsonUtil.Geometry(coordinates = arrayOf(newLng, newLat)),
                properties = mapOf("altitude" to elevation, "fix" to precision)
            )

            val units = database.getAllObservationUnits(studyDbId.toInt())
                .filter { it.observation_unit_db_id == currentRange.uniqueId }

            if (units.isNotEmpty()) {

                val unit = units.first()
                //check if the switch is enabled, then update obs units with average value
                if (averageSwitch.isChecked) {

                    val avgDuration = when (chipGroup.checkedChipId) {
                        R.id.gnss_trait_10s_chip -> 10000L
                        R.id.gnss_trait_5s_chip -> 5000L
                        else -> -1L
                    }

                    //the saved geo coordinate location
                    val location = GeodeticUtils.parseGeoCoordinate(unit.geo_coordinates)

                    //listen for the duration and append lat/lngs to an array
                    val pointsToAverage = arrayListOf<Pair<Double, Double>>()
                    val info = AverageInfo(unit, location, pointsToAverage, latLength, lngLength, precision)
                    if (avgDuration > -1L) {

                        if (!isFirstCollect) {

                            //averaging is updating the location, so ask the user
                            alertLocationUpdate {
                                mProgressDialog?.show()
                                startAverageTimer(info, avgDuration)
                            }

                        } else { //no location has been observed so don't ask the user

                            mProgressDialog?.show()
                            startAverageTimer(info, avgDuration)

                        }

                    } else {

                        if (location != null) {

                            if (!isFirstCollect) {

                                //averaging is updating the location, so ask the user
                                alertLocationUpdate {
                                    val original = (location.latitude) to (location.longitude)
                                    val current = newLat to newLng
                                    pointsToAverage.add(original)
                                    pointsToAverage.add(current)
                                    averagePoints(info)
                                }

                            } else {

                                val original = (location.latitude) to (location.longitude)
                                val current = newLat to newLng
                                pointsToAverage.add(original)
                                pointsToAverage.add(current)
                                averagePoints(info)
                            }

                        } else { //no location has been observed so don't ask the user

                            updateCoordinateObservation(unit, geoJson)

                        }
                    }

                } else { //no averaging, so check if there is an observations and ask to update or not

                    if (collectInputView.text.isNotBlank()) {

                        alertLocationUpdate {

                            updateCoordinateObservation(unit, geoJson)

                        }

                    } else updateCoordinateObservation(unit, geoJson)

                }
            }

//observations: store whatever the hell we want - Trevor circa 2021
//            val fbJson = JSONObject(mapOf("latitude" to latitude,
//                    "longitude" to longitude,
//                    "elevation" to elevation,
//                    "accuracy" to accTextView.text
//                    "utc" to utcTextView.text,
//                    "satellites" to satTextView.text))
        }
    }

    private fun updateCoordinateObservation(unit: ObservationUnitModel, json: GeoJsonUtil.GeoJSON) {

        val coordinates = "${json.geometry.coordinates[1]}; ${json.geometry.coordinates[0]}; ${json.properties?.get("fix")}"

        database.updateObservationUnit(unit, json.toJson().toString())

        collectInputView.text = coordinates

        updateObservation(currentTrait, coordinates)

    }

    /**
     * Averages an array of coordinates
     * using method chosen from http://www.geomidpoint.com/calculation.html
     */
    private fun averagePoints(info: AverageInfo) {

        val points = info.points
        val location = info.location
        val unit = info.unit

        val avgPoint = if (points.size > 1) {

            //convert first point to radians
            var avgLat = points[0].first * Math.PI / 180.0
            var avgLng = points[0].second * Math.PI / 180.0

            for (i in 1 until points.size) {

                //convert the next coordinate to radians
                val new = points[i]
                val newLat = new.first * Math.PI / 180.0
                val newLng = new.second * Math.PI / 180.0

                //mid point averaging
                val x = (cos(newLat)*cos(newLng) + cos(avgLat)*cos(avgLng)) / 2.0
                val y = (cos(newLat)*sin(newLng) + cos(avgLat)*sin(avgLng)) / 2.0
                val z = (sin(newLat) + sin(avgLat)) / 2.0

                val avgDistance = sqrt(x*x+y*y)
                avgLat = (atan2(z, avgDistance))
                avgLng = (atan2(y, x))
            }

            //convert radians back to degrees
            avgLat *= 180.0 / Math.PI
            avgLng *= 180.0 / Math.PI

            //truncate average result based on original location fix
            avgLat = if (avgLat.toString().length > info.latLength)
                avgLat.toString().substring(0 until info.latLength).toDouble()
            else avgLat
            avgLng = if (avgLng.toString().length > info.lngLength)
                avgLng.toString().substring(0 until info.lngLength).toDouble()
            else avgLng

            //convert back to strings and truncate based on original quality
            avgLat to avgLng

        } else 0.0 to 0.0

        val averageJson = GeoJsonUtil.GeoJSON(
            geometry = GeoJsonUtil.Geometry(
                coordinates = arrayOf(avgPoint.second, avgPoint.first)
            ),
            properties = mapOf(
                "altitude" to (location?.altitude?.toString() ?: ""),
                "fix" to info.precision
            )
        )

        updateCoordinateObservation(unit, averageJson)
    }

    private fun alertLocationUpdate(f: () -> Unit) {

        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.trait_gnss_geo_coord_update_dialog_title)
            .setMessage(R.string.trait_gnss_geo_coord_update_dialog_message)
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->

                f()

            }.show()
    }

    /**
     * When the connect button is pressed, this function is called which triggers a Dialog for
     * the user to choose bluetooth devices from.
     **/
    private fun findPairedDevice() {

        //check if the device has bluetooth enabled, if not, request it to be enabled via system action
        controller.getSecurityChecker().withAdapter { adapter ->

            if (adapter.isEnabled) {

                //create a dialog with the paired devices
                val pairedDevices = adapter.bondedDevices

                //create table of names -> bluetooth devices, when the item is selected we can retrieve the device
                val bluetoothMap = HashMap<String, BluetoothDevice>()
                for (bd in pairedDevices) {
                    bluetoothMap[bd.name] = bd
                }

                val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)
                builder.setTitle(R.string.choose_paired_bluetooth_devices_title)

                val internalGpsString = context.getString(R.string.pref_behavior_geonav_internal_gps_choice)

                //add internal gps to device choice
                val devices = pairedDevices.map { it.name }.toTypedArray() +
                        arrayOf(internalGpsString)

                //when a device is chosen, start a connect thread
                builder.setSingleChoiceItems(devices, -1) { dialog, which ->

                    val value = devices[which]

                    if (value != null) {

                        connectDevice(value)

                        dialog.dismiss()
                    }
                }

                builder.show()

            } else context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun connectDevice(value: String) {

        controller.getSecurityChecker().connectWith { pairedDevices ->

            val internal = context.getString(R.string.trait_gnss_internal_tts)

            val chosenDevice = pairedDevices.find { it.name == value }

            if (chosenDevice == null) {

                controller.getLocation()?.let { loc ->

                    onLocationChanged(loc)

                }

                triggerTts(internal)

            } else {

                val deviceTts = context.getString(R.string.trait_gnss_external_device_tts, chosenDevice.name)

                triggerTts(deviceTts)
            }

            prefs.edit().putString(GeneralKeys.GNSS_LAST_PAIRED_DEVICE_NAME, value).apply()

            setupCommunicationsUi(chosenDevice)
        }
    }

    /**
     * Starts connect thread and sets up the UI.
     */
    var isRegistered = false
    private fun setupCommunicationsUi(value: BluetoothDevice? = null) {

        if (value != null) {

            unregisterGnssReceiver()

            mLastDevice = value

            var startNewCommThread = true

            if (prefs.getBoolean(PreferenceKeys.ENABLE_GEONAV, false)) {

                val geoNavAddress = prefs.getString(PreferenceKeys.PAIRED_DEVICE_ADDRESS, null)

                if (geoNavAddress == value.address) {

                    if (!controller.getGeoNavHelper().initialized) {
                        controller.getGeoNavHelper().startGeoNav()
                    }

                    startNewCommThread = false

                    //if (!isRegistered) {
                      //  mLocalBroadcastManager.unregisterReceiver(receiver)

                        val filter = IntentFilter()
                        filter.addAction(GNSSResponseReceiver.ACTION_BROADCAST_GNSS_ROVER)
                        mLocalBroadcastManager.registerReceiver(receiver, filter)

                        isRegistered = true
                    //}


                }
            }

            if (startNewCommThread) {

                if (!getThreadHelper().isAlive) {

                    getThreadHelper().start(value, mHandler)


                }

                val filter = IntentFilter()
                filter.addAction(ACTION_BROADCAST_GNSS_TRAIT)

                /**
                 * When a BROADCAST_BT_OUTPUT is received and parsed, this interface is called.
                 * The parser parameter is a model for the parsed message, and is used to populate the
                 * trait layout UI.
                 */
                mLocalBroadcastManager.registerReceiver(receiver, filter)
            }
        }

        //make connected UI visible
        connectGroup.visibility = View.VISIBLE

        connectButton.visibility = View.GONE

        collectButton.setOnClickListener {

            if (!isCollectEnabled) {

                soundWarning()

            } else {

                val latitude = latTextView.text.toString()
                val longitude = lngTextView.text.toString()
                val elevation = altTextView.text.toString()
                val uiPrecision = accTextView.text.toString()

                val isFloat = try {
                    uiPrecision.toDouble()
                    true
                } catch (e: NumberFormatException) {
                    false
                }

                val precisionName = if (isFloat) "GPS" else uiPrecision

                if (NmeaParser().compareFix(precisionName, precision ?: "Any")) {

                    submitGnss(latitude, longitude, elevation, precisionName)

                    triggerTts(context.getString(R.string.trait_location_saved_tts))

                } else {

                    soundWarning()
                }
            }
        }

        //cancel the thread when the disconnect button is pressed
        disconnectButton.setOnClickListener {

            clearUi()

            collectButton.visibility = View.INVISIBLE
            progressBar.visibility = View.INVISIBLE
            connectButton.visibility = View.VISIBLE
            connectGroup.visibility = View.GONE

            if (value != null) {
                getThreadHelper().stop()
                mHandler.removeMessages(GNSSResponseReceiver.MESSAGE_OUTPUT_FAIL)
            }

            chipGroup.visibility = View.GONE

            prefs.edit().remove(GeneralKeys.GNSS_LAST_PAIRED_DEVICE_NAME).apply()

            setupChooseBluetoothDevice()

        }

        precisionSp.setSelection(
            when (precision) {
                "GPS" -> 1
                "RTK" -> 2
                "Float RTK" -> 3
                else -> 0
            }
        )

        Handler(Looper.getMainLooper()).postDelayed({

            precisionSp.onItemSelectedListener = object : OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    val newPrecision = precisionSp.selectedItem.toString()

                    if (!NmeaParser().compareFix(precision ?: "Any", newPrecision)) {

                        if (newPrecision in setOf("GPS", "Any")) {

                            soundOk()

                            isCollectEnabled = true

                            prefs.edit().remove(GeneralKeys.GNSS_WARNED_PRECISION).apply()

                        } else {

                            showWarning()

                            isCollectEnabled = false

                            prefs.edit().remove(GeneralKeys.GNSS_PRECISION_OK_SOUND).apply()

                        }

                    } else {

                        soundOk()

                        isCollectEnabled = true

                        prefs.edit().remove(GeneralKeys.GNSS_WARNED_PRECISION).apply()

                    }

                    precision = newPrecision

                    prefs.edit().putString(GeneralKeys.GNSS_LAST_CHOSEN_PRECISION, precision).apply()

                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}

            }
        }, 1000)

        setupAveragingUi()

        if (!connectionCheckRunning)
            connectionCheckHandler()

    }

    private fun resolveUiStatus() {
        if (!connectButton.isVisible) {
            if (utcTextView.text.toString().isBlank()) {
                connectGroup.visibility = View.INVISIBLE
                progressBar.visibility = View.VISIBLE
                disconnectButton.visibility = View.VISIBLE
                collectButton.visibility = View.VISIBLE
            } else {
                connectGroup.visibility = View.VISIBLE
                progressBar.visibility = View.INVISIBLE
            }
        }
    }

    private var connectionCheckRunning = false
    private fun connectionCheckHandler() {

        connectionCheckRunning = true

        val deviceName = prefs.getString(GeneralKeys.GNSS_LAST_PAIRED_DEVICE_NAME, null)

        if (deviceName != context.getString(R.string.pref_behavior_geonav_internal_gps_choice)) {

            if (currentUtc.isNotBlank() && (currentUtc == lastUtc)) {

                clearUi()

            }
        }

        lastUtc = currentUtc

        resolveUiStatus()

        Handler(Looper.getMainLooper()).postDelayed({
            connectionCheckHandler()
        }, CONNECTION_STATUS_INTERVAL)
    }

    private fun clearUi() {
        hdopTextView.text = ""
        latTextView.text = ""
        lngTextView.text = ""
        accTextView.text = ""
        utcTextView.text = ""
        satTextView.text = ""
        altTextView.text = ""
    }

    override fun loadLayout() {
        super.loadLayout()

        this.visibility = View.VISIBLE
        collectInputView.visibility = View.VISIBLE
    }

    //delete the obs and the obs.unit geo coord (only if obs exists)
    override fun deleteTraitListener() {

        val rep = (context as CollectActivity).rep

        val studyDbId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

        val observation =
            database.getObservation(studyDbId, currentRange.uniqueId, currentTrait.id, rep)

        if (observation != null) {

            database.deleteTrait(studyDbId, currentRange.uniqueId, currentTrait.id, rep)

            val units = controller.getDatabase().getAllObservationUnits(studyDbId.toInt())
                .filter { it.observation_unit_db_id == currentRange.uniqueId }
            if (units.isNotEmpty()) {
                units.first().let { unit ->
                    controller.getDatabase().updateObservationUnit(unit, "")
                }
            }
        }

        (context as CollectActivity).removeTrait()
    }

    /**
     * This handler is used within the connect thread to broadcast messages.
     */
    private val mHandler = Handler {

        (it.obj as? String)?.let { nmea ->

            if (it.what == GNSSResponseReceiver.MESSAGE_OUTPUT_CODE) {
                //progress is complete, send the final message
                if (::mLocalBroadcastManager.isInitialized) {
                    mLocalBroadcastManager.sendBroadcast(
                            Intent(ACTION_BROADCAST_GNSS_TRAIT)
                                    .putExtra(GNSSResponseReceiver.MESSAGE_STRING_EXTRA_KEY, nmea))
                }
            } else if (it.what == GNSSResponseReceiver.MESSAGE_OUTPUT_FAIL) {
                if (mLastDevice != null) {

                    getThreadHelper().stop()

                    setupCommunicationsUi(mLastDevice)

                }
            }
        }

        true
    }

    /**
     * Implementation of internal GPS location listener.
     * This location listener is only used if internal GPS is selected in the preferences.
     * This should update the UI with the current GPS information.
     * @param location the non null location sent from internal GPS
     */
    override fun onLocationChanged(location: Location) {

        val deviceName = prefs.getString(GeneralKeys.GNSS_LAST_PAIRED_DEVICE_NAME, null)

        if (deviceName == context.getString(R.string.pref_behavior_geonav_internal_gps_choice)) {

            //this will force collect navigation to keep GPS data on screen,
            //otherwise the data is only updated when the fix quality is good
            checkBeforeUpdate("GPS") {

                currentUtc = UUID.randomUUID().toString()

                latTextView.text = truncateFixQuality(location.latitude.toString())
                lngTextView.text = truncateFixQuality(location.longitude.toString())
                altTextView.text = truncateFixQuality(location.altitude.toString())

                accTextView.text = location.accuracy.toString()
                utcTextView.text = location.time.toString()

                resolveUiStatus()
            }
        }
    }

    private fun soundOk() {

        val soundOkFlag = prefs.getBoolean(GeneralKeys.GNSS_PRECISION_OK_SOUND, false)

        if (!soundOkFlag) {

            controller.getSoundHelper().playCelebrate()
            controller.getVibrator().vibrate(1000L)

            prefs.edit().putBoolean(GeneralKeys.GNSS_PRECISION_OK_SOUND, true).apply()
        }

    }

    private fun soundWarning() {
        controller.getSoundHelper().playError()
        controller.getVibrator().vibrate(1000L)
    }

    private fun showWarning() {

        val soundWarningFlag = prefs.getBoolean(GeneralKeys.GNSS_WARNED_PRECISION, false)

        if (!soundWarningFlag) {

            (controller.getContext() as? CollectActivity)?.showLocationPrecisionLossDialog()

            soundWarning()

            prefs.edit().putBoolean(GeneralKeys.GNSS_WARNED_PRECISION, true).apply()
        }
    }

    private fun checkBeforeUpdate(fix: String, update: () -> Unit) {

        val precisionThresh = prefs.getString(GeneralKeys.GNSS_LAST_CHOSEN_PRECISION, "Any") ?: "Any"

        val isQuality = NmeaParser().compareFix(fix, precisionThresh)
        if (isQuality && !currentFixQuality) {
            //quality fix is found, play something good
            isCollectEnabled = true
            soundOk()
            currentFixQuality = true
        } else if (isQuality) {
            //quality is still good
            isCollectEnabled = true
        } else  {
            //quality is bad, play something bad
            //reset last plotId if quality drops
            currentFixQuality = false
            showWarning()
            isCollectEnabled = false

        }

        update()
    }

    override fun validate(data: String?): Boolean {

        if (data == null) return true

        try {
            val gson = GeoJsonUtil.decode(data)
            return true
        } catch (e: Exception) {
            if (";" in data) {
                val parts = data.split(";")
                if (parts.size >= 2) {
                    val lat = parts[0].toDoubleOrNull()
                    val lng = parts[1].toDoubleOrNull()
                    if (lat != null && lng != null) {
                        return true
                    }
                }
            }
            return false
        }
    }
}