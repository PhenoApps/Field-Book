package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.BrapiAuthActivity;
import com.fieldbook.tracker.activities.ConfigActivity;
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
    private EditTextPreference brapiURLPreference;
    private EditTextPreference brapiOIDCURLPreference;
    private ListPreference brapiOIDCFlow;
    private Preference brapiServerBarcode;
    private Preference brapiServerCassavabase;
    private Preference brapiServerDefaultTest;
    private String barcodeResult;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Occurs before onCreate function. We get the context this way.
        BrapiPreferencesFragment.this.context = context;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");

        setPreferencesFromResource(R.xml.preferences_brapi, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.brapi_info_title));

        brapiPrefCategory = prefMgr.findPreference("brapi_category");
        brapiAuthButton = findPreference("authorizeBrapi");
        brapiLogoutButton = findPreference("revokeBrapiAuth");

        brapiURLPreference = findPreference(GeneralKeys.BRAPI_BASE_URL);
        brapiURLPreference.setOnPreferenceChangeListener(this);
        brapiOIDCURLPreference = findPreference(GeneralKeys.BRAPI_OIDC_URL);
        brapiOIDCFlow = findPreference(GeneralKeys.BRAPI_OIDC_FLOW);

        brapiServerBarcode = findPreference("brapi_server_barcode");
        brapiServerCassavabase = findPreference("brapi_server_cassavabase");
        brapiServerDefaultTest = findPreference("brapi_server_default");

        registerBrapiButtonListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        setBaseURLSummary();
        setButtonView();
    }

    private void setBaseURLSummary() {
        String url = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, null);
        brapiURLPreference.setSummary(url);
    }

    private void brapiAuth() {
        String brapiHost = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, null);
        if (brapiHost != null) {
            Intent intent = new Intent();
            intent.setClassName(context, BrapiAuthActivity.class.getName());
            startActivity(intent);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (preference.equals(brapiURLPreference)) {

            // This is done after this function, but set the value for our brapi function
            SharedPreferences sp = prefMgr.getSharedPreferences();
            String oldBaseUrl = sp.getString(GeneralKeys.BRAPI_BASE_URL, "");
            String oldOidcUrl = sp.getString(GeneralKeys.BRAPI_OIDC_URL, "");
            String newOidcUrl = oldOidcUrl.replaceFirst(oldBaseUrl, newValue.toString());
            this.brapiURLPreference.setText(newValue.toString());
            this.brapiOIDCURLPreference.setText(newOidcUrl);

            // Call our brapi authorize function
            if (brapiPrefCategory != null) {
                brapiAuth();
                // Set our button visibility and text
                setButtonView();
            }
        }

        return true;
    }

    private void registerBrapiButtonListeners() {

        if (brapiAuthButton != null) {
            brapiAuthButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    String brapiHost = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, null);
                    if (brapiHost != null) {
                        brapiAuth();
                    }
                    return true;
                }
            });
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

        if (brapiServerBarcode != null) {
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

        if (brapiServerCassavabase != null) {
            brapiServerCassavabase.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    brapiURLPreference.setText("https://www.cassavabase.org");
                    brapiOIDCURLPreference.setText("https://www.cassavabase.org/.well-known/openid-configuration");
                    brapiOIDCFlow.setValue(getString(R.string.preferences_brapi_oidc_flow_old_custom));

                    brapiAuth();
                    setBaseURLSummary();
                    return true;
                }
            });
        }

        if (brapiServerDefaultTest != null) {
            brapiServerDefaultTest.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    brapiURLPreference.setText(getString(R.string.brapi_base_url_default));
                    brapiOIDCURLPreference.setText(getString(R.string.brapi_oidc_url_default));
                    brapiOIDCFlow.setValue(getString(R.string.preferences_brapi_oidc_flow_oauth_implicit));

                    brapiAuth();
                    setBaseURLSummary();
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