package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.fieldbook.tracker.preferences.PreferenceKeys
import dagger.hilt.android.qualifiers.ActivityContext
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.Preconditions
import net.openid.appauth.connectivity.ConnectionBuilder
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

class OpenAuthConfigurationUtil @Inject constructor(
    @ActivityContext private val context: Context,
    private val preferences: SharedPreferences,
) {

    companion object {
        private const val HTTP: String = "http"
        private const val HTTPS: String = "https"
    }

    fun getConnectionBuilder(): ConnectionBuilder {
        return ConnectionBuilder { uri: Uri ->
            Log.d("ConnectionBuilder", "DOING CUSTOM URL")
            Preconditions.checkNotNull(uri, "url must not be null")
            Preconditions.checkArgument(
                HTTP == uri.scheme || HTTPS == uri.scheme,
                "scheme or uri must be http or https"
            )
            var conn = URL(uri.toString()).openConnection() as HttpURLConnection

            //conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            //conn.setReadTimeout(READ_TIMEOUT_MS);

            conn.instanceFollowRedirects = true

            // normally, 3xx is redirect
            val status = conn.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
                // get redirect url from "location" header field
                val newUrl = conn.getHeaderField("Location")
                // get the cookie if need, for login
                val cookies = conn.getHeaderField("Set-Cookie")
                conn.disconnect()
                // open the new connection again
                conn = URL(newUrl).openConnection() as HttpURLConnection
                conn.setRequestProperty("Cookie", cookies)
            } else {
                conn = URL(uri.toString()).openConnection() as HttpURLConnection
            }
            conn
        }
    }

    fun getAuthServiceConfiguration(
        onRetrieveConfiguration: (AuthorizationServiceConfiguration?, Exception?) -> Unit,
    ) {
        preferences.edit {
            putString(PreferenceKeys.BRAPI_TOKEN, null)
        }

        try {

            val oidcConfigURI =
                (preferences.getString(PreferenceKeys.BRAPI_OIDC_URL, "") ?: "").toUri()

            val builder = getConnectionBuilder()

            AuthorizationServiceConfiguration.fetchFromUrl(
                oidcConfigURI, { serviceConfig, ex ->
                    onRetrieveConfiguration(serviceConfig, ex)
                }, builder
            )
        } catch (ex: Exception) {
            onRetrieveConfiguration(null, ex)
        }
    }
}