package com.fieldbook.tracker.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import com.fieldbook.tracker.brapi.BrapiProgram;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.utilities.Utils;

import java.util.ArrayList;
import java.util.List;

public class BrapiProgramActivity extends AppCompatActivity {
    private BrAPIService brAPIService;
    private BrapiProgram brapiProgram;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isConnected(this)) {
            if (BrAPIService.hasValidBaseUrl(this)) {
                setContentView(R.layout.activity_brapi_programs);
                String brapiBaseURL = BrAPIService.getBrapiUrl(this);
                brAPIService = new BrAPIService(brapiBaseURL, new DataHelper(BrapiProgramActivity.this));

                TextView baseURLText = findViewById(R.id.brapiBaseURL);
                baseURLText.setText(brapiBaseURL);

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

    private void loadPrograms() {
        ListView programsView = findViewById(R.id.brapiPrograms);
        programsView.setVisibility(View.GONE);
        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);

        brAPIService.getPrograms(BrAPIService.getBrapiToken(this), new Function<List<BrapiProgram>, Void>() {
            @Override
            public Void apply(List<BrapiProgram> programs) {
                (BrapiProgramActivity.this).runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        programsView.setAdapter(BrapiProgramActivity.this.buildProgramsArrayAdapter(programs));
                        programsView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                brapiProgram = programs.get(position);
                            }
                        });
                        programsView.setVisibility(View.VISIBLE);
                        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                    }
                });
                return null;
            }
        }, new Function<String, Void>() {
            @Override
            public Void apply(String input) {
                (BrapiProgramActivity.this).runOnUiThread(new Runnable() {
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

    private ListAdapter buildProgramsArrayAdapter(List<BrapiProgram> programs) {
        List<Object> itemDataList = new ArrayList<>();
        for (BrapiProgram program : programs) {
            itemDataList.add(program.getProgramName());
        }
        ListAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, itemDataList);
        return adapter;
    }

    public void buttonClicked(View view) {
       switch (view.getId()) {
           case R.id.loadPrograms:
               loadPrograms();
               break;
           case R.id.selectProgram:
               Intent intent = new Intent();
               intent.setData(Uri.parse(this.brapiProgram.getProgramDbId()));
               setResult(RESULT_OK, intent);
               finish();
               break;
       }
    }
}
