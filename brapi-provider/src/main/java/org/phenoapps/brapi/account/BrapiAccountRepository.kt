package org.phenoapps.brapi.account

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import org.phenoapps.brapi.BrapiAccountConstants
import org.phenoapps.brapi.config.BrapiAccountInfo
import org.phenoapps.brapi.config.BrapiConfigClient

/** Outcome of [BrapiAccountRepository.migrateFromPrefsIfNeeded]. */
enum class BrapiMigrationResult {
    /** There was nothing in the legacy preferences to migrate, or it has already been migrated. */
    NOT_NEEDED,

    /** The legacy preference config now has an AccountManager account behind it. */
    MIGRATED,

    /**
     * The account could not be created yet. The legacy preferences are untouched, so calling
     * again once the platform accepts the write finishes the migration.
     */
    DEFERRED,
}

/** Outcome of [BrapiAccountRepository.storeToken]. */
enum class BrapiTokenStoreResult {
    /** The token is on an account this app owns. */
    STORED,

    /**
     * A sibling app already owns an account for this server, so no second one was created and the
     * token lives only in the preference mirrors. Creating one would list the same server twice in
     * the system account settings.
     */
    ALREADY_SHARED,

    /**
     * No account could be written — see [BrapiAccountRepository.authenticatorOwnerPackage]. The
     * token is still mirrored, so the sign-in is not lost.
     */
    ACCOUNT_UNAVAILABLE,
}

