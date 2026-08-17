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
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
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
import com.fieldbook.tracker.brapi.dialogs.BrapiManualAccountDialogFragment;
import com.fieldbook.tracker.brapi.dialogs.BrapiStepperAccountDialogFragment;
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache;
import com.fieldbook.tracker.utilities.BrapiAccountHelper;
import com.fieldbook.tracker.objects.BrAPIConfig;
import com.fieldbook.tracker.preferences.enums.TransferSource;
import com.fieldbook.tracker.utilities.JsonUtil;
import com.fieldbook.tracker.utilities.OpenAuthConfigurationUtil;
import com.fieldbook.tracker.utilities.Utils;
import com.google.gson.Gson;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import android.widget.ImageView;

import net.openid.appauth.AuthorizationService;
import net.openid.appauth.EndSessionRequest;

import org.jetbrains.annotations.NotNull;
import org.phenoapps.brapi.BrapiAccountConstants;
import org.phenoapps.brapi.ui.BrapiAccountConfig;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import kotlin.Unit;

/**
 * Multi-account BrAPI preferences screen.
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
    private static final int AUTH_REQUEST_CODE = 123;
    private static final int CHOOSE_ACCOUNT_REQUEST_CODE = 124;

    // The logout flow spans a trip to the browser, so its pending state has to survive the
    // fragment being recreated while that browser is in front (rotation, low memory).
    private static final String STATE_PENDING_AUTH_ACCOUNT = "pending_auth_account";
    private static final String STATE_PENDING_REMOVE_AFTER_LOGOUT = "pending_remove_after_logout";

    private Context context;
    private PreferenceCategory activeServerCategory;
    private PreferenceCategory availableServersCategory;
    private PreferenceCategory sharedAccountsCategory;

    // Tracks which account triggered the auth flow for re-auth
    private Account pendingAuthAccount = null;

    // Tracks whether to remove the account after logout completes
    private boolean pendingRemoveAfterLogout = false;

    // Tracks the shared account awaiting explicit user selection in AccountManager.
    private Account pendingChooseAccount = null;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_PENDING_AUTH_ACCOUNT, pendingAuthAccount);
        outState.putBoolean(STATE_PENDING_REMOVE_AFTER_LOGOUT, pendingRemoveAfterLogout);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_brapi, rootKey);

        if (savedInstanceState != null) {
            pendingAuthAccount = savedInstanceState.getParcelable(STATE_PENDING_AUTH_ACCOUNT);
            pendingRemoveAfterLogout = savedInstanceState.getBoolean(STATE_PENDING_REMOVE_AFTER_LOGOUT, false);
        }

        CheckBoxPreference brapiEnabledPref = findPreference(PreferenceKeys.BRAPI_ENABLED);
        if (brapiEnabledPref != null) {
            brapiEnabledPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean isChecked = (Boolean) newValue;
                    if (!isChecked) { // on disable, reset default sources if they were set to brapi
                        if (TransferSource.BRAPI.getValue().equals(preferences.getString(PreferenceKeys.IMPORT_SOURCE_DEFAULT, ""))) {
                            preferences.edit().putString(PreferenceKeys.IMPORT_SOURCE_DEFAULT, TransferSource.ASK.getValue()).apply();
                        }
                        if (TransferSource.BRAPI.getValue().equals(preferences.getString(PreferenceKeys.EXPORT_SOURCE_DEFAULT, ""))) {
                            preferences.edit().putString(PreferenceKeys.EXPORT_SOURCE_DEFAULT, TransferSource.ASK.getValue()).apply();
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

        Preference sharedServersPref = findPreference("brapi_shared_servers");
        if (sharedServersPref != null) {
            sharedServersPref.setOnPreferenceClickListener(pref -> {
                showSharedServersChooser();
                return true;
            });
        }

        accountHelper.migrateFromPrefsIfNeeded();

        activeServerCategory = findPreference("brapi_active_server_category");
        availableServersCategory = findPreference("brapi_available_servers_category");
        sharedAccountsCategory = findPreference("brapi_shared_accounts_category");

        // Refresh cards when an account edit is saved from BrapiManualAccountDialogFragment
        getParentFragmentManager().setFragmentResultListener(
                BrapiManualAccountDialogFragment.REQUEST_KEY_EDIT_SAVED,
                this,
                (key, result) -> refreshServerCards()
        );

        // Set initial visibility before the view is shown to avoid a flash of hidden preferences
        boolean brapiEnabled = preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false);
        updateServerSectionsVisibility(brapiEnabled);
    }

    @Override
    public void onResume() {
        super.onResume();
        setupToolbar();
        accountHelper.refreshOwnedAccountVisibility();
        boolean brapiEnabled = preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false);
        updateServerSectionsVisibility(brapiEnabled);
    }

    // ─── Account cards ──────────────────────────────────────────────────────────

    private void refreshServerCards() {
        if (activeServerCategory == null || availableServersCategory == null) return;

        activeServerCategory.removeAll();
        availableServersCategory.removeAll();
        if (sharedAccountsCategory != null) sharedAccountsCategory.removeAll();

        String activeUrl = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "");

        AccountManager am = AccountManager.get(context);
        List<Account> allAccounts = accountHelper.getAllAccounts();
        boolean hasActive = false;

        for (Account account : allAccounts) {
            String serverUrl = am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL);
            String ownerPkg = am.getUserData(account, BrapiAuthenticator.KEY_OWNER_PACKAGE);
            boolean isOwnAccount = context.getPackageName().equals(ownerPkg);
            boolean isActive = activeUrl.equals(serverUrl);

            if (!isOwnAccount) {
                BrapiServerCardPreference card = buildSharedCard(account, isActive);
                if (isActive) {
                    activeServerCategory.addPreference(card);
                    hasActive = true;
                } else if (sharedAccountsCategory != null) {
                    sharedAccountsCategory.addPreference(card);
                }
                continue;
            }

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
        if (sharedAccountsCategory != null) {
            sharedAccountsCategory.setVisible(sharedAccountsCategory.getPreferenceCount() > 0);
        }
    }

    private BrapiServerCardPreference buildSharedCard(Account account, boolean isActive) {
        AccountManager am = AccountManager.get(context);
        String ownerPkg = am.getUserData(account, BrapiAuthenticator.KEY_OWNER_PACKAGE);
        String ownerName = BrapiAccountConstants.INSTANCE.displayNameForPackage(ownerPkg);
        String ownerLabel = ownerName.isEmpty()
                ? ""
                : getString(org.phenoapps.brapi.R.string.pheno_brapi_shared_account_from, ownerName);

        BrapiServerCardPreference card = new BrapiServerCardPreference(context);
        card.setAccount(account);
        card.setActive(isActive);
        card.setExpanded(isActive);
        card.setKey("shared_" + account.name + "_" + (isActive ? "active" : "available"));
        card.setOrder(0);
        final String label = ownerLabel;
        card.setOwnerLabel(label);
        card.setHasToken(accountHelper.peekTokenForAccount(account) != null);
        configureCardActions(card);
        return card;
    }

    private BrapiServerCardPreference buildCard(Account account, boolean isActive) {
        BrapiServerCardPreference card = new BrapiServerCardPreference(context);
        card.setAccount(account);
        card.setActive(isActive);
        card.setExpanded(isActive); // active card starts expanded; inactive cards start collapsed
        card.setKey(account.name + "_" + (isActive ? "active" : "available"));
        card.setOrder(0);
        card.setHasToken(accountHelper.peekTokenForAccount(account) != null);

        configureCardActions(card);

        return card;
    }

    private void configureCardActions(BrapiServerCardPreference card) {
        card.setOnLogOut(this::logOutAccount);
        card.setOnAuthorize(this::authorizeAccount);
        card.setOnEnable(this::enableAccount);
        card.setOnCheckCompatibility(this::checkServerCompatibility);
        card.setOnShareSettings(this::shareAccountSettings);
        card.setOnEdit(this::editAccount);
        card.setOnRemove(this::removeAccount);
        card.setOnSwitchServer(this::showSwitchServerDialog);
    }

    // ─── Account actions ────────────────────────────────────────────────────────

    private @NotNull Unit showSwitchServerDialog(Account account) {
        new AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(R.string.brapi_switch_server_title)
                .setMessage(R.string.brapi_switch_server_unauthenticated_message)
                .setPositiveButton(R.string.dialog_yes, (d, w) -> authorizeAccount(account))
                .setNegativeButton(R.string.dialog_no, (d, w) -> enableAccount(account))
                .show();
        return Unit.INSTANCE;
    }

    private @NotNull Unit logOutAccount(Account account) {
        AccountManager am = AccountManager.get(context);
        String displayName = am.getUserData(account, BrapiAuthenticator.KEY_DISPLAY_NAME);
        if (displayName == null) displayName = account.name;
        final String finalDisplayName = displayName;

        String messageTemplate = getString(R.string.brapi_logout_manage_message, finalDisplayName);
        SpannableString styledMessage = new SpannableString(messageTemplate);
        int nameStart = messageTemplate.indexOf(finalDisplayName);
        if (nameStart >= 0) {
            styledMessage.setSpan(new StyleSpan(Typeface.BOLD),
                    nameStart, nameStart + finalDisplayName.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        new AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(R.string.brapi_logout_manage_title)
                .setMessage(styledMessage)
                .setNeutralButton(R.string.brapi_logout_only, (d, w) -> {
                    pendingRemoveAfterLogout = false;
                    doLogout(account);
                })
                .setPositiveButton(R.string.brapi_logout_and_remove, (d, w) -> {
                    pendingRemoveAfterLogout = true;
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
            // Resolved up front: getAuthServiceConfiguration() calls back asynchronously, by which
            // point the fragment may be detached and getString() would throw.
            final Uri postLogoutRedirect = Uri.parse(getString(R.string.brapi_redirect_uri));
            authUtil.getAuthServiceConfiguration((config, ex) -> {
                EndSessionRequest endSessionRequest =
                        new EndSessionRequest.Builder(config)
                                .setIdTokenHint(idToken)
                                .setPostLogoutRedirectUri(postLogoutRedirect)
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
        if (!accountHelper.canUseToken(account)) {
            pendingChooseAccount = account;
            startActivityForResult(accountHelper.buildChooseAccountIntent(account), CHOOSE_ACCOUNT_REQUEST_CODE);
            return Unit.INSTANCE;
        }

        activateAccount(account);
        return Unit.INSTANCE;
    }

    private void activateAccount(Account account) {
        makeAccountActive(account);
        refreshServerCards();
    }

    /**
     * Makes {@code account} the active one, updating the legacy SharedPreference mirrors that
     * BrAPIService and the re-authorization flow still read, and dropping cached field/trait data
     * when this is a switch to a different server.
     *
     * @return the account's server URL
     */
    private String makeAccountActive(Account account) {
        AccountManager am = AccountManager.get(context);
        String serverUrl = am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL);
        if (serverUrl == null) serverUrl = account.name;
        if (accountHelper.setActiveAccount(serverUrl)) {
            BrapiFilterCache.Companion.delete(context, true);
        }
        return serverUrl;
    }

    private @NotNull Unit authorizeAccount(Account account) {
        AccountManager am = AccountManager.get(context);
        // Make this account active so BrapiAuthActivity picks up its config
        String serverUrl = makeAccountActive(account);
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
        if (act == null) return Unit.INSTANCE;

        try {
            BrapiAccountConfig config = new BrapiAccountConfig(
                    am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL),
                    am.getUserData(account, BrapiAuthenticator.KEY_DISPLAY_NAME),
                    am.getUserData(account, BrapiAuthenticator.KEY_BRAPI_VERSION),
                    // Published as the portable "code"/"implicit" spelling so that installs which
                    // predate the stable flow identifiers still read this config correctly.
                    BrapiAccountConstants.INSTANCE.toSharedConfigOidcFlow(
                            am.getUserData(account, BrapiAuthenticator.KEY_OIDC_FLOW)),
                    am.getUserData(account, BrapiAuthenticator.KEY_OIDC_URL),
                    am.getUserData(account, BrapiAuthenticator.KEY_OIDC_CLIENT_ID),
                    am.getUserData(account, BrapiAuthenticator.KEY_OIDC_SCOPE)
            );

            String jsonConfig = new Gson().toJson(config);
            generateQRCode(act, jsonConfig);
        } catch (Exception e) {
            Toast.makeText(context, R.string.preferences_brapi_server_scan_error, Toast.LENGTH_SHORT).show();
        }
        return Unit.INSTANCE;
    }

    private @NotNull Unit editAccount(Account account) {
        AccountManager am = AccountManager.get(context);
        BrapiAccountConfig config = new BrapiAccountConfig(
                am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL),
                am.getUserData(account, BrapiAuthenticator.KEY_DISPLAY_NAME),
                am.getUserData(account, BrapiAuthenticator.KEY_BRAPI_VERSION),
                am.getUserData(account, BrapiAuthenticator.KEY_OIDC_FLOW),
                am.getUserData(account, BrapiAuthenticator.KEY_OIDC_URL),
                am.getUserData(account, BrapiAuthenticator.KEY_OIDC_CLIENT_ID),
                am.getUserData(account, BrapiAuthenticator.KEY_OIDC_SCOPE)
        );

        BrapiManualAccountDialogFragment frag = BrapiManualAccountDialogFragment.Companion.newInstance(
                null, false, config, true
        );
        frag.show(getParentFragmentManager(), BrapiManualAccountDialogFragment.TAG);
        return Unit.INSTANCE;
    }

    private @NotNull Unit removeAccount(Account account) {
        AccountManager am = AccountManager.get(context);
        String serverUrl = am.getUserData(account, BrapiAuthenticator.KEY_SERVER_URL);
        String displayName = am.getUserData(account, BrapiAuthenticator.KEY_DISPLAY_NAME);
        if (displayName == null) displayName = account.name;

        String removeMessageTemplate = getString(R.string.brapi_logout_manage_message, displayName);
        SpannableString removeStyledMessage = new SpannableString(removeMessageTemplate);
        int removeNameStart = removeMessageTemplate.indexOf(displayName);
        if (removeNameStart >= 0) {
            removeStyledMessage.setSpan(new StyleSpan(Typeface.BOLD),
                    removeNameStart, removeNameStart + displayName.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        new AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(R.string.brapi_logout_manage_title)
                .setMessage(removeStyledMessage)
                .setPositiveButton(R.string.brapi_logout_and_remove, (dialog, which) -> {
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
        BrapiStepperAccountDialogFragment frag =
                BrapiStepperAccountDialogFragment.Companion.newInstance(null);
        frag.show(getParentFragmentManager(), BrapiStepperAccountDialogFragment.TAG);
    }

    private void showSharedServersChooser() {
        pendingChooseAccount = null;
        startActivityForResult(accountHelper.buildChooseAccountIntent(null), CHOOSE_ACCOUNT_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CHOOSE_ACCOUNT_REQUEST_CODE) {
            Account pending = pendingChooseAccount;
            pendingChooseAccount = null;
            if (resultCode == RESULT_OK && pending != null && data != null) {
                String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
                if (pending.name.equals(accountName) &&
                        pending.type.equals(accountType) &&
                        accountHelper.canAccessAccount(pending)) {
                    accountHelper.grantSelectedAccount(pending);
                    if (accountHelper.canUseToken(pending)) {
                        activateAccount(pending);
                    }
                }
            } else if (resultCode == RESULT_OK && data != null) {
                String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
                if (accountName != null && BrapiAuthenticator.ACCOUNT_TYPE.equals(accountType)) {
                    Account selected = new Account(accountName, accountType);
                    if (accountHelper.canAccessAccount(selected)) {
                        accountHelper.grantSelectedAccount(selected);
                        if (accountHelper.canUseToken(selected)) {
                            activateAccount(selected);
                        }
                    }
                }
            }
            return;
        }

        // Handled ahead of the RESULT_OK gate below: a cancelled end-session still has to clear
        // the pending logout state, otherwise it stays armed for whatever consumes it next.
        if (requestCode == BrapiAuthActivity.END_SESSION_REQUEST_CODE) {
            Account loggedOutAccount = pendingAuthAccount;
            boolean removeAfterLogout = pendingRemoveAfterLogout;
            pendingAuthAccount = null;
            pendingRemoveAfterLogout = false;

            if (resultCode != RESULT_OK) {
                // User backed out of the provider's sign-out page; the account stays signed in.
                refreshServerCards();
                return;
            }

            // OIDC end-session completed — clear token for the pending account
            if (loggedOutAccount != null) {
                AccountManager am = AccountManager.get(context);
                String serverUrl = am.getUserData(loggedOutAccount, BrapiAuthenticator.KEY_SERVER_URL);
                accountHelper.clearToken(serverUrl != null ? serverUrl : "");

                String activeUrl = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "");
                boolean wasActiveAccount = serverUrl != null && serverUrl.equals(activeUrl);

                if (wasActiveAccount) {
                    // The legacy BRAPI_TOKEN/BRAPI_ID_TOKEN prefs mirror the *active* account, so
                    // they may only be cleared when it is the one being signed out. Clearing them
                    // while logging out some other account would sign the active account out of
                    // every code path that still reads the mirrors.
                    // clearToken() above already does this; repeating it here keeps the result
                    // correct regardless of the order these two edits run in.
                    preferences.edit()
                            .putString(PreferenceKeys.BRAPI_BASE_URL, "")
                            .remove(PreferenceKeys.BRAPI_ID_TOKEN)
                            .remove(PreferenceKeys.BRAPI_TOKEN)
                            .apply();
                    BrapiFilterCache.Companion.delete(context, true);
                }
                if (removeAfterLogout && serverUrl != null) {
                    accountHelper.removeAccount(serverUrl);
                }
            } else {
                // Lost track of which account this result belongs to; clear the legacy pref
                // mirrors so nothing keeps using a token the provider has just invalidated.
                preferences.edit()
                        .remove(PreferenceKeys.BRAPI_ID_TOKEN)
                        .remove(PreferenceKeys.BRAPI_TOKEN)
                        .apply();
            }
            refreshServerCards();
            return;
        }

        if (resultCode != RESULT_OK) return;

        if (requestCode == AUTH_REQUEST_CODE) {
            // Re-authorization completed — refresh cards to reflect new token state
            pendingAuthAccount = null;
            refreshServerCards();
        }
    }

    // ─── Visibility ─────────────────────────────────────────────────────────────

    private void updateServerSectionsVisibility(boolean brapiEnabled) {
        Preference addAccount = findPreference("brapi_add_account");
        Preference sharedServers = findPreference("brapi_shared_servers");
        Preference advancedSettings = findPreference("brapi_advanced_settings");

        if (addAccount != null) addAccount.setVisible(brapiEnabled);
        if (sharedServers != null) sharedServers.setVisible(brapiEnabled);
        if (advancedSettings != null) advancedSettings.setVisible(brapiEnabled);

        if (brapiEnabled) {
            refreshServerCards();
        } else {
            if (activeServerCategory != null) activeServerCategory.setVisible(false);
            if (availableServersCategory != null) availableServersCategory.setVisible(false);
            if (sharedAccountsCategory != null) sharedAccountsCategory.setVisible(false);
        }

    }

    // ─── Toolbar ────────────────────────────────────────────────────────────────

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
