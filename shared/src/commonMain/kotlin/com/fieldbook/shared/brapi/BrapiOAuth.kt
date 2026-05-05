package com.fieldbook.shared.brapi

import com.fieldbook.shared.preferences.PreferenceKeys
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
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

private data class OpenIdConfiguration(
    val authorizationEndpoint: String,
    val tokenEndpoint: String?
)

suspend fun authorizeBrapi(
    useAuthorizationCodeFlow: Boolean,
    settings: Settings = Settings(),
    httpClient: HttpClient = HttpClient()
): BrapiOAuthResult {
    settings.remove(PreferenceKeys.BRAPI_TOKEN)
    settings.remove(PreferenceKeys.BRAPI_ID_TOKEN)

    return try {
        val discoveryUrl = settings.getString(PreferenceKeys.BRAPI_OIDC_URL, "")
        val openIdConfiguration = fetchOpenIdConfiguration(httpClient, discoveryUrl)
        val authUrl = buildAuthorizationUrl(
            authorizationEndpoint = openIdConfiguration.authorizationEndpoint,
            clientId = settings.getString(PreferenceKeys.BRAPI_OIDC_CLIENT_ID, "fieldbook"),
            scope = settings.getString(PreferenceKeys.BRAPI_OIDC_SCOPE, ""),
            responseType = if (useAuthorizationCodeFlow) "code" else "token"
        )
        val callbackUrl = openBrapiAuthorizationUrl(authUrl, BRAPI_REDIRECT_URI)
            ?: return BrapiOAuthResult.Error("Authorization was cancelled.")

        extractAuthorizationError(callbackUrl)?.let { error ->
            return BrapiOAuthResult.Error(error)
        }

        val tokenResponse = if (useAuthorizationCodeFlow) {
            val authorizationCode = extractParameter(callbackUrl, "code")
                ?: return BrapiOAuthResult.Error("Authorization response did not include an authorization code.")
            exchangeAuthorizationCode(
                httpClient = httpClient,
                tokenEndpoint = openIdConfiguration.tokenEndpoint
                    ?: return BrapiOAuthResult.Error("OIDC discovery document is missing token_endpoint."),
                clientId = settings.getString(PreferenceKeys.BRAPI_OIDC_CLIENT_ID, "fieldbook"),
                code = authorizationCode
            )
        } else {
            OAuthTokenPayload(
                accessToken = extractParameter(callbackUrl, "access_token")
                    ?: return BrapiOAuthResult.Error("Authorization response did not include an access token."),
                idToken = extractParameter(callbackUrl, "id_token")
            )
        }

        settings.putString(
            PreferenceKeys.BRAPI_TOKEN,
            tokenResponse.accessToken.removePrefix("Bearer ")
        )
        tokenResponse.idToken?.let {
            settings.putString(PreferenceKeys.BRAPI_ID_TOKEN, it)
        }
        BrapiOAuthResult.Success
    } catch (exception: Exception) {
        BrapiOAuthResult.Error(exception.message ?: "Authorization failed.")
    } finally {
        httpClient.close()
    }
}

private data class OAuthTokenPayload(
    val accessToken: String,
    val idToken: String? = null
)

private suspend fun fetchOpenIdConfiguration(
    httpClient: HttpClient,
    discoveryUrl: String
): OpenIdConfiguration {
    val discoveryJson = httpClient.get(discoveryUrl).body<String>()
    val discoveryObject = Json.parseToJsonElement(discoveryJson).jsonObject
    val authorizationEndpoint = discoveryObject["authorization_endpoint"]
        ?.jsonPrimitive
        ?.content
        ?: error("OIDC discovery document is missing authorization_endpoint.")
    val tokenEndpoint = discoveryObject["token_endpoint"]
        ?.jsonPrimitive
        ?.content

    return OpenIdConfiguration(
        authorizationEndpoint = authorizationEndpoint,
        tokenEndpoint = tokenEndpoint
    )
}

private fun buildAuthorizationUrl(
    authorizationEndpoint: String,
    clientId: String,
    scope: String,
    responseType: String
): String {
    val authScope = (scope.trim().split(Regex("\\s+")) + OPENID_SCOPE)
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" ")

    return URLBuilder(authorizationEndpoint).apply {
        parameters.append("client_id", clientId)
        parameters.append("redirect_uri", BRAPI_REDIRECT_URI)
        parameters.append("response_type", responseType)
        parameters.append("scope", authScope)
        parameters.append("prompt", "login")
    }.buildString()
}

private suspend fun exchangeAuthorizationCode(
    httpClient: HttpClient,
    tokenEndpoint: String,
    clientId: String,
    code: String
): OAuthTokenPayload {
    val tokenJson = httpClient.submitForm(
        url = tokenEndpoint,
        formParameters = Parameters.build {
            append("grant_type", "authorization_code")
            append("code", code)
            append("redirect_uri", BRAPI_REDIRECT_URI)
            append("client_id", clientId)
        }
    ).body<String>()

    val tokenObject = Json.parseToJsonElement(tokenJson).jsonObject
    val accessToken = tokenObject["access_token"]
        ?.jsonPrimitive
        ?.content
        ?: error("Token response did not include access_token.")

    return OAuthTokenPayload(
        accessToken = accessToken,
        idToken = tokenObject["id_token"]?.jsonPrimitive?.content
    )
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

private fun extractAuthorizationError(url: String): String? {
    val error = extractParameter(url, "error") ?: return null
    val description = extractParameter(url, "error_description")
    return description?.takeIf { it.isNotBlank() } ?: error
}

suspend fun authorizeBrapiImplicit(
    settings: Settings = Settings(),
    httpClient: HttpClient = HttpClient()
): BrapiOAuthResult = authorizeBrapi(
    useAuthorizationCodeFlow = false,
    settings = settings,
    httpClient = httpClient
)

expect suspend fun openBrapiAuthorizationUrl(authUrl: String, redirectUri: String): String?
