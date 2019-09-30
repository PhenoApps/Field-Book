package com.fieldbook.tracker.brapi;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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

public class BrapiExportDialog extends AppCompatActivity {

    private BrAPIService brAPIService;
    private DataHelper dataHelper;
    private List<Observation> observations;
    private List<Observation> observationsNeedingSync;
    private int newObservations;
    private int syncedObservations;
    private int editedObservations;

    public BrapiExportDialog() {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(Utils.isConnected(this)) {
            if (BrapiAuthActivity.hasValidBaseUrl(this)) {

                requestWindowFeature(Window.FEATURE_NO_TITLE);
                setContentView(R.layout.dialog_brapi_export);

                String brapiBaseURL = BrapiAuthActivity.getBrapiUrl(this);

                this.dataHelper = new DataHelper(this);
                brAPIService = new BrAPIService(brapiBaseURL, this.dataHelper);
                observations = dataHelper.getObservations();
                observationsNeedingSync = new ArrayList<>();
                newObservations = 0;
                syncedObservations = 0;
                editedObservations = 0;

                loadStatistics();

            }else{
                Toast.makeText(getApplicationContext(), "Must configure a valid BrAPI URL in settings before proceeding", Toast.LENGTH_SHORT).show();
                finish();
            }
        }else{
            // Check if the user is connected. If not, pull from cache
            Toast.makeText(getApplicationContext(), "Device Offline: Please connect to a network and try again", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.brapi_export_btn:
                if (observationsNeedingSync.size() > 0) {
                    brAPIService.postPhenotypes(observationsNeedingSync,
                            BrapiAuthActivity.getBrapiToken(this),
                            new Function<List<NewObservationDbIdsObservations>, Void>() {
                                @Override
                                public Void apply(final List<NewObservationDbIdsObservations> observationDbIds) {

                                    (BrapiExportDialog.this).runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            updateObservations(observationDbIds);
                                        }
                                    });
                                    return null;
                                }
                    }, new Function<String, Void>() {

                        @Override
                        public Void apply(final String input) {

                            (BrapiExportDialog.this).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(BrapiExportDialog.this, input, Toast.LENGTH_LONG).show();
                                }
                            });

                            return null;
                        }
                    });
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
        finish();
    }

    private void updateObservations(List<NewObservationDbIdsObservations> observationDbIds) {

        boolean error = false;
        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ",
                Locale.getDefault());
        String syncTime = timeStamp.format(Calendar.getInstance().getTime());

        if (observationDbIds.size() != observationsNeedingSync.size()) {
            Toast.makeText(this, "Wrong number of observations returned", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this.getApplicationContext(), "Error: Missing observation", Toast.LENGTH_SHORT).show();
                    error = true;
                }
                else if (first_index != last_index) {
                    Toast.makeText(this.getApplicationContext(), "Error: Multiple observations per variable", Toast.LENGTH_SHORT).show();
                    error = true;
                }
                else {
                    Observation update = observationsNeedingSync.get(first_index);
                    update.setDbId(converted.getDbId());
                    update.setLastSyncedTime(syncTime);
                    observationsNeedingSync.set(first_index, update);
                }
            }

            if (error == false) {
                dataHelper.updateObservations(observationsNeedingSync);
                Toast.makeText(this.getApplicationContext(), "BrAPI Export Successful", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadStatistics() {

        for (Observation observation : observations) {
            switch(observation.getStatus()) {
                case NEW:
                    newObservations++;
                    observationsNeedingSync.add(observation);
                    break;
                case SYNCED:
                    syncedObservations++;
                    break;
                case EDITED:
                    editedObservations++;
                    observationsNeedingSync.add(observation);
                    break;
            }
        }

        SharedPreferences ep = this.getSharedPreferences("Settings", 0);
        String field = ep.getString("FieldFile", "");

        ((TextView) findViewById(R.id.brapistudyValue)).setText(field);
        ((TextView) findViewById(R.id.brapiNumNewValue)).setText(String.valueOf(newObservations));
        ((TextView) findViewById(R.id.brapiNumSyncedValue)).setText(String.valueOf(syncedObservations));
        ((TextView) findViewById(R.id.brapiNumEditedValue)).setText(String.valueOf(editedObservations));
    }
}