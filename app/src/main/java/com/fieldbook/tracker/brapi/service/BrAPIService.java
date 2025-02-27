package com.fieldbook.tracker.brapi.service;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

import androidx.arch.core.util.Function;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.ApiError;
import com.fieldbook.tracker.brapi.ApiErrorCode;
import com.fieldbook.tracker.brapi.BrapiAuthDialogFragment;
import com.fieldbook.tracker.brapi.BrapiControllerResponse;
import com.fieldbook.tracker.brapi.model.BrapiObservationLevel;
import com.fieldbook.tracker.brapi.model.BrapiProgram;
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails;
import com.fieldbook.tracker.brapi.model.BrapiTrial;
import com.fieldbook.tracker.brapi.model.FieldBookImage;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.PreferenceKeys;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.utilities.FailureFunction;
import com.fieldbook.tracker.utilities.SuccessFunction;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.function.BiFunction;

public interface BrAPIService {

    String notUniqueFieldMessage = "not_unique";
    String notUniqueIdMessage = "not_unique_id";
    String noPlots = "no_plots";

    static SharedPreferences getPreferences(Context ctx) {

        return PreferenceManager.getDefaultSharedPreferences(ctx);

    }

    // Helper functions for brapi configurations
    static Boolean isLoggedIn(Context context) {

        String token = getPreferences(context).getString(PreferenceKeys.BRAPI_TOKEN, "");

        return token != null && token != "";
    }

    static Boolean hasValidBaseUrl(Context context) {
        String url = getBrapiUrl(context);

        return Patterns.WEB_URL.matcher(url).matches();
    }

    static Boolean checkMatchBrapiUrl(Context context, String dataSource) {

        try {
            URL externalUrl = new URL(getBrapiUrl(context));
            String hostURL = externalUrl.getHost();

            return (hostURL.equals(dataSource));
        } catch (MalformedURLException e) {
            Log.e("error-cmbu", e.toString());
            return false;
        }

    }

    static String getHostUrl(Context context) {
        try {
            String brapiURL = getBrapiUrl(context);
            URL externalUrl = new URL(brapiURL);
            return externalUrl.getHost();
        } catch (MalformedURLException e) {
            Log.e("error-ghu", e.toString());
            return null;
        }
    }

    static String getBrapiUrl(Context context) {
        String baseURL = getPreferences(context).getString(PreferenceKeys.BRAPI_BASE_URL, "");
        String version = getPreferences(context).getString(PreferenceKeys.BRAPI_VERSION, "");
        String path;
        if (version.equals("V2"))
            path = Constants.BRAPI_PATH_V2;
        else
            path = Constants.BRAPI_PATH_V1;
        return baseURL + path;
    }

    static int checkPreference(Context context, String key, String defaultValue) {
        String prefValueString = getPreferences(context).getString(key, defaultValue);

        int value = Integer.parseInt(defaultValue);

        try {
            if (prefValueString != null) {
                value = Integer.parseInt(prefValueString);
            }
        } catch (NumberFormatException nfe) {
            String message = nfe.getLocalizedMessage();
            if (message != null) {
                Log.d("FieldBookError", nfe.getLocalizedMessage());
            } else {
                Log.d("FieldBookError", "Preference number format error.");
            }
            nfe.printStackTrace();
        }

        return value;
    }

    static Integer getTimeoutValue(Context context) {
        return checkPreference(context, PreferenceKeys.BRAPI_TIMEOUT, "120");
    }

    static int getChunkSize(Context context) {
        return checkPreference(context, PreferenceKeys.BRAPI_CHUNK_SIZE, "500");
    }

    static boolean isConnectionError(int code) {
        return code == 401 || code == 403 || code == 404;
    }

    static boolean handleConnectionError(Context context, int code) {
        ApiErrorCode apiErrorCode = ApiErrorCode.processErrorCode(code);
        String toastMsg;

        boolean returnVal = false;
        switch (apiErrorCode) {
            case UNAUTHORIZED:
                toastMsg = context.getString(R.string.brapi_auth_deny);
                returnVal = true;
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

        ((Activity)context).runOnUiThread(() -> {
            Toast.makeText(context.getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
        });

        return returnVal;
    }

    void postImageMetaData(FieldBookImage image, final Function<FieldBookImage, Void> function, final Function<Integer, Void> failFunction);

    void putImageContent(FieldBookImage image, final Function<FieldBookImage, Void> function, final Function<Integer, Void> failFunction);

    void putImage(FieldBookImage image, final Function<FieldBookImage, Void> function, final Function<Integer, Void> failFunction);

    void getPrograms(final BrapiPaginationManager paginationManager, final Function<List<BrapiProgram>, Void> function, final Function<Integer, Void> failFunction);

    void getTrials(String programDbId, BrapiPaginationManager paginationManager, final Function<List<BrapiTrial>, Void> function, final Function<Integer, Void> failFunction);

    void getStudies(String programDbId, String trialDbId, BrapiPaginationManager paginationManager, final Function<List<BrapiStudyDetails>, Void> function, final Function<Integer, Void> failFunction);

    void getStudyDetails(final String studyDbId, final Function<BrapiStudyDetails, Void> function, final Function<Integer, Void> failFunction);

    void getPlotDetails(final String studyDbId, BrapiObservationLevel observationLevel, final Function<BrapiStudyDetails, Void> function, final Function<Integer, Void> failFunction);

    void getObservations(final String studyDbId, final List<String> observationVariableDbIds, BrapiPaginationManager paginationManager, final Function<List<Observation>, Void> function, final Function<Integer, Void> failFunction);

    void getOntology(BrapiPaginationManager paginationManager, final BiFunction<List<TraitObject>, Integer, Void> function, final Function<Integer, Void> failFunction);

    void createObservations(List<Observation> observations,
                            final Function<List<Observation>, Void> function,
                            final Function<Integer, Void> failFunction);

    void updateObservations(List<Observation> observations,
                            final Function<List<Observation>, Void> function,
                            final Function<Integer, Void> failFunction);

    void createObservationsChunked(int chunkSize, List<Observation> observations, BrAPIChunkedUploadProgressCallback<Observation> uploadProgressCallback, Function<Integer, Void> failFn);
    void updateObservationsChunked(int chunkSize, List<Observation> observations, BrAPIChunkedUploadProgressCallback<Observation> uploadProgressCallback, Function<Integer, Void> failFn);

    /*
    public void postObservations(List<Observation> observations,
                               final Function<List<Observation>, Void> function,
                               final Function<Integer, Void> failFunction);

    // will only ever have one study in current architecture
    public void putObservations(List<Observation> observations,
                                final Function<List<Observation>, Void> function,
                                final Function<Integer, Void> failFunction);

     */

    void getTraits(final String studyDbId, final Function<BrapiStudyDetails, Void> function, final Function<Integer, Void> failFunction);

    BrapiControllerResponse saveStudyDetails(BrapiStudyDetails studyDetails, BrapiObservationLevel selectedObservationLevel, String primaryId, String secondaryId, String sortOrder);

    void authorizeClient();

    void getObservationLevels(String programDbId, final SuccessFunction<List<BrapiObservationLevel>> successFn, final FailureFunction<ApiError> failFn);
}