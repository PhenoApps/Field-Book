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

    fun callingPackageForAccount(account: Account, options: Bundle?): String? {
        val ownerPackage = accountManager.getUserData(account, BrapiAccountConstants.KEY_OWNER_PACKAGE)
        val callingPackages = getCallingPackages(options)
        return callingPackages.firstOrNull { it == ownerPackage }
            ?: callingPackages.firstOrNull {
                BrapiAccountConstants.canPackageAccessAccount(ownerPackage, it)
            }
    }

    fun needsLegacyAccessGrant(account: Account, callingPackage: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return false
        val ownerPackage = accountManager.getUserData(account, BrapiAccountConstants.KEY_OWNER_PACKAGE)
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

    fun findAccount(accountName: String): Account? =
        accountManager.getAccountsByType(BrapiAccountConstants.ACCOUNT_TYPE)
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
