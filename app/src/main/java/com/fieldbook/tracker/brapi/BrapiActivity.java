package com.fieldbook.tracker.brapi;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.arch.core.util.Function;

import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.PreferencesActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API test Screen
 */
public class BrapiActivity extends AppCompatActivity {

    private SharedPreferences preferences;
    private BrAPIService brAPIService;
    private StudySummary selectedStudy;

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_brapi);

        preferences = getSharedPreferences("Settings", 0);
        String brapiBaseURL = preferences.getString(PreferencesActivity.BRAPI_BASE_URL, "");
        brAPIService = new BrAPIService(this, brapiBaseURL);

        TextView baseURLText = findViewById(R.id.brapiBaseURL);
        baseURLText.setText(brapiBaseURL);

        loadToolbar();
        if(isConnected()) {
            loadStudiesList();
        }else{
            Toast.makeText(getApplicationContext(), "Device Offline: Please connect to a network and try again", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(null);
        getSupportActionBar().getThemedContext();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    private void loadStudiesList() {
        final ListView listStudies = findViewById(R.id.brapiStudies);
        listStudies.setVisibility(View.GONE);
        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
        brAPIService.getStudies(new Function<List<StudySummary>, Void>() {
            @Override
            public Void apply(final List<StudySummary> studies) {

                BrapiActivity.this.selectedStudy = null;

                listStudies.setAdapter(BrapiActivity.this.buildStudiesArrayAdapter(studies));
                listStudies.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        selectedStudy = studies.get(position);
                    }
                });

                listStudies.setVisibility(View.VISIBLE);
                findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                return null;
            }
        });
    }

    private ArrayAdapter buildStudiesArrayAdapter(List<StudySummary> studies) {
        ArrayList<String> itemDataList = new ArrayList<>();;

        for(StudySummary study: studies) {
            itemDataList.add(study.getStudyName());
        }

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, itemDataList);

        return arrayAdapter;
    }

    public void buttonClicked(View view) {
        switch(view.getId()) {
            case R.id.loadStudies:
                loadStudiesList();
                break;
            case R.id.save:
                saveStudy();
                break;
        }
    }

    private void saveStudy() {
        BrapiLoadDialog bld = new BrapiLoadDialog(this);
        bld.setSelectedStudy(this.selectedStudy);
        bld.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean isConnected(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;
    }
}