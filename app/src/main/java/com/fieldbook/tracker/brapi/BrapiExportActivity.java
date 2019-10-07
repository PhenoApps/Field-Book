package com.fieldbook.tracker.brapi;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.arch.core.util.Function;

import com.fieldbook.tracker.ConfigActivity;
import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.utilities.Utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import io.swagger.client.model.NewObservationDbIdsObservations;

public class BrapiExportActivity extends AppCompatActivity {

    private BrAPIService brAPIService;
    private DataHelper dataHelper;
    private List<Observation> observations;
    private List<Observation> observationsNeedingSync;
    private List<Observation> userCreatedTraitObservations;
    private List<Observation> emptyValueObservations;
    private List<Observation> wrongSourceObservations;

    private BrapiControllerResponse brapiControllerResponse;
    private int numNewObservations;
    private int numSyncedObservations;
    private int numEditedObservations;
    private UploadError putObservationsError;

    public enum UploadError {
        NONE,
        MISSING_OBSERVATION_IN_RESPONSE,
        MULTIPLE_OBSERVATIONS_PER_VARIABLE,
        WRONG_NUM_OBSERVATIONS_RETURNED,
        API_CALLBACK_ERROR,
        API_UNAUTHORIZED_ERROR,
        API_NOTSUPPORTED_ERROR,
        API_PERMISSION_ERROR
    }

    public BrapiExportActivity() {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if our activity was started up with brapi auth deep link.
        brapiControllerResponse = BrAPIService.checkBrapiAuth(this);

        if(Utils.isConnected(this)) {
            if (BrAPIService.hasValidBaseUrl(this)) {

                requestWindowFeature(Window.FEATURE_NO_TITLE);
                setContentView(R.layout.dialog_brapi_export);

                String brapiBaseURL = BrAPIService.getBrapiUrl(this);

                this.dataHelper = new DataHelper(this);
                brAPIService = new BrAPIService(brapiBaseURL, this.dataHelper);

                putObservationsError = UploadError.NONE;
                observationsNeedingSync = new ArrayList<>();
                numNewObservations = 0;
                numSyncedObservations = 0;
                numEditedObservations = 0;

                loadToolbar();
                loadStatistics();

            }else{
                Toast.makeText(getApplicationContext(), R.string.brapi_must_configure_url, Toast.LENGTH_SHORT).show();
                finish();
            }
        }else{
            // Check if the user is connected. If not, pull from cache
            Toast.makeText(getApplicationContext(), R.string.device_offline_warning, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("BrAPI Export");
        getSupportActionBar().getThemedContext();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    @Override
    public void onResume() {
        super.onResume();

        // If our activity was resumed, we will want to see if it was resumed from a deep link.
        if (brapiControllerResponse.status == null) {
            brapiControllerResponse = BrAPIService.checkBrapiAuth(this);
        }

        // Check whether our brapi auth response was successful
        processBrapiControllerMessage(brapiControllerResponse);

    }

    public void processBrapiControllerMessage(BrapiControllerResponse brapiControllerResponse) {

        if (brapiControllerResponse.status != null) {
            if (!brapiControllerResponse.status) {
                Toast.makeText(this, R.string.brapi_auth_error_starting, Toast.LENGTH_LONG).show();
            }
            else {
                Toast.makeText(this, R.string.brapi_auth_success, Toast.LENGTH_LONG).show();
            }
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.brapi_export_btn:
                if (numNewObservations > 0 || numEditedObservations > 0) {
                    putObservations();
                }
                else {
                    Toast.makeText(this, "Error: Nothing to sync", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.brapi_cancel_btn:
                finish();
                break;
            default:
                break;
        }
    }

    private void putObservations() {

        // Disable the export button so that can't click it again
        Button exportBtn = (Button) this.findViewById(R.id.brapi_export_btn);
        exportBtn.setEnabled(false);
        // Show our saving wheel
        this.findViewById(R.id.saving_panel).setVisibility(View.VISIBLE);

        brAPIService.putObservations(observationsNeedingSync,
            BrAPIService.getBrapiToken(this),
            new Function<List<NewObservationDbIdsObservations>, Void>() {
                @Override
                public Void apply(final List<NewObservationDbIdsObservations> observationDbIds) {

                    (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            processPutObservationsResponse(observationDbIds);
                            uploadComplete();
                        }
                    });
                    return null;
                }
            }, new Function<Integer, Void>() {

                @Override
                public Void apply(final Integer code) {

                    (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            switch (code) {
                                case 403:
                                    // Warn that they do not have permissions to push traits
                                    putObservationsError = UploadError.API_PERMISSION_ERROR;
                                    break;
                                case 401:
                                    // Start the login process
                                    putObservationsError = UploadError.API_UNAUTHORIZED_ERROR;
                                    break;
                                case 400:
                                    // Bad request
                                    putObservationsError = UploadError.API_CALLBACK_ERROR;
                                    break;
                                case 404:
                                    // End point not supported
                                    putObservationsError = UploadError.API_NOTSUPPORTED_ERROR;
                                    break;
                                default:
                                    // Do our generic error handler.
                                    putObservationsError = UploadError.API_CALLBACK_ERROR;

                            }


                            uploadComplete();
                        }
                    });

                    return null;
                }
            }
        );
    }

    private String getMessageForErrorCode(UploadError error) {
        String message;

        switch(error) {
            case API_CALLBACK_ERROR:
                message = getString(R.string.brapi_export_failed);
                break;
            case API_PERMISSION_ERROR:
                message = getString(R.string.brapi_export_permission_deny);
                break;
            case API_NOTSUPPORTED_ERROR:
                message = getString(R.string.brapi_export_not_supported);
                break;
            case WRONG_NUM_OBSERVATIONS_RETURNED:
                message = getString(R.string.brapi_export_wrong_num_obs);
                break;
            case MISSING_OBSERVATION_IN_RESPONSE:
                message = getString(R.string.brapi_export_missing_obs);
                break;
            case MULTIPLE_OBSERVATIONS_PER_VARIABLE:
                message = getString(R.string.brapi_export_multiple_obs);
                break;
            case NONE:
                message = getString(R.string.brapi_export_successful);
                break;
            default:
                message = getString(R.string.brapi_export_unknown_error);
                break;
        }

        return message;
    }

    private void uploadComplete() {

        // Re-enable our login button
        // Disable the export button so that can't click it again
        Button exportBtn = (Button) this.findViewById(R.id.brapi_export_btn);
        exportBtn.setEnabled(true);
        this.findViewById(R.id.saving_panel).setVisibility(View.GONE);

        // show upload status
        if (putObservationsError == UploadError.API_UNAUTHORIZED_ERROR) {
            // Start the login process
            BrapiAuthDialog brapiAuth = new BrapiAuthDialog(BrapiExportActivity.this, BrAPIService.exportTarget);
            brapiAuth.show();
            return;
        }
        else {
            String message = getMessageForErrorCode(putObservationsError);
            Toast.makeText(this.getApplicationContext(), message, Toast.LENGTH_LONG).show();
        }

        // refresh statistics
        loadStatistics();
        // reset
        reset();
    }

    private void reset() {
        putObservationsError = UploadError.NONE;
    }

    private UploadError processResponse(List<NewObservationDbIdsObservations> observationDbIds, List<Observation> observationsNeedingSync)
    {
        UploadError retVal = UploadError.NONE;
        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ",
                Locale.getDefault());
        String syncTime = timeStamp.format(Calendar.getInstance().getTime());

        if (observationDbIds.size() != observationsNeedingSync.size()) {
            retVal = UploadError.WRONG_NUM_OBSERVATIONS_RETURNED;
        }
        else {
            // TODO: update to work with multiple observations per variable
            // Also would be nice to have a cleaner 'find' mechanism
            // For now, just use observationUnitDbId and observationVariableId to key off
            // Won't work for multiple observations of the same variable which we want to support in the future

            for (NewObservationDbIdsObservations observationDbId : observationDbIds) {
                // find observation with matching keys and update observationDbId
                Observation converted = new Observation(observationDbId);
                int first_index = observationsNeedingSync.indexOf(converted);
                int last_index = observationsNeedingSync.lastIndexOf(converted);
                if (first_index == -1) {
                    retVal = UploadError.MISSING_OBSERVATION_IN_RESPONSE;
                } else if (first_index != last_index) {
                    retVal = UploadError.MULTIPLE_OBSERVATIONS_PER_VARIABLE;
                } else {
                    Observation update = observationsNeedingSync.get(first_index);
                    update.setDbId(converted.getDbId());
                    update.setLastSyncedTime(syncTime);
                    observationsNeedingSync.set(first_index, update);
                }
            }

            if (retVal == UploadError.NONE) {
                dataHelper.updateObservations(observationsNeedingSync);
            }
        }

        return retVal;
    }