open class BrapiAccountRepository(
    private val context: Context,
    private val preferences: SharedPreferences,
    private val preferenceKeys: BrapiPreferenceKeys,
) {

    private val configClient = BrapiConfigClient(context)

    /** The account type this app owns and is registered as the authenticator for. */
    val accountType: String = BrapiAccountConstants.accountTypeFor(context.packageName)

    /**
     * Every account this app can see: the ones it owns, plus the ones sibling PhenoApps have
     * shared with it.
     *
     * Siblings own their own account types, so their accounts are reached by enumerating those
     * types rather than by filtering one shared type. AccountManager only returns accounts the
     * calling package has been made visible to, so a sibling account appearing here already means
     * the user granted it.
     */
    fun getAllAccounts(): List<Account> {
        val am = AccountManager.get(context)
        return (listOf(accountType) + siblingAccountTypes() + BrapiAccountConstants.LEGACY_ACCOUNT_TYPE)
            .distinct()
            .flatMap { type -> accountsByType(am, type) }
            .filter { canDisplayAccount(am, it) }
    }

    /**
     * BrAPI account types owned by other installed PhenoApps.
     *
     * Discovered rather than hardcoded: an app's type is its package under the shared prefix, so
     * the registered authenticators are already the list of siblings that can share accounts.
     */
    fun siblingAccountTypes(): List<String> = runCatching {
        AccountManager.get(context).authenticatorTypes
            .filter {
                BrapiAccountConstants.isPerAppAccountType(it.type) &&
                        it.type != accountType &&
                        BrapiAccountConstants.isPackageAllowed(it.packageName)
            }
            .map { it.type }
    }.getOrDefault(emptyList())

    /**
     * The package AccountManager holds as the authenticator for this app's account type, or null
     * when nothing is registered for it.
     *
     * Should always be this app, since the type is namespaced by package name. It is checked
     * anyway because the registry is rebuilt from package broadcasts, so an install that declares
     * the authenticator for the first time has a window where the app is running but the type is
     * not attributed to it yet.
     */
    fun authenticatorOwnerPackage(): String? = authenticatorOwnerPackageFor(accountType)

    /**
     * The package that owns [account].
     *
     * Taken from the account type, which encodes it, rather than from user data — reading user
     * data off another app's account is refused unless the two are co-signed. Legacy accounts
     * predate per-app types and still have to be asked.
     */
    fun ownerPackageOf(account: Account): String? =
        if (BrapiAccountConstants.isPerAppAccountType(account.type)) {
            account.type.removePrefix("${BrapiAccountConstants.ACCOUNT_TYPE_PREFIX}.")
        } else {
            readUserData(
                AccountManager.get(context),
                account,
                BrapiAccountConstants.KEY_OWNER_PACKAGE
            )
        }

    /**
     * Everything known about [account] — its server URL, display name, version and OIDC settings.
     *
     * For an owned account this is AccountManager user data. For a shared one it comes from the
     * owning app's config provider, since user data is unreadable across a signature boundary.
     * Null means the owner published nothing for this app, which is the normal state for an
     * account that has been discovered but not yet granted.
     */
    fun accountInfo(account: Account): BrapiAccountInfo? {
        val owner = ownerPackageOf(account)
        if (owner == context.packageName || owner.isNullOrEmpty()) {
            return ownedAccountInfo(account, owner ?: context.packageName)
        }
        return configClient.accountsFrom(owner).firstOrNull { it.accountName == account.name }
    }

    private fun ownedAccountInfo(account: Account, owner: String): BrapiAccountInfo {
        val am = AccountManager.get(context)
        fun data(key: String) = readUserData(am, account, key).orEmpty()
        return BrapiAccountInfo(
            accountName = account.name,
            ownerPackage = owner,
            serverUrl = data(BrapiAccountConstants.KEY_SERVER_URL),
            displayName = data(BrapiAccountConstants.KEY_DISPLAY_NAME).ifEmpty { account.name },
            brapiVersion = data(BrapiAccountConstants.KEY_BRAPI_VERSION),
            oidcUrl = data(BrapiAccountConstants.KEY_OIDC_URL),
            oidcFlow = data(BrapiAccountConstants.KEY_OIDC_FLOW),
            oidcClientId = data(BrapiAccountConstants.KEY_OIDC_CLIENT_ID),
            oidcScope = data(BrapiAccountConstants.KEY_OIDC_SCOPE),
            hasToken = !peekTokenForAccount(account).isNullOrEmpty(),
        )
    }

    /**
     * [accountInfo] with an empty description substituted when the owner published nothing, so
     * callers rendering an account can read every field without a null check.
     */
    fun accountInfoOrEmpty(account: Account): BrapiAccountInfo =
        accountInfo(account) ?: BrapiAccountInfo(
            accountName = account.name,
            ownerPackage = ownerPackageOf(account).orEmpty(),
        )

    /** A visible account owned by another app that already serves [serverUrl], if there is one. */
    fun sharedAccountForUrl(serverUrl: String): Account? {
        val normalized = runCatching { normalizeUrl(serverUrl) }.getOrDefault(serverUrl)
        return getAllAccounts()
            .filterNot { isOwnAccount(it) }
            .firstOrNull {
                val accountUrl = accountInfo(it)?.serverUrl
                accountUrl == serverUrl || accountUrl == normalized
            }
    }

    /** The server URL [account] points at, from whichever source can describe it. */
    fun serverUrlOf(account: Account): String =
        accountInfo(account)?.serverUrl?.takeIf { it.isNotEmpty() } ?: account.name

    fun getAccountByUrl(serverUrl: String): Account? {
        val normalized = runCatching { normalizeUrl(serverUrl) }.getOrDefault(serverUrl)
        return getAllAccounts()
            .firstOrNull {
                val accountUrl = accountInfo(it)?.serverUrl
                accountUrl == serverUrl || accountUrl == normalized || it.name == serverUrl
            }
    }

    fun findAccount(): Account? {
        val serverUrl = preferences.getString(preferenceKeys.baseUrl, "") ?: ""
        return getAccountByUrl(serverUrl)
    }

    /**
     * Makes [serverUrl] the active account and repoints every preference mirror at it.
     *
     * Writing only the base URL is not enough: the mirrors for BrAPI version and OIDC config are
     * what request building and re-authorization still read, so leaving them behind means the new
     * account is addressed with the previous one's version path and re-authorized against the
     * previous one's provider.
     *
     * Returns true when this actually changed which server is active, so callers can drop data
     * cached from the previous one. Re-selecting the account that is already active returns false
     * — the mirrors are still refreshed, but nothing server-specific has gone stale.
     */
    fun setActiveAccount(serverUrl: String): Boolean {
        val normalized = normalizeUrl(serverUrl)
        val changed = !isActiveAccount(normalized)

        val editor = preferences.edit().putString(preferenceKeys.baseUrl, normalized)
        if (changed) {
            // The token mirrors belong to whichever account was active until now. Carrying them
            // over would report the newly selected server as signed in and send the previous
            // server's token to it. An account this app owns re-peeks its real token from
            // AccountManager; a shared one is repopulated by borrowToken.
            editor.remove(preferenceKeys.accessToken).remove(preferenceKeys.idToken)
        }
        editor.apply()

        getAccountByUrl(normalized)?.let { syncActiveAccountPrefs(it) }
        return changed
    }

    /**
     * Mirrors [account]'s config into the host's SharedPreference keys.
     *
     * Only keys the host declared in [preferenceKeys] are written, and only for values the account
     * actually carries — a missing one leaves the existing mirror alone rather than blanking it.
     * Shared accounts are described by their owner's config provider, so this works the same for
     * an account owned by another app as for one of this app's own.
     */
    fun syncActiveAccountPrefs(account: Account) {
        val info = accountInfo(account) ?: return
        val editor = preferences.edit()

        fun mirror(prefKey: String?, value: String) {
            if (prefKey.isNullOrEmpty() || value.isEmpty()) return
            editor.putString(prefKey, value)
        }

        mirror(preferenceKeys.baseUrl, info.serverUrl)
        mirror(preferenceKeys.displayName, info.displayName)
        mirror(preferenceKeys.oidcUrl, info.oidcUrl)
        mirror(preferenceKeys.oidcFlow, info.oidcFlow)
        mirror(preferenceKeys.oidcClientId, info.oidcClientId)
        mirror(preferenceKeys.oidcScope, info.oidcScope)
        mirror(preferenceKeys.brapiVersion, info.brapiVersion)

        editor.apply()
    }

    /** Whether [serverUrl] is the account the preference mirrors currently describe. */
    fun isActiveAccount(serverUrl: String): Boolean {
        val normalized = runCatching { normalizeUrl(serverUrl) }.getOrDefault(serverUrl)
        val activeUrl = preferences.getString(preferenceKeys.baseUrl, "") ?: ""
        return activeUrl.isNotEmpty() && (activeUrl == serverUrl || activeUrl == normalized)
    }

    /**
     * Whether the active account is signed in.
     *
     * Deliberately does no blocking token fetch: [AccountManager.blockingGetAuthToken] throws when
     * called from the main thread, and this is reached from UI. A shared account's token is not
     * peekable here — it belongs to another app — so the mirror written by [borrowToken] when the
     * account was enabled is what answers for it.
     */
    fun hasActiveAccount(): Boolean {
        if (!preferences.getBoolean(preferenceKeys.enabled, false)) return false
        val account = findAccount() ?: return false
        if (!canUseToken(account)) return false
        if (!peekTokenForAccount(account).isNullOrEmpty()) return true
        return !preferences.getString(preferenceKeys.accessToken, null).isNullOrEmpty()
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

    /**
     * The cached token for [account], or null.
     *
     * Peeking is restricted to the app that owns the account type, so this only ever answers for
     * this app's own accounts; a shared account's token has to be fetched with [borrowToken].
     */
    fun peekTokenForAccount(account: Account): String? =
        AccountManager.get(context).let { am ->
            if (!canUseToken(am, account)) return null
            runCatching {
                am.peekAuthToken(account, BrapiAccountConstants.AUTH_TOKEN_TYPE)
            }.getOrNull()
        }

    /**
     * Fetches [account]'s token from the app that owns it and mirrors it locally.
     *
     * This is what makes a shared server usable rather than merely visible. The request crosses to
     * the owning app's authenticator, which is the one path into another app's token that does not
     * require a matching signature; passing [activity] lets that app raise its consent screen if it
     * has not already granted this one. On success the token and id token are written to the
     * preference mirrors, which is where request building and [hasActiveAccount] read them —
     * nothing is written into AccountManager, because a borrowed token belongs to its owner.
     *
     * Fails with null when the owner has no token to lend, meaning the user has to sign in from
     * that app first.
     */
    fun borrowToken(account: Account, activity: Activity?, onResult: (String?) -> Unit) {
        if (!canUseToken(account)) {
            onResult(null)
            return
        }

        AccountManager.get(context).getAuthToken(
            account,
            BrapiAccountConstants.AUTH_TOKEN_TYPE,
            null,
            activity,
            { future ->
                val token = runCatching {
                    future.result?.getString(AccountManager.KEY_AUTHTOKEN)
                }.onFailure {
                    Log.w(TAG, "Could not borrow a token for ${account.name}", it)
                }.getOrNull()

                if (!token.isNullOrEmpty()) {
                    preferences.edit().putString(preferenceKeys.accessToken, token).apply()
                }
                onResult(token?.takeIf { it.isNotEmpty() })
            },
            null,
        )
    }

    fun peekIdToken(): String? {
        val account = findAccount() ?: return null
        return peekIdTokenForAccount(account)
    }

    fun peekIdTokenForAccount(account: Account): String? {
        return AccountManager.get(context).let { am ->
            if (canUseToken(am, account)) {
                readUserData(am, account, BrapiAccountConstants.KEY_ID_TOKEN)
            } else {
                null
            }
        }
    }

    fun isOwnAccount(account: Account): Boolean = ownerPackageOf(account) == context.packageName

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

    /**
     * The system account chooser, listing every BrAPI type on the device.
     *
     * All of them, not just this app's: choosing a sibling's account here is how the user grants
     * this app visibility of it, which is the entire shared-server flow.
     */
    @Suppress("DEPRECATION")
    fun buildChooseAccountIntent(preselected: Account? = null): Intent =
        AccountManager.newChooseAccountIntent(
            preselected,
            null,
            (listOf(accountType) + siblingAccountTypes() + BrapiAccountConstants.LEGACY_ACCOUNT_TYPE)
                .distinct()
                .toTypedArray(),
            true,
            null,
            null,
            null,
            Bundle().apply {
                putBoolean(BrapiAccountConstants.OPTION_SHARED_ACCOUNT_CHOOSER, true)
            },
        )

    fun getUserData(account: Account, key: String): String? =
        readUserData(AccountManager.get(context), account, key)

    fun setUserData(account: Account, key: String, value: String?) {
        accountWrite("setUserData($key)") {
            AccountManager.get(context).setUserData(account, key, value)
        }
    }

    /**
     * Creates or updates the account for [serverUrl], returning null when it was refused.
     *
     * Refused either because AccountManager rejected the write (see [authenticatorOwnerPackage])
     * or because a sibling app already shares this server — callers should check
     * [sharedAccountForUrl] first if they want to tell the user which it was. Adding a second
     * account for a server a sibling already provides is what puts the same server in the system
     * account settings twice, so it is declined here rather than left to each caller to remember.
     *
     * [adoptExistingShared] opts out of that check for callers moving an account this app already
     * owns, where the account is not new and refusing would lose it.
     */
    fun addAccountConfig(
        serverUrl: String,
        displayName: String,
        oidcUrl: String = "",
        oidcFlow: String = "",
        oidcClientId: String = "",
        oidcScope: String = "",
        brapiVersion: String = "V2",
        originalServerUrl: String? = null,
        adoptExistingShared: Boolean = false,
    ): Account? {
        val am = AccountManager.get(context)
        val normalizedUrl = normalizeUrl(serverUrl)
        val lookupUrl = originalServerUrl?.let { normalizeUrl(it) } ?: normalizedUrl
        val accountName = displayName.ifEmpty { extractHostname(normalizedUrl) }

        if (!adoptExistingShared && getWritableAccountByUrl(am, lookupUrl) == null) {
            sharedAccountForUrl(normalizedUrl)?.let { shared ->
                Log.i(TAG, "Not adding ${shared.name}, already shared by ${ownerPackageOf(shared)}")
                return null
            }
        }

        val account = getWritableAccountByUrl(am, lookupUrl)
            ?: createAccount(am, accountName)
            ?: return null
        initializeOwnedAccount(am, account)

        val written = accountWrite("addAccountConfig") {
            am.setUserData(account, BrapiAccountConstants.KEY_SERVER_URL, normalizedUrl)
            am.setUserData(account, BrapiAccountConstants.KEY_DISPLAY_NAME, accountName)
            am.setUserData(account, BrapiAccountConstants.KEY_OIDC_URL, oidcUrl)
            am.setUserData(account, BrapiAccountConstants.KEY_OIDC_FLOW, oidcFlow)
            am.setUserData(account, BrapiAccountConstants.KEY_OIDC_CLIENT_ID, oidcClientId)
            am.setUserData(account, BrapiAccountConstants.KEY_OIDC_SCOPE, oidcScope)
            am.setUserData(account, BrapiAccountConstants.KEY_BRAPI_VERSION, brapiVersion)
        } != null

        return if (written) account else null
    }

    /**
     * Stores [accessToken] against [serverUrl]'s account and repoints the preference mirrors at it.
     *
     * The mirrors are written whatever the outcome, so a sign-in is never lost even when no account
     * could be written; the result says which of those happened so callers can report it honestly
     * rather than claiming an account was created.
     */
    fun storeToken(
        serverUrl: String,
        accessToken: String,
        idToken: String?
    ): BrapiTokenStoreResult {
        val am = AccountManager.get(context)
        val normalizedUrl = normalizeUrl(serverUrl)
        val displayName = preferences.getString(preferenceKeys.displayName, normalizedUrl)
            ?.takeIf { it.isNotEmpty() } ?: extractHostname(normalizedUrl)

        fun mirror() = preferences.edit()
            .putString(preferenceKeys.baseUrl, normalizedUrl)
            .putString(preferenceKeys.accessToken, accessToken)
            .putString(preferenceKeys.idToken, idToken)
            .apply()

        // A server already covered by a shared account must not get a second, locally owned one:
        // that is how the same server ends up listed twice in the system account settings.
        if (getWritableAccountByUrl(am, normalizedUrl) == null) {
            sharedAccountForUrl(normalizedUrl)?.let { shared ->
                Log.i(TAG, "Not duplicating ${shared.name}, which ${ownerPackageOf(shared)} owns")
                mirror()
                return BrapiTokenStoreResult.ALREADY_SHARED
            }
        }

        val account = getWritableAccountByUrl(am, normalizedUrl) ?: createAccount(am, displayName)

        val stored = account?.let { target ->
            accountWrite("storeToken") {
                initializeOwnedAccount(am, target)
                am.setUserData(target, BrapiAccountConstants.KEY_SERVER_URL, normalizedUrl)
                am.setAuthToken(target, BrapiAccountConstants.AUTH_TOKEN_TYPE, accessToken)
                am.setUserData(target, BrapiAccountConstants.KEY_ID_TOKEN, idToken)
            }
        } != null

        mirror()

        return if (stored) {
            BrapiTokenStoreResult.STORED
        } else {
            BrapiTokenStoreResult.ACCOUNT_UNAVAILABLE
        }
    }

    /**
     * Signs [serverUrl] out: invalidates its cached auth token and drops its id token.
     *
     * The [preferenceKeys] token mirrors describe whichever account is currently active, so they
     * are cleared only when [serverUrl] is that account. Clearing them while signing out some
     * other account would sign the active account out of every caller still reading the mirrors.
     *
     * Accounts owned by another package can't be modified here, but their mirrors are still
     * cleared when they are the active account — so signing out a shared account takes effect
     * locally even though its AccountManager entry is left untouched.
     *
     * The active account itself is left selected; deactivating it is the caller's decision.
     */
    fun clearToken(serverUrl: String) {
        val am = AccountManager.get(context)
        val normalizedUrl = runCatching { normalizeUrl(serverUrl) }.getOrDefault(serverUrl)

        getWritableAccountByUrl(am, serverUrl)?.let { account ->
            accountWrite("clearToken") {
                am.peekAuthToken(account, BrapiAccountConstants.AUTH_TOKEN_TYPE)?.let { token ->
                    am.invalidateAuthToken(account.type, token)
                }
                am.setUserData(account, BrapiAccountConstants.KEY_ID_TOKEN, null)
            }
        }

        val activeUrl = preferences.getString(preferenceKeys.baseUrl, "") ?: ""
        if (activeUrl == serverUrl || activeUrl == normalizedUrl) {
            preferences.edit()
                .remove(preferenceKeys.accessToken)
                .remove(preferenceKeys.idToken)
                .apply()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun removeAccount(serverUrl: String) {
        val am = AccountManager.get(context)
        val normalizedUrl = normalizeUrl(serverUrl)
        writableAccounts(am)
            .filter {
                val accountUrl = readUserData(am, it, BrapiAccountConstants.KEY_SERVER_URL)
                accountUrl == serverUrl || accountUrl == normalizedUrl || it.name == serverUrl
            }
            .forEach { account ->
                accountWrite("removeAccount") {
                    am.removeAccountExplicitly(account)
                }
                // Drop the grant alongside the account, or it lingers in preferences forever and
                // silently re-grants an account later created with the same name.
                preferences.edit().remove(localGrantPreferenceKey(account)).apply()
            }

        val activeUrl = preferences.getString(preferenceKeys.baseUrl, "") ?: ""
        if (activeUrl == serverUrl || activeUrl == normalizedUrl) {
            preferences.edit()
                .remove(preferenceKeys.baseUrl)
                .remove(preferenceKeys.accessToken)
                .remove(preferenceKeys.idToken)
                .apply()
        }
    }

    /**
     * Moves a pre-AccountManager BrAPI config out of the host's SharedPreferences and into an
     * account, leaving the preferences in place as the source to retry from.
     *
     * Nothing here is destructive and nothing latches: the only thing that stops it running again
     * is an account already existing for the configured server. The write can be refused either
     * temporarily — an update that adds the authenticator reaches this before the platform has
     * attributed the account type to the app — or permanently, when another installed app owns
     * that type. Both have to fail quietly rather than take the caller down with them, but only
     * the first is worth retrying, so they are reported apart.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun migrateFromPrefsIfNeeded(): BrapiMigrationResult {
        migrateLegacyTypeAccounts()

        val serverUrl = preferences.getString(preferenceKeys.baseUrl, "") ?: ""
        val existingToken = preferences.getString(preferenceKeys.accessToken, null)
            ?: return BrapiMigrationResult.NOT_NEEDED
        if (serverUrl.isEmpty()) return BrapiMigrationResult.NOT_NEEDED

        return try {
            if (findAccount() != null) return BrapiMigrationResult.NOT_NEEDED
            val idToken = preferences.getString(preferenceKeys.idToken, null)
            when (storeToken(serverUrl, existingToken, idToken)) {
                BrapiTokenStoreResult.STORED -> BrapiMigrationResult.MIGRATED
                // A sibling already provides this server, so there is nothing left to migrate.
                BrapiTokenStoreResult.ALREADY_SHARED -> BrapiMigrationResult.NOT_NEEDED
                BrapiTokenStoreResult.ACCOUNT_UNAVAILABLE -> BrapiMigrationResult.DEFERRED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Deferring BrAPI preference migration for $serverUrl", e)
            BrapiMigrationResult.DEFERRED
        }
    }

    /**
     * Re-creates accounts this app left under the legacy shared type beneath its own type.
     *
     * Only reachable when this app is the one that happened to claim the shared type, which is the
     * state a co-signed development install ends up in. The old account is removed once its
     * replacement exists, so the server appears once rather than twice; a failure part-way leaves
     * the original in place to try again.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun migrateLegacyTypeAccounts() {
        val am = AccountManager.get(context)
        for (legacy in legacyOwnedAccounts(am)) {
            val info = ownedAccountInfo(legacy, context.packageName)
            if (info.serverUrl.isEmpty()) continue
            if (getWritableAccountByUrl(am, info.serverUrl) != null) continue

            val moved = addAccountConfig(
                serverUrl = info.serverUrl,
                displayName = info.displayName,
                oidcUrl = info.oidcUrl,
                oidcFlow = info.oidcFlow,
                oidcClientId = info.oidcClientId,
                oidcScope = info.oidcScope,
                brapiVersion = info.brapiVersion.ifEmpty { "V2" },
                // This account is already this app's; it is being moved, not added. Declining it
                // because a sibling happens to share the same server would lose it outright.
                adoptExistingShared = true,
            ) ?: continue

            readUserData(am, legacy, BrapiAccountConstants.KEY_ID_TOKEN)?.let {
                accountWrite("moveIdToken") {
                    am.setUserData(moved, BrapiAccountConstants.KEY_ID_TOKEN, it)
                }
            }
            am.peekAuthToken(legacy, BrapiAccountConstants.AUTH_TOKEN_TYPE)?.let { token ->
                accountWrite("moveAuthToken") {
                    am.setAuthToken(moved, BrapiAccountConstants.AUTH_TOKEN_TYPE, token)
                }
            }
            accountWrite("removeLegacyAccount") { am.removeAccountExplicitly(legacy) }
            Log.i(TAG, "Moved BrAPI account ${legacy.name} to $accountType")
        }
    }

    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "https://$trimmed"
    }

    /**
     * Adds an account named after [baseName], or null when AccountManager refuses to create it.
     *
     * The refusal is expected rather than exceptional right after an update that introduces the
     * authenticator, so it is reported to the caller instead of thrown — see
     * [authenticatorOwnerPackage].
     */
    private fun createAccount(am: AccountManager, baseName: String): Account? {
        val owner = authenticatorOwnerPackage()
        if (owner != context.packageName) {
            Log.w(
                TAG,
                "BrAPI account type is owned by ${owner ?: "no package"}, not ${context.packageName}",
            )
            return null
        }

        val existingNames = accountsByType(am, accountType).map { it.name }.toSet()
        var uniqueName = baseName
        var suffix = 2
        while (uniqueName in existingNames) {
            uniqueName = "$baseName ($suffix)"
            suffix++
        }

        val account = Account(uniqueName, accountType)
        val added = accountWrite("addAccountExplicitly") {
            am.addAccountExplicitly(account, null, null)
        }
        if (added != true) return null

        initializeOwnedAccount(am, account)
        return account
    }

    /**
     * Drops grant records left behind by BrAPI apps that are no longer installed.
     *
     * Deliberately narrow: a grant is only discarded when nothing on the device registers an
     * authenticator for its account type, which means the app that owned it is gone. Pruning on
     * "no matching account visible" instead would revoke grants for accounts that are merely
     * hidden at the moment, making the user re-pick them.
     */
    fun pruneStaleGrants() {
        val liveTypes = runCatching {
            AccountManager.get(context).authenticatorTypes
                .map { it.type }
                .filter {
                    BrapiAccountConstants.isPerAppAccountType(it) ||
                            it == BrapiAccountConstants.LEGACY_ACCOUNT_TYPE
                }
                .toSet()
        }.getOrNull() ?: return

        if (liveTypes.isEmpty()) return

        val stale = preferences.all.keys.filter { key ->
            key.startsWith(GRANT_PREFERENCE_PREFIX) &&
                    liveTypes.none { key.startsWith("$GRANT_PREFERENCE_PREFIX$it$GRANT_KEY_SEPARATOR") }
        }
        if (stale.isEmpty()) return

        preferences.edit().apply { stale.forEach { remove(it) } }.apply()
        Log.i(TAG, "Pruned ${stale.size} BrAPI grant(s) for uninstalled apps")
    }

    fun refreshOwnedAccountVisibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val am = AccountManager.get(context)
            writableAccounts(am).forEach { configureOwnedAccountVisibility(am, it) }
        }
    }

    private fun initializeOwnedAccount(am: AccountManager, account: Account) {
        val ownerPackage = readUserData(am, account, BrapiAccountConstants.KEY_OWNER_PACKAGE)
        if (ownerPackage.isNullOrEmpty()) {
            accountWrite("claimOwnership") {
                am.setUserData(
                    account,
                    BrapiAccountConstants.KEY_OWNER_PACKAGE,
                    context.packageName
                )
            }
            configureOwnedAccountVisibility(am, account)
        } else if (ownerPackage == context.packageName) {
            configureOwnedAccountVisibility(am, account)
        }
    }

    /**
     * This app's own account for [serverUrl].
     *
     * Deliberately its own type only. Legacy-type accounts are writable too, but a caller looking
     * up an account to write config into wants the one under the current type — otherwise
     * [migrateLegacyTypeAccounts] would update the account it is trying to replace and then delete
     * the result.
     */
    private fun getWritableAccountByUrl(am: AccountManager, serverUrl: String): Account? {
        val normalized = runCatching { normalizeUrl(serverUrl) }.getOrDefault(serverUrl)
        return accountsByType(am, accountType)
            .firstOrNull {
                val accountUrl = readUserData(am, it, BrapiAccountConstants.KEY_SERVER_URL)
                accountUrl == serverUrl || accountUrl == normalized || it.name == serverUrl
            }
    }

    /**
     * Every account this app may modify: its own type, plus any it left behind under the legacy
     * shared type back when it owned that.
     */
    private fun writableAccounts(am: AccountManager): List<Account> =
        accountsByType(am, accountType) + legacyOwnedAccounts(am)

    private fun legacyOwnedAccounts(am: AccountManager): List<Account> {
        if (authenticatorOwnerPackageFor(BrapiAccountConstants.LEGACY_ACCOUNT_TYPE) != context.packageName) {
            return emptyList()
        }
        return accountsByType(am, BrapiAccountConstants.LEGACY_ACCOUNT_TYPE).filter {
            val owner = readUserData(am, it, BrapiAccountConstants.KEY_OWNER_PACKAGE)
            owner.isNullOrEmpty() || owner == context.packageName
        }
    }

    private fun authenticatorOwnerPackageFor(type: String): String? = runCatching {
        AccountManager.get(context).authenticatorTypes
            .firstOrNull { it.type == type }
            ?.packageName
    }.getOrNull()

    private fun accountsByType(am: AccountManager, type: String): List<Account> = runCatching {
        am.getAccountsByType(type).toList()
    }.getOrDefault(emptyList())

    /**
     * Reads account user data, treating a refusal as absent data.
     *
     * Reads are restricted to the package that owns the account type, so this only ever answers for
     * accounts this app owns; a sibling's config comes from its config provider instead.
     */
    private fun readUserData(am: AccountManager, account: Account, key: String): String? = try {
        am.getUserData(account, key)
    } catch (_: SecurityException) {
        null
    }

    /**
     * Runs an AccountManager write, returning null rather than throwing when the platform rejects
     * it. Callers decide what a rejected write means; nothing here is worth crashing the host over.
     */
    private fun <T> accountWrite(operation: String, block: () -> T): T? = try {
        block()
    } catch (e: SecurityException) {
        Log.w(TAG, "BrAPI account write refused: $operation", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "BrAPI account write rejected: $operation", e)
        null
    }

    private fun canDisplayAccount(am: AccountManager, account: Account): Boolean {
        val ownerPackage = ownerPackageOf(account)
        if (!BrapiAccountConstants.canPackageAccessAccount(
                ownerPackage,
                context.packageName
            )
        ) return false
        if (ownerPackage == context.packageName) return true
        return hasGrant(am, account)
    }

    private fun canAccessAccount(am: AccountManager, account: Account): Boolean =
        BrapiAccountConstants.canPackageAccessAccount(ownerPackageOf(account), context.packageName)

    private fun canUseToken(am: AccountManager, account: Account): Boolean {
        val ownerPackage = ownerPackageOf(account)
        if (!BrapiAccountConstants.canPackageAccessAccount(
                ownerPackage,
                context.packageName
            )
        ) return false
        if (ownerPackage.isNullOrEmpty() || ownerPackage == context.packageName) return true
        return hasGrant(am, account)
    }

    /**
     * Whether the user has let this app use [account].
     *
     * The grant the owner records on the account is only readable by co-signed callers, so the
     * local copy written by [grantSelectedAccount] when the user picks the account is what
     * normally answers this.
     */
    private fun hasGrant(am: AccountManager, account: Account): Boolean {
        val accountGrant = readUserData(
            am,
            account,
            BrapiAccountConstants.grantedPackageKey(context.packageName),
        ) == "true"
        val localGrant = preferences.getBoolean(localGrantPreferenceKey(account), false)
        return accountGrant || localGrant
    }

    /**
     * Keyed on the account's identity rather than its server URL, since the URL lives in user data
     * this app cannot read on an account another app owns.
     */
    private fun localGrantPreferenceKey(account: Account): String =
        "$GRANT_PREFERENCE_PREFIX${account.type}$GRANT_KEY_SEPARATOR${account.name}"

    private fun extractHostname(url: String): String = try {
        java.net.URL(url).host.ifEmpty { url }
    } catch (_: Exception) {
        url
    }

    private fun configureOwnedAccountVisibility(am: AccountManager, account: Account) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        accountWrite("setAccountVisibility") {
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

    companion object {
        private const val TAG = "BrapiAccountRepo"
        private const val GRANT_PREFERENCE_PREFIX = "brapi_account_grant_"
        private const val GRANT_KEY_SEPARATOR = "_"
    }
}
