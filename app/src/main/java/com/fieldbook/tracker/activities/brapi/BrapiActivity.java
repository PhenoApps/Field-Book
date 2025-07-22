package com.fieldbook.tracker.activities.brapi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.arch.core.util.Function;
import androidx.fragment.app.FragmentActivity;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.ThemedActivity;
import com.fieldbook.tracker.brapi.BrapiAuthDialogFragment;
import com.fieldbook.tracker.brapi.BrapiLoadDialog;
import com.fieldbook.tracker.brapi.model.BrapiObservationLevel;
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory;
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager;
import com.fieldbook.tracker.utilities.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * API test Screen
 */
public class BrapiActivity extends ThemedActivity {

    private BrAPIService brAPIService;
    private BrapiStudyDetails selectedStudy;

    BrapiLoadDialog brapiLoadDialog;

    // Filter by
    private String programDbId;
    private String trialDbId;
    private BrapiPaginationManager paginationManager;
    private static final int FILTER_BY_PROGRAM_REQUEST_CODE = 1;
    private static final int FILTER_BY_TRIAL_REQUEST_CODE = 2;
    public static final String PROGRAM_DB_ID_INTENT_PARAM = "programDbId";
    private List<BrapiObservationLevel> observationLevels;
    private BrapiObservationLevel selectedObservationLevel;

    private BrapiAuthDialogFragment brapiAuth = new BrapiAuthDialogFragment().newInstance();

    public static Intent getIntent(Context context) {
        return new Intent(context, BrapiActivity.class);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (brapiLoadDialog != null && brapiLoadDialog.isAdded()) {
            brapiLoadDialog.dismiss();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        brAPIService.authorizeClient();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isConnected(this)) {
            if (BrAPIService.hasValidBaseUrl(this)) {
                setContentView(R.layout.activity_brapi);
                paginationManager = new BrapiPaginationManager(this);

                brAPIService = BrAPIServiceFactory.getBrAPIService(BrapiActivity.this);
                brapiLoadDialog = BrapiLoadDialog.newInstance();

                String brapiBaseURL = BrAPIService.getBrapiUrl(this);
                TextView baseURLText = findViewById(R.id.brapiBaseURL);
                baseURLText.setText(brapiBaseURL);

                loadToolbar();
                loadObservationLevels();
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
        setSupportActionBar(findViewById(R.id.toolbar));

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.import_dialog_title_brapi_fields);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
    }

    private void setupObservationLevelsSpinner() {

        if (!observationLevels.isEmpty()) {
            selectedObservationLevel = observationLevels.get(0);
            Spinner spinner = findViewById(R.id.studyObservationLevels);
            List<String> levelOptionsList = new ArrayList<>();
            for(BrapiObservationLevel level : observationLevels) {
                levelOptionsList.add(level.getObservationLevelName());
            }
            ArrayAdapter<String> levelOptions = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, levelOptionsList);

            spinner.setAdapter(levelOptions);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int index, long id) {
                    selectedObservationLevel = observationLevels.get(index);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });
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
                            if (BrAPIService.handleConnectionError(BrapiActivity.this, code)) {
                                showBrapiAuthDialog();
                            }
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

    private void showBrapiAuthDialog() {
        try {
            runOnUiThread(() -> {
                if (!brapiAuth.isVisible()) {
                    brapiAuth.show(getSupportFragmentManager(), "BrapiAuthDialogFragment");
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadObservationLevels() {
        brAPIService.getObservationLevels(programDbId, input -> {
            this.observationLevels = input;
            runOnUiThread(() -> {
                setupObservationLevelsSpinner();
                loadStudiesList();
            });
        }, failureInput -> {
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

        return (ArrayAdapter<String>) new ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, itemDataList);
    }

    public void buttonClicked(View view) {
        int id = view.getId();
        if (id == R.id.loadStudies) {// Start from beginning
            paginationManager.reset();
            loadStudiesList();
        } else if (id == R.id.save) {
            saveStudy();
        } else if (id == R.id.prev || id == R.id.next) {// Update current page (if allowed) and start brapi call.
            paginationManager.setNewPage(view.getId());
            loadStudiesList();
        }
    }

    private void saveStudy() {
        if(this.selectedStudy != null) {
            brapiLoadDialog.setSelectedStudy(this.selectedStudy);
            brapiLoadDialog.setObservationLevel(this.selectedObservationLevel);
            brapiLoadDialog.setPaginationManager(this.paginationManager);
            brapiLoadDialog.show(this.getSupportFragmentManager(), "BrapiLoadDialog");
        } else{
            Toast toast = Toast.makeText(getApplicationContext(), R.string.brapi_warning_select_study, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL, 0, 0);
            toast.show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
        } else if (itemId == R.id.filter_by_program) {
            Intent filterByProgramIntent = new Intent(this, BrapiProgramActivity.class);
            startActivityForResult(filterByProgramIntent, FILTER_BY_PROGRAM_REQUEST_CODE);
        } else if (itemId == R.id.filter_by_trial) {
            Intent filterByTrialIntent = new Intent(this, BrapiTrialActivity.class);
            filterByTrialIntent.putExtra(PROGRAM_DB_ID_INTENT_PARAM, programDbId);
            startActivityForResult(filterByTrialIntent, FILTER_BY_TRIAL_REQUEST_CODE);
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