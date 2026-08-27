package org.phenoapps.brapi.config

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import org.phenoapps.brapi.BrapiAccountConstants
import org.phenoapps.brapi.account.BrapiAccountAccessPolicy

/**
 * Publishes this app's BrAPI account config to sibling PhenoApps.
 *
 * Only accounts this app owns are served, and only to a caller that is both a known PhenoApps
 * package and one the account has been made visible to — the same grant the user performs in the
 * account chooser. The read-token permission on the provider is `normal`, so it is a declaration
 * of intent rather than a boundary; the caller check and the visibility check are what actually
 * gate this.
 */
class BrapiConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null
        if (uri.lastPathSegment != BrapiConfigContract.PATH_ACCOUNTS) return null

        val caller = callingPackage
        if (!BrapiAccountConstants.isPackageAllowed(caller) || caller == null) {
            Log.w(TAG, "Refusing BrAPI config request from $caller")
            return null
        }

        val am = AccountManager.get(context)
        val policy = BrapiAccountAccessPolicy(context)
        val ownType = BrapiAccountConstants.accountTypeFor(context.packageName)
        val cursor = MatrixCursor(BrapiConfigContract.COLUMNS)

        val accounts = runCatching { am.getAccountsByType(ownType) }.getOrNull() ?: return cursor
        for (account in accounts) {
            // The caller has to have been granted this account already. Ungranted accounts are
            // discovered through the system account chooser instead, which is where the user
            // makes that call — publishing them here would route around it.
            if (caller != context.packageName && !policy.isVisibleToCaller(account, caller)) continue
            cursor.addRow(rowFor(am, account))
        }
        return cursor
    }

    private fun rowFor(am: AccountManager, account: Account): Array<Any?> {
        fun data(key: String): String = runCatching { am.getUserData(account, key) }
            .getOrNull()
            .orEmpty()

        val hasToken = runCatching {
            !am.peekAuthToken(account, BrapiAccountConstants.AUTH_TOKEN_TYPE).isNullOrEmpty()
        }.getOrDefault(false)

        return arrayOf(
            account.name,
            data(BrapiAccountConstants.KEY_SERVER_URL),
            data(BrapiAccountConstants.KEY_DISPLAY_NAME).ifEmpty { account.name },
            data(BrapiAccountConstants.KEY_BRAPI_VERSION),
            data(BrapiAccountConstants.KEY_OIDC_URL),
            data(BrapiAccountConstants.KEY_OIDC_FLOW),
            data(BrapiAccountConstants.KEY_OIDC_CLIENT_ID),
            data(BrapiAccountConstants.KEY_OIDC_SCOPE),
            if (hasToken) 1 else 0,
        )
    }

    override fun getType(uri: Uri): String =
        "vnd.android.cursor.dir/vnd.${BrapiAccountConstants.ACCOUNT_TYPE_PREFIX}.account"

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("BrAPI config is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("BrAPI config is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("BrAPI config is read-only")

    companion object {
        private const val TAG = "BrapiConfigProvider"
    }
}
