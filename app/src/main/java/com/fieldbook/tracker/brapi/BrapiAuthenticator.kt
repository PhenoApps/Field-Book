package com.fieldbook.tracker.brapi

import android.content.Context
import com.fieldbook.tracker.activities.brapi.BrapiAddAccountActivity
import org.phenoapps.brapi.BrapiAccountConstants
import org.phenoapps.brapi.account.BrapiAccountAuthenticator

class BrapiAuthenticator(context: Context) : BrapiAccountAuthenticator(
    context = context,
    addAccountActivityClass = BrapiAddAccountActivity::class.java,
    authTokenLabel = context.getString(
        org.phenoapps.brapi.R.string.pheno_brapi_auth_token_label,
    ),
) {

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
}
