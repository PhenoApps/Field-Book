package com.fieldbook.tracker.preferences;

import static android.app.Activity.RESULT_OK;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowMetrics;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.PreferencesActivity;
import com.fieldbook.tracker.activities.ScannerActivity;
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity;
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache;
import com.fieldbook.tracker.objects.BrAPIConfig;
import com.fieldbook.tracker.utilities.JsonUtil;
import com.fieldbook.tracker.utilities.OpenAuthConfigurationUtil;
import com.fieldbook.tracker.utilities.Utils;
import com.google.gson.Gson;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import net.openid.appauth.AuthorizationService;
import net.openid.appauth.EndSessionRequest;

import org.phenoapps.sharedpreferences.dialogs.NeutralButtonEditTextDialog;
import org.phenoapps.sharedpreferences.dialogs.NeutralButtonEditTextDialogFragmentCompat;

import java.util.Arrays;
import java.util.List;
import java.net.URL;
import java.net.MalformedURLException;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * This preference fragment handles all BrAPI related shared preferences.
 * <p>
 * If using oidc flow, changing the base url will change the oidc url to match. If the user
 * explicitly changes the oidc url, then the automatic change will not occur anymore.
 * <p>
 * This will call the BrAPI Authentication activity to handle authentication s.a OIDC or basic.
 * Auth token is saved in the preferences, or set to null when logging out.
 */
