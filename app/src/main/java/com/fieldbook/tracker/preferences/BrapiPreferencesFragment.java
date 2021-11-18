package com.fieldbook.tracker.preferences;

import static android.app.Activity.RESULT_OK;
import android.app.AlertDialog;
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
import androidx.fragment.app.DialogFragment;
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
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.traits.BaseTraitLayout;
import com.fieldbook.tracker.utilities.Utils;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.phenoapps.sharedpreferences.dialogs.NeutralButtonEditTextDialog;
import org.phenoapps.sharedpreferences.dialogs.NeutralButtonEditTextDialogFragmentCompat;

public class BrapiPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    private static final String DIALOG_FRAGMENT_TAG = "com.tracker.fieldbook.preferences.BRAPI_DIALOG_FRAGMENT";

    private static final String BRAPI_BASE_URL = "BRAPI_BASE_URL";
    private Context context;
    private PreferenceManager prefMgr;
    private PreferenceCategory brapiPrefCategory;
    private Preference brapiAuthButton;
    private Preference brapiLogoutButton;
    private NeutralButtonEditTextDialog brapiURLPreference;
    private NeutralButtonEditTextDialog brapiOIDCURLPreference;
    private ListPreference brapiOIDCFlow;
    private Preference brapiServerBarcode;
    private Preference brapiServerCassavabase;
    private Preference brapiServerT3wheat;
    private Preference brapiServerT3oat;
    private Preference brapiServerT3barley;
    private Preference brapiServerDefaultTest;
    private String barcodeResult;

    //alert dialog displays messages when oidc or brapi urls have http
    private AlertDialog mBrapiHttpWarningDialog = null;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Occurs before onCreate function. We get the context this way.
        BrapiPreferencesFragment.this.context = context;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        mBrapiHttpWarningDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.act_brapi_auth_http_warning_title)
                .setMessage(R.string.act_brapi_auth_http_warning_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    startAuth();
                    dialog.dismiss();
                }).create();

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
        brapiOIDCFlow.setOnPreferenceChangeListener(this);

        String url = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, getString(R.string.brapi_base_url_default));
        String oidcUrl = prefMgr.getSharedPreferences().getString(GeneralKeys.BRAPI_OIDC_URL, getString(R.string.brapi_oidc_url_default));
        brapiURLPreference.setText(url);
        brapiOIDCURLPreference.setText(oidcUrl);

//        EditTextPreference brapiPaginationPreference = (EditTextPreference) findPreference(GeneralKeys.BRAPI_PAGE_SIZE);
//        brapiPaginationPreference.setSummary(prefMgr.getSharedPreferences().getString(GeneralKeys.BRAPI_PAGE_SIZE, "1000"));
//        brapiPaginationPreference.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
//            @Override
//            public void onBindEditText(@NonNull EditText editText) {
//                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
//            }
//        });
//
//        EditTextPreference brapiTimeoutPreference = findPreference(GeneralKeys.BRAPI_TIMEOUT);
//        brapiTimeoutPreference.setSummary(prefMgr.getSharedPreferences().getString(GeneralKeys.BRAPI_TIMEOUT, "120"));
//        brapiTimeoutPreference.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
//            @Override
//            public void onBindEditText(@NonNull EditText editText) {
//                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
//            }
//        });

        brapiServerBarcode = findPreference("brapi_server_barcode");
        brapiServerCassavabase = findPreference("brapi_server_cassavabase");
        brapiServerT3wheat = findPreference("brapi_server_t3_wheat");
        brapiServerT3oat = findPreference("brapi_server_t3_oat");
        brapiServerT3barley = findPreference("brapi_server_t3_barley");
        brapiServerDefaultTest = findPreference("brapi_server_default");

