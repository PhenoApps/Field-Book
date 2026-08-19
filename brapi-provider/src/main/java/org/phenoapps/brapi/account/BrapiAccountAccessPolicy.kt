package org.phenoapps.brapi.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.Bundle
import androidx.annotation.StringRes
import org.phenoapps.brapi.BrapiAccountConstants
import org.phenoapps.brapi.R

class BrapiAccountAccessPolicy(
    private val context: Context,
) {
    private val accountManager: AccountManager = AccountManager.get(context)

    fun getCallingPackages(options: Bundle?): List<String> {
        options?.getString(AccountManager.KEY_ANDROID_PACKAGE_NAME)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return listOf(it) }

        return context.packageManager.getPackagesForUid(Binder.getCallingUid())?.toList().orEmpty()
    }

    fun callerCanAddAccount(options: Bundle?): Boolean {
        val packageFromOptions = options?.getString(AccountManager.KEY_ANDROID_PACKAGE_NAME)
        if (!packageFromOptions.isNullOrEmpty()) {
            return BrapiAccountConstants.isPackageAllowed(packageFromOptions)
        }
        return getCallingPackages(options).any { BrapiAccountConstants.isPackageAllowed(it) }
    }

    /**
     * The package that owns [account], read off its type.
     *
     * Per-app types encode their owner, which is both cheaper and more reliable than the user-data
     * copy — the copy is unreadable from anywhere but the owning app. Legacy accounts predate the
     * per-app types and still have to be asked.
     */
    fun ownerPackageOf(account: Account): String? =
        if (BrapiAccountConstants.isPerAppAccountType(account.type)) {
            account.type.removePrefix("${BrapiAccountConstants.ACCOUNT_TYPE_PREFIX}.")
        } else {
            runCatching {
                accountManager.getUserData(account, BrapiAccountConstants.KEY_OWNER_PACKAGE)
            }.getOrNull()
        }

    fun callingPackageForAccount(account: Account, options: Bundle?): String? {
        val ownerPackage = ownerPackageOf(account)
        val callingPackages = getCallingPackages(options)
        return callingPackages.firstOrNull { it == ownerPackage }
            ?: callingPackages.firstOrNull {
                BrapiAccountConstants.canPackageAccessAccount(ownerPackage, it)
            }
    }

    fun needsLegacyAccessGrant(account: Account, callingPackage: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return false
        val ownerPackage = ownerPackageOf(account)
        if (ownerPackage.isNullOrEmpty() || ownerPackage == callingPackage) return false
        return accountManager.getUserData(
            account,
            BrapiAccountConstants.grantedPackageKey(callingPackage),
        ) != "true"
    }

    fun isVisibleToCaller(account: Account, callingPackage: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val visibility = runCatching {
            accountManager.getAccountVisibility(account, callingPackage)
        }.getOrDefault(AccountManager.VISIBILITY_NOT_VISIBLE)
        return visibility == AccountManager.VISIBILITY_VISIBLE ||
            visibility == AccountManager.VISIBILITY_USER_MANAGED_VISIBLE
    }

    fun grantLegacyAccess(account: Account, callingPackage: String) {
        accountManager.setUserData(
            account,
            BrapiAccountConstants.grantedPackageKey(callingPackage),
            "true",
        )
    }

    /** Looks up one of this app's own accounts — the only ones its authenticator serves. */
    fun findAccount(accountName: String): Account? =
        accountManager.getAccountsByType(BrapiAccountConstants.accountTypeFor(context.packageName))
            .firstOrNull { it.name == accountName }

    fun tokenResultBundle(account: Account, authTokenType: String): Bundle =
        Bundle().apply {
            putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
            putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
            putString(AccountManager.KEY_AUTHTOKEN, accountManager.peekAuthToken(account, authTokenType))
        }

    fun permissionDeniedBundle(): Bundle =
        errorBundle(R.string.pheno_brapi_auth_permission_deny)

    fun addAccountInAppOnlyBundle(): Bundle =
        errorBundle(R.string.pheno_brapi_add_account_in_app_only)

    fun accountNotFoundBundle(): Bundle =
        Bundle().apply {
            putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_BAD_ARGUMENTS)
            putString(
                AccountManager.KEY_ERROR_MESSAGE,
                context.getString(R.string.pheno_brapi_account_not_found),
            )
        }

    private fun errorBundle(@StringRes messageRes: Int): Bundle =
        Bundle().apply {
            putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)
            putString(AccountManager.KEY_ERROR_MESSAGE, context.getString(messageRes))
        }
}
