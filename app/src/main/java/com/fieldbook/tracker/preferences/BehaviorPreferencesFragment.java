package com.fieldbook.tracker.preferences;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BehaviorPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    PreferenceManager prefMgr;
    Context context;

    private Preference mPairDevicePref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");

        setPreferencesFromResource(R.xml.preferences_behavior, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.preferences_behavior_title));

        //add click action on pair rover device to search for bt devices
        mPairDevicePref = findPreference(GeneralKeys.PAIR_BLUETOOTH);

        if (mPairDevicePref != null) {
            mPairDevicePref.setOnPreferenceClickListener((view) -> {

                startDeviceChoiceDialog();

                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        updateDeviceAddressSummary();

    }

    /**
     * Updates the pair device preference summary with the currently preferred device mac address.
     */
    private void updateDeviceAddressSummary() {
        if (mPairDevicePref != null) {
            String address = prefMgr.getSharedPreferences()
                    .getString(GeneralKeys.PAIRED_DEVICE_ADDRESS, "");
            mPairDevicePref.setSummary(address);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        BehaviorPreferencesFragment.this.context = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        return false;
    }

    /**
     * Checks if the bluetooth adapter is enabled.
     * If the adapter is enabled, checks for all bonded devices that the user has made.
     * Creates a list for the user to choose from.
     * When the user chooses a device, it's mac address is saved in the preferences.
     * This can later be used to pair and communicate with the device (look in the GNSS trait class)
     */
    private void startDeviceChoiceDialog() {

        //check if the device has bluetooth enabled, if not, request it to be enabled via system action
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter.isEnabled()) {

            Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();

            //if the user already paired an external unit then show a list to connect from
            if (!adapter.getBondedDevices().isEmpty()) {

                //create table of names -> bluetooth devices, when the item is selected we can retrieve the device
                Map<String, BluetoothDevice> bluetoothMap = new HashMap<>();
                List<String> names = new ArrayList<>();

                for (BluetoothDevice bd : pairedDevices) {
                    bluetoothMap.put(bd.getName(), bd);
                    names.add(bd.getName());
                }

                String internalGps = getString(R.string.pref_behavior_geonav_internal_gps_choice);
                names.add(internalGps);

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.choose_paired_bluetooth_devices_title);

                //when a device is chosen, start a connect thread
                builder.setSingleChoiceItems(names.toArray(new String[] {}), -1, (dialog, which) -> {

                    String deviceName = names.get(which);
                    String address = internalGps;

                    if (deviceName != null) {

                        @Nullable BluetoothDevice device = bluetoothMap.get(deviceName);
                        if (device != null) {

                            address = device.getAddress();

                        }

                        prefMgr.getSharedPreferences()
                                .edit().putString(GeneralKeys.PAIRED_DEVICE_ADDRESS, address)
                                .apply();

                        updateDeviceAddressSummary();

                        dialog.dismiss();
                    }
                });

                builder.show();

            } else Toast.makeText(context, R.string.gnss_no_paired_device, Toast.LENGTH_SHORT).show();

        } else context.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
    }
}