package com.fieldbook.tracker.preferences

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import com.fieldbook.tracker.utilities.GeoNavHelper
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.security.Security
import javax.inject.Inject

@AndroidEntryPoint
class GeoNavPreferencesFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener {

    private var mPairDevicePref: Preference? = null

    @Inject
    lateinit var preferences: SharedPreferences

    private val advisor by Security().secureBluetooth()

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            updateUi()
        }

    private fun updateUi() {
        updateMethodSummaryText()
        updateParametersVisibility()
        changeGeoNavLoggingModeView()
        updateParametersSummaryText()
        updateDeviceAddressSummary()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        advisor.initialize()

        setPreferencesFromResource(R.xml.preferences_geonav, rootKey)

        preferences.registerOnSharedPreferenceChangeListener(listener)

        // Show/hide preferences and category titles based on the ENABLE_GEONAV value
        val geonavEnabledPref: CheckBoxPreference? =
            findPreference("com.fieldbook.tracker.geonav.ENABLE_GEONAV")
        if (geonavEnabledPref != null) {
            geonavEnabledPref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val isChecked = newValue as Boolean
                    updatePreferencesVisibility(isChecked)
                    true
                }
            updatePreferencesVisibility(geonavEnabledPref.isChecked)
        }

        (this.activity as PreferencesActivity?)!!.supportActionBar!!.title = getString(R.string.preferences_geonav_title)

        //add click action on pair rover device to search for bt devices
        mPairDevicePref = findPreference(GeneralKeys.PAIR_BLUETOOTH)
        if (mPairDevicePref != null) {
            mPairDevicePref!!.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { view: Preference? ->
                    advisor.connectWith {
                        startDeviceChoiceDialog()
                    }
                    true
                }
        }

        val geoNavMethod = findPreference<ListPreference>(GeneralKeys.GEONAV_SEARCH_METHOD)

        geoNavMethod?.setOnPreferenceChangeListener { preference, newValue ->

            val value = newValue as? String ?: "0"

            preferences.edit()
                .putString(GeneralKeys.GEONAV_SEARCH_METHOD, newValue as? String ?: "0").apply()

            if (value == "1") {

                checkRequiredSensors()

            }

            updateMethodSummaryText()
            updateParametersVisibility()

            true
        }

        val geoNavLoggingMode = findPreference<ListPreference>(GeneralKeys.GEONAV_LOGGING_MODE)
        changeGeoNavLoggingModeView()
        geoNavLoggingMode?.setOnPreferenceChangeListener { _, newValue ->
            preferences.edit()
                .putString(GeneralKeys.GEONAV_LOGGING_MODE, newValue as? String ?: "0").apply()

            changeGeoNavLoggingModeView()

            true
        }

        val trapBase = findPreference<EditTextPreference>(GeneralKeys.GEONAV_PARAMETER_D1)
        val trapDst = findPreference<EditTextPreference>(GeneralKeys.GEONAV_PARAMETER_D2)
        val trapAngle = findPreference<ListPreference>(GeneralKeys.SEARCH_ANGLE)
        val updateInterval = findPreference<ListPreference>(GeneralKeys.UPDATE_INTERVAL)

        updateInterval?.setOnPreferenceChangeListener { _, newValue ->

            preferences.edit()
                .putString(GeneralKeys.UPDATE_INTERVAL, newValue as? String ?: "0").apply()

            updateParametersSummaryText()

            true
        }

        trapBase?.setOnPreferenceChangeListener { _, newValue ->

            preferences.edit()
                .putString(GeneralKeys.GEONAV_PARAMETER_D1, newValue as? String ?: "0.001").apply()

            updateParametersSummaryText()

            true
        }

        trapDst?.setOnPreferenceChangeListener { _, newValue ->

            preferences.edit()
                .putString(GeneralKeys.GEONAV_PARAMETER_D2, newValue as? String ?: "0.01").apply()

            updateParametersSummaryText()

            true
        }

        trapAngle?.setOnPreferenceChangeListener { _, newValue ->

            preferences.edit()
                .putString(GeneralKeys.SEARCH_ANGLE, newValue as? String ?: "22.5").apply()

            updateParametersSummaryText()

            true
        }
    }

    private fun checkRequiredSensors() {

        context?.packageManager?.let { packageManager ->

            val hasAccelerometer =
                packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
            val hasMagneticSensor =
                (context?.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
                    .getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null

            if (!hasAccelerometer || !hasMagneticSensor) {

                AlertDialog.Builder(context, R.style.AppAlertDialog)
                    .setTitle(R.string.dialog_geonav_prefs_sensor_missing_title)
                    .setMessage(R.string.dialog_geonav_prefs_sensor_missing_message)
                    .setPositiveButton(org.phenoapps.androidlibrary.R.string.ok) { dialog, which ->
                        dialog.dismiss()
                    }
                    .create().show()
            }
        }
    }

    private fun changeGeoNavLoggingModeView() {
        val geoNavLoggingMode = findPreference<ListPreference>(GeneralKeys.GEONAV_LOGGING_MODE)
        val currentMode = preferences.getString(GeneralKeys.GEONAV_LOGGING_MODE, "0")

        if (currentMode == GeoNavHelper.GeoNavLoggingMode.OFF.value) {
            geoNavLoggingMode?.summary = getString(R.string.pref_geonav_log_off_description)
            geoNavLoggingMode?.setIcon(R.drawable.ic_note_off_outline)
        } else if (currentMode == GeoNavHelper.GeoNavLoggingMode.LIMITED.value) {
            geoNavLoggingMode?.summary = getString(R.string.pref_geonav_log_limited_description)
            geoNavLoggingMode?.setIcon(R.drawable.ic_note_outline)
        } else if (currentMode == GeoNavHelper.GeoNavLoggingMode.FULL.value) {
            geoNavLoggingMode?.summary = getString(R.string.pref_geonav_log_full_description)
            geoNavLoggingMode?.setIcon(R.drawable.ic_note_multiple_outline)
        } else {
            geoNavLoggingMode?.summary = getString(R.string.pref_geonav_log_both_description)
            geoNavLoggingMode?.setIcon(R.drawable.ic_note_multiple_outline)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateParametersSummaryText() {

        val d1 = preferences.getString(GeneralKeys.GEONAV_PARAMETER_D1, "0.001")
        val d2 = preferences.getString(GeneralKeys.GEONAV_PARAMETER_D2, "0.01")
        val theta = preferences.getString(GeneralKeys.SEARCH_ANGLE, "22.5")
        val interval = preferences.getString(GeneralKeys.UPDATE_INTERVAL, "0")

        val trapBase = findPreference<EditTextPreference>(GeneralKeys.GEONAV_PARAMETER_D1)
        val trapDst = findPreference<EditTextPreference>(GeneralKeys.GEONAV_PARAMETER_D2)
        val trapAngle = findPreference<ListPreference>(GeneralKeys.SEARCH_ANGLE)
        val updateInterval = findPreference<ListPreference>(GeneralKeys.UPDATE_INTERVAL)

        trapBase?.summary = getString(R.string.pref_geonav_search_trapezoid_d1_summary, d1)
        trapDst?.summary = getString(R.string.pref_geonav_search_trapezoid_d2_summary, d2)
        trapAngle?.summary = getString(R.string.pref_geonav_search_angle_summary, theta)
        updateInterval?.summary = getString(R.string.pref_geonav_update_interval_summary, interval)
    }

    private fun updatePreferencesVisibility(isChecked: Boolean) {
        val preferenceScreen = preferenceScreen
        val searchMethodPref = findPreference<ListPreference>("com.fieldbook.tracker.geonav.SEARCH_METHOD")

        for (i in 0 until preferenceScreen.preferenceCount) {
            val preferenceItem = preferenceScreen.getPreference(i)

            // Skip the checkbox preference itself
            if (preferenceItem.key == "com.fieldbook.tracker.geonav.ENABLE_GEONAV") {
                continue
            }

            val isParameter = preferenceItem.key.startsWith("com.fieldbook.tracker.geonav.parameters.")
            if (isParameter && searchMethodPref?.value == "0") { // Set parameter visibility to false if search method is distance
                preferenceItem.isVisible = false
            } else {
                preferenceItem.isVisible = isChecked
            }
        }
    }

    private fun updateMethodSummaryText() {

        val method = if (preferences.getString(
                GeneralKeys.GEONAV_SEARCH_METHOD,
                getString(R.string.pref_geonav_method_distance)
            ) == "0"
        ) getString(R.string.pref_geonav_method_distance)
        else getString(R.string.pref_geonav_method_trapezoid)

        findPreference<ListPreference>(GeneralKeys.GEONAV_SEARCH_METHOD)?.summary =
            getString(R.string.pref_geonav_search_method_summary, method)
    }

    private fun updateParametersVisibility() {
        val geoNavCat = findPreference<PreferenceCategory>(GeneralKeys.GEONAV_PARAMETERS_CATEGORY)
        val geonavEnabled =
            preferences.getBoolean("com.fieldbook.tracker.geonav.ENABLE_GEONAV", false)

        when {
            !geonavEnabled -> {
                geoNavCat?.isVisible = false
            }

            preferences.getString(
                GeneralKeys.GEONAV_SEARCH_METHOD,
                "0"
            ) == "0" -> { // Distance based
                geoNavCat?.isVisible = false
            }

            else -> { // Trapezoidal
                geoNavCat?.isVisible = true
            }
        }
    }

    /**
     * Updates the pair device preference summary with the currently preferred device mac address.
     */
    private fun updateDeviceAddressSummary() {
        if (mPairDevicePref != null) {
            val address = preferences.getString(GeneralKeys.PAIRED_DEVICE_ADDRESS, "")
            mPairDevicePref!!.summary = address
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {

        updateParametersVisibility()

        return true
    }

    /**
     * Checks if the bluetooth adapter is enabled.
     * If the adapter is enabled, checks for all bonded devices that the user has made.
     * Creates a list for the user to choose from.
     * When the user chooses a device, it's mac address is saved in the preferences.
     * This can later be used to pair and communicate with the device (look in the GNSS trait class)
     */
    private fun startDeviceChoiceDialog() {

        //check if the device has bluetooth enabled, if not, request it to be enabled via system action
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter.isEnabled) {
            val pairedDevices = adapter.bondedDevices

            //create table of names -> bluetooth devices, when the item is selected we can retrieve the device
            val bluetoothMap: MutableMap<String, BluetoothDevice> = HashMap()
            val names: MutableList<String> = ArrayList()
            for (bd in pairedDevices) {
                bluetoothMap[bd.name] = bd
                names.add(bd.name)
            }
            val internalGps = getString(R.string.pref_behavior_geonav_internal_gps_choice)
            names.add(internalGps)
            val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)
            builder.setTitle(R.string.choose_paired_bluetooth_devices_title)

            //when a device is chosen, start a connect thread
            builder.setSingleChoiceItems(
                names.toTypedArray(),
                -1
            ) { dialog: DialogInterface, which: Int ->
                val deviceName = names[which]
                var address = internalGps
                if (deviceName != null) {
                    val device = bluetoothMap[deviceName]
                    if (device != null) {
                        address = device.address
                    }
                    preferences.edit().putString(GeneralKeys.PAIRED_DEVICE_ADDRESS, address)
                        .apply()
                    updateDeviceAddressSummary()
                    dialog.dismiss()
                }
            }
            builder.show()
        } else context?.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
}