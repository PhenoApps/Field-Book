package org.phenoapps.brapi.account

/**
 * Names of the host app's SharedPreference keys that mirror the active BrAPI account.
 *
 * [enabled], [baseUrl], [displayName], [accessToken] and [idToken] are required. The remaining
 * keys are optional: they exist so hosts that still build requests or re-authorize from
 * SharedPreferences keep those mirrors in step with the active account. A host that reads its
 * config straight from AccountManager can leave them null and nothing is written for them.
 */
data class BrapiPreferenceKeys(
    val enabled: String,
    val baseUrl: String,
    val displayName: String,
    val accessToken: String,
    val idToken: String,
    val oidcUrl: String? = null,
    val oidcFlow: String? = null,
    val oidcClientId: String? = null,
    val oidcScope: String? = null,
    val brapiVersion: String? = null,
)
