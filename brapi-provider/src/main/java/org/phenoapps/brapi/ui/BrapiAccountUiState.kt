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

data class BrapiAccountConfig(
    val url: String? = null,
    val name: String? = null,
    val version: String? = null,
    val authFlow: String? = null,
    val oidcUrl: String? = null,
    val clientId: String? = null,
    val scope: String? = null,
    val pageSize: String? = null,
    val chunkSize: String? = null,
    val serverTimeoutMilli: String? = null,
)
