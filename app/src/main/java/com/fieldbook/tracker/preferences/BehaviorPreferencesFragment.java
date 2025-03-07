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