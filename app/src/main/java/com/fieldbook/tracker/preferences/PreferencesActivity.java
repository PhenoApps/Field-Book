package com.fieldbook.tracker.preferences;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.MenuItem;

import com.fieldbook.tracker.R;

public class PreferencesActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener{

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


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(getSupportActionBar() != null){
            getSupportActionBar().setTitle(getString(R.string.settings_advanced));
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PreferencesFragment())
                .commit();

        checkBrapiAuth();
        registerSharedPreferencesListener();
    }

    private void registerSharedPreferencesListener() {
        getSharedPreferences("Settings", 0).unregisterOnSharedPreferenceChangeListener(this);
        getSharedPreferences("Settings", 0).registerOnSharedPreferenceChangeListener(this);
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
    protected void onResume() {
        super.onResume();

        checkBrapiAuth();
        registerSharedPreferencesListener();
    }

    private void checkBrapiAuth() {
        Uri data = this.getIntent().getData();
        if (data != null && data.isHierarchical()) {
            String uri = this.getIntent().getDataString();
            uri = uri.substring(uri.indexOf("://")+3);
            SharedPreferences preferences = getSharedPreferences("Settings", 0);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(PreferencesActivity.BRAPI_TOKEN, uri);
            editor.apply();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (BRAPI_BASE_URL.equals(key)) {
            try {
                String url = sharedPreferences.getString(PreferencesActivity.BRAPI_BASE_URL, "") + "/brapi/authorize?display_name=Field Book&success_url=fieldbook://";
                try {
                    Uri uri = Uri.parse("googlechrome://navigate?url="+ url);
                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (ActivityNotFoundException e) {
                    Uri uri = Uri.parse(url);
                    // Chrome is probably not installed
                    // OR not selected as default browser OR if no Browser is selected as default browser
                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
            } catch (Exception ex) {
                Log.e("BrAPI", "Error starting BrAPI auth", ex);
            }
        }
    }
}