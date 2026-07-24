package org.phenoapps.brapi.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import org.phenoapps.brapi.BrapiAccountConstants

open class BrapiAccountRepository(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val preferenceKeys: BrapiPreferenceKeys,
) {

    fun getAllAccounts(): List<Account> {
        val am = AccountManager.get(context)
        return am.getAccountsByType(BrapiAccountConstants.ACCOUNT_TYPE)
            .filter { canDisplayAccount(am, it) }
            .toList()
    }

    fun getAccountByUrl(serverUrl: String): Account? {
        val am = AccountManager.get(context)
        val normalized = runCatching { normalizeUrl(serverUrl) }.getOrDefault(serverUrl)
        return getAllAccounts()
            .firstOrNull {
                val accountUrl = am.getUserData(it, BrapiAccountConstants.KEY_SERVER_URL)
                accountUrl == serverUrl || accountUrl == normalized || it.name == serverUrl
            }
    }

    fun findAccount(): Account? {
        val serverUrl = preferences.getString(preferenceKeys.baseUrl, "") ?: ""
        return getAccountByUrl(serverUrl)
    }

    fun setActiveAccount(serverUrl: String) {
        preferences.edit().putString(preferenceKeys.baseUrl, normalizeUrl(serverUrl)).apply()
    }

    fun hasActiveAccount(): Boolean {
        if (!preferences.getBoolean(preferenceKeys.enabled, false)) return false
        val account = findAccount() ?: return false
        if (!canUseToken(account)) return false
        val token = AccountManager.get(context)
            .peekAuthToken(account, BrapiAccountConstants.AUTH_TOKEN_TYPE)
        if (!token.isNullOrEmpty()) return true
        return !isOwnAccount(account) && !getTokenBlocking().isNullOrEmpty()
    }

    fun hasActiveServer(): Boolean {
        if (!preferences.getBoolean(preferenceKeys.enabled, false)) return false
        val activeUrl = preferences.getString(preferenceKeys.baseUrl, "") ?: ""
        return activeUrl.isNotEmpty() && getAccountByUrl(activeUrl) != null
    }

    fun peekToken(): String? {
        val account = findAccount() ?: return null
        return peekTokenForAccount(account)
    }

    fun getTokenBlocking(): String? {
        val account = findAccount() ?: return null
        if (!canUseToken(account)) return null
        return try {
            AccountManager.get(context).blockingGetAuthToken(
                account,
                BrapiAccountConstants.AUTH_TOKEN_TYPE,
                false,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun peekTokenForAccount(account: Account): String? =
        AccountManager.get(context).let { am ->
            if (canUseToken(am, account)) {
                am.peekAuthToken(account, BrapiAccountConstants.AUTH_TOKEN_TYPE)
            } else {
                null
            }
        }

    fun peekIdToken(): String? {
        val account = findAccount() ?: return null
        return peekIdTokenForAccount(account)
    }

    fun peekIdTokenForAccount(account: Account): String? {
        return AccountManager.get(context).let { am ->
            if (canUseToken(am, account)) {
                am.getUserData(account, BrapiAccountConstants.KEY_ID_TOKEN)
            } else {
                null
            }
        }
    }

    fun isOwnAccount(account: Account): Boolean =
        AccountManager.get(context).getUserData(account, BrapiAccountConstants.KEY_OWNER_PACKAGE) == context.packageName

    fun canDisplayAccount(account: Account): Boolean =
        canDisplayAccount(AccountManager.get(context), account)

    fun canAccessAccount(account: Account): Boolean =
        canAccessAccount(AccountManager.get(context), account)

    fun canUseToken(account: Account): Boolean =
        canUseToken(AccountManager.get(context), account)

    fun grantSelectedAccount(account: Account) {
        if (!canAccessAccount(account)) return
        preferences.edit().putBoolean(localGrantPreferenceKey(account), true).apply()
    }

    @Suppress("DEPRECATION")
    fun buildChooseAccountIntent(preselected: Account? = null): Intent =
        AccountManager.newChooseAccountIntent(
            preselected,
            null,
            arrayOf(BrapiAccountConstants.ACCOUNT_TYPE),
            true,
            null,
            null,
            null,
            Bundle().apply {
                putBoolean(BrapiAccountConstants.OPTION_SHARED_ACCOUNT_CHOOSER, true)
            },
        )

    fun getUserData(account: Account, key: String): String? =
        AccountManager.get(context).getUserData(account, key)

    fun setUserData(account: Account, key: String, value: String?) =
        AccountManager.get(context).setUserData(account, key, value)

    fun addAccountConfig(
        serverUrl: String,
        displayName: String,
        oidcUrl: String = "",
        oidcFlow: String = "",
        oidcClientId: String = "",
        oidcScope: String = "",
        brapiVersion: String = "V2",
        originalServerUrl: String? = null,
    ): Account {
        val am = AccountManager.get(context)
        val normalizedUrl = normalizeUrl(serverUrl)
        val lookupUrl = originalServerUrl?.let { normalizeUrl(it) } ?: normalizedUrl
        val accountName = displayName.ifEmpty { extractHostname(normalizedUrl) }

        val account = getWritableAccountByUrl(am, lookupUrl) ?: createAccount(am, accountName)
        initializeOwnedAccount(am, account)

        am.setUserData(account, BrapiAccountConstants.KEY_SERVER_URL, normalizedUrl)
        am.setUserData(account, BrapiAccountConstants.KEY_DISPLAY_NAME, accountName)
        am.setUserData(account, BrapiAccountConstants.KEY_OIDC_URL, oidcUrl)
        am.setUserData(account, BrapiAccountConstants.KEY_OIDC_FLOW, oidcFlow)
        am.setUserData(account, BrapiAccountConstants.KEY_OIDC_CLIENT_ID, oidcClientId)
        am.setUserData(account, BrapiAccountConstants.KEY_OIDC_SCOPE, oidcScope)
        am.setUserData(account, BrapiAccountConstants.KEY_BRAPI_VERSION, brapiVersion)

        return account
    }

    fun storeToken(serverUrl: String, accessToken: String, idToken: String?) {
        val am = AccountManager.get(context)
        val normalizedUrl = normalizeUrl(serverUrl)
        val displayName = preferences.getString(preferenceKeys.displayName, normalizedUrl)
            ?.takeIf { it.isNotEmpty() } ?: extractHostname(normalizedUrl)
        val account = getWritableAccountByUrl(am, normalizedUrl) ?: createAccount(am, displayName)
        initializeOwnedAccount(am, account)

        am.setUserData(account, BrapiAccountConstants.KEY_SERVER_URL, normalizedUrl)
        am.setAuthToken(account, BrapiAccountConstants.AUTH_TOKEN_TYPE, accessToken)
        am.setUserData(account, BrapiAccountConstants.KEY_ID_TOKEN, idToken)

        preferences.edit()
            .putString(preferenceKeys.baseUrl, normalizedUrl)
            .putString(preferenceKeys.accessToken, accessToken)
            .putString(preferenceKeys.idToken, idToken)
            .apply()
    }

    fun clearToken(serverUrl: String) {
        val am = AccountManager.get(context)
        val account = getWritableAccountByUrl(am, serverUrl) ?: return
        am.peekAuthToken(account, BrapiAccountConstants.AUTH_TOKEN_TYPE)?.let { token ->
            am.invalidateAuthToken(BrapiAccountConstants.ACCOUNT_TYPE, token)
        }
        am.setUserData(account, BrapiAccountConstants.KEY_ID_TOKEN, null)
        preferences.edit()
            .remove(preferenceKeys.accessToken)
            .remove(preferenceKeys.idToken)
            .apply()
    }

    fun removeAccount(serverUrl: String) {
        val am = AccountManager.get(context)
        val normalizedUrl = normalizeUrl(serverUrl)
        am.getAccountsByType(BrapiAccountConstants.ACCOUNT_TYPE)
            .filter {
                val ownerPackage = am.getUserData(it, BrapiAccountConstants.KEY_OWNER_PACKAGE)
                val accountUrl = am.getUserData(it, BrapiAccountConstants.KEY_SERVER_URL)
                (ownerPackage.isNullOrEmpty() || ownerPackage == context.packageName) &&
                    (accountUrl == serverUrl || accountUrl == normalizedUrl || it.name == serverUrl)
            }
            .forEach { am.removeAccountExplicitly(it) }

        val activeUrl = preferences.getString(preferenceKeys.baseUrl, "") ?: ""
        if (activeUrl == serverUrl || activeUrl == normalizedUrl) {
            preferences.edit()
                .remove(preferenceKeys.baseUrl)
                .remove(preferenceKeys.accessToken)
                .remove(preferenceKeys.idToken)
                .apply()
        }
    }

    fun migrateFromPrefsIfNeeded() {
        val serverUrl = preferences.getString(preferenceKeys.baseUrl, "") ?: ""
        val existingToken = preferences.getString(preferenceKeys.accessToken, null) ?: return
        if (serverUrl.isEmpty() || findAccount() != null) return
        storeToken(serverUrl, existingToken, preferences.getString(preferenceKeys.idToken, null))
    }

    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "https://$trimmed"
    }

    private fun createAccount(am: AccountManager, baseName: String): Account {
        val existingNames = am.getAccountsByType(BrapiAccountConstants.ACCOUNT_TYPE)
            .map { it.name }
            .toSet()
        var uniqueName = baseName
        var suffix = 2
        while (uniqueName in existingNames) {
            uniqueName = "$baseName ($suffix)"
            suffix++
        }

        return Account(uniqueName, BrapiAccountConstants.ACCOUNT_TYPE).also { account ->
            am.addAccountExplicitly(account, null, null)
            initializeOwnedAccount(am, account)
        }
    }

    fun refreshOwnedAccountVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val am = AccountManager.get(context)
            am.getAccountsByType(BrapiAccountConstants.ACCOUNT_TYPE)
                .filter { am.getUserData(it, BrapiAccountConstants.KEY_OWNER_PACKAGE) == context.packageName }
                .forEach { configureOwnedAccountVisibility(am, it) }
        }
    }

    private fun initializeOwnedAccount(am: AccountManager, account: Account) {
        val ownerPackage = am.getUserData(account, BrapiAccountConstants.KEY_OWNER_PACKAGE)
        if (ownerPackage.isNullOrEmpty()) {
            am.setUserData(account, BrapiAccountConstants.KEY_OWNER_PACKAGE, context.packageName)
            configureOwnedAccountVisibility(am, account)
        } else if (ownerPackage == context.packageName) {
            configureOwnedAccountVisibility(am, account)
        }
    }

    private fun getWritableAccountByUrl(am: AccountManager, serverUrl: String): Account? {
        val normalized = runCatching { normalizeUrl(serverUrl) }.getOrDefault(serverUrl)
        return am.getAccountsByType(BrapiAccountConstants.ACCOUNT_TYPE)
            .firstOrNull {
                val ownerPackage = am.getUserData(it, BrapiAccountConstants.KEY_OWNER_PACKAGE)
                val accountUrl = am.getUserData(it, BrapiAccountConstants.KEY_SERVER_URL)
                (ownerPackage.isNullOrEmpty() || ownerPackage == context.packageName) &&
                    (accountUrl == serverUrl || accountUrl == normalized || it.name == serverUrl)
            }
    }

    private fun canDisplayAccount(am: AccountManager, account: Account): Boolean {
        val ownerPackage = am.getUserData(account, BrapiAccountConstants.KEY_OWNER_PACKAGE)
        if (!BrapiAccountConstants.canPackageAccessAccount(ownerPackage, context.packageName)) return false
        if (ownerPackage == context.packageName) return true
        return hasGrant(am, account)
    }

    private fun canAccessAccount(am: AccountManager, account: Account): Boolean {
        val ownerPackage = am.getUserData(account, BrapiAccountConstants.KEY_OWNER_PACKAGE)
        return BrapiAccountConstants.canPackageAccessAccount(ownerPackage, context.packageName)
    }

    private fun canUseToken(am: AccountManager, account: Account): Boolean {
        val ownerPackage = am.getUserData(account, BrapiAccountConstants.KEY_OWNER_PACKAGE)
        if (!BrapiAccountConstants.canPackageAccessAccount(ownerPackage, context.packageName)) return false
        if (ownerPackage.isNullOrEmpty() || ownerPackage == context.packageName) return true
        return hasGrant(am, account)
    }

    private fun hasGrant(am: AccountManager, account: Account): Boolean {
        val accountGrant = am.getUserData(
            account,
            BrapiAccountConstants.grantedPackageKey(context.packageName),
        ) == "true"
        val localGrant = preferences.getBoolean(localGrantPreferenceKey(account), false)
        return accountGrant || localGrant
    }

    private fun localGrantPreferenceKey(account: Account): String {
        val am = AccountManager.get(context)
        val ownerPackage = am.getUserData(account, BrapiAccountConstants.KEY_OWNER_PACKAGE) ?: "legacy"
        val serverUrl = am.getUserData(account, BrapiAccountConstants.KEY_SERVER_URL) ?: account.name
        return "brapi_account_grant_${ownerPackage}_${account.type}_${serverUrl}"
    }

    private fun extractHostname(url: String): String = try {
        java.net.URL(url).host.ifEmpty { url }
    } catch (_: Exception) {
        url
    }

    private fun configureOwnedAccountVisibility(am: AccountManager, account: Account) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            am.setAccountVisibility(
                account,
                AccountManager.PACKAGE_NAME_KEY_LEGACY_VISIBLE,
                AccountManager.VISIBILITY_NOT_VISIBLE,
            )
            am.setAccountVisibility(
                account,
                AccountManager.PACKAGE_NAME_KEY_LEGACY_NOT_VISIBLE,
                AccountManager.VISIBILITY_NOT_VISIBLE,
            )
            am.setAccountVisibility(account, context.packageName, AccountManager.VISIBILITY_VISIBLE)

            if (BrapiAccountConstants.isPackageAllowed(context.packageName)) {
                for (pkg in BrapiAccountConstants.ALLOWED_PACKAGES) {
                    if (pkg == context.packageName) continue
                    val currentVisibility = runCatching {
                        am.getAccountVisibility(account, pkg)
                    }.getOrDefault(AccountManager.VISIBILITY_UNDEFINED)
                    if (currentVisibility != AccountManager.VISIBILITY_USER_MANAGED_VISIBLE) {
                        am.setAccountVisibility(
                            account,
                            pkg,
                            AccountManager.VISIBILITY_USER_MANAGED_NOT_VISIBLE,
                        )
                    }
                }
            }
        }
    }
}
