package com.fieldbook.tracker.brapi

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.BrapiAccessConfirmActivity
import com.fieldbook.tracker.activities.brapi.BrapiAddAccountActivity
import org.phenoapps.brapi.BrapiAccountConstants

class BrapiAuthenticator(private val context: Context) : AbstractAccountAuthenticator(context) {

    companion object {
        const val ACCOUNT_TYPE = BrapiAccountConstants.ACCOUNT_TYPE
        const val AUTH_TOKEN_TYPE = BrapiAccountConstants.AUTH_TOKEN_TYPE
        const val READ_TOKEN_PERMISSION = BrapiAccountConstants.READ_TOKEN_PERMISSION
        const val KEY_ID_TOKEN = BrapiAccountConstants.KEY_ID_TOKEN
        const val KEY_SERVER_URL = BrapiAccountConstants.KEY_SERVER_URL
        const val KEY_DISPLAY_NAME = BrapiAccountConstants.KEY_DISPLAY_NAME
        const val KEY_OIDC_URL = BrapiAccountConstants.KEY_OIDC_URL
        const val KEY_OIDC_FLOW = BrapiAccountConstants.KEY_OIDC_FLOW
        const val KEY_OIDC_CLIENT_ID = BrapiAccountConstants.KEY_OIDC_CLIENT_ID
        const val KEY_OIDC_SCOPE = BrapiAccountConstants.KEY_OIDC_SCOPE
        const val KEY_BRAPI_VERSION = BrapiAccountConstants.KEY_BRAPI_VERSION
        const val KEY_OWNER_PACKAGE = BrapiAccountConstants.KEY_OWNER_PACKAGE

        val ALLOWED_PACKAGES get() = BrapiAccountConstants.ALLOWED_PACKAGES

        @JvmStatic
        fun isPackageAllowed(packageName: String?): Boolean =
            BrapiAccountConstants.isPackageAllowed(packageName)

        @JvmStatic
        fun canPackageAccessAccount(ownerPackage: String?, packageName: String?): Boolean =
            BrapiAccountConstants.canPackageAccessAccount(ownerPackage, packageName)

        @JvmStatic
        fun grantedPackageKey(packageName: String): String =
            BrapiAccountConstants.grantedPackageKey(packageName)
    }

    override fun addAccount(
        response: AccountAuthenticatorResponse,
        accountType: String,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle,
    ): Bundle {
        if (options.getBoolean(BrapiAccountConstants.OPTION_SHARED_ACCOUNT_CHOOSER, false)) {
            val intent = Intent(context, BrapiAddAccountActivity::class.java).apply {
                putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
                putExtra(BrapiAccountConstants.EXTRA_SHOW_IN_APP_ADDER_TOAST, true)
            }
            return Bundle().apply {
                putParcelable(AccountManager.KEY_INTENT, intent)
            }
        }

        val callingPackages = getCallingPackages(options)
        val callerIsAllowed = callingPackages.any { isPackageAllowed(it) }
        if (!callerIsAllowed) {
            return Bundle().apply {
                putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)
                putString(
                    AccountManager.KEY_ERROR_MESSAGE,
                    context.getString(org.phenoapps.brapi.R.string.pheno_brapi_add_account_in_app_only),
                )
            }
        }

        val intent = Intent(context, BrapiAddAccountActivity::class.java).apply {
            putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        }
        return Bundle().apply {
            putParcelable(AccountManager.KEY_INTENT, intent)
        }
    }

    override fun getAuthToken(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String,
        options: Bundle,
    ): Bundle {
        val am = AccountManager.get(context)
        val ownerPkg = am.getUserData(account, BrapiAccountConstants.KEY_OWNER_PACKAGE)
        val callingPackages = getCallingPackages(options)
        val callingPkg = callingPackages.firstOrNull { it == ownerPkg }
            ?: callingPackages.firstOrNull { canPackageAccessAccount(ownerPkg, it) }
            ?: return permissionDeniedBundle()

        if (ownerPkg != null && callingPkg != ownerPkg) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!isAccountVisibleToCaller(am, account, callingPkg)) {
                    return permissionDeniedBundle()
                }
            } else {
                val grantKey = grantedPackageKey(callingPkg)
                val alreadyGranted = am.getUserData(account, grantKey) == "true"
                if (!alreadyGranted) {
                    val intent = Intent(context, BrapiAccessConfirmActivity::class.java).apply {
                        putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
                        putExtra(BrapiAccessConfirmActivity.EXTRA_ACCOUNT_NAME, account.name)
                        putExtra(BrapiAccessConfirmActivity.EXTRA_CALLING_PACKAGE, callingPkg)
                    }
                    return Bundle().apply { putParcelable(AccountManager.KEY_INTENT, intent) }
                }
            }
        }

        val token = am.peekAuthToken(account, authTokenType)
        if (!token.isNullOrEmpty()) {
            return Bundle().apply {
                putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
                putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
                putString(AccountManager.KEY_AUTHTOKEN, token)
            }
        }

        if (ownerPkg != null && callingPkg != ownerPkg) {
            return permissionDeniedBundle()
        }

        val intent = Intent(context, BrapiAddAccountActivity::class.java).apply {
            putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
            putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name)
            putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type)
            putExtra(AccountManager.KEY_AUTH_TOKEN_LABEL, authTokenType)
        }
        return Bundle().apply {
            putParcelable(AccountManager.KEY_INTENT, intent)
        }
    }

    override fun getAuthTokenLabel(authTokenType: String): String =
        context.getString(R.string.brapi_auth_token_label)

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        options: Bundle?,
    ): Bundle? = null

    override fun editProperties(
        response: AccountAuthenticatorResponse,
        accountType: String,
    ): Bundle? = null

    override fun updateCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String?,
        options: Bundle?,
    ): Bundle? = null

    override fun hasFeatures(
        response: AccountAuthenticatorResponse,
        account: Account,
        features: Array<out String>,
    ): Bundle = Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }

    private fun getCallingPackages(options: Bundle): List<String> {
        val callerUid = if (options.containsKey(AccountManager.KEY_CALLER_UID)) {
            options.getInt(AccountManager.KEY_CALLER_UID)
        } else {
            Binder.getCallingUid()
        }
        return context.packageManager.getPackagesForUid(callerUid)?.toList().orEmpty()
    }

    private fun isAccountVisibleToCaller(
        am: AccountManager,
        account: Account,
        packageName: String,
    ): Boolean {
        val visibility = runCatching {
            am.getAccountVisibility(account, packageName)
        }.getOrDefault(AccountManager.VISIBILITY_NOT_VISIBLE)
        return visibility == AccountManager.VISIBILITY_VISIBLE ||
            visibility == AccountManager.VISIBILITY_USER_MANAGED_VISIBLE
    }

    private fun permissionDeniedBundle(): Bundle =
        Bundle().apply {
            putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)
            putString(
                AccountManager.KEY_ERROR_MESSAGE,
                context.getString(org.phenoapps.brapi.R.string.pheno_brapi_auth_permission_deny),
            )
        }
}
