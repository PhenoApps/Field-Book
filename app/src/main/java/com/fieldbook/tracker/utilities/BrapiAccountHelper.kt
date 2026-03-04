package com.fieldbook.tracker.utilities

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.brapi.BrapiAuthenticator
import com.fieldbook.tracker.preferences.PreferenceKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BrapiAccountHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: SharedPreferences
){

    companion object {
        private const val KEY_SERVER_URL = "server_url"
    }

    /**
     * Returns the AccountManager account matching the given server URL.
     * Matches by the "server_url" user-data key (new accounts), or by account name as a
     * fallback for accounts created before this naming scheme was introduced.
     */
    fun findAccount(): Account? {
        val am = AccountManager.get(context)
        val serverUrl = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PreferenceKeys.BRAPI_BASE_URL, "") ?: ""
        return am.getAccountsByType(BrapiAuthenticator.ACCOUNT_TYPE)
            .firstOrNull { am.getUserData(it, KEY_SERVER_URL) == serverUrl || it.name == serverUrl }
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
     * Retrieves the OIDC ID token stored as user data for the current server's account.
     */
    fun peekIdToken(): String? {
        val am = AccountManager.get(context)
        val account = findAccount() ?: return null
        return am.getUserData(account, BrapiAuthenticator.KEY_ID_TOKEN)
    }

    /**
     * Stores the access token (and optionally the ID token) in AccountManager.
     * The account is created with the server's display name as the visible account name.
     * The server URL is stored as user data so accounts can be matched by URL across app restarts.
     */
    fun storeToken(serverUrl: String, accessToken: String, idToken: String?) {
        val am = AccountManager.get(context)
        val displayName = preferences.getString(PreferenceKeys.BRAPI_DISPLAY_NAME, serverUrl)
            ?.takeIf { it.isNotEmpty() } ?: serverUrl

        // Find an existing account for this server URL (by user data), or create a new one
        val account = am.getAccountsByType(BrapiAuthenticator.ACCOUNT_TYPE)
            .firstOrNull { am.getUserData(it, KEY_SERVER_URL) == serverUrl }
            ?: Account(displayName, BrapiAuthenticator.ACCOUNT_TYPE).also {
                am.addAccountExplicitly(it, null, null)
            }

        am.setUserData(account, KEY_SERVER_URL, serverUrl)
        am.setAuthToken(account, BrapiAuthenticator.AUTH_TOKEN_TYPE, accessToken)
        if (idToken != null) {
            am.setUserData(account, BrapiAuthenticator.KEY_ID_TOKEN, idToken)
        }
    }

    /**
     * Removes the AccountManager account associated with the given server URL (if present).
     */
    fun removeAccount(serverUrl: String) {
        val am = AccountManager.get(context)
        am.getAccountsByType(BrapiAuthenticator.ACCOUNT_TYPE)
            .filter { am.getUserData(it, KEY_SERVER_URL) == serverUrl || it.name == serverUrl }
            .forEach { am.removeAccountExplicitly(it) }
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
}
