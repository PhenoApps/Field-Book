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
class LocationPreferencesFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener {

    private var mPairDevicePref: Preference? = null

    @Inject
    lateinit var preferences: SharedPreferences

    private val advisor by Security().secureBluetooth()

    companion object {
        const val LOCATION_COLLECTION_OFF = 0
        const val LOCATION_COLLECTION_STUDY = 1
        const val LOCATION_COLLECTION_OBS_UNIT = 2
        const val LOCATION_COLLECTION_OBS = 3
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        advisor.initialize()
        setPreferencesFromResource(R.xml.preferences_location, rootKey)

        setupUi()
        registerPreferenceListeners()

        (activity as PreferencesActivity).supportActionBar?.title = getString(R.string.preferences_location_title)

        mPairDevicePref = findPreference(PreferenceKeys.PAIR_BLUETOOTH)
    }

    private fun setupUi() {
        updateUi()
        updatePreferencesVisibility(isGeoNavEnabled())
    }

    private fun isGeoNavEnabled(): Boolean {
        return findPreference<CheckBoxPreference>("com.fieldbook.tracker.geonav.ENABLE_GEONAV")?.isChecked ?: false
    }

    private fun registerPreferenceListeners() {
        preferences.registerOnSharedPreferenceChangeListener { _, _ -> updateUi() }

        findPreference<CheckBoxPreference>("com.fieldbook.tracker.geonav.ENABLE_GEONAV")?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                updatePreferencesVisibility(newValue as Boolean)
                true
            }

        findPreference<ListPreference>("com.fieldbook.tracker.GENERAL_LOCATION_COLLECTION")?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                updateCoordinateFormatVisibility((newValue as String).toInt())
                true
            }

        findPreference<ListPreference>(PreferenceKeys.GEONAV_SEARCH_METHOD)?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(PreferenceKeys.GEONAV_SEARCH_METHOD, newValue as String).apply()
                if (newValue == "1") checkRequiredSensors()
                updatePreferencesVisibility(isGeoNavEnabled())
                true
            }

        findPreference<Preference>(PreferenceKeys.PAIR_BLUETOOTH)?.let { pairDevicePref ->
            pairDevicePref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                advisor.connectWith {
                    startDeviceChoiceDialog()
                }
                true
            }
        }

        findPreference<EditTextPreference>(PreferenceKeys.GEONAV_DISTANCE_THRESHOLD)?.let { distPref ->
            distPref.setOnBindEditTextListener { editText ->
                editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }

        setPreferenceChangeListeners(
            listOf(
                PreferenceKeys.GEONAV_PARAMETER_D1,
                PreferenceKeys.GEONAV_PARAMETER_D2,
                PreferenceKeys.SEARCH_ANGLE,
                PreferenceKeys.UPDATE_INTERVAL
            )
        )
    }

    private fun setPreferenceChangeListeners(keys: List<String>) {
        keys.forEach { key ->
            findPreference<Preference>(key)?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as? String ?: "").apply()
                updateParametersSummaryText()
                true
            }
        }
    }

    private fun updateUi() {
        updateMethodSummaryText()
        changeGeoNavLoggingModeView()
        updateParametersSummaryText()
        updateDeviceAddressSummary()
        updateCoordinateFormatVisibility()
    }

    private fun updateCoordinateFormatVisibility(selectedValue: Int? = null) {
        val currentValue = selectedValue ?: preferences.getString(PreferenceKeys.GENERAL_LOCATION_COLLECTION, "0")?.toIntOrNull() ?: LOCATION_COLLECTION_OFF
        val coordinateFormatPref = findPreference<Preference>(PreferenceKeys.COORDINATE_FORMAT)
        coordinateFormatPref?.isVisible = currentValue != LOCATION_COLLECTION_OFF
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
        val geoNavLoggingMode = findPreference<ListPreference>(PreferenceKeys.GEONAV_LOGGING_MODE)
        val currentMode = preferences.getString(PreferenceKeys.GEONAV_LOGGING_MODE, "0")

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

        val d1 = preferences.getString(PreferenceKeys.GEONAV_PARAMETER_D1, "0.001")
        val d2 = preferences.getString(PreferenceKeys.GEONAV_PARAMETER_D2, "0.01")
        val theta = preferences.getString(PreferenceKeys.SEARCH_ANGLE, "22.5")
        val interval = preferences.getString(PreferenceKeys.UPDATE_INTERVAL, "0")

        val trapBase = findPreference<EditTextPreference>(PreferenceKeys.GEONAV_PARAMETER_D1)
        val trapDst = findPreference<EditTextPreference>(PreferenceKeys.GEONAV_PARAMETER_D2)
        val trapAngle = findPreference<ListPreference>(PreferenceKeys.SEARCH_ANGLE)
        val updateInterval = findPreference<ListPreference>(PreferenceKeys.UPDATE_INTERVAL)

        trapBase?.summary = getString(R.string.pref_geonav_search_trapezoid_d1_summary, d1)
        trapDst?.summary = getString(R.string.pref_geonav_search_trapezoid_d2_summary, d2)
        trapAngle?.summary = getString(R.string.pref_geonav_search_angle_summary, theta)
        updateInterval?.summary = getString(R.string.pref_geonav_update_interval_summary, interval)
    }


    private fun updatePreferencesVisibility(isChecked: Boolean) {
        val geonavCategory = findPreference<PreferenceCategory>("com.fieldbook.tracker.geonav.CATEGORY")
        val searchMethodValue = preferences.getString(PreferenceKeys.GEONAV_SEARCH_METHOD, "0") ?: "0"

        geonavCategory?.let { category ->
            for (i in 0 until category.preferenceCount) {
                val preferenceItem = category.getPreference(i)
                if (preferenceItem.key == "com.fieldbook.tracker.geonav.ENABLE_GEONAV") {
                    continue
                }
                if (preferenceItem.key?.startsWith("com.fieldbook.tracker.geonav.parameters.") == true && searchMethodValue == "0") {
                    preferenceItem.isVisible = false
                } else {
                    preferenceItem.isVisible = isChecked
                }
            }
        }
    }

    private fun updateMethodSummaryText() {
        val method = if (preferences.getString(PreferenceKeys.GEONAV_SEARCH_METHOD, "0") == "0")
            getString(R.string.pref_geonav_method_distance)
        else
            getString(R.string.pref_geonav_method_trapezoid)

        findPreference<ListPreference>(PreferenceKeys.GEONAV_SEARCH_METHOD)?.summary = getString(R.string.pref_geonav_search_method_summary, method)
    }

    /**
     * Updates the pair device preference summary with the currently preferred device mac address.
     */
    private fun updateDeviceAddressSummary() {
        if (mPairDevicePref != null) {
            val address = preferences.getString(PreferenceKeys.PAIRED_DEVICE_ADDRESS, "")
            mPairDevicePref?.summary = address
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
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
                    preferences.edit().putString(PreferenceKeys.PAIRED_DEVICE_ADDRESS, address)
                        .apply()
                    updateDeviceAddressSummary()
                    dialog.dismiss()
                }
            }
            builder.show()
        } else context?.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
}