package com.fieldbook.tracker.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.documentfile.provider.DocumentFile;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.DefineStorageActivity;
import com.fieldbook.tracker.utilities.DocumentTreeUtil;

import org.phenoapps.utils.BaseDocumentTreeUtil;

public class GeneralPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    private static final int REQUEST_STORAGE_DEFINER_CODE = 999;

    public static final int LOCATION_COLLECTION_MANUAL = 0;
    public static final int LOCATION_COLLECTION_OBS_UNIT = 1;
    public static final int LOCATION_COLLECTION_OBS = 2;

    PreferenceManager prefMgr;
    Context context;
    private Preference defaultStorageLocation;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(GeneralKeys.SHARED_PREF_FILE_NAME);

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

        Preference skipEntriesPref = this.findPreference(GeneralKeys.HIDE_ENTRIES_WITH_DATA_TOOLBAR);

        if (skipEntriesPref != null) {

            //set preference change listener to change summary when needed
            skipEntriesPref.setOnPreferenceChangeListener(this);

            //also initialize the summary whenever the fragment is opened, or else it defaults to "disabled"
            String skipMode = prefMgr.getSharedPreferences().getString(GeneralKeys.HIDE_ENTRIES_WITH_DATA_TOOLBAR, "1");

            switchSkipPreferenceMode(skipMode, skipEntriesPref);

        }
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

            case "4": {

                preference.setSummary(R.string.preferences_general_skip_entries_default_description);

                break;
            }
            default: {

                preference.setSummary(R.string.preferences_general_feature_next_missing_description);

                break;

            }
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        GeneralPreferencesFragment.this.context = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        //When the skip entries preference is changed, update the summary that is defined in string.xml.
        if (preference.hasKey()) {

            if (preference.getKey().equals(GeneralKeys.HIDE_ENTRIES_WITH_DATA_TOOLBAR)) {

                switchSkipPreferenceMode((String) newValue, preference);

            }
        }

        return true;
    }

    private void updateLocationCollectionPreference() {

        try {

            ListPreference pref = findPreference(GeneralKeys.GENERAL_LOCATION_COLLECTION);
            if (pref != null) {

                String obsUnitModeSummary = getString(R.string.pref_general_location_collection_summary_obs_units);
                String obsModeManualSummary = getString(R.string.pref_general_location_collection_summary_manual);
                String obsModeSummary = getString(R.string.pref_general_location_collection_summary_obs);
                String obsModeDialogTitle = getString(R.string.pref_general_location_collection_obs_dialog_title);

                int mode = Integer.parseInt(prefMgr.getSharedPreferences().getString(GeneralKeys.GENERAL_LOCATION_COLLECTION, "0"));
                if (mode == LOCATION_COLLECTION_MANUAL) pref.setSummary(obsModeManualSummary);
                else if (mode == LOCATION_COLLECTION_OBS_UNIT) pref.setSummary(obsUnitModeSummary);
                else pref.setSummary(obsModeSummary);

                pref.setOnPreferenceChangeListener(((preference, newValue) -> {

                    int value = Integer.parseInt((String) newValue);

                    if (value == LOCATION_COLLECTION_OBS) {

                        new AlertDialog.Builder(context)
                            .setTitle(obsModeDialogTitle)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {

                                dialog.dismiss();

                            })
                            .setNegativeButton(android.R.string.cancel, (dialog, which) -> {

                                prefMgr.getSharedPreferences().edit().putString(GeneralKeys.GENERAL_LOCATION_COLLECTION, "0").apply();

                                pref.setValueIndex(0);

                                dialog.dismiss();

                            }).show();

                    }

                    return true;

                }));
            }

        } catch (Exception e) {

            e.printStackTrace();

        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (BaseDocumentTreeUtil.Companion.isEnabled(context)) {

            //get directory name
            DocumentFile root = BaseDocumentTreeUtil.Companion.getRoot(context);

            if (root != null && root.exists()) {

                String path = BaseDocumentTreeUtil.Companion.getStem(root.getUri(), context);

                defaultStorageLocation.setSummary(path);

            }
        }

        updateLocationCollectionPreference();
    }
}