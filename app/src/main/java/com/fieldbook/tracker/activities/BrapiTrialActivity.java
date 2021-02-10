package com.fieldbook.tracker.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.arch.core.util.Function;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.BrAPIService;
import com.fieldbook.tracker.brapi.BrapiPaginationManager;
import com.fieldbook.tracker.brapi.BrapiTrial;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.Utils;

import java.util.ArrayList;
import java.util.List;

import io.swagger.client.ApiException;

public class BrapiTrialActivity extends AppCompatActivity {
    private BrAPIService brAPIService;
    private BrapiTrial brapiTrial;
    private BrapiPaginationManager paginationManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isConnected(this)) {
            if (BrAPIService.hasValidBaseUrl(this)) {
                setContentView(R.layout.activity_brapi_trials);
                paginationManager = new BrapiPaginationManager(this);
                String brapiBaseURL = BrAPIService.getBrapiUrl(this);

                brAPIService = new BrAPIService(brapiBaseURL, new DataHelper(BrapiTrialActivity.this), this);

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
    }

    private void loadTrials() {
        ListView trialsView = findViewById(R.id.brapiTrials);
        trialsView.setVisibility(View.GONE);
        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
        //init page numbers
        paginationManager.refreshPageIndicator();

        String programDbId = getIntent().getStringExtra(BrapiActivity.PROGRAM_DB_ID_INTENT_PARAM);

        brAPIService.getTrials(BrAPIService.getBrapiToken(this), programDbId, paginationManager, new Function<List<BrapiTrial>, Void>() {
            @Override
            public Void apply(List<BrapiTrial> trials) {
                (BrapiTrialActivity.this).runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        trialsView.setAdapter(BrapiTrialActivity.this.buildTrialsArrayAdapter(trials));
                        trialsView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                brapiTrial = trials.get(position);
                            }
                        });
                        trialsView.setVisibility(View.VISIBLE);
                        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                    }
                });
                return null;
            }
        }, new Function<ApiException, Void>() {
            @Override
            public Void apply(ApiException error) {
                (BrapiTrialActivity.this).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Show error message. We don't finish the activity intentionally.
                        if(BrAPIService.isConnectionError(error.getCode())){
                            BrAPIService.handleConnectionError(BrapiTrialActivity.this, error.getCode());
                        }else {
                            Toast.makeText(getApplicationContext(), getString(R.string.brapi_trials_error), Toast.LENGTH_LONG).show();
                        }
                        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                    }
                });
                return null;
            }
        });
    }

    private ListAdapter buildTrialsArrayAdapter(List<BrapiTrial> trials) {
        List<Object> itemDataList = new ArrayList<>();
        for (BrapiTrial trial : trials) {
            itemDataList.add(trial.getTrialName());
        }
        ListAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, itemDataList);
        return adapter;
    }

    public void buttonClicked(View view) {
        switch (view.getId()) {
            case R.id.loadTrials:
                paginationManager.reset();
                loadTrials();
                break;
            case R.id.selectTrial:
                if (this.brapiTrial != null) {
                    Intent intent = new Intent();
                    intent.setData(Uri.parse(this.brapiTrial.getTrialDbId()));
                    setResult(RESULT_OK, intent);
                    finish();
                } else {
                    Toast.makeText(getApplicationContext(), R.string.brapi_warning_select_trial, Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.prev:
            case R.id.next:
                // Update current page (if allowed) and start brapi call.
                paginationManager.setNewPage(view.getId());
                loadTrials();
                break;
        }
    }
}
