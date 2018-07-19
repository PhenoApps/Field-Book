package com.fieldbook.tracker.preferences;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.fieldbook.tracker.R;

public class PreferencesFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");
        addPreferencesFromResource(R.xml.preferences);
    }
}
