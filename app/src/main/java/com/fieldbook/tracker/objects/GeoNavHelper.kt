package com.fieldbook.tracker.objects

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.location.GPSTracker
import com.fieldbook.tracker.location.gnss.ConnectThread
import com.fieldbook.tracker.location.gnss.GNSSResponseReceiver
import com.fieldbook.tracker.location.gnss.NmeaParser
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.GeodeticUtils.Companion.impactZoneSearch
import com.fieldbook.tracker.utilities.GeodeticUtils.Companion.lowPassFilter
import com.fieldbook.tracker.utilities.GeodeticUtils.Companion.truncateFixQuality
import com.fieldbook.tracker.utilities.GeodeticUtils.Companion.writeGeoNavLog
import com.fieldbook.tracker.utilities.Utils
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.SnackbarLayout
import dagger.hilt.android.qualifiers.ActivityContext
import org.phenoapps.utils.BaseDocumentTreeUtil.Companion.getDirectory
import java.io.IOException
import java.io.OutputStreamWriter
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt

class GeoNavHelper @Inject constructor(@ActivityContext private val context: Context):
    SensorEventListener, GPSTracker.GPSTrackerListener {

    /**
     * GeoNav sensors and variables
     */
    var mGeoNavActivated = false
    private var mGravity: FloatArray = floatArrayOf()
    private var mGeomagneticField = floatArrayOf()
    private var mDeclination: Float? = null
    private var mAzimuth: Double? = null
    private var mNotWarnedInterference = true

    private val mGnssResponseReceiver: GNSSResponseReceiver = object : GNSSResponseReceiver() {
        override fun onGNSSParsed(parser: NmeaParser) {
            val time = parser.utc.toDouble()

            //only update the gps if it is a newly parsed coordinate
            if (time > mLastGeoNavTime) {
                if (!mFirstLocationFound) {
                    mFirstLocationFound = true
                    (context as CollectActivity).getSoundHelper().playCycle()
                    Utils.makeToast(context,
                        context.getString(R.string.act_collect_geonav_first_location_found)
                    )
                }
                mLastGeoNavTime = time
                val lat = truncateFixQuality(parser.latitude, parser.fix)
                val lng = truncateFixQuality(parser.longitude, parser.fix)
                var alt = parser.altitude
                val altLength = alt.length
                alt = alt.substring(0, altLength - 1) //drop the "M"

                //always log external gps updates
                writeGeoNavLog(
                    mGeoNavLogWriter,
                    "$lat,$lng,$time,null,null,null,null,null,null,null,null,null,null\n"
                )
                mExternalLocation = Location("GeoNav Rover")

                //initialize the double values, attempt to parse the strings, if impossible then don't update the coordinate.
                var latValue = Double.NaN
                var lngValue = Double.NaN
                var altValue = Double.NaN
                try {
                    latValue = lat.toDouble()
                    lngValue = lng.toDouble()
                    altValue = alt.toDouble()
                } catch (nfe: NumberFormatException) {
                    nfe.printStackTrace()
                }
                if (!java.lang.Double.isNaN(latValue) && !java.lang.Double.isNaN(lngValue)) {
                    mExternalLocation?.time = time.toLong()
                    mExternalLocation?.latitude = latValue
                    mExternalLocation?.longitude = lngValue
                    mExternalLocation?.altitude = altValue
                }
            }
        }
    }

    /*
     * When a BROADCAST_BT_OUTPUT is received and parsed, this interface is called.
     * The parser parameter is a model for the parsed message, and is used to populate the
     * trait layout UI.
     */
    private var mLocalBroadcastManager = LocalBroadcastManager.getInstance(context).also {
        val filter = IntentFilter()
        filter.addAction(GNSSResponseReceiver.ACTION_BROADCAST_GNSS_ROVER)
        it.registerReceiver(mGnssResponseReceiver, filter)
    }

    var mExternalLocation: Location? = null
    var mInternalLocation: Location? = null

    private var mConnectThread: ConnectThread? = null

    private var mTeslas = .0
    private var mLastGeoNavTime = 0.0
    private var mFirstLocationFound = false
    private var mLastDevice: BluetoothDevice? = null
    private var mSchedulerHandlerThread = HandlerThread("scheduler")
    private var mMessageHandlerThread = HandlerThread("messages")
    private var mAverageHandlerThread = HandlerThread("averaging")
    private var schedulerHandler: Handler? = null
    private var averageHandler: Handler? = null
    private var lastPlotIdNav: String? = null
    private var mGeoNavSnackbar: Snackbar? = null
    private val mPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val ep = context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    private var mGeoNavLogWriter: OutputStreamWriter? = null
    private var mGeoNavLogWriterUri: Uri? = null

    var initialized: Boolean = false

    init {
        init()
    }

    private fun init() {
        mMessageHandlerThread.start()
        mMessageHandlerThread.looper
        mSchedulerHandlerThread.start()
        mSchedulerHandlerThread.looper
        schedulerHandler = Handler(mSchedulerHandlerThread.looper)
        mAverageHandlerThread.start()
        mAverageHandlerThread.looper
        averageHandler = Handler(mAverageHandlerThread.looper)
        initialized = true
    }

    fun getGeoNavLogWriterUri(): Uri? {
        return mGeoNavLogWriterUri
    }

    /**
     * Called when the toolbar enable geonav icon is set to true.
     * Begins listening for sensor events to obtain an azimuth for the user.
     * Starts a timer (with interval defined in the preferences) that runs the IZ algorithm.
     */
    fun startGeoNav() {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
        if (sensorManager != null) {
            sensorManager.registerListener(
                this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI
            )
            sensorManager.registerListener(
                this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_UI
            )

            //set update interval from the preferences can be 1s, 5s or 10s
            val interval = mPrefs.getString(GeneralKeys.UPDATE_INTERVAL, "1") ?: "1"
            var period = 1000L
            when (interval) {
                "1" -> {}
                "5" -> {
                    period = 5000L
                }
                "10" -> {
                    period = 10000L
                }
            }

            //find the mac address of the device, if not found then start the internal GPS
            val address: String = mPrefs.getString(GeneralKeys.PAIRED_DEVICE_ADDRESS, "") ?: ""
            val internalGps: String = context.getString(R.string.pref_behavior_geonav_internal_gps_choice)
            var internal = true
            if (address.isEmpty() || address == internalGps) {
                //update no matter the distance change and every 10s
                val mGpsTracker = GPSTracker(context, this, 0, 10000)
            } else {
                getDeviceByAddress(address)?.let { device ->
                    setupCommunicationsUi(device)
                }
                internal = false
            }

            startScheduler(internal, period)

        } else {
            Toast.makeText(
                context, R.string.activity_collect_sensor_manager_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun runScheduler(internal: Boolean, period: Long) {

        schedulerHandler?.postDelayed({
            runImpactZoneAlgorithm(internal)
            runScheduler(internal, period)
        }, period)
    }

    private fun startScheduler(internal: Boolean, period: Long) {

        //start the timer and schedule the IZ algorithm
        runScheduler(internal, period)

        writeGeoNavLog(
            mGeoNavLogWriter,
            "start latitude, start longitude, UTC, end latitude, end longitude, azimuth, teslas, bearing, distance, closest, unique id, primary id, secondary id\n"
        )
    }

    /**
     * This handler is used within the connect thread to broadcast messages.
     */
    private var mHandler = createLocalHandler()

    private fun recreateThreads() {

        mSchedulerHandlerThread = HandlerThread("scheduler")
        mMessageHandlerThread = HandlerThread("messages")
        mAverageHandlerThread = HandlerThread("averaging")

        init()
    }

    private fun createLocalHandler(): Handler = Handler(mMessageHandlerThread.looper) { msg: Message ->
        val nmea = msg.obj as String
        if (msg.what == GNSSResponseReceiver.MESSAGE_OUTPUT_CODE) {
            val broadcastNmea =
                Intent(GNSSResponseReceiver.ACTION_BROADCAST_GNSS_ROVER)
            broadcastNmea.putExtra(GNSSResponseReceiver.MESSAGE_STRING_EXTRA_KEY, nmea)
            mLocalBroadcastManager.sendBroadcast(broadcastNmea)
        } else if (msg.what == GNSSResponseReceiver.MESSAGE_OUTPUT_FAIL) {
            mLastDevice?.let { setupCommunicationsUi(it) }
        }
        true
    }

    /**
     * A simple search function to find the bluetooth device correlated to the given address.
     * @param address the mac address that belongs to a given paired device
     * @return the paired device, could be null if the address is not in the paired list
     */
    private fun getDeviceByAddress(address: String): BluetoothDevice? {
        var device: BluetoothDevice? = null
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter.isEnabled) {
            val paired = adapter.bondedDevices
            if (paired.isNotEmpty()) {
                for (d in paired) {
                    if (d.address == address) {
                        device = d
                        break
                    }
                }
            }
        }
        return device
    }

    /**
     * Starts the connect thread which messages the LBM with nmea messages.
     * @param device the paired device that has been chosen in the preferences.
     */
    private fun setupCommunicationsUi(device: BluetoothDevice) {
        (context as CollectActivity).getSecurityChecker().withNearby { adapter: BluetoothAdapter ->
            adapter.cancelDiscovery()
            mLastDevice = device

            if (!mHandler.looper.thread.isAlive) {
                recreateThreads()
                mHandler = createLocalHandler()
            }

            mConnectThread = ConnectThread(device, mHandler)
            mConnectThread?.start()
        }
    }

    /**
     * This function is called periodically based on preferences, if the geonav enable
     * switch is true in the toolbar. The function takes a parameter to determine whether
     * the internal or an external gps is used. The internal gps uses the GPSTracker class,
     * while the external GPS location is populated with NMEA data parsed from a communications
     * thread with a paired bluetooth device (chosen in the Settings under Behavior/GeoNav).
     * @param internal the flag determining what gps data to use
     */
    private fun runImpactZoneAlgorithm(internal: Boolean) {

        //the angle of the IZ algorithm to use, see Geodetic util class for more details
        val thetaPref: String = mPrefs.getString(GeneralKeys.SEARCH_ANGLE, "0") ?: "0"
        var theta = 22.5
        when (thetaPref) {
            "22.5" -> {
                theta = 22.5
            }
            "45" -> {
                theta = 45.0
            }
            "67.5" -> {
                theta = 67.5
            }
            "90" -> {
                theta = 90.0
            }
        }
        val geoNavMethod: String = mPrefs.getString(GeneralKeys.GEONAV_SEARCH_METHOD, "0") ?: "0"
        val d1: Double = mPrefs.getString(GeneralKeys.GEONAV_PARAMETER_D1, "0.001")?.toDouble() ?: 0.001
        val d2: Double = mPrefs.getString(GeneralKeys.GEONAV_PARAMETER_D2, "0.01")?.toDouble() ?: 0.01

        //user must have a valid pointing direction before attempting the IZ
        //initialize the start position and fill with external or internal GPS coordinates
        val start: Location? = if (internal) {
            mInternalLocation
        } else {
            mExternalLocation
        }

        //get current field id
        val studyId: Int = ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)

        //find all observation units within the field
        val units = (context as CollectActivity).getDatabase().getAllObservationUnits(studyId)
        val coordinates: MutableList<ObservationUnitModel> = ArrayList()

        //add all units that have non null coordinates.
        for (model in units) {
            if (model.geo_coordinates != null && model.geo_coordinates!!.isNotEmpty()) {
                coordinates.add(model)
            }
        }

        //run the algorithm and time how long it takes
        if (start != null) {

            mAzimuth?.let { azimuth ->

                //long toc = System.currentTimeMillis();
                val (first) = impactZoneSearch(
                    mGeoNavLogWriter, start,
                    coordinates.toTypedArray(),
                    azimuth, theta, mTeslas, geoNavMethod, d1, d2
                )

                //long tic = System.currentTimeMillis();

                //if we received a result then show it to the user, create a button to navigate to the plot
                if (first != null) {
                    val id = first.observation_unit_db_id
                    with (context as CollectActivity) {
                        if (id != getRangeBox().cRange.plot_id && id != lastPlotIdNav) {
                            lastPlotIdNav = id
                            runOnUiThread {
                                if (mPrefs.getBoolean(GeneralKeys.GEONAV_AUTO, false)) {
                                    moveToSearch("id", getRangeBox().getRangeID(), null, null, id, -1)
                                    Toast.makeText(
                                        this,
                                        R.string.activity_collect_found_plot,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    mGeoNavSnackbar = Snackbar.make(
                                        findViewById(R.id.traitHolder),
                                        id, Snackbar.LENGTH_INDEFINITE
                                    )
                                    val snackLayout = mGeoNavSnackbar?.view as SnackbarLayout
                                    val snackView: View =
                                        layoutInflater.inflate(
                                            R.layout.geonav_snackbar_layout,
                                            null
                                        )
                                    val params =
                                        ConstraintLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                        )
                                    snackView.layoutParams = params
                                    snackLayout.addView(snackView)
                                    snackLayout.setPadding(0, 0, 0, 0)
                                    val tv =
                                        snackView.findViewById<TextView>(R.id.geonav_snackbar_tv)
                                    if (tv != null) {
                                        tv.text = id
                                    }
                                    val btn =
                                        snackView.findViewById<ImageButton>(R.id.geonav_snackbar_btn)
                                    btn?.setOnClickListener { v: View? ->
                                        mGeoNavSnackbar?.dismiss()
                                        lastPlotIdNav = null

                                        //when navigate button is pressed use rangeBox to go to the plot id
                                        moveToSearch("id", getRangeBox().getRangeID(), null, null, id, -1)
                                    }
                                    mGeoNavSnackbar?.setBackgroundTint(Color.TRANSPARENT)
                                    mGeoNavSnackbar?.show()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when the toolbar GeoNav Enable icon is switched to off, or the activity is paused.
     * Simply stops listening to the sensor manager and stops the geonav timer.
     */
    fun stopGeoNav() {
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this)
        mPrefs.edit().putBoolean(GeneralKeys.GEONAV_AUTO, false).apply() //turn off auto nav
        mSchedulerHandlerThread.quit()
        mAverageHandlerThread.quit()
        mMessageHandlerThread.quit()
        //flush and close geo nav log writer
        try {
            if (mGeoNavLogWriter != null) {
                mGeoNavLogWriter?.flush()
                mGeoNavLogWriter?.close()
            }
        } catch (io: IOException) {
            io.printStackTrace()
        }
        mLocalBroadcastManager.unregisterReceiver(mGnssResponseReceiver)

        mConnectThread?.cancel()
    }

    fun resetGeoNavMessages() {
        if (mGeoNavSnackbar != null) {
            mGeoNavSnackbar!!.dismiss()
            mGeoNavSnackbar = null
            lastPlotIdNav = null
        }
    }

    fun stopAverageHandler() {
        mAverageHandlerThread.quit()
    }

    /**
     * Called in onResume and stopped in onPause
     * Starts a file in storage/geonav/log.txt
     */
    fun setupGeoNavLogger() {
        if (mPrefs.getBoolean(GeneralKeys.GEONAV_LOG, false)) {
            try {
                val resolver: ContentResolver = context.contentResolver
                val geoNavFolder = getDirectory(context, R.string.dir_geonav)
                if (geoNavFolder != null && geoNavFolder.exists()) {
                    val interval = mPrefs.getString(GeneralKeys.UPDATE_INTERVAL, "1")
                    val address = (mPrefs.getString(GeneralKeys.PAIRED_DEVICE_ADDRESS, "") ?: "")
                        .replace(":".toRegex(), "-")
                        .replace("\\s".toRegex(), "_")
                    val thetaPref = mPrefs.getString(GeneralKeys.SEARCH_ANGLE, "22.5")
                    val fileName =
                        "log_" + interval + "_" + address + "_" + thetaPref + "_" + System.nanoTime() + ".csv"
                    val geoNavLogFile = geoNavFolder.createFile("*/csv", fileName)
                    if (geoNavLogFile != null && geoNavLogFile.exists()) {
                        val outputStream = resolver.openOutputStream(geoNavLogFile.uri)
                        Log.d(CollectActivity.TAG, "GeoNav Logger started successfully.")
                        mGeoNavLogWriterUri = geoNavLogFile.uri
                        mGeoNavLogWriter = OutputStreamWriter(outputStream)
                    } else {
                        Log.d(CollectActivity.TAG, "GeoNav Logger start failed.")
                    }
                }
            } catch (io: IOException) {
                io.printStackTrace()
            } catch (io: SecurityException) {
                io.printStackTrace()
            }
        }
    }

    /**
     * Sensor data listener for the collect activity.
     * This is used to find the direction the user is facing.
     * It is possible that the sensors are experiencing nosie and the hardware should be calibrated.
     * TODO: add calibration image on first event or when noise is detected.
     * For Android hardware it is necessary to calibrate before the first event should be accepted.
     * A global flag mNotWarnedInterference is used to defer the interference notification if already seen.
     * @param event the sensor event, could be magnetic or accelerometer
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor != null) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                mGravity = if (mGravity.isEmpty()) event.values
                else lowPassFilter(event.values.clone(), mGravity)
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                mGeomagneticField = if (mGeomagneticField.isEmpty()) event.values
                else lowPassFilter(event.values.clone(), mGeomagneticField)
            }
            mTeslas = calculateNoise(mGeomagneticField)
            if ((mTeslas < 25 || mTeslas > 65) && mNotWarnedInterference) {
                mNotWarnedInterference = false
                Toast.makeText(
                    context, R.string.activity_collect_geomagnetic_noise_detected,
                    Toast.LENGTH_SHORT
                ).show()
            }
            val R = FloatArray(9)
            val I = FloatArray(9)
            if (mGeomagneticField.isNotEmpty() && mGravity.isNotEmpty() &&
                SensorManager.getRotationMatrix(R, I, mGravity, mGeomagneticField)) {

                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)

                /*
                 * values[0]: Azimuth, angle of rotation about the -z axis.
                 * This value represents the angle between the device's y axis and the magnetic north pole.
                 * When facing north, this angle is 0, when facing south, this angle is π.
                 * Likewise, when facing east, this angle is π/2, and when facing west, this angle is -π/2.
                 * The range of values is -π to π.
                 */
                val nextAzimuth = Math.toDegrees(orientation[0].toDouble())
                mAzimuth = (nextAzimuth + 360) % 360

                //"${(Math.toDegrees(orientation[0].toDouble()).toInt() + 360) % 360}"
                if (mExternalLocation != null) {
                    mExternalLocation?.let { loc ->
                        setDeclination(loc)
                    }
                } else if (mInternalLocation != null) {
                    mInternalLocation?.let { loc ->
                        setDeclination(loc)
                    }
                }

                //if the declination has been found, correct the direction
                mDeclination?.let { declination ->
                    mAzimuth?.let { azimuth ->
                        mAzimuth = azimuth + declination.toInt().toDouble()
                    }
                }
            }
        }
    }

    private fun setDeclination(loc: Location) {
        mDeclination = GeomagneticField(
            loc.latitude.toFloat(),
            loc.longitude.toFloat(),
            loc.altitude.toFloat(),
            System.currentTimeMillis()
        ).declination
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Aggregates the vectors of the field array to create a constant.
     * This output is used in a threshold to determine whether the geomagnetic field
     * is experiencing noise.
     * @param field the geomagnetic vectors x, y, z
     * @return the square root of the summation of all squared vectors
     */
    private fun calculateNoise(field: FloatArray): Double {
        var sum = 0.0
        for (xyz in field) {
            sum += xyz.toDouble().pow(2.0)
        }
        return sqrt(sum)
    }

    fun getSnackbar(): Snackbar? {
        return mGeoNavSnackbar
    }

    override fun onLocationChanged(location: Location) {
        mInternalLocation = location

        //always log location updates
        writeGeoNavLog(
            mGeoNavLogWriter,
            """
        ${location.latitude},${location.longitude},${location.time},null,null,null,null,null,null,null,null,null,null
        
        """.trimIndent()
        )
    }

    public fun getAverageHandler(): Handler? {
        return averageHandler
    }
}