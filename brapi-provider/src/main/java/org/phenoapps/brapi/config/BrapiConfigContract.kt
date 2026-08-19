package org.phenoapps.brapi.config

import android.net.Uri
import org.phenoapps.brapi.BrapiAccountConstants

/**
 * The read-only surface each app exposes so sibling PhenoApps can describe its BrAPI accounts.
 *
 * AccountManager gates `getUserData` on a signature match with the account type's authenticator,
 * so an app cannot read another app's account config directly once each owns its own type. Tokens
 * still cross through the authenticator, but a shared server card needs its URL, version and OIDC
 * settings before the user has granted anything — hence this provider.
 *
 * Nothing secret travels here: tokens are deliberately absent, and [COLUMN_HAS_TOKEN] reports only
 * whether one exists so a card can show whether the server is signed in.
 */
object BrapiConfigContract {

    const val PATH_ACCOUNTS = "accounts"

    const val COLUMN_ACCOUNT_NAME = "account_name"
    const val COLUMN_SERVER_URL = "server_url"
    const val COLUMN_DISPLAY_NAME = "display_name"
    const val COLUMN_BRAPI_VERSION = "brapi_version"
    const val COLUMN_OIDC_URL = "oidc_url"
    const val COLUMN_OIDC_FLOW = "oidc_flow"
    const val COLUMN_OIDC_CLIENT_ID = "oidc_client_id"
    const val COLUMN_OIDC_SCOPE = "oidc_scope"
    const val COLUMN_HAS_TOKEN = "has_token"

    val COLUMNS = arrayOf(
        COLUMN_ACCOUNT_NAME,
        COLUMN_SERVER_URL,
        COLUMN_DISPLAY_NAME,
        COLUMN_BRAPI_VERSION,
        COLUMN_OIDC_URL,
        COLUMN_OIDC_FLOW,
        COLUMN_OIDC_CLIENT_ID,
        COLUMN_OIDC_SCOPE,
        COLUMN_HAS_TOKEN,
    )

    /** The accounts table exposed by [packageName]. */
    fun accountsUri(packageName: String): Uri = Uri.parse(
        "content://${BrapiAccountConstants.configAuthorityFor(packageName)}/$PATH_ACCOUNTS",
    )
}

/**
 * A BrAPI account's configuration, sourced either from AccountManager user data (for accounts this
 * app owns) or from the owning app's [BrapiConfigContract] provider (for shared ones).
 */
data class BrapiAccountInfo(
    val accountName: String,
    val ownerPackage: String,
    val serverUrl: String = "",
    val displayName: String = "",
    val brapiVersion: String = "",
    val oidcUrl: String = "",
    val oidcFlow: String = "",
    val oidcClientId: String = "",
    val oidcScope: String = "",
    val hasToken: Boolean = false,
) {
    /** What to show for this account, falling back to the account name when unnamed. */
    val label: String get() = displayName.ifEmpty { accountName }
}
