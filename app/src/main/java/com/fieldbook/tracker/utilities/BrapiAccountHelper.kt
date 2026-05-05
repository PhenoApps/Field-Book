package com.fieldbook.tracker.utilities

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.fieldbook.tracker.preferences.PreferenceKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import org.phenoapps.brapi.account.BrapiAccountRepository
import org.phenoapps.brapi.account.BrapiPreferenceKeys
import javax.inject.Inject

class BrapiAccountHelper @Inject constructor(
    @param:ApplicationContext context: Context,
    preferences: SharedPreferences,
) {

    private val repository = BrapiAccountRepository(
        context = context,
        preferences = preferences,
        preferenceKeys = BrapiPreferenceKeys(
            enabled = PreferenceKeys.BRAPI_ENABLED,
            baseUrl = PreferenceKeys.BRAPI_BASE_URL,
            displayName = PreferenceKeys.BRAPI_DISPLAY_NAME,
            accessToken = PreferenceKeys.BRAPI_TOKEN,
            idToken = PreferenceKeys.BRAPI_ID_TOKEN,
        ),
    )

    fun getAllAccounts(): List<Account> = repository.getAllAccounts()
    fun getAccountByUrl(serverUrl: String): Account? = repository.getAccountByUrl(serverUrl)
    fun findAccount(): Account? = repository.findAccount()
    fun setActiveAccount(serverUrl: String) = repository.setActiveAccount(serverUrl)
    fun hasActiveAccount(): Boolean = repository.hasActiveAccount()
    fun hasActiveServer(): Boolean = repository.hasActiveServer()
    fun peekToken(): String? = repository.peekToken()
    fun getTokenBlocking(): String? = repository.getTokenBlocking()
    fun peekTokenForAccount(account: Account): String? = repository.peekTokenForAccount(account)
    fun peekIdToken(): String? = repository.peekIdToken()
    fun peekIdTokenForAccount(account: Account): String? = repository.peekIdTokenForAccount(account)
    fun getUserData(account: Account, key: String): String? = repository.getUserData(account, key)
    fun setUserData(account: Account, key: String, value: String?) = repository.setUserData(account, key, value)
    fun isOwnAccount(account: Account): Boolean = repository.isOwnAccount(account)
    fun canDisplayAccount(account: Account): Boolean = repository.canDisplayAccount(account)
    fun canAccessAccount(account: Account): Boolean = repository.canAccessAccount(account)
    fun canUseToken(account: Account): Boolean = repository.canUseToken(account)
    fun grantSelectedAccount(account: Account) = repository.grantSelectedAccount(account)
    fun buildChooseAccountIntent(preselected: Account? = null): Intent =
        repository.buildChooseAccountIntent(preselected)

    fun addAccountConfig(
        serverUrl: String,
        displayName: String,
        oidcUrl: String = "",
        oidcFlow: String = "",
        oidcClientId: String = "",
        oidcScope: String = "",
        brapiVersion: String = "V2",
        originalServerUrl: String? = null,
    ): Account = repository.addAccountConfig(
        serverUrl = serverUrl,
        displayName = displayName,
        oidcUrl = oidcUrl,
        oidcFlow = oidcFlow,
        oidcClientId = oidcClientId,
        oidcScope = oidcScope,
        brapiVersion = brapiVersion,
        originalServerUrl = originalServerUrl,
    )

    fun storeToken(serverUrl: String, accessToken: String, idToken: String?) =
        repository.storeToken(serverUrl, accessToken, idToken)

    fun clearToken(serverUrl: String) = repository.clearToken(serverUrl)
    fun removeAccount(serverUrl: String) = repository.removeAccount(serverUrl)
    fun migrateFromPrefsIfNeeded() = repository.migrateFromPrefsIfNeeded()
    fun normalizeUrl(url: String): String = repository.normalizeUrl(url)
    fun refreshOwnedAccountVisibility() = repository.refreshOwnedAccountVisibility()
}
