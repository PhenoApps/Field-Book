package com.fieldbook.tracker.preferences;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.BrAPIService;
import com.fieldbook.tracker.brapi.BrapiControllerResponse;

public class PreferencesActivity extends AppCompatActivity {

    // Appearance
    public static String TOOLBAR_CUSTOMIZE = "TOOLBAR_CUSTOMIZE";
    public static String LANGUAGE = "language";
    public static String INFOBAR_NUMBER = "INFOBAR_NUMBER";
    public static String TOOLBAR_SEARCH = "TOOLBAR_SEARCH";
    public static String TOOLBAR_RESOURCES = "TOOLBAR_RESOURCES";
    public static String TOOLBAR_SUMMARY = "TOOLBAR_SUMMARY";
    public static String TOOLBAR_LOCK = "TOOLBAR_LOCK";

    // Profile
    public static String PROFILE_FIRSTNAME = "FirstName";
    public static String PROFILE_LASTNAME = "LastName";
    public static String PROFILE_LOCATION = "Location";

    // Behavior
    public static String RETURN_CHARACTER = "RETURN_CHARACTER";
    public static String VOLUME_NAVIGATION = "VOLUME_NAVIGATION";

    // General
    public static String TUTORIAL_MODE = "Tips";
    public static String NEXT_ENTRY_NO_DATA = "NextEmptyPlot";
    public static String QUICK_GOTO = "QuickGoTo";
    public static String UNIQUE_CAMERA = "BarcodeScan";
    public static String UNIQUE_TEXT = "JumpToPlot";
    public static String DATAGRID_SETTING = "DataGrid";
    public static String DISABLE_ENTRY_ARROW_LEFT = "DisableEntryNavLeft";
    public static String DISABLE_ENTRY_ARROW_RIGHT = "DisableEntryNavRight";
    public static String CYCLING_TRAITS_ADVANCES = "CycleTraits";
    public static String HIDE_ENTRIES_WITH_DATA = "IgnoreExisting";
    public static String USE_DAY_OF_YEAR = "UseDay";
    public static String DISABLE_SHARE = "DisableShare";

    // Files and Naming
    public static String DEFAULT_STORAGE_LOCATION = "DEFAULT_STORAGE_LOCATION";
    public static String DATE_FORMAT = "DATE_FORMAT";
    public static String FILE_NAME_FORMAT = "FILE_NAME_FORMAT";
    public static String PHOTO_NAME_FORMAT = "PHOTO_NAME_FORMAT";

    // Sounds
    public static String PRIMARY_SOUND = "RangeSound";
    public static String TRAIT_SOUND = "TRAIT_SOUND";

    //BrAPI
    public static String BRAPI_BASE_URL = "BRAPI_BASE_URL";
    public static String BRAPI_TOKEN = "BRAPI_TOKEN";

    private static PreferencesFragment preferencesFragment;
    private static Preference brapiPrefCategory;
    private BrapiControllerResponse brapiControllerResponse;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.settings_advanced));
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        // Check if our activity was started up with brapi auth deep link.
        brapiControllerResponse = BrAPIService.checkBrapiAuth(this);

        // This is not related to the deep link, load normally.
        preferencesFragment = new PreferencesFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, preferencesFragment)
                .commit();
        //TODO: The top tool bar disappears when you go into 'Brapi Configuration'. Fix it.

    }

    public void processMessage(Boolean status, String message) {

        // If we fail or succeed, show our message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onResume() {
        super.onResume();

        // If our preference page was resumed, we will want to see if it was resumed from a deep link.
        if (brapiControllerResponse.status == null) {
            brapiControllerResponse = BrAPIService.checkBrapiAuth(this);
        }

        // Check whether our brapi auth response was successful
        if (brapiControllerResponse.status != null) {
            processMessage(brapiControllerResponse.status, brapiControllerResponse.message);

            // Show our brapi preferences if they just came back from a brapi auth and it is not displayed already.
            PreferenceScreen brapi_prefs = (PreferenceScreen) preferencesFragment.findPreference("brapi_preference_screen");
            if (!preferencesFragment.getPreferenceScreen().equals(brapi_prefs)) {
                preferencesFragment.setPreferenceScreen(brapi_prefs);
            }

        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_OK);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

}