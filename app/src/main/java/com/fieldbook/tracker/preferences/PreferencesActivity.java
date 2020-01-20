package com.fieldbook.tracker.preferences;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceFragmentCompat;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.BrAPIService;
import com.fieldbook.tracker.brapi.BrapiControllerResponse;

public class PreferencesActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

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

    private static PreferencesFragmentBrapi preferencesFragmentBrapi;
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

        preferencesFragmentBrapi = new PreferencesFragmentBrapi();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new PreferencesFragment())
                .commit();
    }

    public void processMessage(BrapiControllerResponse brapiControllerResponse) {
        if (brapiControllerResponse.status != null) {
            if (!brapiControllerResponse.status) {
                Toast.makeText(this, R.string.brapi_auth_error_starting, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.brapi_auth_success, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // If our preference page was resumed, we will want to see if it was resumed from a deep link.
        brapiControllerResponse = BrAPIService.checkBrapiAuth(this);

        // Set our button visibility and text
        //todo null object reference
        //preferencesFragmentBrapi.setButtonView();

        processMessage(brapiControllerResponse);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            setResult(RESULT_OK);
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, androidx.preference.Preference pref) {

        // Instantiate the new Fragment
        final Bundle args = pref.getExtras();
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                pref.getFragment());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);

        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();
        return true;
    }
}