package com.fieldbook.tracker.preferences;

import static android.app.Activity.RESULT_OK;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.DialogFragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.PreferencesActivity;
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.phenoapps.sharedpreferences.dialogs.NeutralButtonEditTextDialog;
import org.phenoapps.sharedpreferences.dialogs.NeutralButtonEditTextDialogFragmentCompat;

import java.util.Arrays;
import java.util.List;

/**
 * This preference fragment handles all BrAPI related shared preferences.
 *
 * If using oidc flow, changing the base url will change the oidc url to match. If the user
 * explicitly changes the oidc url, then the automatic change will not occur anymore.
 *
 * This will call the BrAPI Authentication activity to handle authentication s.a OIDC or basic.
 * Auth token is saved in the preferences, or set to null when logging out.
 */
public class BrapiPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    private static final String TAG = BrapiPreferencesFragment.class.getSimpleName();
    private static final int REQUEST_BARCODE_SCAN_BASE_URL = 99;
    private static final int REQUEST_BARCODE_SCAN_OIDC_URL = 98;
    private static final int AUTH_REQUEST_CODE = 123;
    private static final String DIALOG_FRAGMENT_TAG = "com.tracker.fieldbook.preferences.BRAPI_DIALOG_FRAGMENT";

    private Context context;
    private PreferenceManager prefMgr;
    private PreferenceCategory brapiPrefCategory;
    private Preference brapiLogoutButton;
    private Menu mMenu;
    private NeutralButtonEditTextDialog brapiURLPreference;
    private NeutralButtonEditTextDialog brapiDisplayName;
    private NeutralButtonEditTextDialog brapiOIDCURLPreference;
    private ListPreference brapiOIDCFlow;

    //old base url must be in memory now, since NeutralEditText preference updates preferences
    private String oldBaseUrl = "";

    //alert dialog displays messages when oidc or brapi urls have http
    private AlertDialog mBrapiHttpWarningDialog = null;

    @Override
    public void onAttach(@NonNull Context context) {
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
        prefMgr.setSharedPreferencesName(GeneralKeys.SHARED_PREF_FILE_NAME);
        SharedPreferences sp = prefMgr.getSharedPreferences();

        //remove old custom fb auth if it is being used
        if (sp.getString(GeneralKeys.BRAPI_OIDC_FLOW, getString(R.string.preferences_brapi_oidc_flow_oauth_implicit))
                .equals(getString(R.string.preferences_brapi_oidc_flow_old_custom))) {

            sp.edit().putString(GeneralKeys.BRAPI_OIDC_FLOW, getString(R.string.preferences_brapi_oidc_flow_oauth_implicit)).apply();

        }

        setPreferencesFromResource(R.xml.preferences_brapi, rootKey);

        // Show/hide preferences and category titles based on the BRAPI_ENABLED value
        CheckBoxPreference brapiEnabledPref = findPreference(GeneralKeys.BRAPI_ENABLED);
        if (brapiEnabledPref != null) {
            brapiEnabledPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean isChecked = (Boolean) newValue;
                    if (!isChecked) { // on disable, reset default sources if they were set to brapi
                        if (sp.getString(GeneralKeys.IMPORT_SOURCE_DEFAULT, "").equals("brapi")) {
                            sp.edit().putString(GeneralKeys.IMPORT_SOURCE_DEFAULT, "ask").apply();
                        }
                        if (sp.getString(GeneralKeys.EXPORT_SOURCE_DEFAULT, "").equals("brapi")) {
                            sp.edit().putString(GeneralKeys.EXPORT_SOURCE_DEFAULT, "ask").apply();
                        }
                        // remove brapi auth token when brapi is disabled
                        sp.edit().remove(GeneralKeys.BRAPI_TOKEN).apply();
                    }
                    updatePreferencesVisibility(isChecked);
                    return true;
                }
            });
            updatePreferencesVisibility(brapiEnabledPref.isChecked());
        }

        setupToolbar();
        setHasOptionsMenu(true);

        brapiPrefCategory = prefMgr.findPreference("brapi_category");
        brapiLogoutButton = findPreference("revokeBrapiAuth");

        brapiURLPreference = findPreference(GeneralKeys.BRAPI_BASE_URL);
        brapiDisplayName = findPreference(GeneralKeys.BRAPI_DISPLAY_NAME);
        if (brapiURLPreference != null) {
            brapiURLPreference.setOnPreferenceChangeListener(this);
        }
        if (brapiDisplayName != null) {
            brapiDisplayName.setOnPreferenceChangeListener(this);
        }

        brapiOIDCURLPreference = findPreference(GeneralKeys.BRAPI_OIDC_URL);
        brapiOIDCFlow = findPreference(GeneralKeys.BRAPI_OIDC_FLOW);
        if (brapiOIDCFlow != null) {
            brapiOIDCFlow.setOnPreferenceChangeListener(this);
        }

        //set saved urls, default to the test server
        String url = sp.getString(GeneralKeys.BRAPI_BASE_URL, getString(R.string.brapi_base_url_default));
        String displayName = sp.getString(GeneralKeys.BRAPI_DISPLAY_NAME, getString(R.string.preferences_brapi_server_test));
        String oidcUrl = sp.getString(GeneralKeys.BRAPI_OIDC_URL, getString(R.string.brapi_oidc_url_default));
        oldBaseUrl = url;
        brapiURLPreference.setText(url);
        brapiDisplayName.setText(displayName);
        brapiOIDCURLPreference.setText(oidcUrl);

        //set logout button
        if (brapiLogoutButton != null) {
            brapiLogoutButton.setOnPreferenceClickListener(preference -> {
                SharedPreferences preferences = context
                        .getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE);

                // Clear our brapi token
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(GeneralKeys.BRAPI_TOKEN, null);
                editor.apply();

                // Set our button visibility and text
                setButtonView();

                return true;
            });
        }

        //set barcode click listener to start zxing intent
        Preference brapiServerBarcode = findPreference("brapi_server_barcode");
        if (brapiServerBarcode != null) {
            brapiServerBarcode.setOnPreferenceClickListener(preference -> {

                new IntentIntegrator(getActivity())
                        .setPrompt(getString(R.string.barcode_scanner_text))
                        .setBeepEnabled(true)
                        .setRequestCode(IntentIntegrator.REQUEST_CODE)
                        .initiateScan();
                return true;
            });
        }

        setOidcFlowUi();
    }

    private void updatePreferencesVisibility(boolean isChecked) {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        for (int i = 0; i < preferenceScreen.getPreferenceCount(); i++) {
            Preference preferenceItem = preferenceScreen.getPreference(i);
            if (preferenceItem.getKey().equals(GeneralKeys.BRAPI_ENABLED)) { // Skip the checkbox preference itself
                continue;
            }
            preferenceItem.setVisible(isChecked);
        }

        // Also show/hide the BrAPI toolbar authentication option
        if (mMenu != null) {
            MenuItem brapiPrefAuthItem = mMenu.findItem(R.id.action_menu_brapi_pref_auth);
            if (brapiPrefAuthItem != null) {
                brapiPrefAuthItem.setVisible(isChecked);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_brapi_pref, menu);
        mMenu = menu; // Store a reference to the menu
        CheckBoxPreference brapiEnabledPref = findPreference(GeneralKeys.BRAPI_ENABLED);
        updatePreferencesVisibility(brapiEnabledPref.isChecked());
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_menu_brapi_pref_auth) {
            brapiAuth();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        setBaseURLSummary();
        setButtonView();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mBrapiHttpWarningDialog.isShowing()) mBrapiHttpWarningDialog.cancel();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (preference.equals(brapiURLPreference)) {
            updateUrls(newValue.toString());

        }

        if (preference.equals(brapiDisplayName)) {
            brapiDisplayName.setSummary(newValue.toString());

        }

        if (preference.equals(brapiOIDCFlow)) {

            prefMgr.getSharedPreferences().edit().putString(GeneralKeys.BRAPI_OIDC_FLOW, (String) newValue).apply();

            setOidcFlowUi();
        }

        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        SharedPreferences.Editor editor = prefMgr.getSharedPreferences().edit();
        switch (preference.getKey()){
            case "brapi_server_cassavabase":
                editor.putBoolean(GeneralKeys.BRAPI_EXPLICIT_OIDC_URL, false).apply();
                setServer("https://www.cassavabase.org",
                        getString(R.string.preferences_brapi_server_cassavabase),
                        "https://www.cassavabase.org/.well-known/openid-configuration",
                        getString(R.string.preferences_brapi_oidc_flow_oauth_implicit));
                break;
            case "brapi_server_t3_wheat":
                editor.putBoolean(GeneralKeys.BRAPI_EXPLICIT_OIDC_URL, false).apply();
                setServer("https://wheat-sandbox.triticeaetoolbox.org",
                        getString(R.string.preferences_brapi_server_t3_wheat),
                        "https://wheat-sandbox.triticeaetoolbox.org/.well-known/openid-configuration",
                        getString(R.string.preferences_brapi_oidc_flow_oauth_implicit));
                break;
            case "brapi_server_t3_oat":
                editor.putBoolean(GeneralKeys.BRAPI_EXPLICIT_OIDC_URL, false).apply();
                setServer("https://oat-sandbox.triticeaetoolbox.org",
                        getString(R.string.preferences_brapi_server_t3_oat),
                        "https://oat-sandbox.triticeaetoolbox.org/.well-known/openid-configuration",
                        getString(R.string.preferences_brapi_oidc_flow_oauth_implicit));
                break;
            case "brapi_server_t3_barley":
                editor.putBoolean(GeneralKeys.BRAPI_EXPLICIT_OIDC_URL, false).apply();
                setServer("https://barley-sandbox.triticeaetoolbox.org",
                        getString(R.string.preferences_brapi_server_t3_barley),
                        "https://barley-sandbox.triticeaetoolbox.org/.well-known/openid-configuration",
                        getString(R.string.preferences_brapi_oidc_flow_oauth_implicit));
                break;
            case "brapi_server_default":
                editor.putBoolean(GeneralKeys.BRAPI_EXPLICIT_OIDC_URL, false).apply();
                setServer(getString(R.string.brapi_base_url_default),
                        getString(R.string.preferences_brapi_server_test),
                        getString(R.string.brapi_oidc_url_default),
                        getString(R.string.preferences_brapi_oidc_flow_oauth_implicit));
                break;
        }

        return super.onPreferenceTreeClick(preference);
    }

    /**
     * This is required to use a dialog as a preference, used to add a barcode scan button to the
     * url preferences utilizing the NeutralButtonEditTextDialog
     * @param preference this is the base url or the oidc url preferences
     */
    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        // check if dialog is already showing
        if (getFragmentManager() != null && getFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
            return;
        }

        final DialogFragment f;

        if (preference instanceof NeutralButtonEditTextDialog) {

            /*
             * Takes three callbacks, on neutral (barcode scan), positive (save to prefs), negative (do nothing)
             */
            f = NeutralButtonEditTextDialogFragmentCompat.Companion.newInstance(preference.getKey(), (dialog, text) -> {

                //neutral edit text callback
                //change request code for brapi url vs oidc url
                if (preference.getKey().equals(brapiURLPreference.getKey())) {
                    new IntentIntegrator(getActivity())
                            .setPrompt(getString(R.string.barcode_scanner_text))
                            .setBeepEnabled(false)
                            .setRequestCode(REQUEST_BARCODE_SCAN_BASE_URL)
                            .initiateScan();
                } else if (preference.getKey().equals(brapiDisplayName.getKey())) {
                    text = oldBaseUrl.replaceAll("https?://(?:www\\.)?(.*?)(?:/.*)?$", "$1");
                    brapiDisplayName.setText(text);
                    onPreferenceChange(brapiDisplayName, text);
                } else {
                    prefMgr.getSharedPreferences().edit().putBoolean(GeneralKeys.BRAPI_EXPLICIT_OIDC_URL, true).apply();
                    new IntentIntegrator(getActivity())
                            .setPrompt(getString(R.string.barcode_scanner_text))
                            .setBeepEnabled(false)
                            .setRequestCode(REQUEST_BARCODE_SCAN_OIDC_URL)
                            .initiateScan();
                }

                if (dialog != null)
                    dialog.dismiss();

                return null;
            }, (dialog, text) -> {

                //positive edit text callback
                if (preference.getKey().equals(brapiURLPreference.getKey())) {
                    brapiURLPreference.setText(text);
                    if (!oldBaseUrl.equals(text)) { // skip updates if url hasn't actually changed
                        onPreferenceChange(brapiURLPreference, text);
                    }
                    brapiAuth();

                } else if (preference.getKey().equals(brapiDisplayName.getKey())) {
                    text = (text == null || text.isEmpty()) ? getString(R.string.export_source_brapi) : text;
                    brapiDisplayName.setText(text);
                    onPreferenceChange(brapiDisplayName, text);
                } else {
                    prefMgr.getSharedPreferences().edit().putBoolean(GeneralKeys.BRAPI_EXPLICIT_OIDC_URL, true).apply();
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

    private void setupToolbar() {
        Activity act = (PreferencesActivity) getActivity();
        if (act != null) {
            ActionBar bar = ((PreferencesActivity) this.getActivity()).getSupportActionBar();
            if (bar != null) {
                bar.setTitle(getString(R.string.brapi_info_title));
            }
        }
    }

    private void setBaseURLSummary() {
        String url = prefMgr.getSharedPreferences().getString(GeneralKeys.BRAPI_BASE_URL, "https://test-server.brapi.org");
        String displayName = prefMgr.getSharedPreferences().getString(GeneralKeys.BRAPI_DISPLAY_NAME, getString(R.string.preferences_brapi_server_test));
        brapiURLPreference.setSummary(url);
        brapiDisplayName.setSummary(displayName);
    }

    //should only be called from brapi auth or the warning dialog
    private void startAuth() {
        String brapiHost = prefMgr.getSharedPreferences().getString(GeneralKeys.BRAPI_BASE_URL, null);
        if (brapiHost != null) {
            Intent intent = new Intent();
            intent.setClassName(context, BrapiAuthActivity.class.getName());
            startActivityForResult(intent, AUTH_REQUEST_CODE);
        }
    }

    //checks the uri scheme, uris without http/https causes auth to crash
    private void brapiAuth() {

        if (brapiOIDCURLPreference.getText().startsWith("http://")
            || brapiURLPreference.getText().startsWith("http://")) {

                if (mBrapiHttpWarningDialog != null
                    && !mBrapiHttpWarningDialog.isShowing()) {
                    mBrapiHttpWarningDialog.show();
                }

        } else if (brapiOIDCURLPreference.getText().startsWith("https://")
            && brapiURLPreference.getText().startsWith("https://")) {

            //without this check, urls without https:// or http:// were causing brapi auth to crash
            startAuth();

        }
    }

    // shows a dialog to set newly authorized brapi server as the default import/export option
    private void displaySuccessDialog() {
        final String[] options = new String[]{
                getString(R.string.brapi_choice_to_make_default_import),
                getString(R.string.brapi_choice_to_make_default_export)
        };
        final boolean[] checkedOptions = new boolean[options.length];

        final List<String> selectedItems = Arrays.asList(options);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.brapi_choice_to_make_default_dialog_title);
        builder.setMultiChoiceItems(options, checkedOptions, (dialog, which, isChecked) -> {
            checkedOptions[which] = isChecked;
        });
        builder.setPositiveButton(getString(R.string.brapi_choice_to_make_default_positive), (dialog, which) -> {
            for (int i = 0; i < checkedOptions.length; i++) {
                if (checkedOptions[i]) {
                    if (selectedItems.get(i).equals(getString(R.string.brapi_choice_to_make_default_import))) {
                        prefMgr.getSharedPreferences().edit().putString(GeneralKeys.IMPORT_SOURCE_DEFAULT, "brapi").apply();
                    } else if (selectedItems.get(i).equals(getString(R.string.brapi_choice_to_make_default_export))) {
                        prefMgr.getSharedPreferences().edit().putString(GeneralKeys.EXPORT_SOURCE_DEFAULT, "brapi").apply();
                    }
                }
            }
        });
        builder.setNegativeButton(getString(R.string.brapi_choice_to_make_default_negative), (dialog, which) -> {});
        builder.create().show();
    }

    private void setServer(String url, String displayName, String oidcUrl, String oidcFlow) {
        oldBaseUrl = url;
        brapiURLPreference.setText(url);
        brapiDisplayName.setText(displayName);
        brapiOIDCURLPreference.setText(oidcUrl);
        prefMgr.getSharedPreferences().edit().putString(GeneralKeys.BRAPI_OIDC_URL, oidcUrl).apply();
        if(oidcFlow != null)
            brapiOIDCFlow.setValue(oidcFlow);

        setBaseURLSummary();
        setOidcFlowUi();
        brapiAuth();
    }

    /**
     * Check the explicit flag.
     * @param newValue the newly changed url
     */
    private void updateUrls(String newValue) {
        SharedPreferences sp = prefMgr.getSharedPreferences();
        String oldOidcUrl = sp.getString(GeneralKeys.BRAPI_OIDC_URL, "");

        // remove scheme and subdomain for initial display name
        String displayName = newValue.replaceAll("https?://(?:www\\.)?(.*?)(?:/.*)?$", "$1");

        Log.d(TAG, oldBaseUrl + " to " + oldOidcUrl);

        if (!sp.getBoolean(GeneralKeys.BRAPI_EXPLICIT_OIDC_URL, false)) {

            //regex replace old base within oidc to new value
            //this might lead to invalid urls if the user forgets a '/' and other cases
            //where oidc is explicitly changed first (fixed with preference flag)
            String newOidcUrl = oldOidcUrl.replaceFirst(oldBaseUrl, newValue);

            Log.d(TAG, newOidcUrl);

            setServer(newValue, displayName, newOidcUrl, null);
        }

        oldBaseUrl = newValue;

        setBaseURLSummary();
    }

    /**
     * Handles the UI state for authorize button.
     * When there is a valid host, the authorize button is visible.
     * If there is a token, the logout and reauthorize buttons appear.
     */
    public void setButtonView() {

        String brapiToken = context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
                .getString(GeneralKeys.BRAPI_TOKEN, null);
        String brapiHost = prefMgr.getSharedPreferences().getString(GeneralKeys.BRAPI_BASE_URL, null);

        if (brapiHost != null) {  // && !brapiHost.equals(getString(R.string.brapi_base_url_default))) {

            if (brapiToken != null) {
                // Show if our logout button if it is not shown already
                brapiPrefCategory.addPreference(brapiLogoutButton);
            } else {
                brapiPrefCategory.removePreference(brapiLogoutButton);
            }

        } else {
            brapiPrefCategory.removePreference(brapiLogoutButton);
        }
    }

    private void setOidcFlowUi() {

        PreferenceCategory preferenceCategory = findPreference("brapi_oidc_settings");

        if (preferenceCategory != null) {

            if(!prefMgr.getSharedPreferences().getString(GeneralKeys.BRAPI_OIDC_FLOW, getString(R.string.preferences_brapi_oidc_flow_oauth_implicit))
                    .equals(getString(R.string.preferences_brapi_oidc_flow_old_custom))
            ) {

                preferenceCategory.addPreference(brapiOIDCURLPreference);

            } else {

                preferenceCategory.removePreference(brapiOIDCURLPreference);

            }
        }

        if (brapiOIDCFlow != null) {

            Log.d(TAG, brapiOIDCFlow.getValue());

        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case IntentIntegrator.REQUEST_CODE:
                IntentResult urlDataResult = IntentIntegrator.parseActivityResult(resultCode, data);
                String barcodeResult = urlDataResult.getContents();

                SharedPreferences.Editor editor = prefMgr.getSharedPreferences().edit();
                editor.putString(GeneralKeys.BRAPI_BASE_URL, barcodeResult);
                editor.apply();

                brapiAuth();
                break;
            case REQUEST_BARCODE_SCAN_OIDC_URL: //barcode scan result for oidc url
                if(resultCode == RESULT_OK) {
                    IntentResult plotDataResult = IntentIntegrator.parseActivityResult(resultCode, data);
                    String scannedBarcode = plotDataResult.getContents();
                    brapiOIDCURLPreference.setText(scannedBarcode);
                }
                break;
            case REQUEST_BARCODE_SCAN_BASE_URL: //barcode scan result for brapi url
                if(resultCode == RESULT_OK) {
                    IntentResult plotDataResult = IntentIntegrator.parseActivityResult(resultCode, data);
                    oldBaseUrl = brapiURLPreference.getText();
                    String scannedBarcode = plotDataResult.getContents();
                    updateUrls(scannedBarcode);
                }
                break;
            case AUTH_REQUEST_CODE: // Add your new request code here
                if (resultCode == RESULT_OK) {
                    displaySuccessDialog();
                }
                break;
        }
    }
}