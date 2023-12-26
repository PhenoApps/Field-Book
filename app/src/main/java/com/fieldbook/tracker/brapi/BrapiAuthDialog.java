package com.fieldbook.tracker.brapi;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity;


public class BrapiAuthDialog extends Dialog implements android.view.View.OnClickListener {

    private final Context context;

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
        int id = view.getId();
        if (id == R.id.brapi_auth_cancel_btn) {// Cancel
            dismiss();
        } else if (id == R.id.brapi_auth_btn) {
            Intent intent = new Intent();
            intent.setClassName(context, BrapiAuthActivity.class.getName());
            context.startActivity(intent);
            dismiss();
        }
    }
}