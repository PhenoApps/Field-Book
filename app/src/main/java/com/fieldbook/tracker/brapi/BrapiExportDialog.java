package com.fieldbook.tracker.brapi;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.PreferencesActivity;

import java.util.List;
import java.util.Map;

public class BrapiExportDialog extends Dialog implements android.view.View.OnClickListener {

    private Button saveBtn, cancelBtn;
    private BrAPIService brAPIService;
    private Context context;
    private DataHelper dataHelper;

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

        ;
        String brapiBaseURL = this.context.getSharedPreferences("Settings", 0)
                .getString(PreferencesActivity.BRAPI_BASE_URL, "");
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

                //brAPIService.putStudyObservations();
                brAPIService.postPhenotypes();
                break;
            case R.id.brapi_cancel_btn:
                dismiss();
                break;
            default:
                break;
        }
        dismiss();
    }

    private void loadStatistics() {
        // TODO: don't run this twice, organize better
        List<Map<String, String>> data = dataHelper.getDataBrapiExport();
        String numUpdated = String.valueOf(data.size());

        // For now everything is treated as new
        ((TextView) findViewById(R.id.brapiNumNewValue)).setText(numUpdated);
        // TODO: update with real numbers
        ((TextView) findViewById(R.id.brapiNumEditedValue)).setText("0");
    }
}