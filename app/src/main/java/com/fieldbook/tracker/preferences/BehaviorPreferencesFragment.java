package com.fieldbook.tracker.preferences;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.PreferencesActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BehaviorPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    private static final String TAG = BehaviorPreferencesFragment.class.getSimpleName();

    @Inject
    SharedPreferences preferences;

    Context context;

    private Preference mPairDevicePref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        //bugfix commit 60ab59c4a82db79b350d72f8dbf51d08548bd846 fixes deprecated preference data for volume nav
        try {
            preferences.getString(PreferenceKeys.VOLUME_NAVIGATION, "");
        } catch (ClassCastException e) {
            preferences.edit().putString(PreferenceKeys.VOLUME_NAVIGATION, "0").apply();
            Log.d(TAG, "Stagnant Deprecated preference data found, fixing.");
        }

        setPreferencesFromResource(R.xml.preferences_behavior, rootKey);
        CheckBoxPreference cycleTraitsPref = findPreference("CycleTraits");

        if (cycleTraitsPref != null) {
            cycleTraitsPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean isChecked = (Boolean) newValue;
                if (isChecked) {
                    if (!preferences.getBoolean("CYCLE_TRAITS_SOUND", false)) {
                        promptEnableCycleTraitsSound();
                    }
                }
                return true;
            });
        }

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
            String address = preferences.getString(GeneralKeys.PAIRED_DEVICE_ADDRESS, "");
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
        return true;
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

            //create table of names -> bluetooth devices, when the item is selected we can retrieve the device
            Map<String, BluetoothDevice> bluetoothMap = new HashMap<>();
            List<String> names = new ArrayList<>();

            for (BluetoothDevice bd : pairedDevices) {
                bluetoothMap.put(bd.getName(), bd);
                names.add(bd.getName());
            }

            String internalGps = getString(R.string.pref_behavior_geonav_internal_gps_choice);
            names.add(internalGps);

            AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppAlertDialog);
            builder.setTitle(R.string.choose_paired_bluetooth_devices_title);

            //when a device is chosen, start a connect thread
            builder.setSingleChoiceItems(names.toArray(new String[]{}), -1, (dialog, which) -> {

                String deviceName = names.get(which);
                String address = internalGps;

                if (deviceName != null) {

                    @Nullable BluetoothDevice device = bluetoothMap.get(deviceName);
                    if (device != null) {

                        address = device.getAddress();

                    }

                    preferences.edit().putString(GeneralKeys.PAIRED_DEVICE_ADDRESS, address)
                            .apply();

                    updateDeviceAddressSummary();

                    dialog.dismiss();
                }
            });

            builder.show();

        } else context.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
    }

    private void promptEnableCycleTraitsSound() {
        new AlertDialog.Builder(context)
                .setTitle(R.string.preferences_behavior_cycle_sound_title)
                .setMessage(R.string.preferences_behavior_cycle_sound_description)
                .setPositiveButton(R.string.dialog_enable, (dialog, which) -> {
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean("CYCLE_TRAITS_SOUND", true);
                    editor.apply();
                })
                .setNegativeButton(R.string.dialog_no, null)
                .show();
    }

}