//        brapiPaginationPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
//            @Override
//            public boolean onPreferenceChange(Preference preference, Object newValue) {
//                brapiPaginationPreference.setSummary(newValue.toString());
//                return true;
//            }
//        });
//
//        brapiTimeoutPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
//            @Override
//            public boolean onPreferenceChange(Preference preference, Object newValue) {
//                brapiTimeoutPreference.setSummary(newValue.toString());
//                return true;
//            }
//        });

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
    }

    @Override
    public void onResume() {
        super.onResume();
        setBaseURLSummary();
        setButtonView();
    }

    private void setBaseURLSummary() {
        String url = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, "https://test-server.brapi.org");
        brapiURLPreference.setSummary(url);
    }
  
    @Override
    public void onPause() {
        super.onPause();
        if (mBrapiHttpWarningDialog.isShowing()) mBrapiHttpWarningDialog.cancel();
    }

    private void startAuth() {
        String brapiHost = prefMgr.getSharedPreferences().getString(BRAPI_BASE_URL, null);
        if (brapiHost != null) {
            Intent intent = new Intent();
            intent.setClassName(context, BrapiAuthActivity.class.getName());
            startActivity(intent);
        }
    }

    private void brapiAuth() {

        if (brapiOIDCURLPreference.getText().contains("http://")
            || brapiURLPreference.getText().contains("http://")) {

                if (mBrapiHttpWarningDialog != null
                    && !mBrapiHttpWarningDialog.isShowing()) {
                    mBrapiHttpWarningDialog.show();
                }

        } else startAuth();
    }

    private void setServer(String url, String oidcUrl, String oidcFlow) {
        brapiURLPreference.setText(url);
        brapiOIDCURLPreference.setText(oidcUrl);
        if(oidcFlow != null)
            brapiOIDCFlow.setValue(oidcFlow);

        setBaseURLSummary();
        brapiAuth();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        switch (preference.getKey()){
            case "brapi_server_cassavabase":
                setServer("https://www.cassavabase.org",
                        "https://www.cassavabase.org/.well-known/openid-configuration",
                        getString(R.string.preferences_brapi_oidc_flow_old_custom));
                break;
            case "brapi_server_t3_wheat":
                setServer("https://wheat-sandbox.triticeaetoolbox.org",
                        "https://wheat-sandbox.triticeaetoolbox.org/.well-known/openid-configuration",
                        getString(R.string.preferences_brapi_oidc_flow_old_custom));
                break;
            case "brapi_server_t3_oat":
                setServer("https://oat-sandbox.triticeaetoolbox.org",
                        "https://oat-sandbox.triticeaetoolbox.org/.well-known/openid-configuration",
                        getString(R.string.preferences_brapi_oidc_flow_old_custom));
                break;
            case "brapi_server_t3_barley":
                setServer("https://barley-sandbox.triticeaetoolbox.org",
                        "https://barley-sandbox.triticeaetoolbox.org/.well-known/openid-configuration",
                        getString(R.string.preferences_brapi_oidc_flow_old_custom));
                break;
            case "brapi_server_default":
                setServer(getString(R.string.brapi_base_url_default),
                        getString(R.string.brapi_oidc_url_default),
                        getString(R.string.preferences_brapi_oidc_flow_oauth_implicit));
                break;
        }

        return super.onPreferenceTreeClick(preference);
    }

    private void updateUrls(String newValue) {
        SharedPreferences sp = prefMgr.getSharedPreferences();
        String oldBaseUrl = sp.getString(GeneralKeys.BRAPI_BASE_URL, "");
        String oldOidcUrl = sp.getString(GeneralKeys.BRAPI_OIDC_URL, "");
        String newOidcUrl = oldOidcUrl.replaceFirst(oldBaseUrl, newValue);
        setServer(newValue, newOidcUrl, null);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.equals(brapiURLPreference)) {
            updateUrls(newValue.toString());
        }

        if (preference.equals(brapiOIDCFlow)) {
            PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference("brapi_oidc_settings");

            if(newValue.equals("OAuth2 Implicit Grant")) {
                preferenceCategory.addPreference(brapiOIDCURLPreference);
            } else {
                preferenceCategory.removePreference(brapiOIDCURLPreference);
            }

            Log.d("Field Book", brapiOIDCFlow.getValue());
        }

        return true;
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
            case 98: //barcode scan result for oidc url
                if(resultCode == RESULT_OK) {
                    IntentResult plotDataResult = IntentIntegrator.parseActivityResult(resultCode, data);
                    String scannedBarcode = plotDataResult.getContents();
                    brapiOIDCURLPreference.setText(scannedBarcode);
                }
                break;
            case 99: //barcode scan result for brapi url
                if(resultCode == RESULT_OK) {
                    IntentResult plotDataResult = IntentIntegrator.parseActivityResult(resultCode, data);
                    String scannedBarcode = plotDataResult.getContents();
                    updateUrls(scannedBarcode);
                }
                break;
        }
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        // check if dialog is already showing
        if (getFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
            return;
        }

        final DialogFragment f;

        if (preference instanceof NeutralButtonEditTextDialog) {

            f = NeutralButtonEditTextDialogFragmentCompat.Companion.newInstance(preference.getKey(), (dialog, text) -> {

                //neutral edit text callback
                //change request code for brapi url vs oidc url
                if (preference.getKey().equals(brapiURLPreference.getKey())) {
                    new IntentIntegrator(getActivity())
                            .setPrompt(getString(R.string.main_barcode_text))
                            .setBeepEnabled(false)
                            .setRequestCode(99)
                            .initiateScan();
                } else {
                    new IntentIntegrator(getActivity())
                            .setPrompt(getString(R.string.main_barcode_text))
                            .setBeepEnabled(false)
                            .setRequestCode(98)
                            .initiateScan();
                }

                if (dialog != null)
                    dialog.dismiss();

                return null;
            }, (dialog, text) -> {

                //positive edit text callback
                if (preference.getKey().equals(brapiURLPreference.getKey())) {
                    brapiURLPreference.setText(text);
                    brapiAuth();
                } else {
                    brapiOIDCURLPreference.setText(text);
                }

                if (dialog != null)
                    dialog.dismiss();

                return null;
            }, (dialog, text) -> {

                //negative edit text callback
                if (dialog != null)
                    dialog.dismiss();

                return null;
            });

        } else {
            f = null;
        }

        if (f != null) {
            f.setTargetFragment(this, 0);
            f.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }
}