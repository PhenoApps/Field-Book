package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;

public class BehaviorPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    PreferenceManager prefMgr;
    Context context;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");

        setPreferencesFromResource(R.xml.preferences_behavior, rootKey);

        Preference skipEntriesPref = this.findPreference(GeneralKeys.HIDE_ENTRIES_WITH_DATA);

        if (skipEntriesPref != null) {

            //set preference change listener to change summary when needed
            skipEntriesPref.setOnPreferenceChangeListener(this);

            //also initialize the summary whenever the fragment is opened, or else it defaults to "disabled"
            String skipMode = prefMgr.getSharedPreferences().getString(GeneralKeys.HIDE_ENTRIES_WITH_DATA, "1");

            switchSkipPreferenceMode(skipMode, skipEntriesPref);

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


        //When the skip entries preference is changed, update the summary that is defined in string.xml.
        if (preference.hasKey()) {

            if (preference.getKey().equals(GeneralKeys.HIDE_ENTRIES_WITH_DATA)) {

                switchSkipPreferenceMode((String) newValue, preference);

            }
        }

        return true;
    }

    private void switchSkipPreferenceMode(String mode, Preference preference) {

        switch (mode) {

            case "2": {

                preference.setSummary(R.string.preferences_general_skip_entries_with_data_description);

                break;

            }

            case "3": {

                preference.setSummary(R.string.preferences_general_skip_entries_across_all_traits_description);

                break;

            }

            default: {

                preference.setSummary(R.string.preferences_general_skip_entries_disabled);

                break;

            }
        }
    }
}