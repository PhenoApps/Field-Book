package com.fieldbook.tracker.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.arch.core.util.Function;

import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.fieldbook.tracker.brapi.BrAPIService;
import com.fieldbook.tracker.brapi.BrapiLoadDialog;
import com.fieldbook.tracker.brapi.BrapiStudySummary;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.utilities.Utils;


import java.util.ArrayList;
import java.util.List;

/**
 * API test Screen
 */
public class BrapiActivity extends AppCompatActivity {

    private BrAPIService brAPIService;
    private BrapiStudySummary selectedStudy;

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

        if (Utils.isConnected(this)) {
            if (BrAPIService.hasValidBaseUrl(this)) {
                setContentView(R.layout.activity_brapi);
                String brapiBaseURL = BrAPIService.getBrapiUrl(this);
                brAPIService = new BrAPIService(brapiBaseURL, new DataHelper(BrapiActivity.this));

                TextView baseURLText = findViewById(R.id.brapiBaseURL);
                baseURLText.setText(brapiBaseURL);

                loadToolbar();
                loadStudiesList();
            } else {
                Toast.makeText(getApplicationContext(), R.string.brapi_must_configure_url, Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(getApplicationContext(), R.string.device_offline_warning, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null){
            getSupportActionBar().setTitle(null);
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
    }

    private void loadStudiesList() {
        final ListView listStudies = findViewById(R.id.brapiStudies);
        listStudies.setVisibility(View.GONE);
        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);

        brAPIService.getStudies(BrAPIService.getBrapiToken(this), new Function<List<BrapiStudySummary>, Void>() {
            @Override
            public Void apply(final List<BrapiStudySummary> studies) {

                (BrapiActivity.this).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
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
                    }
                });

                return null;
            }
        }, new Function<String, Void>() {


            @Override
            public Void apply(final String input) {

                (BrapiActivity.this).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Show error message. We don't finish the activity intentionally.
                        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                        Toast.makeText(getApplicationContext(), input, Toast.LENGTH_LONG).show();
                    }
                });

                return null;

            }
        });
    }

    private ArrayAdapter buildStudiesArrayAdapter(List<BrapiStudySummary> studies) {
        ArrayList<String> itemDataList = new ArrayList<>();

        for (BrapiStudySummary study : studies) {
            itemDataList.add(study.getStudyName());
        }

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, itemDataList);

        return arrayAdapter;
    }

    public void buttonClicked(View view) {
        switch (view.getId()) {
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
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

}