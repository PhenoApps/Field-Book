package org.phenoapps.brapi.ui

import android.content.Context
import org.json.JSONObject
import org.phenoapps.brapi.R

fun defaultBrapiAccountState(
    context: Context,
    oidcClientId: String = "",
): BrapiAccountUiState =
    BrapiAccountUiState(
        oidcFlow = context.getString(R.string.pheno_brapi_oidc_flow_oauth_implicit),
        brapiVersion = "V2",
        oidcClientId = oidcClientId,
    )

fun BrapiAccountUiState.withUrlUpdate(url: String): BrapiAccountUiState {
    val derivedOidcUrl = if (!oidcUrlExplicitlySet && url.isNotEmpty() && url != "https://") {
        url.trimEnd('/') + "/.well-known/openid-configuration"
    } else {
        oidcUrl
    }
    return copy(url = url, oidcUrl = derivedOidcUrl)
}

fun parseBrapiConfig(json: String): BrapiAccountConfig? = runCatching {
    val obj = JSONObject(json)
    BrapiAccountConfig(
        url = obj.nonEmptyString("url"),
        name = obj.nonEmptyString("name"),
        version = obj.nonEmptyString("version", "v"),
        authFlow = obj.nonEmptyString("authFlow", "flow"),
        oidcUrl = obj.nonEmptyString("oidcUrl", "oidc"),
        clientId = obj.nonEmptyString("clientId"),
        scope = obj.nonEmptyString("scope"),
        pageSize = obj.nonEmptyString("pageSize", "ps"),
        chunkSize = obj.nonEmptyString("chunkSize", "cs"),
        serverTimeoutMilli = obj.nonEmptyString("serverTimeoutMilli", "st"),
    )
}.getOrNull()

private fun JSONObject.nonEmptyString(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        optString(key).takeIf { it.isNotEmpty() }
    }

fun BrapiAccountUiState.withConfig(config: BrapiAccountConfig): BrapiAccountUiState =
    copy(
        url = config.url ?: url,
        displayName = config.name ?: displayName,
        brapiVersion = when {
            config.version.equals("v1", ignoreCase = true) -> "V1"
            config.version.equals("v2", ignoreCase = true) -> "V2"
            else -> brapiVersion
        },
        oidcFlow = normalizeOidcFlow(config.authFlow) ?: oidcFlow,
        oidcUrl = config.oidcUrl ?: oidcUrl,
        oidcClientId = config.clientId ?: oidcClientId,
        oidcScope = config.scope ?: oidcScope,
        oidcUrlExplicitlySet = !config.oidcUrl.isNullOrEmpty() || oidcUrlExplicitlySet,
    )

/**
 * Normalizes a raw OAuth flow value (e.g. from a scanned QR code) to the display string
 * expected by RadioPickerField and BrapiAuthActivity. Returns null when [raw] is null so
 * the caller can fall back to the existing state value.
 */
private fun normalizeOidcFlow(raw: String?): String? = when {
    raw == null -> null
    raw.equals("code", ignoreCase = true) ||
    raw.equals("authorization_code", ignoreCase = true) -> "OAuth2 Authorization Code"
    raw.equals("implicit", ignoreCase = true) ||
    raw.equals("token", ignoreCase = true) -> "OAuth2 Implicit Grant"
    else -> raw  // already a display string or app-specific value — pass through
}

fun isValidBrapiUrl(url: String): Boolean {
    if (url.contains(' ')) return false
    return runCatching {
        val parsed = java.net.URL(url)
        val scheme = parsed.protocol
        val host = parsed.host ?: return false
        (scheme == "http" || scheme == "https") && host.isNotEmpty() &&
            (host.contains('.') || host.startsWith('[') || host == "localhost")
    }.getOrDefault(false)
}
