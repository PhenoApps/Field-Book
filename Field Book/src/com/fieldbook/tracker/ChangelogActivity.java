package com.fieldbook.tracker;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.TypedValue;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by trife on 6/24/2014.
 */

public class ChangelogActivity extends Activity {
    Handler mHandler = new Handler();

    private SharedPreferences ep;

    private String currentTable;
    private String importId;
    private String local;
    private String region;
    WindowManager.LayoutParams params;
    private LinearLayout parent;

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        // Enforce language
        local = ep.getString("language", "en");
        region = ep.getString("region",region);
        Locale locale2 = new Locale(local,"");
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2, getBaseContext().getResources()
                .getDisplayMetrics());

        SharedPreferences.Editor ed = ep.edit();
        ed.putInt("UpdateVersion", getVersion());
        ed.commit();


        setContentView(R.layout.changelog);
        setTitle(R.string.updatemsg);
        params = getWindow().getAttributes();

        this.getWindow().setAttributes(params);
        parent = (LinearLayout) findViewById(R.id.data);

        Button close = (Button) findViewById(R.id.closeBtn);

        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                finish();
            }
        });

        parseLog(R.raw.changelog);
    }

    public int getVersion() {
        int v = 0;
        try {
            v = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // Huh? Really?
        }
        return v;
    }

    // Helper function to add row
    public void parseLog(int resId) {
        List views = new ArrayList();

        try {
            InputStream is = getResources().openRawResource(resId);
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr, 8192);

            int curVersionCode = -1;
            String curVersionName = null;

            String line;
            while ((line = br.readLine()) != null) {
                TextView header = (TextView) new TextView(this);
                TextView content = (TextView) new TextView(this);
                TextView spacer = (TextView) new TextView(this);
                View ruler = new View(this);

                ruler.setBackgroundColor(0xff33b5e5);
                header.setTextAppearance(getApplicationContext(), R.style.Dialog_SectionTitles);
                content.setTextAppearance(getApplicationContext(), R.style.Dialog_SectionSubtitles);

                if (line.length() == 0) {
                    curVersionCode = -1;
                    curVersionName = null;
                    spacer.setText("\n");
                    parent.addView(spacer);
                } else if (curVersionName == null) {
                    final String[] lineSplit = line.split("/");
                    curVersionCode = Integer.parseInt(lineSplit[0]);
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