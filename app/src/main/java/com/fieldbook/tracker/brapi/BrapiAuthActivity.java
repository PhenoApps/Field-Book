package com.fieldbook.tracker.brapi;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Patterns;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.arch.core.util.Function;

import com.fieldbook.tracker.ConfigActivity;
import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.utilities.Constants;


public class BrapiAuthActivity extends Dialog implements android.view.View.OnClickListener {

    private BrAPIService brAPIService;
    private SharedPreferences preferences;
    private String target;
    private Context context;
    private Button authBtn;
    private Button cancelBtn;

    public BrapiAuthActivity(@NonNull Context context, String target) {
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
        authBtn = findViewById(R.id.brapi_auth_btn);
        authBtn.setOnClickListener(this);
        cancelBtn = findViewById(R.id.brapi_auth_cancel_btn);
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
                BrAPIService.authorizeBrAPI(preferences, context, target);
                break;

        }
    }


}
