package com.fieldbook.tracker.activities.brapi;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.fieldbook.tracker.utilities.BrapiAccountHelper;

import org.phenoapps.brapi.BrapiAccountConstants;

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

import org.phenoapps.brapi.account.BrapiTokenStoreResult;
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
            launchOidcClientId = i.hasExtra(EXTRA_OIDC_CLIENT_ID) ? i.getStringExtra(EXTRA_OIDC_CLIENT_ID) : preferences.getString(PreferenceKeys.BRAPI_OIDC_CLIENT_ID, getString(R.string.brapi_oidc_clientid_default));
            launchOidcScope    = i.hasExtra(EXTRA_OIDC_SCOPE)     ? i.getStringExtra(EXTRA_OIDC_SCOPE)     : preferences.getString(PreferenceKeys.BRAPI_OIDC_SCOPE, "");
            launchBrapiVersion = i.hasExtra(EXTRA_BRAPI_VERSION)  ? i.getStringExtra(EXTRA_BRAPI_VERSION)  : "";
        }
        if (launchServerUrl    == null) launchServerUrl    = "";
        if (launchOidcUrl      == null) launchOidcUrl      = "";
        if (launchOidcFlow     == null) launchOidcFlow     = "";
        if (launchOidcClientId == null || launchOidcClientId.isEmpty()) launchOidcClientId = "fieldbook";
        if (launchOidcScope    == null) launchOidcScope    = "";
        if (launchBrapiVersion == null) launchBrapiVersion = "";

        // Start our login process only for a fresh launch. AppAuth can return via intent extras
        // without data, so checking only getData() can accidentally start a second auth request
        // before the first response is handled.
        if (!hasAuthResult()) {
            authorizeBrAPI(preferences, this);
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
            activityStarting = false;
            handleAuthResultIfPresent();
            return;
        }

        if (!handleAuthResultIfPresent()) {
            // Returning from the browser with no deep link/result should finish the activity;
            // otherwise the progress bar hangs.
            getIntent().setData(null);
            finish();
        }
    }

    public void authorizeBrAPI(SharedPreferences sharedPreferences, Context context) {
        // Compared against a stable identifier, never the picker's label: the label is translated,
        // so an account configured in one language stopped matching after a language change.
        final boolean isImplicitFlow = BrapiAccountConstants.INSTANCE
                .normalizeOidcFlow(launchOidcFlow)
                .equals(BrapiAccountConstants.OIDC_FLOW_OAUTH_IMPLICIT);

        final String responseType = isImplicitFlow ? ResponseTypeValues.TOKEN : ResponseTypeValues.CODE;

        try {
            final String finalClientId = launchOidcClientId;
            final String finalScope    = launchOidcScope;

            // Authorization code flow works better with custom URL scheme (e.g. fieldbook://app/auth)
            // https://github.com/openid/AppAuth-Android/issues?q=is%3Aissue+intent+null
            Uri redirectURI = isImplicitFlow
                    ? Uri.parse(getString(R.string.brapi_implicit_redirect_uri))
                    : Uri.parse(getString(R.string.brapi_redirect_uri));

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
        responseIntent.putExtra(EXTRA_SERVER_URL,     launchServerUrl);
        responseIntent.putExtra(EXTRA_OIDC_URL,       launchOidcUrl);
        responseIntent.putExtra(EXTRA_OIDC_FLOW,      launchOidcFlow);
        responseIntent.putExtra(EXTRA_OIDC_CLIENT_ID, launchOidcClientId);
        responseIntent.putExtra(EXTRA_OIDC_SCOPE,     launchOidcScope);
        responseIntent.putExtra(EXTRA_BRAPI_VERSION,  launchBrapiVersion);

        authService.performAuthorizationRequest(
                authRequest,
                PendingIntent.getActivity(
                        context,
                        0,
                        responseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
    }

    private void authError(Exception ex) {

        // Clear our data from our deep link so the app doesn't think it is
        // coming from a deep link if it is coming from deep link on pause and resume.
        getIntent().setData(null);

        Log.e("BrAPI", "Error starting BrAPI auth", ex);

        // A bare "Error Starting BrAPI Auth" gives the user nothing to act on and nothing to
        // report, so surface whatever the provider actually said — a bad client id and a refused
        // redirect URI are very different problems and look identical without this.
        String reason = describeAuthFailure(ex);
        String message = reason.isEmpty()
                ? getString(R.string.brapi_auth_error_starting)
                : getString(R.string.brapi_auth_error_starting_reason, reason);

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * Best available human-readable cause for an auth failure, or empty when nothing was reported.
     *
     * AppAuth puts the provider's own wording in {@code errorDescription} and the OAuth error code
     * in {@code error}; both are absent for transport-level failures, where the exception message
     * is all there is.
     */
    private String describeAuthFailure(Exception ex) {
        if (ex == null) return "";

        if (ex instanceof AuthorizationException) {
            AuthorizationException authEx = (AuthorizationException) ex;
            if (authEx.errorDescription != null && !authEx.errorDescription.isEmpty()) {
                return authEx.errorDescription;
            }
            if (authEx.error != null && !authEx.error.isEmpty()) {
                return authEx.error;
            }
            if (authEx.getCause() != null && authEx.getCause().getMessage() != null) {
                return authEx.getCause().getMessage();
            }
        }

        return ex.getMessage() == null ? "" : ex.getMessage();
    }

    private void authSuccess(String accessToken, @Nullable String idToken) {

        String serverUrl = launchServerUrl;

        BrapiTokenStoreResult stored = BrapiTokenStoreResult.STORED;
        if (!serverUrl.isEmpty()) {
            stored = accountHelper.storeToken(serverUrl, accessToken, idToken);
        }

        // Clear our data from our deep link so the app doesn't think it is
        // coming from a deep link if it is coming from deep link on pause and resume.
        getIntent().setData(null);

        // The provider signed us in either way, but only STORED means the server now has an
        // account of its own here. Saying "authorization successful" for the other outcomes would
        // promise a server card that never appears.
        int message;
        switch (stored) {
            case ALREADY_SHARED:
                message = R.string.brapi_auth_success_already_shared;
                break;
            case ACCOUNT_UNAVAILABLE:
                message = R.string.brapi_auth_success_no_account;
                break;
            default:
                message = R.string.brapi_auth_success;
                break;
        }

        Log.d("BrAPI", "Auth successful, token stored: " + stored);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
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

    private boolean handleAuthResultIfPresent() {
        AuthorizationException ex = AuthorizationException.fromIntent(getIntent());
        AuthorizationResponse response = AuthorizationResponse.fromIntent(getIntent());
        Uri data = getIntent().getData();

        if (ex != null) {
            authError(ex);
            return true;
        }

        if (response != null || data != null) {
            checkBrapiAuth(data);
            return true;
        }

        return false;
    }

    private boolean hasAuthResult() {
        Intent intent = getIntent();
        return intent != null &&
                (intent.getData() != null ||
                        AuthorizationException.fromIntent(intent) != null ||
                        AuthorizationResponse.fromIntent(intent) != null);
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
                                // The exchange failure carries the provider's reason; dropping it
                                // here is what made token-exchange problems unreportable.
                                authError(ex);
                            }
                        }
                    });
            return;
        }

        if (response != null && response.accessToken != null) {
            authSuccess(response.accessToken, null);
            return;
        }

        if (data == null) {
            authError(null);
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
