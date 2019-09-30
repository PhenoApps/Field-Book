package com.fieldbook.tracker.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.BrAPIService;


public class PreferencesFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {

    PreferenceManager prefMgr;
    Context context;
    PreferenceCategory brapiPrefCategory;
    private Preference brapiAuthButton;
    private Preference brapiLogoutButton;
    private Preference brapiURLPreference;

    public static String BRAPI_BASE_URL = "BRAPI_BASE_URL";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");
        SharedPreferences sharedPreferences;
        addPreferencesFromResource(R.xml.preferences);
        brapiPrefCategory = (PreferenceCategory) prefMgr.findPreference("brapi_category");
        brapiAuthButton = findPreference("authorizeBrapi");
        brapiLogoutButton = findPreference("revokeBrapiAuth");
        brapiURLPreference = findPreference("BRAPI_BASE_URL");

        brapiURLPreference.setOnPreferenceChangeListener(this);
        registerBrapiButtonListeners();
    }

    // Support for > API 23
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        PreferencesFragment.this.context = context;

    }

    // Support for < API 23
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Occurs before the on create function. We get the context this way.
        PreferencesFragment.this.context = activity;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (preference.equals(brapiURLPreference)) {

            // This is done after this function, but set the value for our brapi function
            SharedPreferences.Editor editor = prefMgr.getSharedPreferences().edit();
            editor.putString(PreferencesActivity.BRAPI_BASE_URL, newValue.toString());
            editor.apply();

            // Call our brapi authorize function
            if (brapiPrefCategory != null) {
                brapiPrefCategory.addPreference(brapiAuthButton);
                brapiPrefCategory.addPreference(brapiLogoutButton);
                BrAPIService.authorizeBrAPI(prefMgr.getSharedPreferences(), context, null);
            }
        }

        return true;
    }

    private void registerBrapiButtonListeners() {

        if (brapiAuthButton != null) {
            String brapiToken = prefMgr.getSharedPreferences().getString(PreferencesActivity.BRAPI_TOKEN, null);
            String brapiHost = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, null);

            brapiAuthButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    String brapiHost = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, null);
                    if (brapiHost != null) {
                        BrAPIService.authorizeBrAPI(prefMgr.getSharedPreferences(), PreferencesFragment.this.context, null);
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
                brapiPrefCategory.removePreference(brapiAuthButton);
                brapiPrefCategory.removePreference(brapiLogoutButton);
            }
        }

        if (brapiLogoutButton != null) {
            brapiLogoutButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    SharedPreferences preferences = prefMgr.getSharedPreferences();

                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(PreferencesActivity.BRAPI_TOKEN, null);
                    editor.apply();

                    brapiAuthButton.setTitle(R.string.brapi_authorize);
                    brapiAuthButton.setSummary(null);


                    brapiPrefCategory.removePreference(brapiLogoutButton);
                    return true;
                }
            });
        }
    }
}
