package com.fieldbook.tracker.brapi

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.BrapiAddAccountActivity

class BrapiAuthenticator(private val context: Context) : AbstractAccountAuthenticator(context) {

    companion object {
        const val ACCOUNT_TYPE = "org.phenoapps.brapi"
        const val AUTH_TOKEN_TYPE = "access_token"
        const val READ_TOKEN_PERMISSION = "org.phenoapps.brapi.READ_TOKEN"
        const val KEY_ID_TOKEN = "id_token"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_OIDC_URL = "oidc_url"
        const val KEY_OIDC_FLOW = "oidc_flow"
        const val KEY_OIDC_CLIENT_ID = "oidc_client_id"
        const val KEY_OIDC_SCOPE = "oidc_scope"
        const val KEY_BRAPI_VERSION = "brapi_version"
    }

    override fun addAccount(
        response: AccountAuthenticatorResponse,
        accountType: String,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle
    ): Bundle {
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
        options: Bundle
    ): Bundle {
        // Enforce that callers hold the org.phenoapps.brapi.READ_TOKEN permission
        if (context.checkCallingOrSelfPermission(READ_TOKEN_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return Bundle().apply {
                putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_INVALID_RESPONSE)
                putString(AccountManager.KEY_ERROR_MESSAGE, "Caller does not hold $READ_TOKEN_PERMISSION")
            }
        }
        val am = AccountManager.get(context)
        val token = am.peekAuthToken(account, authTokenType)
        if (!token.isNullOrEmpty()) {
            return Bundle().apply {
                putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
                putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
                putString(AccountManager.KEY_AUTHTOKEN, token)
            }
        }
        // Token not cached — launch re-authentication
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

    override fun getAuthTokenLabel(authTokenType: String): String = context.getString(R.string.brapi_auth_token_label)

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        options: Bundle?
    ): Bundle? = null

    override fun editProperties(
        response: AccountAuthenticatorResponse,
        accountType: String
    ): Bundle? = null

    override fun updateCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String?,
        options: Bundle?
    ): Bundle? = null

    override fun hasFeatures(
        response: AccountAuthenticatorResponse,
        account: Account,
        features: Array<out String>
    ): Bundle = Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }
}
