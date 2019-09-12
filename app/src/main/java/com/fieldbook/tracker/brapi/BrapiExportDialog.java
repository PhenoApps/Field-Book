package com.fieldbook.tracker.brapi;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;

import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.utilities.Constants;

import java.util.List;
import java.util.Map;

import io.swagger.client.model.NewObservationDbIdsObservations;

public class BrapiExportDialog extends Dialog implements android.view.View.OnClickListener {

    private Button saveBtn, cancelBtn;
    private BrAPIService brAPIService;
    private Context context;
    private DataHelper dataHelper;
    private List<Observation> observations;

    public BrapiExportDialog(@NonNull Context context) {
        super(context);
        this.context = context;
        this.dataHelper = new DataHelper(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_brapi_export);

        String brapiBaseURL = this.context.getSharedPreferences("Settings", 0)
                .getString(PreferencesActivity.BRAPI_BASE_URL, "") + Constants.BRAPI_PATH;
        brAPIService = new BrAPIService(this.context, brapiBaseURL);
        saveBtn = findViewById(R.id.brapi_export_btn);
        saveBtn.setOnClickListener(this);
        cancelBtn = findViewById(R.id.brapi_cancel_btn);
        cancelBtn.setOnClickListener(this);

        loadStatistics();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.brapi_export_btn:
                // TODO: hardcoded, replace with actual studyid
                observations = dataHelper.getObservations();
                brAPIService.postPhenotypes(observations, new Function<List<NewObservationDbIdsObservations>, Void>() {
                    @Override
                    public Void apply(final List<NewObservationDbIdsObservations> observationDbIds) {
                        updateObservations(observationDbIds);
                        return null;
                    }
                });
                break;
            case R.id.brapi_cancel_btn:
                dismiss();
                break;
            default:
                break;
        }
        dismiss();
    }

    private void updateObservations(List<NewObservationDbIdsObservations> observationDbIds) {

        if (observationDbIds.size() != observations.size()) {
            Toast.makeText(getContext().getApplicationContext(), "Wrong number of observations returned", Toast.LENGTH_SHORT).show();
        }
        else {
            // can't use streams/filter with API < 24
            for (NewObservationDbIdsObservations observationDbId : observationDbIds) {
                // get observation associated with this

            }
        }

    }

    private void loadStatistics() {
        // TODO: don't run this twice, organize better
        List<Observation> data = dataHelper.getObservations();
        String numUpdated = String.valueOf(data.size());

        // For now everything is treated as new
        ((TextView) findViewById(R.id.brapiNumNewValue)).setText(numUpdated);
        // TODO: update with real numbers
        ((TextView) findViewById(R.id.brapiNumEditedValue)).setText("0");
    }
}