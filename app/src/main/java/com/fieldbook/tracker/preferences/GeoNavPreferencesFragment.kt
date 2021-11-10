package com.fieldbook.tracker.preferences

import android.app.AlertDialog
import android.os.Bundle
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.PreferencesActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import androidx.preference.*
import java.util.ArrayList
import java.util.HashMap

class GeoNavPreferencesFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener {
    private var mPairDevicePref: Preference? = null
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "Settings"
        setPreferencesFromResource(R.xml.preferences_geonav, rootKey)
        (this.activity as PreferencesActivity?)!!.supportActionBar!!.title = getString(R.string.preferences_behavior_title)

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

            preferenceManager.sharedPreferences.edit()
                .putString(GeneralKeys.GEONAV_SEARCH_METHOD, newValue as? String ?: "0").apply()

            updateParametersVisibility()

            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateDeviceAddressSummary()
        updateParametersVisibility()
    }

    private fun updateParametersVisibility() {

        val geoNavCat = findPreference<PreferenceCategory>(GeneralKeys.GEONAV_PARAMETERS_CATEGORY)

        when (preferenceManager.sharedPreferences
            .getString(GeneralKeys.GEONAV_SEARCH_METHOD, "0")) {

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
            val address = preferenceManager!!.sharedPreferences
                .getString(GeneralKeys.PAIRED_DEVICE_ADDRESS, "")
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
                    preferenceManager!!.sharedPreferences
                        .edit().putString(GeneralKeys.PAIRED_DEVICE_ADDRESS, address)
                        .apply()
                    updateDeviceAddressSummary()
                    dialog.dismiss()
                }
            }
            builder.show()
        } else context?.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
}