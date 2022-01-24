package com.fieldbook.tracker.preferences

import android.app.AlertDialog
import android.os.Bundle
import com.fieldbook.tracker.R
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.DialogInterface
import android.content.Intent
import androidx.preference.*
import java.util.ArrayList
import java.util.HashMap

class VuzixPreferencesFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener {

    private var mPairDevicePref: Preference? = null

    private val mPrefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.preferences_vuzix, rootKey)

        (this.activity as PreferencesActivity?)!!.supportActionBar!!.title = getString(R.string.preferences_vuzix_title)

        mPairDevicePref = findPreference(GeneralKeys.VUZIX_PAIR)
        if (mPairDevicePref != null) {
            mPairDevicePref!!.onPreferenceClickListener =
                Preference.OnPreferenceClickListener { view: Preference? ->
                    startDeviceChoiceDialog()
                    true
                }
        }

        val updateInterval = findPreference<ListPreference>(GeneralKeys.VUZIX_INTERVAL)

        updateInterval?.setOnPreferenceChangeListener { _, newValue ->

            mPrefs.edit()
                .putString(GeneralKeys.VUZIX_INTERVAL, newValue as? String ?: "0").apply()

            updateParametersSummaryText()

            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateParametersSummaryText()
        updateDeviceAddressSummary()
    }

    private fun updateParametersSummaryText() {

        val interval = mPrefs.getString(GeneralKeys.VUZIX_INTERVAL, "0")

        val updateInterval = findPreference<ListPreference>(GeneralKeys.VUZIX_INTERVAL)

        updateInterval?.summary = getString(R.string.pref_vuzix_update_interval_summary, interval)
    }

    /**
     * Updates the pair device preference summary with the currently preferred device mac address.
     */
    private fun updateDeviceAddressSummary() {
        if (mPairDevicePref != null) {
            val address = mPrefs.getString(GeneralKeys.VUZIX_DEVICE, "")
            mPairDevicePref!!.summary = address
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

            val builder = AlertDialog.Builder(context)
            builder.setTitle(R.string.choose_paired_bluetooth_devices_title)

            //when a device is chosen, start a connect thread
            builder.setSingleChoiceItems(
                names.toTypedArray(),
                -1
            ) { dialog: DialogInterface, which: Int ->
                val deviceName = names[which]
                var address = ""
                if (deviceName != null) {
                    val device = bluetoothMap[deviceName]
                    if (device != null) {
                        address = device.address
                    }
                    mPrefs.edit().putString(GeneralKeys.VUZIX_DEVICE, address)
                        .apply()
                    updateDeviceAddressSummary()
                    dialog.dismiss()
                }
            }
            builder.show()
        } else context?.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
}