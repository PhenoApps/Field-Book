package com.fieldbook.tracker.preferences;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.BrAPIService;

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

    private PreferenceManager prefMgr;
    private Preference brapiAuthButton;
    private Preference brapiLogoutButton;
    private PreferenceGroup brapiConfig;


    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.settings_advanced));
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        final PreferencesFragment preferencesFragment = new PreferencesFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, preferencesFragment)
                .commit();

        registerBrapiButtonListeners(this, preferencesFragment);
        registerBrapiHostChangeListener(this);

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

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (BRAPI_BASE_URL.equals(key)) {
            if(brapiConfig != null) {
                brapiConfig.addPreference(brapiAuthButton);
                brapiConfig.addPreference(brapiLogoutButton);
                //TODO: BrAPIService.authorizeBrAPI(sharedPreferences, this);
            }
        }
    }

    private void registerBrapiHostChangeListener(final PreferencesActivity prefActivity) {
        getSharedPreferences("Settings", 0).registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (BRAPI_BASE_URL.equals(key)) {
                    if(brapiConfig != null) {
                        brapiConfig.addPreference(brapiAuthButton);
                        brapiConfig.addPreference(brapiLogoutButton);
                        BrAPIService.authorizeBrAPI(sharedPreferences, prefActivity, null);
                    }
                }
            }
        });
    }

    @SuppressLint("NewApi")
    private void registerBrapiButtonListeners(final PreferencesActivity prefActivity, final PreferencesFragment preferencesFragment) {
        getFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentCreated(FragmentManager fm, Fragment f, Bundle savedInstanceState) {
                super.onFragmentCreated(fm, f, savedInstanceState);

                prefMgr = preferencesFragment.getPreferenceManager();
                brapiAuthButton = prefMgr.findPreference("authorizeBrapi");
                brapiLogoutButton = prefMgr.findPreference("revokeBrapiAuth");
                brapiConfig = brapiAuthButton.getParent();
                if (brapiAuthButton != null) {
                    String brapiToken = prefMgr.getSharedPreferences().getString(PreferencesActivity.BRAPI_TOKEN, null);
                    String brapiHost = prefMgr.getSharedPreferences().getString(PreferencesActivity.BRAPI_BASE_URL, null);

                    brapiAuthButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            String brapiHost = preference.getSharedPreferences().getString(PreferencesActivity.BRAPI_BASE_URL, null);
                            if (brapiHost != null) {
                                BrAPIService.authorizeBrAPI(preference.getSharedPreferences(), prefActivity, null);
                            }
                            return true;
                        }
                    });

                    if(brapiHost != null && !brapiHost.equals(getString(R.string.brapi_base_url_default))) {
                        brapiAuthButton.setTitle(R.string.brapi_authorize);
                        brapiAuthButton.setSummary(null);
                        if (brapiToken != null) {
                            brapiAuthButton.setTitle(R.string.brapi_reauthorize);
                            brapiAuthButton.setSummary(getString(R.string.brapi_btn_auth_summary, brapiHost));
                        }
                    } else {
                        brapiConfig.removePreference(brapiAuthButton);
                        brapiConfig.removePreference(brapiLogoutButton);
                    }
                }

                if (brapiLogoutButton != null) {
                    brapiLogoutButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            SharedPreferences preferences = getSharedPreferences("Settings", 0);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString(PreferencesActivity.BRAPI_TOKEN, null);
                            editor.apply();

                            brapiAuthButton.setTitle(R.string.brapi_authorize);
                            brapiAuthButton.setSummary(null);

                            brapiConfig.removePreference(brapiLogoutButton);
                            return true;
                        }
                    });
                }

            }
        }, false);
    }

}