package com.fieldbook.tracker.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.arthenica.ffmpegkit.Statistics;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.adapters.StatisticsAdapter;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.dialogs.DatePickerFragment;

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
    String seasonStartDate;
    List<String> seasons = new ArrayList<>();
    RecyclerView rvStatisticsCard;
    private static final String TIME_FORMAT_PATTERN = "yyyy-MM-dd";
    private SimpleDateFormat timeStamp = new SimpleDateFormat(TIME_FORMAT_PATTERN, Locale.getDefault());

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

        timeStamp = new SimpleDateFormat(TIME_FORMAT_PATTERN, Locale.getDefault());

        seasonStartDate = "-01-01";

        rvStatisticsCard = findViewById(R.id.statistics_card_rv);
        rvStatisticsCard.setLayoutManager(new LinearLayoutManager(this));

        setSeasons();

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
            DialogFragment newFragment = new DatePickerFragment().newInstance(timeStamp, (y, m, d) -> {
                seasonStartDate = "-" + String.format("%02d", m + 1) + "-" + String.format("%02d", d);
                setSeasons();
                return null;
            });
            newFragment.show(getSupportFragmentManager(), TAG);
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

    public void setSeasons() {
        Set<String> uniqueSeasons = new TreeSet<>(Comparator.reverseOrder());

        ObservationModel[] observations = database.getAllObservations();
        for (ObservationModel observation : observations) {
            String timeStamp = observation.getObservation_time_stamp();
            String season;
            if (seasonStartDate.compareTo(timeStamp.substring(4, 10)) < 0) {
                season = timeStamp.substring(0, 4) + seasonStartDate;
            } else {
                season = Integer.parseInt(timeStamp.substring(0, 4)) - 1 + seasonStartDate;
            }
            uniqueSeasons.add(season);
        }

        seasons = new ArrayList<>(uniqueSeasons);
        rvStatisticsCard.setAdapter(new StatisticsAdapter(this, seasons));

    }
}