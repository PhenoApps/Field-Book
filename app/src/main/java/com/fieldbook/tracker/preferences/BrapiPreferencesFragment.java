package com.fieldbook.tracker.preferences;

import static android.app.Activity.RESULT_OK;

import android.accounts.Account;
import android.accounts.AccountManager;
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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowMetrics;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.PreferencesActivity;
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity;
import com.fieldbook.tracker.brapi.BrapiAuthenticator;
import com.fieldbook.tracker.brapi.dialogs.BrapiAddAccountDialogFragment;
import com.fieldbook.tracker.brapi.dialogs.BrapiManualAccountDialogFragment;
import com.fieldbook.tracker.objects.BrAPIConfig;
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache;
import com.fieldbook.tracker.utilities.BrapiAccountHelper;
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

import android.widget.ImageView;

import net.openid.appauth.AuthorizationService;
import net.openid.appauth.EndSessionRequest;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import kotlin.Unit;

/**
 * Multi-account BrAPI preferences screen.
 *
 * Displays:
 *  - Enable BrAPI checkbox
 *  - Add Account preference
 *  - Advanced Settings preference (navigates to BrapiAdvancedPreferencesFragment)
 *  - Active Server category (one BrapiServerCardPreference for the connected account)
 *  - Available Servers category (one BrapiServerCardPreference per additional account)
 *
 * Accounts are stored in Android AccountManager with type "org.phenoapps.brapi".
 * The active account URL is persisted in BRAPI_BASE_URL SharedPreference.
 */
@AndroidEntryPoint
public class BrapiPreferencesFragment extends PreferenceFragmentCompat {

    @Inject
    BrapiAccountHelper accountHelper;

    @Inject
    SharedPreferences preferences;

    @Inject
    OpenAuthConfigurationUtil authUtil;

    private static final String TAG = BrapiPreferencesFragment.class.getSimpleName();
    private static final int REQUEST_BARCODE_SCAN_CONFIG = 97;
    private static final int AUTH_REQUEST_CODE = 123;

    private Context context;
    private PreferenceCategory activeServerCategory;
    private PreferenceCategory availableServersCategory;
    private boolean mlkitEnabled;

    // Tracks which account triggered the auth flow for re-auth
    private Account pendingAuthAccount = null;

