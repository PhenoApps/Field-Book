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

import com.fieldbook.tracker.utilities.BrapiAccountHelper;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.ThemedActivity;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.preferences.PreferenceKeys;
import com.fieldbook.tracker.utilities.OpenAuthConfigurationUtil;
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
import kotlin.Unit;
import kotlin.jvm.functions.Function2;

@AndroidEntryPoint
public class BrapiAuthActivity extends ThemedActivity {

    //first number that came to Pete's head --IRRI hackathon '25
    public static final int END_SESSION_REQUEST_CODE = 456;

    public static String REDIRECT_URI = "fieldbook://app/auth";

    // Intent extras for per-account config (set by BrapiManualAccountDialogFragment)
    public static final String EXTRA_SERVER_URL = "brapi_extra_server_url";
    public static final String EXTRA_OIDC_URL = "brapi_extra_oidc_url";
    public static final String EXTRA_OIDC_FLOW = "brapi_extra_oidc_flow";
    public static final String EXTRA_OIDC_CLIENT_ID = "brapi_extra_oidc_client_id";
    public static final String EXTRA_OIDC_SCOPE = "brapi_extra_oidc_scope";
    public static final String EXTRA_BRAPI_VERSION = "brapi_extra_brapi_version";

    @Inject
    BrapiAccountHelper accountHelper;

    @Inject
    SharedPreferences preferences;

    @Inject
    OpenAuthConfigurationUtil authUtil;

    private boolean activityStarting = false;

    private String launchServerUrl;
    private String launchOidcUrl;
    private String launchOidcFlow;
    private String launchOidcClientId;
    private String launchOidcScope;
    private String launchBrapiVersion;

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

        // Capture launch-time config before onNewIntent() can replace getIntent() with the OAuth
        // callback intent (which carries no extras), causing authSuccess() and onResume() to fall
        // back to stale SharedPreferences instead of the per-account values passed by the caller.
        if (savedInstanceState != null) {
            launchServerUrl    = savedInstanceState.getString(EXTRA_SERVER_URL, "");
            launchOidcUrl      = savedInstanceState.getString(EXTRA_OIDC_URL, "");
            launchOidcFlow     = savedInstanceState.getString(EXTRA_OIDC_FLOW, "");
            launchOidcClientId = savedInstanceState.getString(EXTRA_OIDC_CLIENT_ID, "");
            launchOidcScope    = savedInstanceState.getString(EXTRA_OIDC_SCOPE, "");
            launchBrapiVersion = savedInstanceState.getString(EXTRA_BRAPI_VERSION, "");
        } else {
            Intent i = getIntent();
            launchServerUrl    = i.hasExtra(EXTRA_SERVER_URL)     ? i.getStringExtra(EXTRA_SERVER_URL)     : preferences.getString(PreferenceKeys.BRAPI_BASE_URL, "");
            launchOidcUrl      = i.hasExtra(EXTRA_OIDC_URL)       ? i.getStringExtra(EXTRA_OIDC_URL)       : preferences.getString(PreferenceKeys.BRAPI_OIDC_URL, "");
            launchOidcFlow     = i.hasExtra(EXTRA_OIDC_FLOW)      ? i.getStringExtra(EXTRA_OIDC_FLOW)      : preferences.getString(PreferenceKeys.BRAPI_OIDC_FLOW, "");
            launchOidcClientId = i.hasExtra(EXTRA_OIDC_CLIENT_ID) ? i.getStringExtra(EXTRA_OIDC_CLIENT_ID) : preferences.getString(PreferenceKeys.BRAPI_OIDC_CLIENT_ID, "fieldbook");
            launchOidcScope    = i.hasExtra(EXTRA_OIDC_SCOPE)     ? i.getStringExtra(EXTRA_OIDC_SCOPE)     : preferences.getString(PreferenceKeys.BRAPI_OIDC_SCOPE, "");
            launchBrapiVersion = i.hasExtra(EXTRA_BRAPI_VERSION)  ? i.getStringExtra(EXTRA_BRAPI_VERSION)  : "";
        }
        if (launchServerUrl    == null) launchServerUrl    = "";
        if (launchOidcUrl      == null) launchOidcUrl      = "";
        if (launchOidcFlow     == null) launchOidcFlow     = "";
        if (launchOidcClientId == null || launchOidcClientId.isEmpty()) launchOidcClientId = "fieldbook";
        if (launchOidcScope    == null) launchOidcScope    = "";
        if (launchBrapiVersion == null) launchBrapiVersion = "";

        // Start our login process
        //when coming back from deep link this check keeps app from auto-re-authenticating
        if (getIntent() != null && getIntent().getData() == null) {
            if (launchOidcFlow.equals(getString(R.string.preferences_brapi_oidc_flow_old_custom))) {
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_SERVER_URL,     launchServerUrl);
        outState.putString(EXTRA_OIDC_URL,       launchOidcUrl);
        outState.putString(EXTRA_OIDC_FLOW,      launchOidcFlow);
        outState.putString(EXTRA_OIDC_CLIENT_ID, launchOidcClientId);
        outState.putString(EXTRA_OIDC_SCOPE,     launchOidcScope);
        outState.putString(EXTRA_BRAPI_VERSION,  launchBrapiVersion);
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
                if (launchOidcFlow.equals(getString(R.string.preferences_brapi_oidc_flow_old_custom))) {
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

    public void authorizeBrAPI(SharedPreferences sharedPreferences, Context context) {
        final String responseType = launchOidcFlow.equals(getString(R.string.preferences_brapi_oidc_flow_oauth_implicit))
                ? ResponseTypeValues.TOKEN : ResponseTypeValues.CODE;

        try {
            final String finalClientId = launchOidcClientId;
            final String finalScope    = launchOidcScope;

            // Authorization code flow works better with custom URL scheme fieldbook://app/auth
            // https://github.com/openid/AppAuth-Android/issues?q=is%3Aissue+intent+null
            Uri redirectURI = launchOidcFlow.equals(getString(R.string.preferences_brapi_oidc_flow_oauth_implicit))
                    ? Uri.parse("https://phenoapps.org/field-book") : Uri.parse("fieldbook://app/auth");

            authUtil.getAuthServiceConfiguration((authorizationServiceConfiguration, ex) -> {

                if (ex != null) {
                    Log.e("BrAPIService", "failed to fetch configuration", ex);
                    authError(ex);
                    finish();
                    return null;
                }

                try {

                    requestAuthorization(authorizationServiceConfiguration, finalClientId, responseType, redirectURI, finalScope, context);

                } catch (IllegalArgumentException e) {

                    e.printStackTrace();

                    Toast.makeText(context, R.string.oauth_configured_incorrectly, Toast.LENGTH_LONG).show();

                    finish();
                }

                return null;
            }, launchOidcUrl);

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

            authRequestBuilder.setScope(scope + " openid");

        } else {

            authRequestBuilder.setScopes("openid");

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

    private void authSuccess(String accessToken, @Nullable String idToken) {

        String serverUrl = launchServerUrl;

        if (!serverUrl.isEmpty()) {
            accountHelper.storeToken(serverUrl, accessToken, idToken);
        }

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
            authSuccess(token, null);

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
        builder.setConnectionBuilder(authUtil.getConnectionBuilder());
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
                                authSuccess(response.accessToken, response.idToken);
                            } else {
                                authError(null);
                            }
                        }
                    });
            return;
        }

        if (response != null && response.accessToken != null) {
            authSuccess(response.accessToken, null);
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

        authSuccess(token, null);
    }
}
