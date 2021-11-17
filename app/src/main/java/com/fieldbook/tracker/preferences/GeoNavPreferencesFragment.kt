package com.fieldbook.tracker.preferences

import android.app.AlertDialog
import android.os.Bundle
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.PreferencesActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.content.Intent
import androidx.preference.*
import java.util.ArrayList
import java.util.HashMap

class GeoNavPreferencesFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener {

    private var mPairDevicePref: Preference? = null

    private val mPrefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.preferences_geonav, rootKey)

        (this.activity as PreferencesActivity?)!!.supportActionBar!!.title = getString(R.string.preferences_geonav_title)

        //add click action on pair rover device to search for bt devices
        mPairDevicePref = findPreference(GeneralKeys.PAIR_BLUETOOTH)
        if (mPairDevicePref != null) {
            mPairDevicePref!!.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { view: Preference? ->
                    startDeviceChoiceDialog()
                    true
                }
        }

        val geoNavMethod = findPreference<ListPreference>(GeneralKeys.GEONAV_SEARCH_METHOD)

        geoNavMethod?.setOnPreferenceChangeListener { preference, newValue ->

            mPrefs.edit()
                .putString(GeneralKeys.GEONAV_SEARCH_METHOD, newValue as? String ?: "0").apply()

            updateMethodSummaryText()
            updateParametersVisibility()

            true
        }

        val trapBase = findPreference<EditTextPreference>(GeneralKeys.GEONAV_PARAMETER_D1)
        val trapDst = findPreference<EditTextPreference>(GeneralKeys.GEONAV_PARAMETER_D2)
        val trapAngle = findPreference<ListPreference>(GeneralKeys.SEARCH_ANGLE)
        val updateInterval = findPreference<ListPreference>(GeneralKeys.UPDATE_INTERVAL)

        updateInterval?.setOnPreferenceChangeListener { _, newValue ->

            mPrefs.edit()
                .putString(GeneralKeys.UPDATE_INTERVAL, newValue as? String ?: "0").apply()

            updateParametersSummaryText()

            true
        }

        trapBase?.setOnPreferenceChangeListener { _, newValue ->

            mPrefs.edit()
                .putString(GeneralKeys.GEONAV_PARAMETER_D1, newValue as? String ?: "0.001").apply()

            updateParametersSummaryText()

            true
        }

        trapDst?.setOnPreferenceChangeListener { _, newValue ->

            mPrefs.edit()
                .putString(GeneralKeys.GEONAV_PARAMETER_D2, newValue as? String ?: "0.01").apply()

            updateParametersSummaryText()

            true
        }

        trapAngle?.setOnPreferenceChangeListener { _, newValue ->

            mPrefs.edit()
                .putString(GeneralKeys.SEARCH_ANGLE, newValue as? String ?: "22.5").apply()

            updateParametersSummaryText()

            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateParametersSummaryText()
        updateMethodSummaryText()
        updateDeviceAddressSummary()
        updateParametersVisibility()
    }

    private fun updateParametersSummaryText() {

        val d1 = mPrefs.getString(GeneralKeys.GEONAV_PARAMETER_D1, "0.001")
        val d2 = mPrefs.getString(GeneralKeys.GEONAV_PARAMETER_D2, "0.01")
        val theta = mPrefs.getString(GeneralKeys.SEARCH_ANGLE, "22.5")
        val interval = mPrefs.getString(GeneralKeys.UPDATE_INTERVAL, "0")

        val trapBase = findPreference<EditTextPreference>(GeneralKeys.GEONAV_PARAMETER_D1)
        val trapDst = findPreference<EditTextPreference>(GeneralKeys.GEONAV_PARAMETER_D2)
        val trapAngle = findPreference<ListPreference>(GeneralKeys.SEARCH_ANGLE)
        val updateInterval = findPreference<ListPreference>(GeneralKeys.UPDATE_INTERVAL)

        trapBase?.summary = getString(R.string.pref_geonav_search_trapezoid_d1_summary, d1)
        trapDst?.summary = getString(R.string.pref_geonav_search_trapezoid_d2_summary, d2)
        trapAngle?.summary = getString(R.string.pref_geonav_search_angle_summary, theta)
        updateInterval?.summary = getString(R.string.pref_geonav_update_interval_summary, interval)
    }

    private fun updateMethodSummaryText() {

        val method = if (mPrefs.getString(GeneralKeys.GEONAV_SEARCH_METHOD,
            getString(R.string.pref_geonav_method_distance)) == "0") getString(R.string.pref_geonav_method_distance)
            else getString(R.string.pref_geonav_method_trapezoid)

        findPreference<ListPreference>(GeneralKeys.GEONAV_SEARCH_METHOD)?.summary =
            getString(R.string.pref_geonav_search_method_summary, method)
    }

    private fun updateParametersVisibility() {

        val geoNavCat = findPreference<PreferenceCategory>(GeneralKeys.GEONAV_PARAMETERS_CATEGORY)

        when (mPrefs.getString(GeneralKeys.GEONAV_SEARCH_METHOD, "0")) {

            "0" -> { //distance based

                geoNavCat?.isVisible = false
            }

            else -> { //trapezoidal

                geoNavCat?.isVisible = true
            }
        }
    }

    /**
     * Updates the pair device preference summary with the currently preferred device mac address.
     */
    private fun updateDeviceAddressSummary() {
        if (mPairDevicePref != null) {
            val address = mPrefs.getString(GeneralKeys.PAIRED_DEVICE_ADDRESS, "")
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
            val builder = AlertDialog.Builder(context)
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
                    mPrefs.edit().putString(GeneralKeys.PAIRED_DEVICE_ADDRESS, address)
                        .apply()
                    updateDeviceAddressSummary()
                    dialog.dismiss()
                }
            }
            builder.show()
        } else context?.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
}