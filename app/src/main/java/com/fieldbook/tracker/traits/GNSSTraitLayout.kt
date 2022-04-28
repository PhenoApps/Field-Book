package com.fieldbook.tracker.traits

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.Group
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.dao.ObservationDao
import com.fieldbook.tracker.database.dao.ObservationUnitDao
import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.location.GPSTracker
import com.fieldbook.tracker.location.gnss.ConnectThread
import com.fieldbook.tracker.location.gnss.GNSSResponseReceiver
import com.fieldbook.tracker.location.gnss.GNSSResponseReceiver.Companion.ACTION_BROADCAST_GNSS_TRAIT
import com.fieldbook.tracker.location.gnss.NmeaParser
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.GeodeticUtils
import com.fieldbook.tracker.utilities.GeodeticUtils.Companion.truncateFixQuality
import com.fieldbook.tracker.utilities.PrefsConstants
import com.google.android.material.chip.ChipGroup
import org.json.JSONObject
import kotlin.collections.HashMap
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

    //thread used to establish a connection with
    private lateinit var mConnectThread: ConnectThread

    //used for communication between threads and ui thread
    private lateinit var mLocalBroadcastManager: LocalBroadcastManager

    private var mGpsTracker: GPSTracker? = null

    private var mLastDevice: BluetoothDevice? = null

    private var mProgressDialog: AlertDialog? = null

    private val mAverageResponseHandler = Handler(Looper.getMainLooper()) {

        val info = it.obj as AverageInfo

        if (!CollectActivity.thisActivity.isDestroyed)
            averagePoints(info)

        mProgressDialog?.dismiss()

        true
    }

    constructor(context: Context?) : super(context) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    data class AverageInfo(var unit: ObservationUnitModel, var location: Location?,
                           var points: List<Pair<Double, Double>>, val latLength: Int, val lngLength: Int)

    private fun startAverageTimer(info: AverageInfo, period: Long) {

        val runnable = Runnable {
            val latTextView = findViewById<TextView>(R.id.latTextView)
            val lngTextView = findViewById<TextView>(R.id.lngTextView)

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

        if (CollectActivity.mAverageHandler.looper != null)
            Handler(CollectActivity.mAverageHandler.looper).post(runnable)
        else mProgressDialog?.dismiss()

    }

    override fun setNaTraitsText() {}

    //the type of this trait is "GNSS" Global Navigation Satellite System
    override fun type(): String {
        return "gnss"
    }

    private fun initialize() {

        mProgressDialog = AlertDialog.Builder(context)
            .setTitle(R.string.gnss_trait_averaging_dialog_title)
            .setMessage(R.string.gnss_trait_averaging_dialog_message)
            .setView(R.layout.dialog_gnss_trait_averaging)
            .setCancelable(false)
            .create()

        //initialize lbm and create a filter to broadcast nmea strings
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(context)
        val filter = IntentFilter()
        filter.addAction(ACTION_BROADCAST_GNSS_TRAIT)

        /**
         * When a BROADCAST_BT_OUTPUT is received and parsed, this interface is called.
         * The parser parameter is a model for the parsed message, and is used to populate the
         * trait layout UI.
         */
        mLocalBroadcastManager.registerReceiver(
            object : GNSSResponseReceiver() {
                override fun onGNSSParsed(parser: NmeaParser) {

                    //query for all views to populate
                    val latTextView = findViewById<TextView>(R.id.latTextView)
                    val lngTextView = findViewById<TextView>(R.id.lngTextView)
                    val accTextView = findViewById<TextView>(R.id.accTextView)
                    val utcTextView = findViewById<TextView>(R.id.utcTextView)
                    val satTextView = findViewById<TextView>(R.id.satTextView)
                    val altTextView = findViewById<TextView>(R.id.altTextView)
                    val hdopTextView = findViewById<TextView>(R.id.hdopTextView)

                    //populate ui
                    accTextView.text = parser.fix
                    latTextView.text = truncateFixQuality(parser.latitude, parser.fix)
                    lngTextView.text = truncateFixQuality(parser.longitude, parser.fix)
                    utcTextView.text = parser.utc
                    hdopTextView.text = parser.hdop

                    if (parser.satellites.isEmpty()) {
                        satTextView.text = "${parser.gsv.size}"
                    } else {
                        val maxSats = maxOf(parser.satellites.toInt(), parser.gsv.size)
                        satTextView.text = "${parser.gsv.size}/$maxSats"
                    }
                    altTextView.text = truncateFixQuality(parser.altitude, parser.fix)
                }

            },
            filter
        )

        setupChooseBluetoothDevice()

    }

    /**
     * Persists switch state and interval choice.
     */
    private fun setupAveragingUi() {

        val chipGroup = findViewById<ChipGroup>(R.id.gnss_trait_averaging_chip_group)
        val averageSwitch = findViewById<SwitchCompat>(R.id.gnss_trait_averaging_switch)
        val checked = prefs.getBoolean(GeneralKeys.GEONAV_AVERAGING, false)

        averageSwitch.setOnCheckedChangeListener { _, isChecked ->

            prefs.edit().putBoolean(GeneralKeys.GEONAV_AVERAGING, isChecked).apply()

            chipGroup.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE
        }

        averageSwitch.isChecked = checked

        chipGroup.visibility = if (checked) View.VISIBLE else View.INVISIBLE

        val chipId = prefs.getInt(GeneralKeys.GEONAV_AVERAGING_INTERVAL, R.id.gnss_trait_5s_chip)
        chipGroup.check(chipId)

        chipGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putInt(GeneralKeys.GEONAV_AVERAGING_INTERVAL, checkedId).apply()
        }
    }

    /**
     * This function is called to initialize the UI. All trait layouts are set to "gone" by default.
     */
    override fun init() {

        initialize()
    }

    private fun setupChooseBluetoothDevice() {

        //setup connect button
        val connectBtn = findViewById<ImageButton>(R.id.gnss_connect_button)
        connectBtn.visibility = View.VISIBLE

        // Get Location
        connectBtn.setOnClickListener {
            findPairedDevice()
        }
    }

    //based on RFC 7956
    //{
    //  "type": "Feature",
    //  "geometry": {
    //    "type": "Point",
    //    "coordinates": [125.6, 10.1]
    //  },
    //  "properties": {
    //    "name": "Dinagat Islands"
    //  }
    //}
    data class Geometry(val type: String = "Point", val coordinates: Array<String>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Geometry

            if (type != other.type) return false
            if (!coordinates.contentEquals(other.coordinates)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + coordinates.contentHashCode()
            return result
        }
    }

    data class GeoJSON(val type: String = "Feature", val geometry: Geometry, val properties: Map<String, String>? = null) {
        fun toJson() = JSONObject(mapOf("type" to this.type,
                "geometry" to mapOf("type" to this.geometry.type,
                        "coordinates" to this.geometry.coordinates,
                        "properties" to properties
                )))
    }

    /**
     * This function is called to make a database update statement.
     * First the selected studyDbId is found in the preferences, and the static ObservationUnitDao
     * is used to find the relevant Obs. Unit. and update the row with the NMEA data.
     */
    private fun submitGnss(latitude: String, longitude: String, elevation: String) {

//        val utcTextView = findViewById<TextView>(R.id.utcTextView)
//        val satTextView = findViewById<TextView>(R.id.satTextView)
//        val altTextView = findViewById<TextView>(R.id.altTextView)
//        val accTextView = findViewById<TextView>(R.id.accTextView)

        val averageSwitch = findViewById<SwitchCompat>(R.id.gnss_trait_averaging_switch)

        if (latitude.isNotBlank() && longitude.isNotBlank()) {

            val studyDbId = prefs.getInt(PrefsConstants.SELECTED_FIELD_ID, 0).toString()

            //geo json object : elevation (stored in obs. units, used in navigation)
            //geo json has properties map for additional info
            val geoJson = GeoJSON(geometry = Geometry(coordinates = arrayOf(latitude, longitude)),
                    properties = mapOf("altitude" to elevation))

            //save fix length to truncate the average later if needed
            val latLength = latitude.length
            val lngLength = longitude.length

            //temporary variables to also update obs if obs units are averaged
            val newLat = latitude.toDouble()
            val newLng = longitude.toDouble()

            val unit = ObservationUnitDao.getAll(studyDbId.toInt()).first { it.observation_unit_db_id == cRange.plot_id }

            //check if the switch is enabled, then update obs units with average value
            if (averageSwitch.isChecked) {

                val chipGroup = findViewById<ChipGroup>(R.id.gnss_trait_averaging_chip_group)

                val avgDuration = when (chipGroup.checkedChipId) {
                    R.id.gnss_trait_10s_chip -> 10000L
                    R.id.gnss_trait_5s_chip -> 5000L
                    else -> -1L
                }

                //the saved geo coordinate location
                val location = GeodeticUtils.parseGeoCoordinate(unit.geo_coordinates)

                //listen for the duration and append lat/lngs to an array
                val pointsToAverage = arrayListOf<Pair<Double, Double>>()
                val info = AverageInfo(unit, location, pointsToAverage, latLength, lngLength)
                if (avgDuration > -1L) {

                    if (location != null) {

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

                        //averaging is updating the location, so ask the user
                        alertLocationUpdate {
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

                if (etCurVal.text.isNotBlank()) {

                    alertLocationUpdate {

                        updateCoordinateObservation(unit, geoJson)

                    }

                } else updateCoordinateObservation(unit, geoJson)

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

    private fun updateCoordinateObservation(unit: ObservationUnitModel, json: GeoJSON) {

        val coordinates = "${json.geometry.coordinates[0]}; ${json.geometry.coordinates[1]}"

        ObservationUnitDao.updateObservationUnit(unit, json.toJson().toString())

        etCurVal.setText(coordinates)

        updateTrait(currentTrait.trait, "gnss", coordinates)

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

        val averageJson = GeoJSON(geometry = Geometry(
            coordinates = arrayOf(avgPoint.first.toString(), avgPoint.second.toString())),
            properties = mapOf("altitude" to (location?.altitude?.toString() ?: "")))

        updateCoordinateObservation(unit, averageJson)
    }

    private fun alertLocationUpdate(f: () -> Unit) {

        AlertDialog.Builder(context)
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

        clearUi()

        //check if the device has bluetooth enabled, if not, request it to be enabled via system action
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter.isEnabled) {

            //create a dialog with the paired devices
            val pairedDevices = adapter.bondedDevices

            //create table of names -> bluetooth devices, when the item is selected we can retrieve the device
            val bluetoothMap = HashMap<String, BluetoothDevice>()
            for (bd in pairedDevices) {
                bluetoothMap[bd.name] = bd
            }

            val builder = AlertDialog.Builder(context)
            builder.setTitle(R.string.choose_paired_bluetooth_devices_title)

            val internalGpsString = context.getString(R.string.pref_behavior_geonav_internal_gps_choice)

            //add internal gps to device choice
            val devices = pairedDevices.map { it.name }.toTypedArray() +
                    arrayOf(internalGpsString)

            //when a device is chosen, start a connect thread
            builder.setSingleChoiceItems(devices, -1) { dialog, which ->

                val value = devices[which]

                if (value != null) {

                    val chosenDevice = pairedDevices.find { it.name == value }

                    if (chosenDevice == null) {
                        //register the location listener
                        //update no matter the distance change and every 10s
                        mGpsTracker = GPSTracker(context, this, 0, 10000)
                    }

                    setupCommunicationsUi(chosenDevice)

                    dialog.dismiss()
                }
            }

            builder.show()

        } else context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    /**
     * Starts connect thread and sets up the UI.
     */
    private fun setupCommunicationsUi(value: BluetoothDevice? = null) {

        BluetoothAdapter.getDefaultAdapter().cancelDiscovery()

        if (value != null) {
            if (::mConnectThread.isInitialized) mConnectThread.cancel()
            mLastDevice = value
            mConnectThread = ConnectThread(value, mHandler)
            mConnectThread.start()
        }

        //make connected UI visible
        val connectGroup = findViewById<Group>(R.id.gnss_group)
        connectGroup.visibility = View.VISIBLE

        val connectButton = findViewById<ImageButton>(R.id.gnss_connect_button)
        connectButton.visibility = View.GONE

        val collectButton = findViewById<ImageButton>(R.id.gnss_collect_button)
        collectButton.setOnClickListener {

            val latTextView = findViewById<TextView>(R.id.latTextView)
            val lngTextView = findViewById<TextView>(R.id.lngTextView)
            val altTextView = findViewById<TextView>(R.id.altTextView)

            val latitude = latTextView.text.toString()
            val longitude = lngTextView.text.toString()
            val elevation = altTextView.text.toString()

            submitGnss(latitude, longitude, elevation)
        }

        //cancel the thread when the disconnect button is pressed
        val disconnectButton = findViewById<ImageButton>(R.id.disconnect_button)
        disconnectButton.setOnClickListener {
            connectButton.visibility = View.VISIBLE
            connectGroup.visibility = View.GONE

            if (value != null) {
                mConnectThread.cancel()
                mHandler.removeMessages(GNSSResponseReceiver.MESSAGE_OUTPUT_FAIL)
            }

            if (mGpsTracker != null) {
                mGpsTracker = null
            }

            val chipGroup = findViewById<ChipGroup>(R.id.gnss_trait_averaging_chip_group)
            chipGroup.visibility = View.GONE

            setupChooseBluetoothDevice()
        }

        setupAveragingUi()

    }

    private fun clearUi() {

        val latTextView = findViewById<TextView>(R.id.latTextView)
        val lngTextView = findViewById<TextView>(R.id.lngTextView)
        val accTextView = findViewById<TextView>(R.id.accTextView)
        val utcTextView = findViewById<TextView>(R.id.utcTextView)
        val satTextView = findViewById<TextView>(R.id.satTextView)
        val altTextView = findViewById<TextView>(R.id.altTextView)
        val hdopTextView = findViewById<TextView>(R.id.hdopTextView)

        hdopTextView.text = ""
        latTextView.text = ""
        lngTextView.text = ""
        accTextView.text = ""
        utcTextView.text = ""
        satTextView.text = ""
        altTextView.text = ""
    }

    override fun loadLayout() {
        this.visibility = View.VISIBLE
        etCurVal.visibility = VISIBLE
        if (newTraits.containsKey(currentTrait.trait)) {
            etCurVal.setText(newTraits[currentTrait.trait].toString())
            etCurVal.setTextColor(Color.parseColor(displayColor))
        } else {
            etCurVal.setText("")
            etCurVal.setTextColor(Color.BLACK)
            if (currentTrait.defaultValue != null
                    && currentTrait.defaultValue.isNotEmpty()) etCurVal.setText(currentTrait.defaultValue)
        }
    }

    //delete the obs and the obs.unit geo coord (only if obs exists)
    override fun deleteTraitListener() {

        val studyDbId = prefs.getInt("SelectedFieldExpId", 0).toString()

        val observation = ObservationDao.getObservation(studyDbId, cRange.plot_id, currentTrait.trait)

        if (observation != null) {

            ObservationDao.deleteTrait(studyDbId, cRange.plot_id, currentTrait.trait)

            ObservationUnitDao.getAll(studyDbId.toInt())
                .first { it.observation_unit_db_id == cRange.plot_id }.let { unit ->

                ObservationUnitDao.updateObservationUnit(unit, "")

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

                    if (::mConnectThread.isInitialized) mConnectThread.cancel()

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

        val latTextView = findViewById<TextView>(R.id.latTextView)
        val lngTextView = findViewById<TextView>(R.id.lngTextView)
        val accTextView = findViewById<TextView>(R.id.accTextView)
        val utcTextView = findViewById<TextView>(R.id.utcTextView)
        val altTextView = findViewById<TextView>(R.id.altTextView)

        //LocationManager accuracy is horizontal accuracy in meters
        //>=100 use three decimal places
        val fixQuality = when (location.accuracy) {
           in 0.0..11.0 -> {
               "DGPS"         //<11 use five
           }
           in 12.0..50.0 -> {
               "internal"     //>11 <=50 use four
           }
           else -> {
               "bad"
           }
        }

        latTextView.text = truncateFixQuality(location.latitude.toString(), fixQuality)
        lngTextView.text = truncateFixQuality(location.longitude.toString(), fixQuality)
        altTextView.text = truncateFixQuality(location.altitude.toString(), fixQuality)

        accTextView.text = location.accuracy.toString()
        utcTextView.text = location.time.toString()

    }

    //close the thread when the linear layout is removed
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        if (::mConnectThread.isInitialized)
            mConnectThread.cancel()
    }
}