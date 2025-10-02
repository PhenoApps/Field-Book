package com.fieldbook.tracker.activities.brapi;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.ThemedActivity;
import com.fieldbook.tracker.brapi.BrapiAuthDialogFragment;
import com.fieldbook.tracker.brapi.model.BrapiTrial;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory;
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager;
import com.fieldbook.tracker.utilities.InsetHandler;
import com.fieldbook.tracker.utilities.Utils;

import java.util.ArrayList;
import java.util.List;

public class BrapiTrialActivity extends ThemedActivity {
    private BrAPIService brAPIService;
    private BrapiTrial brapiTrial;
    private BrapiPaginationManager paginationManager;

    private final BrapiAuthDialogFragment brapiAuth = new BrapiAuthDialogFragment().newInstance();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isConnected(this)) {
            if (BrAPIService.hasValidBaseUrl(this)) {
                setContentView(R.layout.activity_brapi_trials);
                paginationManager = new BrapiPaginationManager(this);
                brAPIService = BrAPIServiceFactory.getBrAPIService(BrapiTrialActivity.this);

                String brapiBaseURL = BrAPIService.getBrapiUrl(this);
                TextView baseURLText = findViewById(R.id.brapiBaseURL);
                baseURLText.setText(brapiBaseURL);

                loadToolbar();
                loadTrials();
            } else {
                Toast.makeText(getApplicationContext(), R.string.brapi_must_configure_url, Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(getApplicationContext(), R.string.device_offline_warning, Toast.LENGTH_SHORT).show();
            finish();
        }

        getOnBackPressedDispatcher().addCallback(this, standardBackCallback());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(null);
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        View rootView = findViewById(android.R.id.content);
        InsetHandler.INSTANCE.setupStandardInsets(rootView, toolbar);
    }

    private void loadTrials() {
        ListView trialsView = findViewById(R.id.brapiTrials);
        trialsView.setVisibility(View.GONE);
        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
        //init page numbers
        paginationManager.refreshPageIndicator();

        String programDbId = getIntent().getStringExtra(BrapiActivity.PROGRAM_DB_ID_INTENT_PARAM);

        brAPIService.getTrials(programDbId, paginationManager, trials -> {
            (BrapiTrialActivity.this).runOnUiThread(() -> {
                trialsView.setAdapter(BrapiTrialActivity.this.buildTrialsArrayAdapter(trials));
                trialsView.setOnItemClickListener((parent, view, position, id) ->
                        brapiTrial = trials.get(position)
                );
                trialsView.setVisibility(View.VISIBLE);
                findViewById(R.id.loadingPanel).setVisibility(View.GONE);
            });
            return null;
        }, code -> {
            (BrapiTrialActivity.this).runOnUiThread(() -> {
                // Show error message. We don't finish the activity intentionally.
                if(BrAPIService.isConnectionError(code)){
                    if (BrAPIService.handleConnectionError(BrapiTrialActivity.this, code)) {
                        showBrapiAuthDialog();
                    }
                }else {
                    Toast.makeText(getApplicationContext(), getString(R.string.brapi_trials_error), Toast.LENGTH_LONG).show();
                }
                findViewById(R.id.loadingPanel).setVisibility(View.GONE);
            });
            return null;
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

    private ListAdapter buildTrialsArrayAdapter(List<BrapiTrial> trials) {
        List<Object> itemDataList = new ArrayList<>();
        for (BrapiTrial trial : trials) {
            if(trial.trialName != null)
                itemDataList.add(trial.trialName);
            else
                itemDataList.add(trial.trialDbId);
        }
        return new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, itemDataList);
    }

    public void buttonClicked(View view) {
        int id = view.getId();
        if (id == R.id.loadTrials) {
            paginationManager.reset();
            loadTrials();
        } else if (id == R.id.selectTrial) {
            if (this.brapiTrial != null) {
                Intent intent = new Intent();
                intent.setData(Uri.parse(this.brapiTrial.trialDbId));
                setResult(RESULT_OK, intent);
                finish();
            } else {
                Toast.makeText(getApplicationContext(), R.string.brapi_warning_select_trial, Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.prev || id == R.id.next) {// Update current page (if allowed) and start brapi call.
            paginationManager.setNewPage(view.getId());
            loadTrials();
        }
    }
}
