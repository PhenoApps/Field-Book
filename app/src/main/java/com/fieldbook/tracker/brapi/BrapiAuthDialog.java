package com.fieldbook.tracker.brapi;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fieldbook.tracker.R;


public class BrapiAuthDialog extends Dialog implements android.view.View.OnClickListener {

    private SharedPreferences preferences;
    private String target;
    private Context context;

    public BrapiAuthDialog(@NonNull Context context, String target) {
        super(context);
        this.context = context;
        this.target = target;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = context.getSharedPreferences("Settings", 0);

        // User is not authenticated. Show our authentication window.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_brapi_auth);

        // Set our button click event
        Button authBtn = findViewById(R.id.brapi_auth_btn);
        authBtn.setOnClickListener(this);
        Button cancelBtn = findViewById(R.id.brapi_auth_cancel_btn);
        cancelBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.brapi_auth_cancel_btn:
                // Cancel
                dismiss();
                break;

            case R.id.brapi_auth_btn:

                // Start our brapi authentication process.
                dismiss();
                // Show our error message if it exists
                BrapiControllerResponse brapiControllerResponse = BrAPIService.authorizeBrAPI(preferences, context, target);
                processBrapiControllerMessage(brapiControllerResponse);
                break;

        }
    }

    private void processBrapiControllerMessage(BrapiControllerResponse brapiControllerResponse) {

        if (brapiControllerResponse.status != null) {
            if (!brapiControllerResponse.status) {
                Toast.makeText(context, R.string.brapi_auth_error_starting, Toast.LENGTH_LONG).show();
            }
        }
    }
}