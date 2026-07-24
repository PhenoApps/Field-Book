package org.phenoapps.brapi.account

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.phenoapps.brapi.BrapiAccountConstants

open class BrapiAccountAuthenticator(
    private val context: Context,
    private val addAccountActivityClass: Class<out Activity>,
    private val authTokenLabel: String,
) : AbstractAccountAuthenticator(context) {

    private val accessPolicy = BrapiAccountAccessPolicy(context)

    override fun addAccount(
        response: AccountAuthenticatorResponse,
        accountType: String,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle?,
    ): Bundle {
        if (options?.getBoolean(BrapiAccountConstants.OPTION_SHARED_ACCOUNT_CHOOSER, false) == true) {
            return addAccountIntentBundle(response, showInAppAdderToast = true)
        }

        if (!accessPolicy.callerCanAddAccount(options)) {
            return accessPolicy.addAccountInAppOnlyBundle()
        }

        return addAccountIntentBundle(response, showInAppAdderToast = false)
    }

    override fun getAuthToken(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String,
        options: Bundle?,
    ): Bundle {
        val am = AccountManager.get(context)
        val ownerPackage = am.getUserData(account, BrapiAccountConstants.KEY_OWNER_PACKAGE)
        val callingPackage = accessPolicy.callingPackageForAccount(account, options)
            ?: return accessPolicy.permissionDeniedBundle()

        if (ownerPackage != null && callingPackage != ownerPackage) {
            if (!accessPolicy.isVisibleToCaller(account, callingPackage)) {
                return accessPolicy.permissionDeniedBundle()
            }
            if (accessPolicy.needsLegacyAccessGrant(account, callingPackage)) {
                return Bundle().apply {
                    putParcelable(
                        AccountManager.KEY_INTENT,
                        BrapiAccessConfirmActivity.intent(
                            context = context,
                            response = response,
                            accountName = account.name,
                            callingPackage = callingPackage,
                        ),
                    )
                }
            }
        }

        val token = am.peekAuthToken(account, authTokenType)
        if (!token.isNullOrEmpty()) {
            return accessPolicy.tokenResultBundle(account, authTokenType)
        }

        if (ownerPackage != null && callingPackage != ownerPackage) {
            return accessPolicy.permissionDeniedBundle()
        }

        return Bundle().apply {
            putParcelable(
                AccountManager.KEY_INTENT,
                Intent(context, addAccountActivityClass).apply {
                    putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
                    putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name)
                    putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type)
                    putExtra(AccountManager.KEY_AUTH_TOKEN_LABEL, authTokenType)
                },
            )
        }
    }

    override fun getAuthTokenLabel(authTokenType: String): String = authTokenLabel

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

    private fun addAccountIntentBundle(
        response: AccountAuthenticatorResponse,
        showInAppAdderToast: Boolean,
    ): Bundle =
        Bundle().apply {
            putParcelable(
                AccountManager.KEY_INTENT,
                Intent(context, addAccountActivityClass).apply {
                    putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
                    putExtra(BrapiAccountConstants.EXTRA_SHOW_IN_APP_ADDER_TOAST, showInAppAdderToast)
                },
            )
        }
}
