package com.fieldbook.tracker.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.ConfigActivity;
import com.fieldbook.tracker.utilities.FileUtil;
import com.fieldbook.tracker.utilities.Utils;

public class GeneralPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    PreferenceManager prefMgr;
    Context context;
    private Preference defaultStorageLocation;
    private PreferenceCategory transferDefaultsCategory;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");

        setPreferencesFromResource(R.xml.preferences_general, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.preferences_general_title));

        transferDefaultsCategory = prefMgr.findPreference("transfer_defaults");
        defaultStorageLocation = findPreference("DEFAULT_STORAGE_LOCATION_PREFERENCE");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            assert defaultStorageLocation != null;
            transferDefaultsCategory.removePreference(defaultStorageLocation);
        } else {
            String storageSummary = prefMgr.getSharedPreferences().getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, null);
            defaultStorageLocation.setSummary(storageSummary);

            defaultStorageLocation.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    //TODO if folderCreatedPref false, create folder first

                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivityForResult(intent, 999);
                    return true;
                }
            });
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

        return false;
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        if (requestCode == 999
                && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            Uri treeUri = null;
            if (resultData != null) {
                //get directory name
                treeUri = resultData.getData();
                String path = FileUtil.getFullPathFromTreeUri(treeUri, getContext());
                defaultStorageLocation.setSummary(path);

                //save summary to preference
                SharedPreferences.Editor editor = prefMgr.getSharedPreferences().edit();
                editor.putString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, path);

                //save uri to preference
                //todo use saved uri instead of directory for file nav/saving/loading to handle scoped storage limitations
                editor.putString(GeneralKeys.DEFAULT_STORAGE_LOCATION_URI, treeUri.toString());
                editor.apply();

                Utils.createDirs(getContext(),path);
            }
        }
    }
}