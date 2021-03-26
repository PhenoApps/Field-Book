package com.fieldbook.tracker.brapi.service;

        import android.app.Activity;
        import android.app.PendingIntent;
        import android.content.ActivityNotFoundException;
        import android.content.Context;
        import android.content.Intent;
        import android.content.SharedPreferences;
        import android.net.Uri;
        import android.util.Log;
        import android.util.Patterns;
        import android.widget.Toast;

        import androidx.annotation.Nullable;
        import androidx.arch.core.util.Function;
        import androidx.browser.customtabs.CustomTabsIntent;

        import com.fieldbook.tracker.R;
        import com.fieldbook.tracker.brapi.ApiError;
        import com.fieldbook.tracker.brapi.BrapiAuthDialog;
        import com.fieldbook.tracker.brapi.BrapiControllerResponse;
        import com.fieldbook.tracker.brapi.model.BrapiProgram;
        import com.fieldbook.tracker.brapi.model.BrapiStudyDetails;
        import com.fieldbook.tracker.brapi.model.BrapiTrial;
        import com.fieldbook.tracker.brapi.model.FieldBookImage;
        import com.fieldbook.tracker.brapi.model.Observation;
        import com.fieldbook.tracker.preferences.GeneralKeys;
        import com.fieldbook.tracker.objects.TraitObject;
        import com.fieldbook.tracker.preferences.PreferencesActivity;
        import com.fieldbook.tracker.utilities.Constants;

        import net.openid.appauth.AuthorizationException;
        import net.openid.appauth.AuthorizationRequest;
        import net.openid.appauth.AuthorizationService;
        import net.openid.appauth.AuthorizationServiceConfiguration;
        import net.openid.appauth.ResponseTypeValues;
        import net.openid.appauth.browser.CustomTabManager;

        import java.net.MalformedURLException;
        import java.net.URL;
        import java.util.List;

public interface BrAPIService {

    public static String notUniqueFieldMessage = "not_unique";
    public static String notUniqueIdMessage = "not_unique_id";

    // Helper functions for brapi configurations
    public static Boolean isLoggedIn(Context context) {

        String auth_token = context.getSharedPreferences("Settings", 0)
                .getString(GeneralKeys.BRAPI_TOKEN, "");

        if (auth_token == null || auth_token == "") {
            return false;
        }

        return true;
    }

    public static Boolean hasValidBaseUrl(Context context) {
        String url = getBrapiUrl(context);

        return Patterns.WEB_URL.matcher(url).matches();
    }

    public static Boolean checkMatchBrapiUrl(Context context, String dataSource) {

        try {
            URL externalUrl = new URL(getBrapiUrl(context));
            String hostURL = externalUrl.getHost();

            return (hostURL.equals(dataSource));
        } catch (MalformedURLException e) {
            Log.e("error-cmbu", e.toString());
            return false;
        }

    }

    public static String getHostUrl(Context context) {
        try {
            String brapiURL = getBrapiUrl(context);
            URL externalUrl = new URL(brapiURL);
            return externalUrl.getHost();
        } catch (MalformedURLException e) {
            Log.e("error-ghu", e.toString());
            return null;
        }
    }

    public static String getBrapiUrl(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("Settings", 0);
        String baseURL = preferences.getString(GeneralKeys.BRAPI_BASE_URL, "");
        String version = preferences.getString(GeneralKeys.BRAPI_VERSION, "");
        String path;
        if(version.equals("V2"))
            path = Constants.BRAPI_PATH_V2;
        else
            path = Constants.BRAPI_PATH_V1;
        return baseURL + path;
    }

    public static boolean isConnectionError(int code) {
        return code == 401 || code == 403 || code == 404;
    }

    public static void handleConnectionError(Context context, int code) {
        ApiError apiError = ApiError.processErrorCode(code);
        String toastMsg = "";

        switch (apiError) {
            case UNAUTHORIZED:
                // Start the login process
                BrapiAuthDialog brapiAuth = new BrapiAuthDialog(context);
                brapiAuth.show();
                toastMsg = context.getString(R.string.brapi_auth_deny);
                break;
            case FORBIDDEN:
                toastMsg = context.getString(R.string.brapi_auth_permission_deny);
                break;
            case NOT_FOUND:
                toastMsg = context.getString(R.string.brapi_not_found);
                break;
            default:
                toastMsg = "";
        }
        Toast.makeText(context.getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
    }

    public void postImageMetaData(FieldBookImage image, final Function<FieldBookImage, Void> function, final Function<Integer, Void> failFunction);

    public void putImageContent(FieldBookImage image, final Function<FieldBookImage, Void> function, final Function<Integer, Void> failFunction);

    public void putImage(FieldBookImage image, final Function<FieldBookImage, Void> function, final Function<Integer, Void> failFunction);

    public void getPrograms(final BrapiPaginationManager paginationManager, final Function<List<BrapiProgram>, Void> function, final Function<Integer, Void> failFunction);

    public void getTrials(String programDbId, BrapiPaginationManager paginationManager, final Function<List<BrapiTrial>, Void> function, final Function<Integer, Void> failFunction);

    public void getStudies(String programDbId, String trialDbId, BrapiPaginationManager paginationManager, final Function<List<BrapiStudyDetails>, Void> function, final Function<Integer, Void> failFunction);

    public void getStudyDetails(final String studyDbId, final Function<BrapiStudyDetails, Void> function, final Function<Integer, Void> failFunction);

    public void getPlotDetails(final String studyDbId, final Function<BrapiStudyDetails, Void> function, final Function<Integer, Void> failFunction);

    public void getOntology(BrapiPaginationManager paginationManager, final Function<List<TraitObject>, Void> function, final Function<Integer, Void> failFunction);

    public void postPhenotypes(List<Observation> observations,
                               final Function<List<Observation>, Void> function,
                               final Function<Integer, Void> failFunction);

    // will only ever have one study in current architecture
    public void putObservations(List<Observation> observations,
                                final Function<List<Observation>, Void> function,
                                final Function<Integer, Void> failFunction);

    public void getTraits(final String studyDbId, final Function<BrapiStudyDetails, Void> function, final Function<Integer, Void> failFunction);

    public BrapiControllerResponse saveStudyDetails(BrapiStudyDetails studyDetails);

    public void authorizeClient();
}