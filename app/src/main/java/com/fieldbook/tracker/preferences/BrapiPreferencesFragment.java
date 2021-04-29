package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.BrAPIService;
import com.fieldbook.tracker.brapi.BrapiControllerResponse;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class BrapiPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    private static String BRAPI_BASE_URL = "BRAPI_BASE_URL";
    private Context context;
    private PreferenceManager prefMgr;
    private PreferenceCategory brapiPrefCategory;
    private Preference brapiAuthButton;
    private Preference brapiLogoutButton;
    private EditTextPreference brapiURLPreference;
    private Preference brapiServerBarcode;
    private Preference brapiServerCassavabase;
    private Preference brapiServerT3wheat;
    private Preference brapiServerT3oat;
    private Preference brapiServerT3barley;
    private Preference brapiServerDefaultTest;
    private BrapiControllerResponse brapiControllerResponse;
    private String barcodeResult;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");

        setPreferencesFromResource(R.xml.preferences_brapi, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.brapi_info_title));

        brapiPrefCategory = prefMgr.findPreference("brapi_category");
        brapiAuthButton = findPreference("authorizeBrapi");
        brapiLogoutButton = findPreference("revokeBrapiAuth");

        brapiURLPreference = findPreference("BRAPI_BASE_URL");
        brapiURLPreference.setOnPreferenceChangeListener(this);

        EditTextPreference brapiPaginationPreference = (EditTextPreference) findPreference("BRAPI_PAGINATION");
        brapiPaginationPreference.setSummary(prefMgr.getSharedPreferences().getString("BRAPI_PAGINATION", "1000"));
        brapiPaginationPreference.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
        });

        EditTextPreference brapiTimeoutPreference = findPreference("BRAPI_TIMEOUT");
        brapiTimeoutPreference.setSummary(prefMgr.getSharedPreferences().getString("BRAPI_TIMEOUT", "120"));
        brapiTimeoutPreference.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull EditText editText) {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
        });

        brapiServerBarcode = findPreference("brapi_server_barcode");
        brapiServerCassavabase = findPreference("brapi_server_cassavabase");
        brapiServerT3wheat = findPreference("brapi_server_t3_wheat");
        brapiServerT3oat = findPreference("brapi_server_t3_oat");
        brapiServerT3barley = findPreference("brapi_server_t3_barley");
        brapiServerDefaultTest = findPreference("brapi_server_default");

        setBaseURLSummary();
        setButtonView();

        brapiPaginationPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                brapiPaginationPreference.setSummary(newValue.toString());
                return true;
            }
        });

        brapiTimeoutPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                brapiTimeoutPreference.setSummary(newValue.toString());
                return true;
            }
        });

        if (brapiAuthButton != null) {
            brapiAuthButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    brapiAuth();
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
    }

    private void setBaseURLSummary() {
        String url = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, null);
        brapiURLPreference.setSummary(url);
    }

    private void setServer(String url) {
        brapiURLPreference.setText(url);
        setBaseURLSummary();
        brapiAuth();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        switch (preference.getKey()){
            case "brapi_server_cassavabase":
                setServer("https://www.cassavabase.org");
                break;
            case "brapi_server_t3_wheat":
                setServer("https://wheat-sandbox.triticeaetoolbox.org");
                break;
            case "brapi_server_t3_oat":
                setServer("https://oat-sandbox.triticeaetoolbox.org");
                break;
            case "brapi_server_t3_barley":
                setServer("https://barley-sandbox.triticeaetoolbox.org");
                break;
            case "brapi_server_default":
                setServer(getString(R.string.brapi_base_url_default));
                break;
        }

        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onResume() {
        super.onResume();
        setBaseURLSummary();
        setButtonView();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.equals(brapiURLPreference)) {

            // This is done after this function, but set the value for our brapi function
            setServer(newValue.toString());

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