    // Tracks whether to remove the account after logout completes
    private boolean pendingRemoveAfterLogout = false;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_brapi, rootKey);

        mlkitEnabled = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean(PreferenceKeys.MLKIT_PREFERENCE_KEY, false);

        CheckBoxPreference brapiEnabledPref = findPreference(PreferenceKeys.BRAPI_ENABLED);
        if (brapiEnabledPref != null) {
            brapiEnabledPref.setOnPreferenceChangeListener((pref, newValue) -> {
                boolean enabled = (Boolean) newValue;
                if (!enabled) {
                    // Reset default import/export sources if they were pointing to brapi
                    if ("brapi".equals(preferences.getString(PreferenceKeys.IMPORT_SOURCE_DEFAULT, ""))) {
                        preferences.edit().putString(PreferenceKeys.IMPORT_SOURCE_DEFAULT, "ask").apply();
                    }
                    if ("brapi".equals(preferences.getString(PreferenceKeys.EXPORT_SOURCE_DEFAULT, ""))) {
                        preferences.edit().putString(PreferenceKeys.EXPORT_SOURCE_DEFAULT, "ask").apply();
                    }
                }
                updateServerSectionsVisibility(enabled);
                return true;
            });
        }

        Preference addAccountPref = findPreference("brapi_add_account");
        if (addAccountPref != null) {
            addAccountPref.setOnPreferenceClickListener(pref -> {
                showAddAccountDialog();
                return true;
            });
        }

        activeServerCategory = findPreference("brapi_active_server_category");
        availableServersCategory = findPreference("brapi_available_servers_category");

        // Refresh cards when an account edit is saved from BrapiManualAccountDialogFragment
        getParentFragmentManager().setFragmentResultListener(
                BrapiManualAccountDialogFragment.REQUEST_KEY_EDIT_SAVED,
                this,
                (key, result) -> refreshServerCards()
        );

        setupToolbar();
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        setupToolbar();
        boolean brapiEnabled = preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false);
        updateServerSectionsVisibility(brapiEnabled);
        refreshServerCards();
    }

    // ─── Account cards ──────────────────────────────────────────────────────────

    private void refreshServerCards() {
        if (activeServerCategory == null || availableServersCategory == null) return;

        activeServerCategory.removeAll();
        availableServersCategory.removeAll();

        String activeUrl = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "");

        List<Account> accounts = accountHelper.getAllAccounts();
        boolean hasActive = false;

        for (Account account : accounts) {
            AccountManager am = AccountManager.get(context);
            String serverUrl = am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL);
            boolean isActive = activeUrl.equals(serverUrl);

            BrapiServerCardPreference card = buildCard(account, isActive);

            if (isActive) {
                activeServerCategory.addPreference(card);
                hasActive = true;
            } else {
                availableServersCategory.addPreference(card);
            }
        }

        // Hide empty categories
        if (activeServerCategory != null) {
            activeServerCategory.setVisible(hasActive);
        }
        if (availableServersCategory != null) {
            availableServersCategory.setVisible(availableServersCategory.getPreferenceCount() > 0);
        }
    }

    private BrapiServerCardPreference buildCard(Account account, boolean isActive) {
        BrapiServerCardPreference card = new BrapiServerCardPreference(context);
        card.setAccount(account);
        card.setActive(isActive);
        card.setExpanded(isActive); // active card starts expanded; inactive cards start collapsed
        card.setKey(account.name + "_" + (isActive ? "active" : "available"));
        card.setOrder(0);

        card.setOnLogOut(this::logOutAccount);
        card.setOnAuthorize(this::authorizeAccount);
        card.setOnEnable(this::enableAccount); // still wired for backward compat
        card.setOnCheckCompatibility(this::checkServerCompatibility);
        card.setOnShareSettings(this::shareAccountSettings);
        card.setOnEdit(this::editAccount);
        card.setOnRemove(this::removeAccount);

        return card;
    }

    // ─── Account actions ────────────────────────────────────────────────────────

    private @NotNull Unit logOutAccount(Account account) {
        AccountManager am = AccountManager.get(context);
        String displayName = am.getUserData(account, BrapiAuthenticator.KEY_DISPLAY_NAME);
        if (displayName == null) displayName = account.name;
        final String finalDisplayName = displayName;

        new AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(R.string.brapi_revoke_auth)
                .setMessage(getString(R.string.brapi_logout_remove_message, finalDisplayName))
                .setPositiveButton(R.string.brapi_logout_and_remove, (d, w) -> {
                    pendingRemoveAfterLogout = true;
                    doLogout(account);
                })
                .setNeutralButton(R.string.brapi_logout_only, (d, w) -> {
                    pendingRemoveAfterLogout = false;
                    doLogout(account);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        return Unit.INSTANCE;
    }

    private void doLogout(Account account) {
        AccountManager am = AccountManager.get(context);
        String idToken = am.getUserData(account, BrapiAuthenticator.KEY_ID_TOKEN);

        if (idToken != null) {
            Toast.makeText(context, R.string.logging_out_please_wait, Toast.LENGTH_SHORT).show();
            pendingAuthAccount = account;
            String accountOidcUrl = am.getUserData(account, BrapiAuthenticator.KEY_OIDC_URL);
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
            }, accountOidcUrl);
        } else {
            String serverUrl = am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL);
            accountHelper.clearToken(serverUrl != null ? serverUrl : "");

            String activeUrl = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "");
            if (serverUrl != null && serverUrl.equals(activeUrl)) {
                preferences.edit().putString(PreferenceKeys.BRAPI_BASE_URL, "").apply();
                BrapiFilterCache.Companion.delete(context, true);
            }
            if (pendingRemoveAfterLogout) {
                pendingRemoveAfterLogout = false;
                if (serverUrl != null) {
                    accountHelper.removeAccount(serverUrl);
                }
            }
            refreshServerCards();
        }
    }

    private @NotNull Unit enableAccount(Account account) {
        AccountManager am = AccountManager.get(context);
        String serverUrl = am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL);
        if (serverUrl == null) serverUrl = account.name;
        accountHelper.setActiveAccount(serverUrl);
        // Also update legacy SharedPreference mirrors so BrAPIService continues to work
        syncActiveAccountPrefs(account);
        // Invalidate cached field/trait data so the new server's data is loaded fresh
        BrapiFilterCache.Companion.delete(context, true);
        refreshServerCards();
        return Unit.INSTANCE;
    }

    private @NotNull Unit authorizeAccount(Account account) {
        AccountManager am = AccountManager.get(context);
        String serverUrl = am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL);
        if (serverUrl == null) serverUrl = account.name;
        // Temporarily make this account active so BrapiAuthActivity can pick up its config
        syncActiveAccountPrefs(account);
        pendingAuthAccount = account;
        Intent authIntent = new Intent(context, BrapiAuthActivity.class);
        authIntent.putExtra(BrapiAuthActivity.EXTRA_SERVER_URL, serverUrl);
        authIntent.putExtra(BrapiAuthActivity.EXTRA_OIDC_URL,
                am.getUserData(account, BrapiAuthenticator.KEY_OIDC_URL));
        authIntent.putExtra(BrapiAuthActivity.EXTRA_OIDC_FLOW,
                am.getUserData(account, BrapiAuthenticator.KEY_OIDC_FLOW));
        authIntent.putExtra(BrapiAuthActivity.EXTRA_OIDC_CLIENT_ID,
                am.getUserData(account, BrapiAuthenticator.KEY_OIDC_CLIENT_ID));
        authIntent.putExtra(BrapiAuthActivity.EXTRA_OIDC_SCOPE,
                am.getUserData(account, BrapiAuthenticator.KEY_OIDC_SCOPE));
        startActivityForResult(authIntent, AUTH_REQUEST_CODE);
        return Unit.INSTANCE;
    }

    /**
     * Mirrors active account settings from AccountManager user data into SharedPreferences
     * so that existing BrAPIService code continues to function without changes.
     */
    private void syncActiveAccountPrefs(Account account) {
        AccountManager am = AccountManager.get(context);
        String serverUrl = am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL);
        String displayName = am.getUserData(account, BrapiAuthenticator.KEY_DISPLAY_NAME);
        String oidcUrl = am.getUserData(account, BrapiAuthenticator.KEY_OIDC_URL);
        String oidcFlow = am.getUserData(account, BrapiAuthenticator.KEY_OIDC_FLOW);
        String oidcClientId = am.getUserData(account, BrapiAuthenticator.KEY_OIDC_CLIENT_ID);
        String oidcScope = am.getUserData(account, BrapiAuthenticator.KEY_OIDC_SCOPE);
        String brapiVersion = am.getUserData(account, BrapiAuthenticator.KEY_BRAPI_VERSION);

        SharedPreferences.Editor editor = preferences.edit();
        if (serverUrl != null) editor.putString(PreferenceKeys.BRAPI_BASE_URL, serverUrl);
        if (displayName != null) editor.putString(PreferenceKeys.BRAPI_DISPLAY_NAME, displayName);
        if (oidcUrl != null) editor.putString(PreferenceKeys.BRAPI_OIDC_URL, oidcUrl);
        if (oidcFlow != null) editor.putString(PreferenceKeys.BRAPI_OIDC_FLOW, oidcFlow);
        if (oidcClientId != null) editor.putString(PreferenceKeys.BRAPI_OIDC_CLIENT_ID, oidcClientId);
        if (oidcScope != null) editor.putString(PreferenceKeys.BRAPI_OIDC_SCOPE, oidcScope);
        if (brapiVersion != null) editor.putString(PreferenceKeys.BRAPI_VERSION, brapiVersion);
        editor.apply();
    }

    private @NotNull Unit checkServerCompatibility(Account account) {
        AccountManager am = AccountManager.get(context);
        String serverUrl = am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL);
        if (serverUrl == null) serverUrl = account.name;

        FragmentActivity act = getActivity();
        if (act != null) {
            if (Utils.isConnected(act)) {
                act.getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.prefs_container, BrapiServerInfoFragment.Companion.newInstance(serverUrl))
                        .addToBackStack(null)
                        .commit();
            } else {
                Toast.makeText(act, R.string.device_offline_warning, Toast.LENGTH_SHORT).show();
            }
        }
        return Unit.INSTANCE;
    }

    private @NotNull Unit shareAccountSettings(Account account) {
        AccountManager am = AccountManager.get(context);
        Activity act = getActivity();
        if (act == null) return null;

        try {
            BrAPIConfig config = new BrAPIConfig();
            config.setUrl(am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL));
            config.setName(am.getUserData(account, BrapiAuthenticator.KEY_DISPLAY_NAME));
            config.setVersion(am.getUserData(account, BrapiAuthenticator.KEY_BRAPI_VERSION));
            config.setAuthFlow(am.getUserData(account, BrapiAuthenticator.KEY_OIDC_FLOW));
            config.setOidcUrl(am.getUserData(account, BrapiAuthenticator.KEY_OIDC_URL));
            config.setClientId(am.getUserData(account, BrapiAuthenticator.KEY_OIDC_CLIENT_ID));
            config.setScope(am.getUserData(account, BrapiAuthenticator.KEY_OIDC_SCOPE));
            // Advanced settings from global prefs
            config.setPageSize(preferences.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50"));
            config.setChunkSize(preferences.getString(PreferenceKeys.BRAPI_CHUNK_SIZE, "500"));
            config.setServerTimeoutMilli(preferences.getString(PreferenceKeys.BRAPI_TIMEOUT, "120"));

            String jsonConfig = new Gson().toJson(config);
            generateQRCode(act, jsonConfig);
        } catch (Exception e) {
            Toast.makeText(context, R.string.preferences_brapi_server_scan_error, Toast.LENGTH_SHORT).show();
        }
        return Unit.INSTANCE;
    }

    private @NotNull Unit editAccount(Account account) {
        AccountManager am = AccountManager.get(context);
        BrAPIConfig config = new BrAPIConfig();
        config.setUrl(am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL));
        config.setName(am.getUserData(account, BrapiAuthenticator.KEY_DISPLAY_NAME));
        config.setVersion(am.getUserData(account, BrapiAuthenticator.KEY_BRAPI_VERSION));
        config.setAuthFlow(am.getUserData(account, BrapiAuthenticator.KEY_OIDC_FLOW));
        config.setOidcUrl(am.getUserData(account, BrapiAuthenticator.KEY_OIDC_URL));
        config.setClientId(am.getUserData(account, BrapiAuthenticator.KEY_OIDC_CLIENT_ID));
        config.setScope(am.getUserData(account, BrapiAuthenticator.KEY_OIDC_SCOPE));

        BrapiManualAccountDialogFragment frag = BrapiManualAccountDialogFragment.Companion.newInstance(
                null, false, config, true
        );
        frag.show(getParentFragmentManager(), BrapiManualAccountDialogFragment.TAG);
        return Unit.INSTANCE;
    }

    private @NotNull Unit removeAccount(Account account) {
        AccountManager am = AccountManager.get(context);
        String serverUrl = am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL);

        new AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(R.string.brapi_card_remove)
                .setMessage(account.name)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (serverUrl != null) {
                        accountHelper.removeAccount(serverUrl);
                        String activeUrl = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "");
                        if (serverUrl.equals(activeUrl)) {
                            preferences.edit().putString(PreferenceKeys.BRAPI_BASE_URL, "").apply();
                        }
                    }
                    refreshServerCards();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        return Unit.INSTANCE;
    }

    // ─── Add Account ────────────────────────────────────────────────────────────

    private void showAddAccountDialog() {
        BrapiAddAccountDialogFragment dialog = BrapiAddAccountDialogFragment.Companion.newInstance(null);
        dialog.setListener(new BrapiAddAccountDialogFragment.Listener() {
            @Override
            public void onManualEntry(android.accounts.AccountAuthenticatorResponse authResponse) {
                BrapiManualAccountDialogFragment frag =
                        BrapiManualAccountDialogFragment.Companion.newInstance(null, false, null, false);
                frag.show(getParentFragmentManager(), BrapiManualAccountDialogFragment.TAG);
            }

            @Override
            public void onScanConfig(android.accounts.AccountAuthenticatorResponse authResponse) {
                startBarcodeScan(REQUEST_BARCODE_SCAN_CONFIG);
            }
        });
        dialog.show(getParentFragmentManager(), BrapiAddAccountDialogFragment.TAG);
    }

    // ─── Barcode scanning ───────────────────────────────────────────────────────

    private void startBarcodeScan(int requestCode) {
        if (mlkitEnabled) {
            com.fieldbook.tracker.activities.ScannerActivity.Companion.requestCameraAndStartScanner(
                    getActivity(), requestCode, null, null, null);
        } else {
            new IntentIntegrator(getActivity())
                    .setPrompt(getString(R.string.barcode_scanner_text))
                    .setBeepEnabled(true)
                    .setRequestCode(requestCode)
                    .initiateScan();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQUEST_BARCODE_SCAN_CONFIG) {
            String scanned;
            if (mlkitEnabled) {
                scanned = data.getStringExtra("barcode");
            } else {
                IntentResult result = IntentIntegrator.parseActivityResult(resultCode, data);
                scanned = result != null ? result.getContents() : null;
            }
            if (scanned != null) {
                BrAPIConfig config = null;
                if (JsonUtil.Companion.isJsonValid(scanned)) {
                    try {
                        config = new Gson().fromJson(scanned, BrAPIConfig.class);
                    } catch (Exception e) { /* fall through */ }
                }
                BrapiManualAccountDialogFragment frag =
                        BrapiManualAccountDialogFragment.Companion.newInstance(null, false, config, false);
                frag.show(getParentFragmentManager(), BrapiManualAccountDialogFragment.TAG);
            }
        } else if (requestCode == AUTH_REQUEST_CODE) {
            // Re-authorization completed — refresh cards to reflect new token state
            pendingAuthAccount = null;
            refreshServerCards();
        } else if (requestCode == BrapiAuthActivity.END_SESSION_REQUEST_CODE) {
            // OIDC end-session completed — clear token for the pending account
            if (pendingAuthAccount != null) {
                AccountManager am = AccountManager.get(context);
                String serverUrl = am.getUserData(pendingAuthAccount, BrapiAuthenticator.KEY_SERVER_URL);
                accountHelper.clearToken(serverUrl != null ? serverUrl : "");
                String activeUrl = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "");
                if (serverUrl != null && serverUrl.equals(activeUrl)) {
                    preferences.edit().putString(PreferenceKeys.BRAPI_BASE_URL, "").apply();
                    BrapiFilterCache.Companion.delete(context, true);
                }
                if (pendingRemoveAfterLogout) {
                    pendingRemoveAfterLogout = false;
                    if (serverUrl != null) {
                        accountHelper.removeAccount(serverUrl);
                    }
                }
                pendingAuthAccount = null;
            }
            preferences.edit()
                    .remove(PreferenceKeys.BRAPI_ID_TOKEN)
                    .remove(PreferenceKeys.BRAPI_TOKEN)
                    .apply();
            refreshServerCards();
        }
    }

    // ─── Visibility ─────────────────────────────────────────────────────────────

    private void updateServerSectionsVisibility(boolean brapiEnabled) {
        Preference addAccount = findPreference("brapi_add_account");
        Preference advancedSettings = findPreference("brapi_advanced_settings");

        if (addAccount != null) addAccount.setVisible(brapiEnabled);
        if (advancedSettings != null) advancedSettings.setVisible(brapiEnabled);

        if (brapiEnabled) {
            refreshServerCards();
        } else {
            if (activeServerCategory != null) activeServerCategory.setVisible(false);
            if (availableServersCategory != null) availableServersCategory.setVisible(false);
        }

        // Update toolbar auth menu item visibility
        if (mMenu != null) {
            MenuItem authItem = mMenu.findItem(R.id.action_menu_brapi_auto_configure);
            if (authItem != null) authItem.setVisible(brapiEnabled);
        }
    }

    // ─── Toolbar ────────────────────────────────────────────────────────────────

    private Menu mMenu;

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_brapi_pref, menu);
        mMenu = menu;
        boolean brapiEnabled = preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false);
        MenuItem authItem = menu.findItem(R.id.action_menu_brapi_auto_configure);
        if (authItem != null) authItem.setVisible(brapiEnabled);
        MenuItem brapiPrefAuthItem = menu.findItem(R.id.action_menu_brapi_pref_auth);
        if (brapiPrefAuthItem != null) brapiPrefAuthItem.setVisible(false); // auth is now per-card
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_menu_brapi_auto_configure) {
            showCommunityServerListDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showCommunityServerListDialog() {
        String[] serverNames = getResources().getStringArray(R.array.community_servers_names);
        String[] serverUrls = getResources().getStringArray(R.array.community_servers_urls);
        String[] serverOidcUrls = getResources().getStringArray(R.array.community_servers_oidc_urls);
        String[] serverGrantTypes = getResources().getStringArray(R.array.community_servers_grant_types);

        String[] extendedNames = new String[serverNames.length + 1];
        System.arraycopy(serverNames, 0, extendedNames, 0, serverNames.length);
        extendedNames[serverNames.length] = getString(R.string.preferences_brapi_server_add);

        new AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(R.string.preferences_brapi_servers_title)
                .setItems(extendedNames, (dialog, which) -> {
                    if (which == serverNames.length) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/PhenoApps/Field-Book/issues/new?assignees=&labels=enhancement,feature+request&template=feature_request.md&title=[REQUEST]"));
                        startActivity(browserIntent);
                    } else {
                        // Pre-fill a new account dialog with community server info
                        BrAPIConfig config = new BrAPIConfig();
                        config.setUrl(serverUrls[which]);
                        config.setName(serverNames[which]);
                        config.setOidcUrl(serverOidcUrls[which]);
                        config.setAuthFlow(serverGrantTypes[which]);
                        BrapiManualAccountDialogFragment frag =
                                BrapiManualAccountDialogFragment.Companion.newInstance(null, false, config, false);
                        frag.show(getParentFragmentManager(), BrapiManualAccountDialogFragment.TAG);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create().show();
    }

    private void setupToolbar() {
        Activity act = getActivity();
        if (act instanceof PreferencesActivity) {
            ActionBar bar = ((PreferencesActivity) act).getSupportActionBar();
            if (bar != null) bar.setTitle(getString(R.string.brapi_info_title));
        }
    }

    // ─── QR Code generation ─────────────────────────────────────────────────────

    private void generateQRCode(Activity act, String jsonConfig) {
        try {
            int screenWidth;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowMetrics wm = act.getWindowManager().getCurrentWindowMetrics();
                screenWidth = wm.getBounds().width();
            } else {
                DisplayMetrics dm = new DisplayMetrics();
                act.getWindowManager().getDefaultDisplay().getMetrics(dm);
                screenWidth = dm.widthPixels;
            }
            int size = (int) (screenWidth * 0.8);

            BitMatrix bitMatrix = new MultiFormatWriter().encode(jsonConfig, BarcodeFormat.QR_CODE, size, size);
            int w = bitMatrix.getWidth(), h = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            ImageView imageView = new ImageView(context);
            imageView.setImageBitmap(bmp);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAdjustViewBounds(true);

            new AlertDialog.Builder(act, R.style.AppAlertDialog)
                    .setTitle(getString(R.string.preferences_brapi_barcode_config_dialog_title))
                    .setView(imageView)
                    .setPositiveButton(getString(R.string.dialog_close), null)
                    .show();
        } catch (WriterException e) {
            Toast.makeText(context, R.string.preferences_brapi_server_scan_error, Toast.LENGTH_SHORT).show();
        }
    }
}
