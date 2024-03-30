package com.fieldbook.tracker.activities;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.adapters.StatisticsAdapter;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.dialogs.StatisticsCalendarFragment;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class StatisticsActivity extends ThemedActivity implements StatisticsAdapter.statisticsCardLongPressedListener {
    public static String TAG = "Statistics Activity";
    @Inject
    DataHelper database;
    List<String> seasons = new ArrayList<>();
    RecyclerView rvStatisticsCard;
    private int toggleVariable = 0;
    private Snackbar snackbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.settings_statistics));
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        rvStatisticsCard = findViewById(R.id.statistics_card_rv);
        rvStatisticsCard.setLayoutManager(new LinearLayoutManager(this));

        setSeasons();

        snackbar = Snackbar.make(rvStatisticsCard, R.string.stats_export, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(getString(R.string.dialog_close), view -> snackbar.dismiss());
        snackbar.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_statistics, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        final int toggleViewId = R.id.stats_toggle_view;
        final int heatmapId = R.id.stats_heatmap;

        int itemId = item.getItemId();

        if (itemId == toggleViewId) {
            toggleVariable = 1 - toggleVariable;
            setSeasons();
            return true;
        } else if (itemId == heatmapId) {
            StatisticsCalendarFragment calendarFragment = new StatisticsCalendarFragment(this);
            getSupportFragmentManager().beginTransaction().replace(android.R.id.content, calendarFragment).addToBackStack(null).commit();
        } else if (itemId == android.R.id.home) {
            onBackPressed();
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
            if (toggleVariable == 0)
                uniqueSeasons.add(timeStamp.substring(0, 4));
            else
                uniqueSeasons.add(timeStamp.substring(0, 7));
        }

        seasons = new ArrayList<>(uniqueSeasons);
        rvStatisticsCard.setAdapter(new StatisticsAdapter(this, seasons, this));

    }

    @Override
    public void dismissSnackBar() {
        snackbar.dismiss();
    }
}