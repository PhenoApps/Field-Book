package com.fieldbook.tracker.activities.brapi;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
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
import com.fieldbook.tracker.brapi.model.BrapiProgram;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory;
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager;
import com.fieldbook.tracker.utilities.InsetHandler;
import com.fieldbook.tracker.utilities.Utils;

import java.util.ArrayList;
import java.util.List;

public class BrapiProgramActivity extends ThemedActivity {
    private static final String TAG = BrapiProgramActivity.class.getSimpleName();
    private BrAPIService brAPIService;
    private BrapiProgram brapiProgram;
    private BrapiPaginationManager paginationManager;
    private ListView programsView;
    private final ArrayList<BrapiProgram> programsList = new ArrayList<>();

    private final BrapiAuthDialogFragment brapiAuth = new BrapiAuthDialogFragment().newInstance();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isConnected(this)) {
            if (BrAPIService.hasValidBaseUrl(this)) {
                setContentView(R.layout.activity_brapi_programs);
                paginationManager = new BrapiPaginationManager(this);
                brAPIService = BrAPIServiceFactory.getBrAPIService(BrapiProgramActivity.this);

                String brapiBaseURL = BrAPIService.getBrapiUrl(this);
                TextView baseURLText = findViewById(R.id.brapiBaseURL);
                baseURLText.setText(brapiBaseURL);

                loadUi();
                loadToolbar();
                loadPrograms();

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

    private void loadUi() {

        programsView = findViewById(R.id.brapiPrograms);
        programsView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

                Log.d(TAG, "onScroll: firstVisibleItem: " + firstVisibleItem + " visibleItemCount: " + visibleItemCount + " totalItemCount: " + totalItemCount);

                if (paginationManager.getPage() < paginationManager.getTotalPages() - 1) {

                    if (firstVisibleItem + visibleItemCount == totalItemCount && totalItemCount != 0) {

                        //programsView.setOnScrollListener(null);

                        //programsView.smoothScrollToPosition(0);

                        Log.d(TAG, "onScroll: firstVisibleItem + visibleItemCount == totalItemCount");

                        paginationManager.setNewPage(R.id.next);

                        loadPrograms();
                    }
                }
            }
        });
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

    private void loadPrograms() {

        programsView.setVisibility(View.GONE);
        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
        //init page numbers
        paginationManager.refreshPageIndicator();

        brAPIService.getPrograms(paginationManager, programs -> {

            programsList.addAll(programs);

            (BrapiProgramActivity.this).runOnUiThread(() -> {
                programsView.setAdapter(BrapiProgramActivity.this.buildProgramsArrayAdapter(programsList));
                programsView.setOnItemClickListener((parent, view, position, id) -> brapiProgram = programsList.get(position));
                programsView.setVisibility(View.VISIBLE);
                findViewById(R.id.loadingPanel).setVisibility(View.GONE);
            });

            return null;

        }, code -> {
            (BrapiProgramActivity.this).runOnUiThread(() -> {
                // Show error message. We don't finish the activity intentionally.
                if(BrAPIService.isConnectionError(code)){
                    if (BrAPIService.handleConnectionError(BrapiProgramActivity.this, code)) {
                        showBrapiAuthDialog();
                    }
                }else {
                    Toast.makeText(getApplicationContext(), getString(R.string.brapi_programs_error), Toast.LENGTH_LONG).show();
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

    private ListAdapter buildProgramsArrayAdapter(List<BrapiProgram> programs) {
        List<Object> itemDataList = new ArrayList<>();
        for (BrapiProgram program : programs) {
            if(program.programName != null)
                itemDataList.add(program.programName);
            else
                itemDataList.add(program.programDbId);
        }
        return new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, itemDataList);
    }

    public void buttonClicked(View view) {
        int id = view.getId();
        if (id == R.id.loadPrograms) {
            paginationManager.reset();
            loadPrograms();
        } else if (id == R.id.selectProgram) {
            if (this.brapiProgram != null) {
                Intent intent = new Intent();
                intent.setData(Uri.parse(this.brapiProgram.programDbId));
                setResult(RESULT_OK, intent);
                finish();
            } else {
                Toast.makeText(getApplicationContext(), R.string.brapi_warning_select_program, Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.prev || id == R.id.next) {// Update current page (if allowed) and start brapi call.
            paginationManager.setNewPage(view.getId());
            loadPrograms();
        }
    }
}