@AndroidEntryPoint
public class BrapiPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    @Inject
    SharedPreferences preferences;

    @Inject
    OpenAuthConfigurationUtil authUtil;

    private static final String TAG = BrapiPreferencesFragment.class.getSimpleName();
    private static final int REQUEST_BARCODE_SCAN_BASE_URL = 99;
    private static final int REQUEST_BARCODE_SCAN_OIDC_URL = 98;
    private static final int REQUEST_BARCODE_SCAN_BRAPI_CONFIG = 97;
    private static final int AUTH_REQUEST_CODE = 123;
    private static final String DIALOG_FRAGMENT_TAG = "com.tracker.fieldbook.preferences.BRAPI_DIALOG_FRAGMENT";

    private Context context;
    private PreferenceCategory brapiServerPrefCategory;
    private Preference brapiLogoutButton;
    private Menu mMenu;
    private NeutralButtonEditTextDialog brapiURLPreference;
    private NeutralButtonEditTextDialog brapiDisplayName;
    private NeutralButtonEditTextDialog brapiOIDCURLPreference;
    private NeutralButtonEditTextDialog brapiClientIdPreference;

    private ListPreference brapiOIDCFlow;

    //old base url must be in memory now, since NeutralEditText preference updates preferences
    private String oldBaseUrl = "";

    //alert dialog displays messages when oidc or brapi urls have http
    private AlertDialog mBrapiHttpWarningDialog = null;
    private boolean mlkitEnabled;

    private Preference brapiServerInfoButton;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Occurs before onCreate function. We get the context this way.
        BrapiPreferencesFragment.this.context = context;

    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        mBrapiHttpWarningDialog = new AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(R.string.act_brapi_auth_http_warning_title)
                .setMessage(R.string.act_brapi_auth_http_warning_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    startAuth();
                    dialog.dismiss();
                }).create();

        //remove old custom fb auth if it is being used
        if (preferences.getString(PreferenceKeys.BRAPI_OIDC_FLOW, getString(R.string.preferences_brapi_oidc_flow_oauth_implicit))
                .equals(getString(R.string.preferences_brapi_oidc_flow_old_custom))) {

            preferences.edit().putString(PreferenceKeys.BRAPI_OIDC_FLOW, getString(R.string.preferences_brapi_oidc_flow_oauth_implicit)).apply();

        }

        setPreferencesFromResource(R.xml.preferences_brapi, rootKey);

        // Show/hide preferences and category titles based on the BRAPI_ENABLED value
        CheckBoxPreference brapiEnabledPref = findPreference(PreferenceKeys.BRAPI_ENABLED);
        if (brapiEnabledPref != null) {
            brapiEnabledPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean isChecked = (Boolean) newValue;
                    if (!isChecked) { // on disable, reset default sources if they were set to brapi
                        if (preferences.getString(PreferenceKeys.IMPORT_SOURCE_DEFAULT, "").equals("brapi")) {
                            preferences.edit().putString(PreferenceKeys.IMPORT_SOURCE_DEFAULT, "ask").apply();
                        }
                        if (preferences.getString(PreferenceKeys.EXPORT_SOURCE_DEFAULT, "").equals("brapi")) {
                            preferences.edit().putString(PreferenceKeys.EXPORT_SOURCE_DEFAULT, "ask").apply();
                        }
                        // remove brapi auth token when brapi is disabled
                        preferences.edit().remove(PreferenceKeys.BRAPI_TOKEN).apply();
                    }
                    updatePreferencesVisibility(isChecked);
                    return true;
                }
            });
            updatePreferencesVisibility(brapiEnabledPref.isChecked());
        }

        setupToolbar();
        setHasOptionsMenu(true);

        brapiServerPrefCategory = findPreference("brapi_server");
        brapiLogoutButton = findPreference("revokeBrapiAuth");

        brapiClientIdPreference = findPreference(PreferenceKeys.BRAPI_OIDC_CLIENT_ID);
        brapiURLPreference = findPreference(PreferenceKeys.BRAPI_BASE_URL);
        brapiDisplayName = findPreference(PreferenceKeys.BRAPI_DISPLAY_NAME);
        if (brapiURLPreference != null) {
            brapiURLPreference.setOnPreferenceChangeListener(this);
        }
        if (brapiDisplayName != null) {
            brapiDisplayName.setOnPreferenceChangeListener(this);
        }

        brapiOIDCURLPreference = findPreference(PreferenceKeys.BRAPI_OIDC_URL);
        brapiOIDCFlow = findPreference(PreferenceKeys.BRAPI_OIDC_FLOW);
        if (brapiOIDCFlow != null) {
            brapiOIDCFlow.setOnPreferenceChangeListener(this);
        }

        //set saved urls, default to the test server
        String url = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, getString(R.string.brapi_base_url_default));
        String displayName = preferences.getString(PreferenceKeys.BRAPI_DISPLAY_NAME, getString(R.string.brapi_edit_display_name_default));
        String oidcUrl = preferences.getString(PreferenceKeys.BRAPI_OIDC_URL, getString(R.string.brapi_oidc_url_default));
        oldBaseUrl = url;
        brapiURLPreference.setText(url);
        brapiDisplayName.setText(displayName);
        brapiOIDCURLPreference.setText(oidcUrl);

        //set logout button
        if (brapiLogoutButton != null) {
            brapiLogoutButton.setOnPreferenceClickListener(preference -> {

                String idToken = preferences.getString(PreferenceKeys.BRAPI_ID_TOKEN, null);

                if (idToken != null) {

                    Toast.makeText(context, R.string.logging_out_please_wait, Toast.LENGTH_SHORT).show();

                    authUtil.getAuthServiceConfiguration((config, ex) -> {

                        EndSessionRequest endSessionRequest =
                                new EndSessionRequest.Builder(config)
                                        .setIdTokenHint(idToken)
                                        .setPostLogoutRedirectUri(Uri.parse(BrapiAuthActivity.REDIRECT_URI))
                                        .build();

                        AuthorizationService authService = new AuthorizationService(context);
                        Intent endSessionIntent = authService.getEndSessionRequestIntent(endSessionRequest);
                        startActivityForResult(endSessionIntent, BrapiAuthActivity.END_SESSION_REQUEST_CODE);

                        return null;

                    });

                } else {

                    preferences.edit().remove(PreferenceKeys.BRAPI_TOKEN).apply();

                    setButtonView();

                }


                return true;
            });
        }

        //set barcode click listener to start zxing intent
        mlkitEnabled = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PreferenceKeys.MLKIT_PREFERENCE_KEY, false);
        Preference brapiServerBarcode = findPreference("brapi_server_barcode");
        if (brapiServerBarcode != null) {
            brapiServerBarcode.setOnPreferenceClickListener(preference -> {
                startBarcodeScan(IntentIntegrator.REQUEST_CODE);
                return true;
            });
        }
      
        //set barcode click listener to start zxing intent
        Preference brapiConfigBarcode = findPreference("brapi_config_barcode");
        if (brapiConfigBarcode != null) {
            brapiConfigBarcode.setOnPreferenceClickListener(preference -> {
                String title = getString(R.string.qr_code_share_choose_action_title);
                new AlertDialog.Builder(getContext(), R.style.AppAlertDialog)
                        .setTitle(title)
                        .setItems(new String[]{getString(R.string.preferences_brapi_barcode_config_scan), getString(R.string.preferences_brapi_barcode_config_share)}, (dialog, which) -> {
                            switch (which) {
                                case 0: // Scan QR Code to import settings
                                    startBarcodeScan(REQUEST_BARCODE_SCAN_BRAPI_CONFIG);
                                    break;
                                case 1: // Generate QR Code for sharing settings
                                    if (getActivity() != null) {
                                        generateQRCodeFromPreferences(getActivity());
                                    }
                                    break;
                            }
                        })
                        .show();
                return true;
            });
        }
        setOidcFlowUi();

        brapiServerInfoButton = findPreference("brapi_server_info");
        if (brapiServerInfoButton != null) {
            brapiServerInfoButton.setOnPreferenceClickListener(preference -> {
                checkServerInfo();
                return true;
            });
        }

    }

    private void barcodeAutoConfigure() {
        String title = getString(R.string.qr_code_share_choose_action_title);
        new AlertDialog.Builder(getContext(), R.style.AppAlertDialog)
            .setTitle(title)
            .setItems(new String[]{getString(R.string.preferences_brapi_barcode_config_scan), getString(R.string.preferences_brapi_barcode_config_share)}, (dialog, which) -> {
                switch (which) {
                    case 0: // Scan QR Code to import settings
                        startBarcodeScan(REQUEST_BARCODE_SCAN_BRAPI_CONFIG);
                        break;
                    case 1: // Generate QR Code for sharing settings
                        if (getActivity() != null) {
                            generateQRCodeFromPreferences(getActivity());
                        }
                        break;
                }
            })
            .show();
    }

    private void updatePreferencesVisibility(boolean isChecked) {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        for (int i = 0; i < preferenceScreen.getPreferenceCount(); i++) {
            Preference preferenceItem = preferenceScreen.getPreference(i);
            if (preferenceItem.getKey().equals(PreferenceKeys.BRAPI_ENABLED)) { // Skip the checkbox preference itself
                continue;
            }
            preferenceItem.setVisible(isChecked);
        }

        // Also show/hide the BrAPI toolbar authentication option
        if (mMenu != null) {
            MenuItem brapiAutoConfigureItem = mMenu.findItem(R.id.action_menu_brapi_auto_configure);
            if (brapiAutoConfigureItem != null) {
                brapiAutoConfigureItem.setVisible(isChecked);
            }
            MenuItem brapiPrefAuthItem = mMenu.findItem(R.id.action_menu_brapi_pref_auth);
            if (brapiPrefAuthItem != null) {
                brapiPrefAuthItem.setVisible(isChecked);
            }
        }
    }

    private void startBarcodeScan(int requestCode) {
        if (mlkitEnabled) {
            if (getActivity() != null) {
                ScannerActivity.Companion.requestCameraAndStartScanner(getActivity(), requestCode, null, null, null);
            }
        } else {
            new IntentIntegrator(getActivity())
                    .setPrompt(getString(R.string.barcode_scanner_text))
                    .setBeepEnabled(true)
                    .setRequestCode(requestCode)
                    .initiateScan();
        }
    }

    private void generateQRCodeFromPreferences(Activity act) {
        try {
            BrAPIConfig config = new BrAPIConfig();
            config.setUrl(preferences.getString(PreferenceKeys.BRAPI_BASE_URL, getString(R.string.brapi_base_url_default)));
            config.setName(preferences.getString(PreferenceKeys.BRAPI_DISPLAY_NAME, getString(R.string.brapi_edit_display_name_default)));
            config.setVersion(preferences.getString(PreferenceKeys.BRAPI_VERSION, "V2"));
            config.setPageSize(preferences.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50"));
            config.setChunkSize(preferences.getString(PreferenceKeys.BRAPI_CHUNK_SIZE, "500"));
            config.setServerTimeoutMilli(preferences.getString(PreferenceKeys.BRAPI_TIMEOUT, "120"));
            config.setAuthFlow(preferences.getString(PreferenceKeys.BRAPI_OIDC_FLOW, getString(R.string.preferences_brapi_oidc_flow_oauth_implicit)));
            config.setOidcUrl(preferences.getString(PreferenceKeys.BRAPI_OIDC_URL, getString(R.string.brapi_oidc_url_default)));
            config.setClientId(preferences.getString(PreferenceKeys.BRAPI_OIDC_CLIENT_ID, getString(R.string.brapi_oidc_clientid_default)));
            config.setScope(preferences.getString(PreferenceKeys.BRAPI_OIDC_SCOPE, getString(R.string.brapi_oidc_scope_default)));

            Gson gson = new Gson();
            String jsonConfig = gson.toJson(config);

            int screenWidth;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowMetrics windowMetrics = act.getWindowManager().getCurrentWindowMetrics();
                screenWidth = windowMetrics.getBounds().width();
            } else {
                DisplayMetrics displayMetrics = new DisplayMetrics();
                act.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                screenWidth = displayMetrics.widthPixels;
            }

            // Set the QR Code size to be 80% of the screen width
            int qrCodeSize = (int) (screenWidth * 0.8);

            BitMatrix bitMatrix = new MultiFormatWriter().encode(jsonConfig, BarcodeFormat.QR_CODE, qrCodeSize, qrCodeSize);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            ImageView imageView = new ImageView(getContext());
            imageView.setImageBitmap(bmp);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAdjustViewBounds(true);

            new AlertDialog.Builder(act, R.style.AppAlertDialog)
                    .setTitle(getString(R.string.preferences_brapi_barcode_config_dialog_title))
                    .setView(imageView)
                    .setPositiveButton(getString(R.string.dialog_close), null)
                    .show();

        } catch (WriterException e) {
            Toast.makeText(getContext(), "Error generating QR code", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_brapi_pref, menu);
        mMenu = menu; // Store a reference to the menu
        CheckBoxPreference brapiEnabledPref = findPreference(PreferenceKeys.BRAPI_ENABLED);
        updatePreferencesVisibility(brapiEnabledPref.isChecked());
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // Ensure the menu has been fully inflated before longpress listener setup
        getView().post(() -> setupLongPressListener());
    }

    private void setupLongPressListener() {
        final View menuItemView = getActivity().findViewById(R.id.action_menu_brapi_auto_configure);
        if (menuItemView != null) {
            menuItemView.setOnLongClickListener(v -> {
                showCommunityServerListDialog();
                return true;
            });
        }
    }

    private void showCommunityServerListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppAlertDialog);
        builder.setTitle(R.string.preferences_brapi_servers_title);

        String[] serverNames = getResources().getStringArray(R.array.community_servers_names);
        String[] serverUrls = getResources().getStringArray(R.array.community_servers_urls);
        String[] serverOidcUrls = getResources().getStringArray(R.array.community_servers_oidc_urls);
        String[] serverGrantTypes = getResources().getStringArray(R.array.community_servers_grant_types);

        // Add "Submit a server" option
        String[] extendedServerNames = new String[serverNames.length + 1];
        System.arraycopy(serverNames, 0, extendedServerNames, 0, serverNames.length);
        extendedServerNames[serverNames.length] = getString(R.string.preferences_brapi_server_add);

        builder.setItems(extendedServerNames, (dialog, which) -> {
            if (which == serverNames.length) {
                // Handle the "Submit a server" option
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/PhenoApps/Field-Book/issues/new?assignees=&labels=enhancement,feature+request&template=feature_request.md&title=[REQUEST]"));
                startActivity(browserIntent);
            } else {
                String selectedServerUrl = serverUrls[which];
                String selectedServerName = serverNames[which];
                String selectedOidcUrl = serverOidcUrls[which];
                String selectedGrantType = serverGrantTypes[which];
                setServer(selectedServerUrl, selectedServerName, selectedOidcUrl, selectedGrantType);
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_menu_brapi_auto_configure) {
            barcodeAutoConfigure();
            return true;
        }
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
        setupToolbar();
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

            setOidcFlowUi();
        }

        return true;
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
                    startBarcodeScan(REQUEST_BARCODE_SCAN_BASE_URL);
                } else if (preference.getKey().equals(brapiDisplayName.getKey())) {
                    text = oldBaseUrl.replaceAll("https?://(?:www\\.)?(.*?)(?:/.*)?$", "$1");
                    brapiDisplayName.setText(text);
                    onPreferenceChange(brapiDisplayName, text);
                } else {
                    preferences.edit().putBoolean(PreferenceKeys.BRAPI_EXPLICIT_OIDC_URL, true).apply();
                    startBarcodeScan(REQUEST_BARCODE_SCAN_OIDC_URL);
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
                } else if (preference.getKey().equals(brapiClientIdPreference.getKey())) {
                    //pass to ensure oidc isn't updated as well, client id is automatically handled by xml definition
                } else {
                    preferences.edit().putBoolean(PreferenceKeys.BRAPI_EXPLICIT_OIDC_URL, true).apply();
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
        Activity act = getActivity();
        if (act != null) {
            ActionBar bar = ((PreferencesActivity) this.getActivity()).getSupportActionBar();
            if (bar != null) {
                bar.setTitle(getString(R.string.brapi_info_title));
            }
        }
    }

    private void setBaseURLSummary() {
        String url = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "https://test-server.brapi.org");
        String displayName = preferences.getString(PreferenceKeys.BRAPI_DISPLAY_NAME, getString(R.string.brapi_edit_display_name_default));
        brapiURLPreference.setSummary(url);
        brapiDisplayName.setSummary(displayName);
    }

    //should only be called from brapi auth or the warning dialog
    private void startAuth() {
        String brapiHost = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, null);
        if (brapiHost != null) {
            Intent intent = new Intent();
            intent.setClassName(context, BrapiAuthActivity.class.getName());
            startActivityForResult(intent, AUTH_REQUEST_CODE);
        }
    }

    //checks the uri scheme, uris without http/https causes auth to crash
    private void brapiAuth() {

        BrapiFilterCache.Companion.delete(context, true);

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
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppAlertDialog);
        builder.setTitle(R.string.brapi_choice_to_make_default_dialog_title);
        builder.setMultiChoiceItems(options, checkedOptions, (dialog, which, isChecked) -> {
            checkedOptions[which] = isChecked;
        });
        builder.setPositiveButton(getString(R.string.brapi_choice_to_make_default_positive), (dialog, which) -> {
            for (int i = 0; i < checkedOptions.length; i++) {
                if (checkedOptions[i]) {
                    if (selectedItems.get(i).equals(getString(R.string.brapi_choice_to_make_default_import))) {
                        preferences.edit().putString(PreferenceKeys.IMPORT_SOURCE_DEFAULT, "brapi").apply();
                    } else if (selectedItems.get(i).equals(getString(R.string.brapi_choice_to_make_default_export))) {
                        preferences.edit().putString(PreferenceKeys.EXPORT_SOURCE_DEFAULT, "brapi").apply();
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
        preferences.edit().putString(PreferenceKeys.BRAPI_OIDC_URL, oidcUrl).apply();
        if(oidcFlow != null)
            brapiOIDCFlow.setValue(oidcFlow);

        setOidcFlowUi();
        brapiAuth();
    }

    /**
     * Check the explicit flag.
     * @param newValue the newly changed url
     */
    private void updateUrls(String newValue) {
        String oldOidcUrl = preferences.getString(PreferenceKeys.BRAPI_OIDC_URL, "");

        // remove scheme and subdomain for initial display name
        String displayName = newValue.replaceAll("https?://(?:www\\.)?(.*?)(?:/.*)?$", "$1");

        Log.d(TAG, oldBaseUrl + " to " + oldOidcUrl);

        if (!preferences.getBoolean(PreferenceKeys.BRAPI_EXPLICIT_OIDC_URL, false)) {

            //regex replace old base within oidc to new value
            //this might lead to invalid urls if the user forgets a '/' and other cases
            //where oidc is explicitly changed first (fixed with preference flag)
            String newOidcUrl = oldOidcUrl.replaceFirst(oldBaseUrl, newValue);

            Log.d(TAG, newOidcUrl);

            setServer(newValue, displayName, newOidcUrl, null);
        }

        oldBaseUrl = newValue;
    }

    /**
     * Handles the UI state for authorize button.
     * When there is a valid host, the authorize button is visible.
     * If there is a token, the logout and reauthorize buttons appear.
     */
    public void setButtonView() {

        String brapiToken = preferences.getString(PreferenceKeys.BRAPI_TOKEN, null);
        String brapiHost = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, null);

        if (brapiHost != null) {  // && !brapiHost.equals(getString(R.string.brapi_base_url_default))) {

            if (brapiToken != null) {
                // Show if our logout button if it is not shown already
                brapiServerPrefCategory.addPreference(brapiLogoutButton);
            } else {
                brapiServerPrefCategory.removePreference(brapiLogoutButton);
            }

        } else {
            brapiServerPrefCategory.removePreference(brapiLogoutButton);
        }
    }

    private void setOidcFlowUi() {

        PreferenceCategory preferenceCategory = findPreference("brapi_oidc_settings");

        if (preferenceCategory != null) {

            if (!preferences.getString(PreferenceKeys.BRAPI_OIDC_FLOW, getString(R.string.preferences_brapi_oidc_flow_oauth_implicit))
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

    private boolean isValidUrl(String url) {
        try {
            URL obj = new URL(url);
            return obj.getProtocol().equals("http") || obj.getProtocol().equals("https");
        } catch (MalformedURLException e) {
            Log.e(TAG, "Invalid URL: " + url, e);
            return false;
        }
    }

    private void handleScannedData(String scannedData) {
        if (JsonUtil.Companion.isJsonValid(scannedData)) {
            // Process as JSON config
            Log.d(TAG, "onActivityResult: config data received: " + scannedData);
            BrAPIConfig brAPIConfig = new Gson().fromJson(scannedData, BrAPIConfig.class);

            String brapiVersion = getString(R.string.preferences_brapi_version_v2);
            if("v1".equalsIgnoreCase(brAPIConfig.getVersion())) {
                brapiVersion = getString(R.string.preferences_brapi_version_v1);
            }
            ((ListPreference)findPreference(PreferenceKeys.BRAPI_VERSION)).setValue(brapiVersion);
            ((BetterEditTextPreference)findPreference(PreferenceKeys.BRAPI_PAGE_SIZE)).setText(brAPIConfig.getPageSize());
            ((BetterEditTextPreference)findPreference(PreferenceKeys.BRAPI_CHUNK_SIZE)).setText(brAPIConfig.getChunkSize());
            ((BetterEditTextPreference)findPreference(PreferenceKeys.BRAPI_TIMEOUT)).setText(brAPIConfig.getServerTimeoutMilli());

            String clientId = brAPIConfig.getClientId();
            String scope = brAPIConfig.getScope();

            if (clientId != null) {
                ((NeutralButtonEditTextDialog)findPreference(PreferenceKeys.BRAPI_OIDC_CLIENT_ID)).setText(clientId);
            }

            if (scope != null) {
                ((NeutralButtonEditTextDialog)findPreference(PreferenceKeys.BRAPI_OIDC_SCOPE)).setText(scope);
            }
            String oidcFlow = getString(R.string.preferences_brapi_oidc_flow_oauth_implicit);
            String codeFlow = getString(R.string.preferences_brapi_oidc_flow_oauth_code);
            if(codeFlow.equalsIgnoreCase(brAPIConfig.getAuthFlow())) {
                oidcFlow = getString(R.string.preferences_brapi_oidc_flow_oauth_code);
            }

            setServer(brAPIConfig.getUrl(), brAPIConfig.getName(), brAPIConfig.getOidcUrl(), oidcFlow);
        } else if (isValidUrl(scannedData)) {
            updateUrls(scannedData);
            brapiAuth();
        } else {
            // Neither JSON nor valid URL, show error message
            showErrorDialog("Scanned data "+scannedData+" is neither a valid URL nor a valid QR-encoded BrAPI config object.");
        }
    }

    private void showErrorDialog(String message) {
        new AlertDialog.Builder(this.getActivity(), R.style.AppAlertDialog)
                .setTitle(R.string.preferences_brapi_server_scan_error)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Check server information and log supported calls
     */
    private void checkServerInfo() {
        FragmentActivity act = getActivity();
        if (act != null) {
            if (Utils.isConnected(act)) {
                act.getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.prefs_container, new BrapiServerInfoFragment())
                        .addToBackStack(null)
                        .commit();
            } else {
                Toast.makeText(act, R.string.device_offline_warning, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            String scannedData = null;
            // Check if the result is from a barcode scan
            if (requestCode == REQUEST_BARCODE_SCAN_BASE_URL ||
                    requestCode == REQUEST_BARCODE_SCAN_OIDC_URL ||
                    requestCode == REQUEST_BARCODE_SCAN_BRAPI_CONFIG ||
                    requestCode == IntentIntegrator.REQUEST_CODE) {
                if (mlkitEnabled) {
                    scannedData = data.getStringExtra("barcode");
                } else {
                    IntentResult result = IntentIntegrator.parseActivityResult(resultCode, data);
                    scannedData = result.getContents();
                }
            } else if (requestCode == BrapiAuthActivity.END_SESSION_REQUEST_CODE) {
                preferences.edit().remove(PreferenceKeys.BRAPI_ID_TOKEN).apply();
                preferences.edit().remove(PreferenceKeys.BRAPI_TOKEN).apply();
                setButtonView();
            }

            switch (requestCode) {
                case REQUEST_BARCODE_SCAN_BASE_URL:
                    if (isValidUrl(scannedData)) {
                        updateUrls(scannedData);
                        brapiAuth();
                    } else {
                        showErrorDialog("Scanned data "+scannedData+" is not a valid URL.");
                    }
                    break;
                case REQUEST_BARCODE_SCAN_OIDC_URL:
                    brapiOIDCURLPreference.setText(scannedData);
                    break;
                case REQUEST_BARCODE_SCAN_BRAPI_CONFIG:
                case IntentIntegrator.REQUEST_CODE:
                    handleScannedData(scannedData);
                    break;
                case AUTH_REQUEST_CODE:
                    //displaySuccessDialog();
                    break;
            }
        }
    }
}