package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.activities.ConfigActivity;
import com.fieldbook.tracker.R;

public class ProfilePreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    PreferenceManager prefMgr;
    Context context;
    private Preference profilePerson;
    private Preference profileLocation;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");

        setPreferencesFromResource(R.xml.preferences_profile, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.settings_profile));

        profilePerson = findPreference("pref_profile_person");
        profileLocation = findPreference("pref_profile_location");

        profilePerson.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent d = new Intent(context, ConfigActivity.class);
                d.putExtra("dialog", "person");
                startActivity(d);
                return true;
            }
        });

        profileLocation.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent d = new Intent(context, ConfigActivity.class);
                d.putExtra("dialog", "location");
                startActivity(d);
                return true;
            }
        });
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        ProfilePreferencesFragment.this.context = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        return false;
    }
}