package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.activities.ConfigActivity;
import com.fieldbook.tracker.activities.FileExploreActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.utilities.Constants;

public class DatabasePreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    PreferenceManager prefMgr;
    Context context;
    private Preference databaseImport;
    private Preference databaseExport;
    private Preference databaseDelete;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");

        setPreferencesFromResource(R.xml.preferences_database, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.database_dialog_title));

        databaseImport = findPreference("pref_database_import");
        databaseExport = findPreference("pref_database_export");
        databaseDelete = findPreference("pref_database_delete");

        databaseImport.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent d = new Intent(context, ConfigActivity.class);
                d.putExtra("dialog", "database-import");
                startActivity(d);
                return true;
            }
        });

        databaseExport.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent d = new Intent(context, ConfigActivity.class);
                d.putExtra("dialog", "database-export");
                startActivity(d);
                return true;
            }
        });

        databaseDelete.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent d = new Intent(context, ConfigActivity.class);
                d.putExtra("dialog", "database-delete");
                startActivity(d);
                return true;
            }
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        DatabasePreferencesFragment.this.context = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        return false;
    }
}