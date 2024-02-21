package com.fieldbook.tracker.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationModel;

import org.threeten.bp.format.DateTimeFormatter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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

        try {
            calculateStats();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

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

    public void calculateStats() throws ParseException {
        int fields = database.getAllFieldObjects().size();
        int plots = database.getAllObservationUnits().length;
        ObservationModel[] observations = database.getAllObservations();

        Set<String> collectors = new HashSet<>();
        ArrayList<Date> dateObjects = new ArrayList<>();
        Map<String, Integer> dateCount = new HashMap<>();

        for (ObservationModel observation : observations) {
            String collector = observation.getCollector();
            if (collector != null && !collector.trim().isEmpty()) {
                collectors.add(collector);
            }

            String time = observation.getObservation_time_stamp();
            Date dateObject = timeStamp.parse(time);
            dateObjects.add(dateObject);
        }

        long totalInterval = 0;
        for (int i = 1; i< dateObjects.size(); i++){
            long diff = dateObjects.get(i).getTime() - dateObjects.get(i-1).getTime();
            if (diff <= TimeUnit.MINUTES.toMillis(30)){
                totalInterval += TimeUnit.MILLISECONDS.toSeconds(diff);
            }
        }
        String timeString = String.format("%02d:%02d:%02d", totalInterval / 3600, (totalInterval % 3600) / 60, totalInterval % 60);

        Log.d(TAG, "calculateStats: " + fields);
        View statisticsCard = findViewById(R.id.statistics_cards);

        TextView stat1 = statisticsCard.findViewById(R.id.stat_value_1);
        TextView stat2 = statisticsCard.findViewById(R.id.stat_value_2);
        TextView stat3 = statisticsCard.findViewById(R.id.stat_value_3);
        TextView stat4 = statisticsCard.findViewById(R.id.stat_value_4);
        TextView stat5 = statisticsCard.findViewById(R.id.stat_value_5);
        TextView stat6 = statisticsCard.findViewById(R.id.stat_value_6);
        TextView stat7 = statisticsCard.findViewById(R.id.stat_value_7);
        TextView stat8 = statisticsCard.findViewById(R.id.stat_value_8);


        stat1.setText(String.valueOf(fields));
        stat2.setText(String.valueOf(plots));
        stat3.setText(String.valueOf(observations.length));
        stat4.setText(timeString);
        stat5.setText(String.valueOf(collectors.size()));


    }
}