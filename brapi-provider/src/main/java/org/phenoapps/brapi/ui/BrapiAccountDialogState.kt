package org.phenoapps.brapi.ui

import org.json.JSONObject
import org.phenoapps.brapi.BrapiAccountConstants

fun defaultBrapiAccountState(
    oidcClientId: String = "",
): BrapiAccountUiState =
    BrapiAccountUiState(
        // Stable identifier, not the localized label — this value is persisted and compared.
        oidcFlow = BrapiAccountConstants.OIDC_FLOW_OAUTH_IMPLICIT,
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
        // pageSize / chunkSize / serverTimeoutMilli are intentionally not read. Older configs
        // may still carry them from when a device had a single server; they are device-wide
        // settings and are left to the scanning device.
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
 * Normalizes a raw OAuth flow value to one of the stable identifiers in [BrapiAccountConstants].
 *
 * Handles the spec-style spellings a shared config (e.g. a scanned QR code) may carry, and
 * delegates anything else — stable ids, and legacy English display labels stored by older
 * versions — to the shared normalizer. Returns null when [raw] is null so the caller can fall
 * back to the existing state value.
 */
private fun normalizeOidcFlow(raw: String?): String? = when {
    raw == null -> null
    raw.equals("code", ignoreCase = true) ||
    raw.equals("authorization_code", ignoreCase = true) -> BrapiAccountConstants.OIDC_FLOW_OAUTH_CODE
    raw.equals("implicit", ignoreCase = true) ||
    raw.equals("token", ignoreCase = true) -> BrapiAccountConstants.OIDC_FLOW_OAUTH_IMPLICIT
    else -> BrapiAccountConstants.normalizeOidcFlow(raw)
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
