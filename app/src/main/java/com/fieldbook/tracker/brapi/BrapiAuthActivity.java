package com.fieldbook.tracker.brapi;

import android.app.Activity;
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


public class BrapiAuthActivity extends AppCompatActivity {

    private BrAPIService brAPIService;
    private SharedPreferences preferences;
    private String target;

    public BrapiAuthActivity() {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = this.getSharedPreferences("Settings", 0);

        Intent originIntent = getIntent();
        target = originIntent.getStringExtra("target");

        // Check if this is a return from Brapi authentication
        checkBrapiAuth();

        // User is not authenticated. Show our authentication window.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_brapi_auth);

    }

    // This will be call when we resume our Brapi login.
    @Override
    public void onResume() {
        super.onResume();

        // Check if this is a return from Brapi authentication
        checkBrapiAuth();
    }

    public static Boolean isLoggedIn(Context context) {

        String auth_token = context.getSharedPreferences("Settings", 0)
                .getString(PreferencesActivity.BRAPI_TOKEN, "");

        if (auth_token == null || auth_token == "") {
            return false;
        }

        return true;
    }

    public static Boolean hasValidBaseUrl(Context context) {
        String url = getBrapiUrl(context);

        return Patterns.WEB_URL.matcher(url).matches();
    }

    public static String getBrapiUrl(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("Settings", 0);
        return preferences.getString(PreferencesActivity.BRAPI_BASE_URL, "") + Constants.BRAPI_PATH;
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.brapi_auth_cancel_btn:
                // Cancel
                finish();
                break;

            case R.id.brapi_auth_btn:

                // Start our brapi authentication process.
                finish();
                BrAPIService.authorizeBrAPI(preferences, this, target);
                break;

        }
    }

    private void checkBrapiAuth() {

        Uri data = this.getIntent().getData();

        if (data != null && data.isHierarchical()) {

            Boolean parseSuccess = BrAPIService.parseBrapiAuth(this);
            String brapiHost = this.getSharedPreferences("Settings", 0)
                    .getString(PreferencesActivity.BRAPI_BASE_URL, "");

            if (parseSuccess) {

                // Get the target destination
                String target = data.getHost();

                // Config Page
                Intent nextPage = null;
                if (target.equals("export")) {
                    nextPage = new Intent(this, BrapiExportDialog.class);
                }
                else if (target.equals("")) {
                    nextPage = new Intent(this, ConfigActivity.class);
                }
                else {
                    nextPage = new Intent(this, ConfigActivity.class);
                }

                startActivity(nextPage);
                Toast.makeText(this, String.format("Successfully authenticated with %s", brapiHost), Toast.LENGTH_LONG);
            }
            else {
                Toast.makeText(this, String.format("Unable to authenticate with %s", brapiHost), Toast.LENGTH_LONG);
                Intent nextPage = new Intent(this, ConfigActivity.class);
                startActivity(nextPage);
            }

            finish();
        }


    }
}
