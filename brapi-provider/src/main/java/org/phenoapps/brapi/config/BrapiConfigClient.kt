package org.phenoapps.brapi.config

import android.content.Context
import android.database.Cursor
import android.util.Log

/**
 * Reads BrAPI account config published by sibling PhenoApps.
 *
 * Every failure here is expected traffic rather than an error: the sibling may not be installed,
 * may predate the provider, or may simply not have granted this app the account. All of them come
 * back as "no config", which callers render as an account they can see but not yet describe.
 */
class BrapiConfigClient(private val context: Context) {

    /** Every account [ownerPackage] has published to this app, empty when it publishes none. */
    fun accountsFrom(ownerPackage: String): List<BrapiAccountInfo> {
        val uri = BrapiConfigContract.accountsUri(ownerPackage)
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toAccountInfo(ownerPackage))
                    }
                }
            }.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "No BrAPI config available from $ownerPackage", e)
            emptyList()
        }
    }

    private fun Cursor.toAccountInfo(ownerPackage: String): BrapiAccountInfo {
        fun text(column: String): String {
            val index = getColumnIndex(column)
            return if (index < 0) "" else getString(index).orEmpty()
        }

        fun flag(column: String): Boolean {
            val index = getColumnIndex(column)
            return index >= 0 && getInt(index) == 1
        }

        val accountName = text(BrapiConfigContract.COLUMN_ACCOUNT_NAME)
        return BrapiAccountInfo(
            accountName = accountName,
            ownerPackage = ownerPackage,
            serverUrl = text(BrapiConfigContract.COLUMN_SERVER_URL),
            displayName = text(BrapiConfigContract.COLUMN_DISPLAY_NAME).ifEmpty { accountName },
            brapiVersion = text(BrapiConfigContract.COLUMN_BRAPI_VERSION),
            oidcUrl = text(BrapiConfigContract.COLUMN_OIDC_URL),
            oidcFlow = text(BrapiConfigContract.COLUMN_OIDC_FLOW),
            oidcClientId = text(BrapiConfigContract.COLUMN_OIDC_CLIENT_ID),
            oidcScope = text(BrapiConfigContract.COLUMN_OIDC_SCOPE),
            hasToken = flag(BrapiConfigContract.COLUMN_HAS_TOKEN),
        )
    }

    companion object {
        private const val TAG = "BrapiConfigClient"
    }
}
