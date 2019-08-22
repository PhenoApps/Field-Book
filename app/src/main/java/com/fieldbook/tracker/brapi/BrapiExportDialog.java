package com.fieldbook.tracker.brapi;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.PreferencesActivity;

public class BrapiExportDialog extends Dialog implements android.view.View.OnClickListener{

    private Button saveBtn, cancelBtn;
    private BrAPIService brAPIService;
    private Context context;

    public BrapiExportDialog(@NonNull Context context) {
        super(context);
        this.context = context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_brapi_import);

        ;
        String brapiBaseURL = this.context.getSharedPreferences("Settings", 0)
                .getString(PreferencesActivity.BRAPI_BASE_URL, "");
        brAPIService = new BrAPIService(this.context, brapiBaseURL);
        saveBtn = findViewById(R.id.brapi_save_btn);
        saveBtn.setOnClickListener(this);
        cancelBtn = findViewById(R.id.brapi_cancel_btn);
        cancelBtn.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.brapi_save_btn:
                // TODO: hardcoded, replace with actual studyid
                brAPIService.putStudyObservations("1001");
                break;
            case R.id.brapi_cancel_btn:
                dismiss();
                break;
            default:
                break;
        }
        dismiss();
    }

}
