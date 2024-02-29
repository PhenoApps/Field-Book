package com.fieldbook.tracker.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.adapters.StatisticsAdapter;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationModel;

import org.threeten.bp.format.DateTimeFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class StatisticsActivity extends ThemedActivity {
    public static String TAG = "Statistics Activity";
    @Inject
    DataHelper database;
    private SimpleDateFormat timeStamp;
    private DateTimeFormatter timeFormat;
    private static final String TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss.SSSZZZZZ";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Statistics");
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        timeStamp = new SimpleDateFormat(TIME_FORMAT_PATTERN,
                Locale.getDefault());

        timeFormat = DateTimeFormatter.ofPattern(TIME_FORMAT_PATTERN, Locale.getDefault());

        Set<String> uniqueSeasons = new TreeSet<>(Comparator.reverseOrder());

        ObservationModel[] observations = database.getAllObservations();
        for (ObservationModel observation: observations)
        {
            String timeStamp = observation.getObservation_time_stamp();
            String year = timeStamp.substring(0,4);
            uniqueSeasons.add(year);
        }

        List<String> seasons = new ArrayList<>(uniqueSeasons);

        RecyclerView rvStatisticsCard = findViewById(R.id.statistics_card_rv);
        rvStatisticsCard.setAdapter(new StatisticsAdapter(this, seasons));
        rvStatisticsCard.setLayoutManager(new LinearLayoutManager(this));

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_statistics, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        final int exportId = R.id.stats_export;
        final int calendarId = R.id.stats_calendar;

        int itemId = item.getItemId();

        if (itemId == exportId) {
            return true;
        } else if (itemId == calendarId) {
            return true;
        } else if (itemId == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @NonNull
    public DataHelper getDatabase() {
        return database;
    }
}