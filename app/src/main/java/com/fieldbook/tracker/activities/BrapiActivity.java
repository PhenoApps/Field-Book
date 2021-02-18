package com.fieldbook.tracker.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.arch.core.util.Function;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory;
import com.fieldbook.tracker.brapi.BrapiLoadDialog;
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager;
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails;
import com.fieldbook.tracker.utilities.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * API test Screen
 */
public class BrapiActivity extends AppCompatActivity {

    private BrAPIService brAPIService;
    private BrapiStudyDetails selectedStudy;

    // Filter by
    private String programDbId;
    private String trialDbId;
    private BrapiPaginationManager paginationManager;
    private static final int FILTER_BY_PROGRAM_REQUEST_CODE = 1;
    private static final int FILTER_BY_TRIAL_REQUEST_CODE = 2;
    public static final String PROGRAM_DB_ID_INTENT_PARAM = "programDbId";

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
                paginationManager = new BrapiPaginationManager(this);

                brAPIService = BrAPIServiceFactory.getBrAPIService(BrapiActivity.this);

                String brapiBaseURL = BrAPIService.getBrapiUrl(this);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_fields_brapi, menu);
        return true;
    }

    private void loadToolbar() {
        if (getSupportActionBar() != null) {
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

        //init page numbers
        paginationManager.refreshPageIndicator();
        Integer initPage = paginationManager.getPage();

        brAPIService.getStudies(this.programDbId, this.trialDbId, paginationManager, new Function<List<BrapiStudyDetails>, Void>() {
            @Override
            public Void apply(final List<BrapiStudyDetails> studies) {

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
        }, new Function<Integer, Void>() {


            @Override
            public Void apply(final Integer code) {

                (BrapiActivity.this).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Show error message. We don't finish the activity intentionally.
                        if(BrAPIService.isConnectionError(code)){
                            BrAPIService.handleConnectionError(BrapiActivity.this, code);
                        }else {
                            Toast.makeText(getApplicationContext(), getString(R.string.brapi_studies_error), Toast.LENGTH_LONG).show();
                        }
                        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                    }
                });

                return null;

            }
        });
    }

    private ArrayAdapter buildStudiesArrayAdapter(List<BrapiStudyDetails> studies) {
        ArrayList<String> itemDataList = new ArrayList<>();

        for (BrapiStudyDetails study : studies) {
            if(study.getStudyName() != null)
                itemDataList.add(study.getStudyName());
            else
                itemDataList.add(study.getStudyDbId());
        }

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, itemDataList);

        return arrayAdapter;
    }

    public void buttonClicked(View view) {
        switch (view.getId()) {
            case R.id.loadStudies:
                // Start from beginning
                paginationManager.reset();
                loadStudiesList();
                break;
            case R.id.save:
                saveStudy();
                break;
            case R.id.prev:
            case R.id.next:
                // Update current page (if allowed) and start brapi call.
                paginationManager.setNewPage(view.getId());
                loadStudiesList();
                break;
        }
    }

    private void saveStudy() {
        if(this.selectedStudy != null) {
            BrapiLoadDialog bld = new BrapiLoadDialog(this);
            bld.setSelectedStudy(this.selectedStudy);
            //brapi load dialog no longer explicitly finishes the calling activity
            //this listener was added to finish the activity after a small delay,
            //allowing the async task to finish before the field editor list is populated.
            bld.setOnDismissListener(dialog ->
                    new Handler().postDelayed(() ->
                            BrapiActivity.this.runOnUiThread(this::finish), 1500));
            bld.show();
        }else{
            Toast.makeText(getApplicationContext(), R.string.brapi_warning_select_study, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.filter_by_program:
                Intent filterByProgramIntent = new Intent(this, BrapiProgramActivity.class);
                startActivityForResult(filterByProgramIntent, FILTER_BY_PROGRAM_REQUEST_CODE);
                break;
            case R.id.filter_by_trial:
                Intent filterByTrialIntent = new Intent(this, BrapiTrialActivity.class);
                filterByTrialIntent.putExtra(PROGRAM_DB_ID_INTENT_PARAM, programDbId);
                startActivityForResult(filterByTrialIntent, FILTER_BY_TRIAL_REQUEST_CODE);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILTER_BY_PROGRAM_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                programDbId = data.getDataString();
                // reset previous filter
                trialDbId = null;
                paginationManager.reset();
                loadStudiesList();
            }
        } else if (requestCode == FILTER_BY_TRIAL_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                trialDbId = data.getDataString();
                paginationManager.reset();
                loadStudiesList();
            }
        }
    }
}