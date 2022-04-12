package com.fieldbook.tracker.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.documentfile.provider.DocumentFile;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.DefineStorageActivity;
import com.fieldbook.tracker.utilities.DocumentTreeUtil;

public class GeneralPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    private static final int REQUEST_STORAGE_DEFINER_CODE = 999;

    PreferenceManager prefMgr;
    Context context;
    private Preference defaultStorageLocation;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");

        setPreferencesFromResource(R.xml.preferences_general, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.preferences_general_title));

        defaultStorageLocation = findPreference("DEFAULT_STORAGE_LOCATION_PREFERENCE");

        String storageSummary = prefMgr.getSharedPreferences().getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, null);
        defaultStorageLocation.setSummary(storageSummary);

        defaultStorageLocation.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(context, DefineStorageActivity.class);
            startActivityForResult(intent, REQUEST_STORAGE_DEFINER_CODE);
            return true;
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        GeneralPreferencesFragment.this.context = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (DocumentTreeUtil.Companion.isEnabled(context)) {

            //get directory name
            DocumentFile root = DocumentTreeUtil.Companion.getRoot(context);

            if (root != null && root.exists()) {

                String path = DocumentTreeUtil.Companion.getStem(root.getUri(), context);

                defaultStorageLocation.setSummary(path);

            }
        }
    }
}