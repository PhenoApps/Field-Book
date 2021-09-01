package com.fieldbook.tracker.brapi;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.BrapiAuthActivity;
import com.fieldbook.tracker.brapi.service.BrAPIService;


public class BrapiAuthDialog extends Dialog implements android.view.View.OnClickListener {

    private Context context;

    public BrapiAuthDialog(@NonNull Context context) {
        super(context);
        this.context = context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                Intent intent = new Intent();
                intent.setClassName(context, BrapiAuthActivity.class.getName());
                context.startActivity(intent);
                dismiss();
                break;

        }
    }
}