    private void processPutObservationsResponse(List<NewObservationDbIdsObservations> observationDbIds) {
        UploadError error = processResponse(observationDbIds, observationsNeedingSync);
        putObservationsError = error;
    }

    private void loadStatistics() {

        numNewObservations = 0;
        numSyncedObservations = 0;
        numEditedObservations = 0;
        String hostURL = BrAPIService.getHostUrl(BrAPIService.getBrapiUrl(this));
        observations = dataHelper.getObservations(hostURL);
        observationsNeedingSync.clear();
        userCreatedTraitObservations = dataHelper.getUserTraitObservations();
        emptyValueObservations = dataHelper.getEmptyValueObservations();
        wrongSourceObservations = dataHelper.getWrongSourceObservations(hostURL);

        for (Observation observation : observations) {
            switch(observation.getStatus()) {
                case NEW:
                    numNewObservations++;
                    observationsNeedingSync.add(observation);
                    break;
                case SYNCED:
                    numSyncedObservations++;
                    break;
                case EDITED:
                    numEditedObservations++;
                    observationsNeedingSync.add(observation);
                    break;
            }
        }

        SharedPreferences ep = this.getSharedPreferences("Settings", 0);
        String field = ep.getString("FieldFile", "");

        ((TextView) findViewById(R.id.brapistudyValue)).setText(field);
        ((TextView) findViewById(R.id.brapiNumNewValue)).setText(String.valueOf(numNewObservations));
        ((TextView) findViewById(R.id.brapiNumSyncedValue)).setText(String.valueOf(numSyncedObservations));
        ((TextView) findViewById(R.id.brapiNumEditedValue)).setText(String.valueOf(numEditedObservations));
        ((TextView) findViewById(R.id.brapiUserCreatedValue)).setText(String.valueOf(userCreatedTraitObservations.size()));
        ((TextView) findViewById(R.id.brapiEmptyValue)).setText(String.valueOf(emptyValueObservations.size()));
        ((TextView) findViewById(R.id.brapiWrongSource)).setText(String.valueOf(wrongSourceObservations.size()));

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}