package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.bytehamster.lib.preferencesearch.SearchConfiguration;
import com.bytehamster.lib.preferencesearch.SearchPreference;
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.PreferencesActivity;

public class PreferencesFragment extends BasePreferenceFragment {

    private PreferenceManager prefMgr;
    private Context context;
    private SearchPreference searchPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(GeneralKeys.SHARED_PREF_FILE_NAME);

        setPreferencesFromResource(R.xml.preferences, rootKey);

        searchPreference = findPreference("searchPreference");
        SearchConfiguration config = searchPreference.getSearchConfiguration();
        config.setActivity((AppCompatActivity) getActivity());
        config.setFragmentContainerViewId(android.R.id.content);

        config.index(R.xml.preferences);
        config.index(R.xml.preferences_appearance);
        config.index(R.xml.preferences_theme);
        config.index(R.xml.preferences_behavior);
        config.index(R.xml.preferences_brapi);
        config.index(R.xml.preferences_database);
        config.index(R.xml.preferences_general);
        config.index(R.xml.preferences_profile);
        config.index(R.xml.preferences_sounds);
        config.index(R.xml.preferences_experimental);
        config.index(R.xml.preferences_canon);
        config.index(R.xml.preferences_geonav);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.settings_advanced));
    }

    public void onSearchResultClicked(SearchPreferenceResult result) {
        if (result.getResourceFile() == R.xml.preferences) {
            searchPreference.setVisible(false); // Do not allow to click search multiple times
            scrollToPreference(result.getKey());
            findPreference(result.getKey());
        } else {
            getPreferenceScreen().removeAll();
            addPreferencesFromResource(result.getResourceFile());
            //todo figure out why this doesn't ripple like it should
            result.highlight(this);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        PreferencesFragment.this.context = context;
    }

    @Override
    public void onResume() {
        super.onResume();
        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.settings_advanced));
    }
}