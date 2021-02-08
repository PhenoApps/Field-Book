package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.BrapiControllerResponse;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class BrapiPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    private static final String BRAPI_BASE_URL = "BRAPI_BASE_URL";
    private Context context;
    private PreferenceManager prefMgr;
    private PreferenceCategory brapiPrefCategory;
    private Preference brapiAuthButton;
    private Preference brapiLogoutButton;
    private Preference brapiURLPreference;
    private EditTextPreference brapiPaginationPreference;
    private Preference brapiServerBarcode;
    private Preference brapiServerCassavabase;
    private Preference brapiServerDefaultTest;
    private BrapiControllerResponse brapiControllerResponse;
    private String barcodeResult;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");

        setPreferencesFromResource(R.xml.preferences_brapi, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.brapi_info_title));

        registerBrapiButtonListeners();

        brapiPrefCategory = prefMgr.findPreference("brapi_category");
        brapiAuthButton = findPreference("authorizeBrapi");
        brapiLogoutButton = findPreference("revokeBrapiAuth");

        brapiURLPreference = findPreference("BRAPI_BASE_URL");
        brapiURLPreference.setOnPreferenceChangeListener(this);
        brapiPaginationPreference = findPreference("BRAPI_PAGE_SIZE");

        brapiServerBarcode = findPreference("brapi_server_barcode");
        brapiServerCassavabase = findPreference("brapi_server_cassavabase");
        brapiServerDefaultTest = findPreference("brapi_server_default");

        setBaseURLSummary();
        setButtonView();

        brapiServerBarcode.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                new IntentIntegrator(getActivity())
                        .setPrompt(getString(R.string.main_barcode_text))
                        .setBeepEnabled(true)
                        .setRequestCode(101)
                        .initiateScan();

                return true;
            }
        });

        brapiServerCassavabase.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                SharedPreferences.Editor editor = prefMgr.getSharedPreferences().edit();
                editor.putString(GeneralKeys.BRAPI_BASE_URL, "https://www.cassavabase.org");
                editor.apply();

                brapiAuth();
                return true;
            }
        });

        brapiServerDefaultTest.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                SharedPreferences.Editor editor = prefMgr.getSharedPreferences().edit();
                editor.putString(GeneralKeys.BRAPI_BASE_URL, getString(R.string.brapi_base_url_default));
                editor.apply();

                setBaseURLSummary();
                return true;
            }
        });
    }

    private void setBaseURLSummary() {
        String url = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, null);
        brapiURLPreference.setSummary(url);
    }

    @Override
    public void onResume() {
        super.onResume();
        setBaseURLSummary();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (preference.equals(brapiURLPreference)) {

            // This is done after this function, but set the value for our brapi function
            SharedPreferences.Editor editor = prefMgr.getSharedPreferences().edit();
            editor.putString(GeneralKeys.BRAPI_BASE_URL, newValue.toString());
            editor.apply();

            // Call our brapi authorize function
            if (brapiPrefCategory != null) {

                // Start our login process
                BrapiControllerResponse brapiControllerResponse = BrAPIService.authorizeBrAPI(prefMgr.getSharedPreferences(), context, null);

                // Show our error message if it exists
                processResponseMessage(brapiControllerResponse);

                // Set our button visibility and text
                setButtonView();
            }
        }

        return true;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        BrapiPreferencesFragment.this.context = context;
    }

    private void processResponseMessage(BrapiControllerResponse brapiControllerResponse) {
        // Only show the error message
        if (brapiControllerResponse.status != null) {
            if (!brapiControllerResponse.status) {
                Toast.makeText(context, R.string.brapi_auth_error_starting, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void brapiAuth() {
        String brapiHost = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, null);
        if (brapiHost != null) {
            // Start our login process
            BrapiControllerResponse brapiControllerResponse = BrAPIService.authorizeBrAPI(prefMgr.getSharedPreferences(), context, null);

            // Show our error message if it exists
            processResponseMessage(brapiControllerResponse);
        }
    }

    private void registerBrapiButtonListeners() {

        if (brapiAuthButton != null) {
            brapiAuthButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    String brapiHost = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, null);
                    if (brapiHost != null) {
                        // Start our login process
                        BrapiControllerResponse brapiControllerResponse = BrAPIService.authorizeBrAPI(prefMgr.getSharedPreferences(), context, null);

                        // Show our error message if it exists
                        processResponseMessage(brapiControllerResponse);
                    }
                    return true;
                }
            });

            // Set our button visibility and text
            setButtonView();
        }

        if (brapiLogoutButton != null) {
            brapiLogoutButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    SharedPreferences preferences = prefMgr.getSharedPreferences();

                    // Clear our brapi token
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(GeneralKeys.BRAPI_TOKEN, null);
                    editor.apply();

                    // Set our button visibility and text
                    setButtonView();

                    return true;
                }
            });
        }
    }

    public void setButtonView() {
        String brapiToken = prefMgr.getSharedPreferences().getString(GeneralKeys.BRAPI_TOKEN, null);
        String brapiHost = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, null);

        if (brapiHost != null) {  // && !brapiHost.equals(getString(R.string.brapi_base_url_default))) {

            brapiPrefCategory.addPreference(brapiAuthButton);

            if (brapiToken != null) {
                // Show our reauthorize button and remove logout button
                brapiAuthButton.setTitle(R.string.brapi_reauthorize);
                brapiAuthButton.setSummary(getString(R.string.brapi_btn_auth_summary, brapiHost));
                // Show if our logout button if it is not shown already
                brapiPrefCategory.addPreference(brapiLogoutButton);
            } else {
                // Show authorize button and remove our logout button
                brapiAuthButton.setTitle(R.string.brapi_authorize);
                brapiAuthButton.setSummary(null);
                brapiPrefCategory.removePreference(brapiLogoutButton);
            }

        } else {
            brapiPrefCategory.removePreference(brapiAuthButton);
            brapiPrefCategory.removePreference(brapiLogoutButton);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 101:
                Log.d("Field Book","test");

                IntentResult urlDataResult = IntentIntegrator.parseActivityResult(resultCode, data);
                barcodeResult = urlDataResult.getContents();

                SharedPreferences.Editor editor = prefMgr.getSharedPreferences().edit();
                editor.putString(GeneralKeys.BRAPI_BASE_URL, barcodeResult);
                editor.apply();

                brapiAuth();
                break;
        }
    }
}