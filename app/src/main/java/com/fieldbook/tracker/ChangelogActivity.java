package com.fieldbook.tracker;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class ChangelogActivity extends AppCompatActivity {

    WindowManager.LayoutParams params;
    private LinearLayout parent;

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences ep = getSharedPreferences("Settings", 0);

        SharedPreferences.Editor ed = ep.edit();
        ed.putInt("UpdateVersion", getVersion());
        ed.apply();


        setContentView(R.layout.activity_changelog);
        setTitle(R.string.changelog_title);

        params = getWindow().getAttributes();
        params.width = LinearLayout.LayoutParams.MATCH_PARENT;
        this.getWindow().setAttributes(params);

        parent = findViewById(R.id.data);

        Button close = findViewById(R.id.closeBtn);

        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                finish();
            }
        });

        parseLog(R.raw.changelog_releases);
    }

    public int getVersion() {
        int v = 0;
        try {
            v = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            String TAG = "Field Book";
            Log.e(TAG, "" + e.getMessage());
        }
        return v;
    }

    // Helper function to add row
    public void parseLog(int resId) {
        try {
            InputStream is = getResources().openRawResource(resId);
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr, 8192);

            String curVersionName = null;

            String line;
            while ((line = br.readLine()) != null) {
                TextView header = new TextView(this);
                TextView content = new TextView(this);
                TextView spacer = new TextView(this);
                spacer.setTextSize(5);
                View ruler = new View(this);

                ruler.setBackgroundColor(getResources().getColor(R.color.main_colorAccent));
                header.setTextAppearance(getApplicationContext(), R.style.ChangelogTitles);
                content.setTextAppearance(getApplicationContext(), R.style.ChangelogContent);

                //header.setTextSize(TypedValue.COMPLEX_UNIT_SP,(int) getResources().getDimension(R.dimen.text_size_small)/ getResources().getDisplayMetrics().density);
                //content.setTextSize(TypedValue.COMPLEX_UNIT_SP,(int) getResources().getDimension(R.dimen.text_size_small)/ getResources().getDisplayMetrics().density);

                if (line.length() == 0) {
                    curVersionName = null;
                    spacer.setText("\n");
                    parent.addView(spacer);
                } else if (curVersionName == null) {
                    final String[] lineSplit = line.split("/");
                    curVersionName = lineSplit[1];
                    header.setText(curVersionName);
                    parent.addView(header);
                    parent.addView(ruler,
                            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 3));

                } else {
                    content.setText("•  " + line);
                    parent.addView(content);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}