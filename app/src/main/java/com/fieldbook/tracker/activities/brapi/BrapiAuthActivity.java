package com.fieldbook.tracker.activities.brapi;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.ThemedActivity;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.preferences.PreferenceKeys;
import com.fieldbook.tracker.utilities.InsetHandler;

import net.openid.appauth.AppAuthConfiguration;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.Preconditions;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenResponse;
import net.openid.appauth.connectivity.ConnectionBuilder;

import java.net.HttpURLConnection;
import java.net.URL;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BrapiAuthActivity extends ThemedActivity {

    @Inject
    SharedPreferences preferences;

    private boolean activityStarting = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_brapi_auth);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(null);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        View rootView = findViewById(android.R.id.content);
        InsetHandler.INSTANCE.setupStandardInsets(rootView, toolbar);

        activityStarting = true;

        // Start our login process
        //when coming back from deep link this check keeps app from auto-re-authenticating
        if (getIntent() != null && getIntent().getData() == null) {
            String flow = preferences.getString(PreferenceKeys.BRAPI_OIDC_FLOW, "");
            if (flow.equals(getString(R.string.preferences_brapi_oidc_flow_old_custom))) {
                authorizeBrAPI_OLD(preferences, this);
            } else {
                authorizeBrAPI(preferences, this);
            }
        }

        getOnBackPressedDispatcher().addCallback(this, standardBackCallback());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        //getIntent() should always return the last received intent
    }

    @Override
    public void onResume() {
        super.onResume();

        if(activityStarting) {
            // If the activity has just started, ignore the onResume code
            activityStarting = false;
        }else{
            AuthorizationException ex = AuthorizationException.fromIntent(getIntent());
            Uri data = getIntent().getData();

            if (data != null) {
                // authorization completed
                String flow = preferences.getString(PreferenceKeys.BRAPI_OIDC_FLOW, "");
                if (flow.equals(getString(R.string.preferences_brapi_oidc_flow_old_custom))) {
                    checkBrapiAuth_OLD(data);
                } else {
                    checkBrapiAuth(data);
                }


            } else if (ex != null) {

                // authorization completed in error
                authError(ex);

            } else { //returning from deep link with null data should finish activity
                //otherwise the progress bar hangs

                getIntent().setData(null);

                finish();

            }
        }
    }

    private static final String HTTP = "http";
    private static final String HTTPS = "https";

    private ConnectionBuilder getConnectionBuilder() {
        return uri -> {
            Log.d("ConnectionBuilder", "DOING CUSTOM URL");
            Preconditions.checkNotNull(uri, "url must not be null");
            Preconditions.checkArgument(HTTP.equals(uri.getScheme()) || HTTPS.equals(uri.getScheme()),
                    "scheme or uri must be http or https");
            HttpURLConnection conn = (HttpURLConnection) new URL(uri.toString()).openConnection();
//                    conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
//                    conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            // normally, 3xx is redirect
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER) {
                // get redirect url from "location" header field
                String newUrl = conn.getHeaderField("Location");
                // get the cookie if need, for login
                String cookies = conn.getHeaderField("Set-Cookie");
                conn.disconnect();
                // open the new connection again
                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                conn.setRequestProperty("Cookie", cookies);
            } else {
                conn = (HttpURLConnection) new URL(uri.toString()).openConnection();
            }

            return conn;
        };
    }

    public void authorizeBrAPI(SharedPreferences sharedPreferences, Context context) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.BRAPI_TOKEN, null);
        editor.apply();

        String flow = sharedPreferences.getString(PreferenceKeys.BRAPI_OIDC_FLOW, "");
        final String responseType = flow.equals(getString(R.string.preferences_brapi_oidc_flow_oauth_implicit)) ?
                ResponseTypeValues.TOKEN : ResponseTypeValues.CODE;

        try {
            String clientId = sharedPreferences.getString(PreferenceKeys.BRAPI_OIDC_CLIENT_ID, "fieldbook");
            String scope = sharedPreferences.getString(PreferenceKeys.BRAPI_OIDC_SCOPE, "");

            // Authorization code flow works better with custom URL scheme fieldbook://app/auth
            // https://github.com/openid/AppAuth-Android/issues?q=is%3Aissue+intent+null
            Uri redirectURI = flow.equals(getString(R.string.preferences_brapi_oidc_flow_oauth_implicit)) ?
                    Uri.parse("https://phenoapps.org/field-book") : Uri.parse("fieldbook://app/auth");
            Uri oidcConfigURI = Uri.parse(sharedPreferences.getString(PreferenceKeys.BRAPI_OIDC_URL, ""));

            ConnectionBuilder builder = getConnectionBuilder();

            AuthorizationServiceConfiguration.fetchFromUrl(oidcConfigURI,
                    new AuthorizationServiceConfiguration.RetrieveConfigurationCallback() {
                        public void onFetchConfigurationCompleted(
                                @Nullable AuthorizationServiceConfiguration serviceConfig,
                                @Nullable AuthorizationException ex) {

                            if (ex != null) {
                                Log.e("BrAPIService", "failed to fetch configuration", ex);
                                authError(ex);
                                finish();
                                return;
                            }

                            try {

                                requestAuthorization(serviceConfig, clientId, responseType, redirectURI, scope, context);

                            } catch (IllegalArgumentException e) {

                                e.printStackTrace();

                                Toast.makeText(context, R.string.oauth_configured_incorrectly, Toast.LENGTH_LONG).show();

                                finish();
                            }
                        }

                    }, builder);

        } catch (Exception ex) {

            authError(ex);

        }
    }

    private void requestAuthorization(
            AuthorizationServiceConfiguration serviceConfig,
            String clientId,
            String responseType,
            Uri redirectURI,
            String scope,
            Context context) {

        AuthorizationRequest.Builder authRequestBuilder =
                new AuthorizationRequest.Builder(
                        serviceConfig, // the authorization service configuration
                        clientId, // the client ID, typically pre-registered and static
                        responseType, // the response_type value: token or code
                        redirectURI); // the redirect URI to which the auth response is sent

        if (!scope.trim().isEmpty()){
            authRequestBuilder.setScope(scope);
        }

        AuthorizationRequest authRequest = authRequestBuilder.setPrompt("login").build();

        AuthorizationService authService = getAuthorizationService();

        Intent responseIntent = new Intent(context, BrapiAuthActivity.class);
        responseIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        authService.performAuthorizationRequest(
                authRequest,
                PendingIntent.getActivity(context, 0, responseIntent, PendingIntent.FLAG_MUTABLE));
    }

    public void authorizeBrAPI_OLD(SharedPreferences sharedPreferences, Context context) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferenceKeys.BRAPI_TOKEN, null);
        editor.apply();

        try {
            String url = sharedPreferences.getString(PreferenceKeys.BRAPI_BASE_URL, "") + "/brapi/authorize?display_name=Field Book&return_url=fieldbook://";
            try {
                // Go to url with the default browser
                Uri uri = Uri.parse(url);
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                context.startActivity(i);
            } catch (ActivityNotFoundException ex) {
                Log.e("BrAPI", "Error starting BrAPI auth", ex);
                authError(ex);
            }
        } catch (Exception ex) {
            Log.e("BrAPI", "Error starting BrAPI auth", ex);
            authError(ex);
        }
    }

    private void authError(Exception ex) {

        // Clear our data from our deep link so the app doesn't think it is
        // coming from a deep link if it is coming from deep link on pause and resume.
        getIntent().setData(null);

        Log.e("BrAPI", "Error starting BrAPI auth", ex);
        Toast.makeText(this, R.string.brapi_auth_error_starting, Toast.LENGTH_LONG).show();
        setResult(RESULT_CANCELED);
        finish();
    }

    private void authSuccess(String accessToken) {

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PreferenceKeys.BRAPI_TOKEN, accessToken);
        editor.apply();

        // Clear our data from our deep link so the app doesn't think it is
        // coming from a deep link if it is coming from deep link on pause and resume.
        getIntent().setData(null);

        Log.d("BrAPI", "Auth successful");
        Toast.makeText(this, R.string.brapi_auth_success, Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
    }

    public void checkBrapiAuth_OLD(Uri data) {

        Integer status = Integer.parseInt(data.getQueryParameter("status"));

        // Check that we actually have the data. If not return failure.
        if (status == null) {
            authError(null);
            return;
        }

        if (status == 200) {
            String token = data.getQueryParameter("token");

            // Check that we received a token.
            if (token == null) {
                authError(null);
                return;
            }
            authSuccess(token);

        } else {
            authError(null);
        }
    }

    /**
     * Create an instance of AuthorizationService with custom connection builder.
     * @return Configured auth service
     */
    private AuthorizationService getAuthorizationService() {
        AppAuthConfiguration.Builder builder = new AppAuthConfiguration.Builder();
        builder.setConnectionBuilder(getConnectionBuilder());
        return new AuthorizationService(this, builder.build());
    }

    public void checkBrapiAuth(Uri data) {
        AuthorizationService authService = getAuthorizationService();
        AuthorizationException ex = AuthorizationException.fromIntent(getIntent());
        AuthorizationResponse response = AuthorizationResponse.fromIntent(getIntent());

        if (ex != null) {
            authError(ex);
            return;
        }

        if (response != null && response.authorizationCode != null) {
            authService.performTokenRequest(
                    response.createTokenExchangeRequest(),
                    new AuthorizationService.TokenResponseCallback() {
                        @Override
                        public void onTokenRequestCompleted(@Nullable TokenResponse response, @Nullable AuthorizationException ex) {
                            if (response != null && response.accessToken != null) {
                                authSuccess(response.accessToken);
                            } else {
                                authError(null);
                            }
                        }
                    });
            return;
        }

        if (response != null && response.accessToken != null) {
            authSuccess(response.accessToken);
            return;
        }

        // Original check for access_token
        data = Uri.parse(data.toString().replaceFirst("#", "?"));
        String token = data.getQueryParameter("access_token");
        // Check that we received a token.
        if (token == null) {
            authError(null);
            return;
        }

        if (token.startsWith("Bearer ")) {
            token = token.replaceFirst("Bearer ", "");
        }

        authSuccess(token);
    }
}
