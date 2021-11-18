package com.fieldbook.tracker.activities;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.Utils;

import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.Preconditions;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.connectivity.ConnectionBuilder;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class BrapiAuthActivity extends AppCompatActivity {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_brapi_auth);

        SharedPreferences sp = getSharedPreferences("Settings", 0);
        // Start our login process
        //when coming back from deep link this check keeps app from auto-re-authenticating
        if (getIntent() != null && getIntent().getData() == null) {
            String flow = sp.getString(GeneralKeys.BRAPI_OIDC_FLOW, "");
            if(flow.equals(getString(R.string.preferences_brapi_oidc_flow_oauth_implicit))) {
                authorizeBrAPI(sp, this);
            }else if(flow.equals(getString(R.string.preferences_brapi_oidc_flow_old_custom))) {
                authorizeBrAPI_OLD(sp, this);
            }
        }
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

        SharedPreferences sp = getSharedPreferences("Settings", 0);
        AuthorizationException ex = AuthorizationException.fromIntent(getIntent());
        Uri data = getIntent().getData();

        if (data != null) {
            // authorization completed
            String flow = sp.getString(GeneralKeys.BRAPI_OIDC_FLOW, "");
            if(flow.equals(getString(R.string.preferences_brapi_oidc_flow_oauth_implicit))) {
                checkBrapiAuth(data);
            }else if(flow.equals(getString(R.string.preferences_brapi_oidc_flow_old_custom))) {
                checkBrapiAuth_OLD(data);
            }

            // Clear our data from our deep link so the app doesn't think it is
            // coming from a deep link if it is coming from deep link on pause and resume.
            getIntent().setData(null);

            setResult(RESULT_OK);

            finish();

        } else if (ex != null) {

            // authorization completed in error
            authError(ex);

            finish();

        } else { //returning from deep link with null data should finish activity
            //otherwise the progress bar hangs

            getIntent().setData(null);

            finish();

        }
    }

    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    public void authorizeBrAPI(SharedPreferences sharedPreferences, Context context) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(GeneralKeys.BRAPI_TOKEN, null);
        editor.apply();

        try {
            String clientId = "fieldbook";
            Uri redirectURI = Uri.parse("https://fieldbook.phenoapps.org/");
            Uri oidcConfigURI = Uri.parse(sharedPreferences.getString(GeneralKeys.BRAPI_OIDC_URL, ""));

            ConnectionBuilder builder = uri -> {
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
                    // open the new connnection again
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.setRequestProperty("Cookie", cookies);
                }else{
                    conn = (HttpURLConnection) new URL(uri.toString()).openConnection();
                }

                return conn;
            };

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

                            AuthorizationRequest.Builder authRequestBuilder =
                                    new AuthorizationRequest.Builder(
                                            serviceConfig, // the authorization service configuration
                                            clientId, // the client ID, typically pre-registered and static
                                            ResponseTypeValues.TOKEN, // the response_type value: we want a token
                                            redirectURI); // the redirect URI to which the auth response is sent

                            AuthorizationRequest authRequest = authRequestBuilder.setPrompt("login").build();

                            AuthorizationService authService = new AuthorizationService(context);

                            Intent responseIntent = new Intent(context, BrapiAuthActivity.class);
                            responseIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                            authService.performAuthorizationRequest(
                                    authRequest,
                                    PendingIntent.getActivity(context, 0, responseIntent, 0));

                        }

                    }, builder);

        } catch (Exception ex) {

            authError(ex);

            setResult(RESULT_CANCELED);

            finish();
        }
    }

    public void authorizeBrAPI_OLD(SharedPreferences sharedPreferences, Context context) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(GeneralKeys.BRAPI_TOKEN, null);
        editor.apply();

        try {
            String url = sharedPreferences.getString(GeneralKeys.BRAPI_BASE_URL, "") + "/brapi/authorize?display_name=Field Book&return_url=fieldbook://";
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
        Log.e("BrAPI", "Error starting BrAPI auth", ex);
        Toast.makeText(this, R.string.brapi_auth_error_starting, Toast.LENGTH_LONG).show();
    }
    private void authSuccess() {
        Log.d("BrAPI", "Auth successful");
        Toast.makeText(this, R.string.brapi_auth_success, Toast.LENGTH_LONG).show();
    }

    public void checkBrapiAuth_OLD(Uri data) {

        Integer status = Integer.parseInt(data.getQueryParameter("status"));

        // Check that we actually have the data. If not return failure.
        if (status == null) {
            authError(null);
            return;
        }

        SharedPreferences preferences = getSharedPreferences("Settings", 0);
        SharedPreferences.Editor editor = preferences.edit();
        if (status == 200) {
            String token = data.getQueryParameter("token");

            // Check that we received a token.
            if (token == null) {
                authError(null);
                return;
            }

            editor.putString(GeneralKeys.BRAPI_TOKEN, token);
            editor.apply();

            authSuccess();
            return;
        } else {
            editor.putString(GeneralKeys.BRAPI_TOKEN, null);
            editor.apply();

            authError(null);
            return;
        }
    }

    public void checkBrapiAuth(Uri data) {

            SharedPreferences preferences = getSharedPreferences("Settings", 0);
            SharedPreferences.Editor editor = preferences.edit();
            data = Uri.parse(data.toString().replaceFirst("#", "?"));
            String token = data.getQueryParameter("access_token");
            // Check that we received a token.
            if (token == null) {
                authError(null);
                return;
            }

            if(token.startsWith("Bearer ")){
                token = token.replaceFirst("Bearer ", "");
            }

            editor.putString(GeneralKeys.BRAPI_TOKEN, token);
            editor.apply();

            authSuccess();
    }
}
