package org.phenoapps.brapi.account

data class BrapiPreferenceKeys(
    val enabled: String,
    val baseUrl: String,
    val displayName: String,
    val accessToken: String,
    val idToken: String,
)
