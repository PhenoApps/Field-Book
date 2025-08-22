package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.bytehamster.lib.preferencesearch.SearchConfiguration;
import com.bytehamster.lib.preferencesearch.SearchPreference;
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.PreferencesActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.dialogs.ListAddDialog;
import com.fieldbook.tracker.utilities.DocumentTreeUtil;
import com.fieldbook.tracker.utilities.NearbyShareUtil;
import com.fieldbook.tracker.utilities.ZipUtil;

import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PreferencesFragment extends BasePreferenceFragment implements NearbyShareUtil.FileHandler {

    private PreferenceManager prefMgr;
    private Context context;
    private SearchPreference searchPreference;

    private  Menu systemMenu;
    @Inject
    DataHelper database;
    @Inject
    NearbyShareUtil nearbyShareUtil;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(GeneralKeys.SHARED_PREF_FILE_NAME);

        setPreferencesFromResource(R.xml.preferences, rootKey);

        searchPreference = findPreference("searchPreference");
        SearchConfiguration config = searchPreference.getSearchConfiguration();
        config.setActivity((AppCompatActivity) getActivity());
        config.setFragmentContainerViewId(R.id.prefs_container);
        
        config.index(R.xml.preferences_appearance);
        config.index(R.xml.preferences_theme);
        config.index(R.xml.preferences_behavior);
        config.index(R.xml.preferences_brapi);
        config.index(R.xml.preferences_system);
        config.index(R.xml.preferences_profile);
        config.index(R.xml.preferences_sounds);
        config.index(R.xml.preferences_experimental);
        config.index(R.xml.preferences_location);

        if (getActivity() != null && ((PreferencesActivity) getActivity()).getSupportActionBar() != null) {
            ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.settings_advanced));
        }
        setHasOptionsMenu(true);
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

        nearbyShareUtil.initialize();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_pref, menu);
        systemMenu = menu;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_menu_nearby_share) {
            showPreferenceShareDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        nearbyShareUtil.cleanup();
    }

    @Override
    public void onResume() {
        super.onResume();
        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.settings_advanced));

        if (systemMenu != null) {
            systemMenu.clear();
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.menu_pref, systemMenu);
        }
    }

    private void showPreferenceShareDialog() {
        String[] options = {
                getString(R.string.nearby_share_import_preferences),
                getString(R.string.nearby_share_export_preferences)
        };

        int[] icons = {
                R.drawable.ic_prefs_import,
                R.drawable.ic_prefs_export
        };


        if (getActivity() != null) {
            AdapterView.OnItemClickListener onItemClickListener = (parent, view, position, id) -> {
                switch (position) {
                    case 0:
                        nearbyShareUtil.startReceiving(PreferencesFragment.this);
                        break;
                    case 1:
                        nearbyShareUtil.startSharing(PreferencesFragment.this);
                        break;
                }
            };
            ListAddDialog dialog = new ListAddDialog(getActivity(), getString(R.string.nearby_share_preferences_title), options, icons, onItemClickListener);
            dialog.show(getActivity().getSupportFragmentManager(), "ListAddDialog");
        }
    }
    @Override
    public int getSaveFileDirectory() {
        return R.string.dir_preferences;
    }

    @Override
    public void onFileReceived(@NonNull DocumentFile receivedFile) {
        try {
            if (context != null) {
                ObjectInputStream objectStream = new ObjectInputStream(context.getContentResolver().openInputStream(receivedFile.getUri()));
                Map<String, ?> prefMap = (Map<String, ?>) objectStream.readObject();
                objectStream.close();
                ZipUtil.Companion.updatePreferences(context, prefMap);
            }
        } catch (Exception e) {
            Log.e("PreferencesFragment", "Failed to import preferences", e);
        }
    }

    @Override
    public DocumentFile prepareFileForTransfer() {
        try {
            SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", Locale.getDefault());
            String filename = "preferences_" + timeStamp.format(Calendar.getInstance().getTime());
            return DocumentTreeUtil.Companion.exportPreferences(context, filename, true);
        } catch (Exception e) {
            Log.e("PreferencesFragment", "Failed to export preferences", e);
            return null;
        }
    }
}