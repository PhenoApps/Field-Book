package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceFragmentCompat;

import com.fieldbook.tracker.R;

public class PreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    PreferenceManager prefMgr;
    Context context;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");

        setPreferencesFromResource(R.xml.preferences, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle("Settings");
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        PreferencesFragment.this.context = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle("Settings");
    }
}