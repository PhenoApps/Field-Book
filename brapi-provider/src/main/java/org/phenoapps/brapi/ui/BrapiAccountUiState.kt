package org.phenoapps.brapi.ui

data class BrapiAccountUiState(
    val url: String = "",
    val displayName: String = "",
    val oidcUrl: String = "",
    val oidcClientId: String = "",
    val oidcScope: String = "",
    val oidcFlow: String = "",
    val brapiVersion: String = "V2",
    val oidcUrlExplicitlySet: Boolean = false,
    val currentStep: Int = 0,
    val isFetchingDisplayName: Boolean = false,
)

/**
 * A single BrAPI account's connection settings, as carried by a shared config (QR transfer).
 *
 * Deliberately limited to per-account values. Transfer tuning such as page size, chunk size and
 * timeout is configured once per device rather than per server, so sharing one account's config
 * must not carry it along and overwrite the scanning device's settings.
 */
data class BrapiAccountConfig(
    val url: String? = null,
    val name: String? = null,
    val version: String? = null,
    val authFlow: String? = null,
    val oidcUrl: String? = null,
    val clientId: String? = null,
    val scope: String? = null,
)
