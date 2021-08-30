package com.fieldbook.tracker.traits

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.Group
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.dao.ObservationUnitDao
import com.fieldbook.tracker.location.gnss.ConnectThread
import com.fieldbook.tracker.location.gnss.GNSSResponseReceiver
import com.fieldbook.tracker.location.gnss.GNSSResponseReceiver.Companion.ACTION_BROADCAST_GNSS
import com.fieldbook.tracker.location.gnss.NmeaParser
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.GeodeticUtils.Companion.truncateFixQuality
import org.json.JSONObject

/**
 * A FieldBook trait for acquiring GPS coordinates from an external, bluetooth-connected device.
 * Users should have external devices already paired through their operating system.
 * First step is to click the connect button, this shows a list of paired devices.
 * Second step is to choose the device to listen to, this will automatically establish a connection
 * and begin listening for NMEA messages.
 */
class GNSSTraitLayout : BaseTraitLayout {

    private val mPrefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    //thread used to establish a connection with
    private lateinit var mConnectThread: ConnectThread

    //used for communication between threads and ui thread
    private lateinit var mLocalBroadcastManager: LocalBroadcastManager

    constructor(context: Context?) : super(context) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    override fun setNaTraitsText() {}

    //the type of this trait is "GNSS" Global Navigation Satellite System
    override fun type(): String {
        return "gnss"
    }

    /**
     * This function is called to initialize the UI. All trait layouts are set to "gone" by default.
     */
    override fun init() {

        //initialize lbm and create a filter to broadcast nmea strings
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(context)
        val filter = IntentFilter()
        filter.addAction(ACTION_BROADCAST_GNSS)

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
                        //TODO when this is RTK the string overlaps a bit
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
                        altTextView.text = parser.altitude
                    }

                },
                filter
        )

        //check for the address in the preferences
        val address = mPrefs.getString(GeneralKeys.PAIRED_DEVICE_ADDRESS, "")

        //if an address has not been chosen, then create the connection button
        if (address.isNullOrBlank()) {

            setupChooseBluetoothDevice()

        } else { //otherwise get the bluetooth device and start comms

            val device = getDeviceByAddress(address)

            if (device != null) {
                setupCommunicationsUi(device)

            } else setupChooseBluetoothDevice()
        }
    }

    private fun setupChooseBluetoothDevice() {

        //setup connect button
        val getLocation = findViewById<ImageButton>(R.id.gnss_connect_button)
        getLocation.visibility = View.VISIBLE

        // Get Location
        getLocation.setOnClickListener {
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
    data class Coordinates(val latitude: String, val longitude: String)
    data class Geometry(val type: String = "Point", val coordinates: Coordinates)
    data class GeoJSON(val type: String = "Feature", val geometry: Geometry, val properties: Map<String, String>? = null) {
        fun toJson() = JSONObject(mapOf("type" to this.type,
                "geometry" to mapOf("type" to this.geometry.type,
                        "coordinates" to with(this.geometry.coordinates) {
                            arrayOf(longitude, latitude)
                        },
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

        if (latitude.isNotBlank() && longitude.isNotBlank()) {

            val studyDbId = prefs.getInt("SelectedFieldExpId", 0).toString()

            //geo json object : elevation (stored in obs. units, used in navigation)
            //geo json has properties map for additional info
            val geoJson = GeoJSON(geometry = Geometry(coordinates = Coordinates(latitude, longitude)),
                    properties = mapOf("altitude" to elevation))

            with(ObservationUnitDao.getAll(studyDbId.toInt()).first { it.observation_unit_db_id == cRange.plot_id }) {
                ObservationUnitDao.updateObservationUnit(this, geoJson.toJson().toString())
            }

//observations: store whatever the hell we want - Trevor circa 2021
//            val fbJson = JSONObject(mapOf("latitude" to latitude,
//                    "longitude" to longitude,
//                    "elevation" to elevation,
//                    "accuracy" to accTextView.text
//                    "utc" to utcTextView.text,
//                    "satellites" to satTextView.text))

            val coordinates = "$latitude; $longitude"
            etCurVal.setText(coordinates)
            updateTrait(currentTrait.trait, "gnss", coordinates)
        }
    }

    /**
     * When the connect button is pressed, this function is called which triggers a Dialog for
     * the user to choose bluetooth devices from.
     **/
    private fun findPairedDevice() {

        clearUi()

        if (::mConnectThread.isInitialized) {
            mConnectThread.cancel()
        }

        //check if the device has bluetooth enabled, if not, request it to be enabled via system action
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter.isEnabled) {

            //if the user already paired an external unit then show a list to connect from
            if (adapter.bondedDevices.isNotEmpty()) {

                //create a dialog with the paired devices
                val pairedDevices = adapter.bondedDevices
                if (pairedDevices.isNotEmpty()) {

                    //create table of names -> bluetooth devices, when the item is selected we can retrieve the device
                    val bluetoothMap = HashMap<String, BluetoothDevice>()
                    for (bd in pairedDevices) {
                        bluetoothMap[bd.name] = bd
                    }

                    val builder = AlertDialog.Builder(context)
                    builder.setTitle(R.string.choose_paired_bluetooth_devices_title)

                    //when a device is chosen, start a connect thread
                    builder.setSingleChoiceItems(pairedDevices.map { it.name }.toTypedArray(), -1) { dialog, which ->

                        val value = pairedDevices.toTypedArray()[which]

                        if (value != null) {

                            setupCommunicationsUi(value)

                            dialog.dismiss()
                        }
                    }

                    builder.show()
                }

            } else Toast.makeText(context, R.string.gnss_no_paired_device, Toast.LENGTH_SHORT).show()

        } else context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    private fun getDeviceByAddress(address: String): BluetoothDevice? {

        //device to return is by default null
        var device: BluetoothDevice? = null

        //check if the device has bluetooth enabled, if not, request it to be enabled via system action
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter.isEnabled) {

            //if the user already paired an external unit then show a list to connect from
            if (adapter.bondedDevices.isNotEmpty()) {

                device = adapter.bondedDevices.first { it.address == address }

            } else Toast.makeText(context, R.string.gnss_no_paired_device, Toast.LENGTH_SHORT).show()

        } else context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))

        return device
    }

    private fun setupCommunicationsUi(value: BluetoothDevice) {

        mConnectThread = ConnectThread(value, mHandler).apply {
            start()
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
        val disconnectButton = findViewById<Button>(R.id.disconnect_button)
        disconnectButton.setOnClickListener {
            connectButton.visibility = View.VISIBLE
            connectGroup.visibility = View.GONE
            mConnectThread.cancel()
        }
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

    override fun deleteTraitListener() {
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
                            Intent(ACTION_BROADCAST_GNSS)
                                    .putExtra(GNSSResponseReceiver.MESSAGE_STRING_EXTRA_KEY, nmea))
                }
            }
        }

        true
    }
}