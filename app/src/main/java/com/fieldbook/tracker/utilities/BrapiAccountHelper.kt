package com.fieldbook.tracker.utilities

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.brapi.BrapiAuthenticator
import com.fieldbook.tracker.preferences.PreferenceKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BrapiAccountHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: SharedPreferences
){

    /**
     * Returns all BrAPI accounts stored in AccountManager.
     */
    fun getAllAccounts(): List<Account> =
        AccountManager.get(context).getAccountsByType(BrapiAuthenticator.ACCOUNT_TYPE).toList()

    /**
     * Returns the AccountManager account matching the given server URL, or null.
     */
    fun getAccountByUrl(serverUrl: String): Account? {
        val am = AccountManager.get(context)
        return am.getAccountsByType(BrapiAuthenticator.ACCOUNT_TYPE)
            .firstOrNull {
                am.getUserData(it, BrapiAuthenticator.KEY_SERVER_URL) == serverUrl
                        || it.name == serverUrl
            }
    }

    /**
     * Returns the AccountManager account matching the active server URL (BRAPI_BASE_URL pref).
     */
    fun findAccount(): Account? {
        val serverUrl = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "") ?: ""
        return getAccountByUrl(serverUrl)
    }

    /**
     * Sets the given server URL as the active BrAPI server by writing BRAPI_BASE_URL.
     */
    fun setActiveAccount(serverUrl: String) {
        preferences.edit().putString(PreferenceKeys.BRAPI_BASE_URL, serverUrl).apply()
    }

    /**
     * Returns true if BrAPI is enabled, there is a saved account for the active server URL,
     * and that account has a valid (non-null) token. A 401 will cause the token to be
     * invalidated via AccountManager.invalidateAuthToken(), causing this to return false
     * until the user re-authorizes.
     */
    fun hasActiveAccount(): Boolean {
        if (!preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)) return false
        val activeUrl = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "") ?: ""
        if (activeUrl.isEmpty()) return false
        val account = getAccountByUrl(activeUrl) ?: return false
        val token = AccountManager.get(context).peekAuthToken(account, BrapiAuthenticator.AUTH_TOKEN_TYPE)
        return !token.isNullOrEmpty()
    }

    /**
     * Returns true if BrAPI is enabled and an active server account exists, regardless of
     * whether that account has been authenticated. Use this when authentication is not
     * required to show BrAPI as an option (e.g. import field/trait dialogs).
     */
    fun hasActiveServer(): Boolean {
        if (!preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)) return false
        val activeUrl = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "") ?: ""
        if (activeUrl.isEmpty()) return false
        return getAccountByUrl(activeUrl) != null
    }

    /**
     * Retrieves the BrAPI access token from AccountManager for the current server.
     * Returns null if no account or no token is found.
     */
    fun peekToken(): String? {
        val am = AccountManager.get(context)
        val account = findAccount() ?: return null
        return am.peekAuthToken(account, BrapiAuthenticator.AUTH_TOKEN_TYPE)
    }

    /**
     * Retrieves the BrAPI access token via AccountManager.getAuthToken() (blocking).
     *
     * Unlike [peekToken], this routes through the authenticator and can surface tokens
     * that were stored by other PhenoApps apps (which share the same account type and
     * signing certificate). Must be called from a background thread.
     *
     * Returns null silently if no token is available or on any AccountManager error.
     */
    fun getTokenBlocking(): String? {
        val account = findAccount() ?: return null
        return try {
            AccountManager.get(context).blockingGetAuthToken(
                account,
                BrapiAuthenticator.AUTH_TOKEN_TYPE,
                false // notifyAuthFailure=false: don't launch re-auth UI from background
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Retrieves the BrAPI access token for a specific account.
     */
    fun peekTokenForAccount(account: Account): String? {
        val am = AccountManager.get(context)
        return am.peekAuthToken(account, BrapiAuthenticator.AUTH_TOKEN_TYPE)
    }

    /**
     * Retrieves the OIDC ID token stored as user data for the current server's account.
     */
    fun peekIdToken(): String? {
        val am = AccountManager.get(context)
        val account = findAccount() ?: return null
        return am.getUserData(account, BrapiAuthenticator.KEY_ID_TOKEN)
    }

    /**
     * Retrieves the OIDC ID token for a specific account.
     */
    fun peekIdTokenForAccount(account: Account): String? =
        AccountManager.get(context).getUserData(account, BrapiAuthenticator.KEY_ID_TOKEN)

    /**
     * Reads a user-data value for the given account.
     */
    fun getUserData(account: Account, key: String): String? =
        AccountManager.get(context).getUserData(account, key)

    /**
     * Writes a user-data value for the given account.
     */
    fun setUserData(account: Account, key: String, value: String) =
        AccountManager.get(context).setUserData(account, key, value)

    /**
     * Creates an AccountManager entry for a new server without yet storing a token.
     * All per-account settings (OIDC URL, flow, version, etc.) are persisted as user data.
     * Returns the created or updated Account.
     */
    fun addAccountConfig(
        serverUrl: String,
        displayName: String,
        oidcUrl: String = "",
        oidcFlow: String = "",
        oidcClientId: String = "",
        oidcScope: String = "",
        brapiVersion: String = "V2"
    ): Account {
        val am = AccountManager.get(context)
        val normUrl = normalizeUrl(serverUrl)
        val name = displayName.ifEmpty { extractHostname(normUrl) }

        val existing = getAccountByUrl(normUrl)
        val account = existing ?: Account(name, BrapiAuthenticator.ACCOUNT_TYPE).also {
            am.addAccountExplicitly(it, null, null)
            grantVisibilityToAllowedPackages(am, it)
        }

        am.setUserData(account, BrapiAuthenticator.KEY_SERVER_URL, normUrl)
        am.setUserData(account, BrapiAuthenticator.KEY_DISPLAY_NAME, name)
        am.setUserData(account, BrapiAuthenticator.KEY_OIDC_URL, oidcUrl)
        am.setUserData(account, BrapiAuthenticator.KEY_OIDC_FLOW, oidcFlow)
        am.setUserData(account, BrapiAuthenticator.KEY_OIDC_CLIENT_ID, oidcClientId)
        am.setUserData(account, BrapiAuthenticator.KEY_OIDC_SCOPE, oidcScope)
        am.setUserData(account, BrapiAuthenticator.KEY_BRAPI_VERSION, brapiVersion)

        return account
    }

    /**
     * Stores the access token (and optionally the ID token) in AccountManager.
     * The account is created with the server's display name as the visible account name.
     * The server URL is stored as user data so accounts can be matched by URL across app restarts.
     */
    fun storeToken(serverUrl: String, accessToken: String, idToken: String?) {
        val am = AccountManager.get(context)
        val normUrl = normalizeUrl(serverUrl)
        val displayName = preferences.getString(PreferenceKeys.BRAPI_DISPLAY_NAME, normUrl)
            ?.takeIf { it.isNotEmpty() } ?: normUrl

        val account = getAccountByUrl(normUrl)
            ?: Account(displayName, BrapiAuthenticator.ACCOUNT_TYPE).also {
                am.addAccountExplicitly(it, null, null)
                grantVisibilityToAllowedPackages(am, it)
            }

        am.setUserData(account, BrapiAuthenticator.KEY_SERVER_URL, normUrl)
        am.setAuthToken(account, BrapiAuthenticator.AUTH_TOKEN_TYPE, accessToken)
        if (idToken != null) {
            am.setUserData(account, BrapiAuthenticator.KEY_ID_TOKEN, idToken)
        }
    }

    /**
     * Clears only the token for the given account, leaving the account config intact.
     * The account will still appear in Available Servers but show "Authorized" status lost.
     */
    fun clearToken(serverUrl: String) {
        val am = AccountManager.get(context)
        val account = getAccountByUrl(serverUrl) ?: return
        val token = am.peekAuthToken(account, BrapiAuthenticator.AUTH_TOKEN_TYPE)
        if (token != null) {
            am.invalidateAuthToken(BrapiAuthenticator.ACCOUNT_TYPE, token)
        }
        am.setUserData(account, BrapiAuthenticator.KEY_ID_TOKEN, null)
    }

    /**
     * Removes the AccountManager account associated with the given server URL (if present).
     * Also clears BRAPI_BASE_URL if it currently points to the removed server.
     */
    fun removeAccount(serverUrl: String) {
        val am = AccountManager.get(context)
        am.getAccountsByType(BrapiAuthenticator.ACCOUNT_TYPE)
            .filter {
                am.getUserData(it, BrapiAuthenticator.KEY_SERVER_URL) == serverUrl
                        || it.name == serverUrl
            }
            .forEach { am.removeAccountExplicitly(it) }
        val activeUrl = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "") ?: ""
        if (activeUrl == serverUrl) {
            preferences.edit().remove(PreferenceKeys.BRAPI_BASE_URL).apply()
        }
    }

    /**
     * One-time migration: if an access token exists in SharedPreferences but no AccountManager
     * account exists for the current server, migrate the token into AccountManager.
     */
    fun migrateFromPrefsIfNeeded() {
        val serverUrl = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "") ?: ""
        val existingToken = preferences.getString(PreferenceKeys.BRAPI_TOKEN, null) ?: return
        if (serverUrl.isEmpty()) return
        if (findAccount() != null) return // already migrated
        val idToken = preferences.getString(PreferenceKeys.BRAPI_ID_TOKEN, null)
        storeToken(serverUrl, existingToken, idToken)
    }

    /**
     * Prepends https:// if no scheme is present in the URL.
     */
    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    /**
     * Extracts the hostname from a URL for use as a default display name.
     */
    private fun extractHostname(url: String): String {
        return try {
            java.net.URL(url).host.ifEmpty { url }
        } catch (e: Exception) {
            url
        }
    }

    /**
     * Grants full account visibility to each package in ALLOWED_PACKAGES (API 26+).
     * This replaces the non-existent AbstractAccountAuthenticator.getAccountVisibilityForPackage()
     * override — visibility must be set explicitly after addAccountExplicitly().
     */
    private fun grantVisibilityToAllowedPackages(am: AccountManager, account: Account) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (pkg in BrapiAuthenticator.ALLOWED_PACKAGES) {
                am.setAccountVisibility(account, pkg, AccountManager.VISIBILITY_VISIBLE)
            }
        }
    }
}
