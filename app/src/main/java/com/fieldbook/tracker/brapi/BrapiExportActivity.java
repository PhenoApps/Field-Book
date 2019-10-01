package com.fieldbook.tracker.brapi;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.arch.core.util.Function;

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
        API_CALLBACK_ERROR
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
                            putObservationsError = UploadError.API_CALLBACK_ERROR;
                            uploadComplete();
                        }
                    });

                    return null;
                }
            }
        );
    }

    private void uploadComplete() {
        // show upload status
        if (putObservationsError != UploadError.NONE) {
            Toast.makeText(this.getApplicationContext(), "BrAPI Export Failed", Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(this.getApplicationContext(), "BrAPI Export Successful", Toast.LENGTH_SHORT).show();
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
        observations = dataHelper.getObservations();
        observationsNeedingSync.clear();

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