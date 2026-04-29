package com.fieldbook.shared.brapi

import com.fieldbook.shared.preferences.PreferenceKeys
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.decodeURLQueryComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val BRAPI_REDIRECT_URI = "fieldbook-kmp://brapi/auth"
private const val OPENID_SCOPE = "openid"

sealed interface BrapiOAuthResult {
    data object Success : BrapiOAuthResult
    data class Error(val message: String) : BrapiOAuthResult
}

suspend fun authorizeBrapiImplicit(
    settings: Settings = Settings(),
    httpClient: HttpClient = HttpClient()
): BrapiOAuthResult {
    settings.remove(PreferenceKeys.BRAPI_TOKEN)

    return try {
        val discoveryUrl = settings.getString(PreferenceKeys.BRAPI_OIDC_URL, "")
        val authorizationEndpoint = fetchAuthorizationEndpoint(httpClient, discoveryUrl)
        val authUrl = buildImplicitAuthorizationUrl(
            authorizationEndpoint = authorizationEndpoint,
            clientId = settings.getString(PreferenceKeys.BRAPI_OIDC_CLIENT_ID, "fieldbook"),
            scope = settings.getString(PreferenceKeys.BRAPI_OIDC_SCOPE, "")
        )
        val callbackUrl = openBrapiAuthorizationUrl(authUrl, BRAPI_REDIRECT_URI)
            ?: return BrapiOAuthResult.Error("Authorization was cancelled.")
        val token = extractParameter(callbackUrl, "access_token")
            ?: return BrapiOAuthResult.Error("Authorization response did not include an access token.")

        settings.putString(PreferenceKeys.BRAPI_TOKEN, token.removePrefix("Bearer "))
        BrapiOAuthResult.Success
    } catch (exception: Exception) {
        BrapiOAuthResult.Error(exception.message ?: "Authorization failed.")
    } finally {
        httpClient.close()
    }
}

private suspend fun fetchAuthorizationEndpoint(
    httpClient: HttpClient,
    discoveryUrl: String
): String {
    val discoveryJson = httpClient.get(discoveryUrl).body<String>()
    return Json.parseToJsonElement(discoveryJson)
        .jsonObject["authorization_endpoint"]
        ?.jsonPrimitive
        ?.content
        ?: error("OIDC discovery document is missing authorization_endpoint.")
}

private fun buildImplicitAuthorizationUrl(
    authorizationEndpoint: String,
    clientId: String,
    scope: String
): String {
    val authScope = (scope.trim().split(Regex("\\s+")) + OPENID_SCOPE)
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" ")

    return URLBuilder(authorizationEndpoint).apply {
        parameters.append("client_id", clientId)
        parameters.append("redirect_uri", BRAPI_REDIRECT_URI)
        parameters.append("response_type", "token")
        parameters.append("scope", authScope)
        parameters.append("prompt", "login")
    }.buildString()
}

private fun extractParameter(url: String, name: String): String? {
    val fragment = url.substringAfter('#', missingDelimiterValue = "")
    val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
    return (fragment.takeIf { it.isNotEmpty() } ?: query)
        .split('&')
        .asSequence()
        .mapNotNull { part ->
            val key = part.substringBefore('=', missingDelimiterValue = "")
            val value = part.substringAfter('=', missingDelimiterValue = "")
            if (key == name) value.decodeURLQueryComponent() else null
        }
        .firstOrNull()
}

expect suspend fun openBrapiAuthorizationUrl(authUrl: String, redirectUri: String): String?
