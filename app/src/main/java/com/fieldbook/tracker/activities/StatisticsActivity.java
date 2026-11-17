package com.fieldbook.tracker.activities;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.adapters.StatisticsAdapter;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.dialogs.StatisticsCalendarFragment;
import com.fieldbook.tracker.utilities.InsetHandler;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class StatisticsActivity extends ThemedActivity {
    public static String TAG = "Statistics Activity";
    @Inject
    DataHelper database;
    List<String> seasons = new ArrayList<>();
    RecyclerView rvStatisticsCard;
    private ToggleVariable toggleVariable = ToggleVariable.TOTAL;
    AlertDialog loadingDialog;

    /**
     * Once the loading dialog is actually on screen it stays there at least this long. Without a
     * floor, a fast load shows and hides it within a frame or two, which registers as a flicker
     * rather than as feedback.
     */
    private static final long LOADING_MINIMUM_VISIBLE_MS = 500L;

    private final Handler loadingHandler = new Handler(Looper.getMainLooper());
    /** Uptime at which the dialog first drew, or 0 while it has yet to be drawn. */
    private long loadingVisibleAtMs = 0L;
    private Runnable pendingLoadingDismiss;
    public enum ToggleVariable {
        TOTAL,
        YEAR,
        MONTH
    }

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

        TabLayout tabLayout = findViewById(R.id.tab_layout);

        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.stats_tab_layout_total)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.stats_tab_layout_year)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.stats_tab_layout_month)));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0: toggleVariable = ToggleVariable.TOTAL; break;
                    case 1: toggleVariable = ToggleVariable.YEAR; break;
                    case 2: toggleVariable = ToggleVariable.MONTH; break;
                }
                loadData();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setView(getLayoutInflater().inflate(R.layout.dialog_loading, null));
        loadingDialog = builder.create();

        loadData();

        setupStatisticsInsets(toolbar);

        getOnBackPressedDispatcher().addCallback(this, fragmentBasedBackCallback());
    }

    @Override
    protected void onDestroy() {
        // A delayed dismiss must not outlive the activity, or it fires against a destroyed
        // window. Dismissing here also avoids leaking the dialog if the minimum has not elapsed.
        if (pendingLoadingDismiss != null) {
            loadingHandler.removeCallbacks(pendingLoadingDismiss);
            pendingLoadingDismiss = null;
        }
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_statistics, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        final int heatmapId = R.id.stats_heatmap;

        int itemId = item.getItemId();

        if (itemId == heatmapId) {
            StatisticsCalendarFragment calendarFragment = new StatisticsCalendarFragment(this);
            getSupportFragmentManager().beginTransaction().replace(android.R.id.content, calendarFragment).addToBackStack(null).commit();
        } else if (itemId == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    @NonNull
    public DataHelper getDatabase() {
        return database;
    }

    /**
     * Displays the loading screen and loads the statistics asynchronously
     */
    public void loadData() {
        showLoadingDialog();

        loadingHandler.post(this::setSeasons);
    }

    private void showLoadingDialog() {
        if (pendingLoadingDismiss != null) {
            loadingHandler.removeCallbacks(pendingLoadingDismiss);
            pendingLoadingDismiss = null;
        }

        loadingVisibleAtMs = 0L;
        loadingDialog.show();

        // The minimum is timed from the first draw rather than from show(). setSeasons() and the
        // adapter's row binding both run on the main thread, so the dialog cannot be drawn until
        // the load has already finished — a minimum measured from show() would elapse entirely
        // while the thread was blocked and the dialog would still vanish on its first frame.
        View decorView = loadingDialog.getWindow() != null
                ? loadingDialog.getWindow().getDecorView()
                : null;
        if (decorView != null) {
            OneShotPreDrawListener.add(
                    decorView, () -> loadingVisibleAtMs = SystemClock.uptimeMillis());
        }
    }

    private void dismissLoadingDialog() {
        if (!loadingDialog.isShowing()) return;

        // A dialog that has not drawn yet counts as zero time on screen, so it waits the full
        // minimum rather than being dismissed before the user ever sees it.
        long visibleForMs = loadingVisibleAtMs == 0L
                ? 0L
                : SystemClock.uptimeMillis() - loadingVisibleAtMs;
        long remainingMs = Math.max(0L, LOADING_MINIMUM_VISIBLE_MS - visibleForMs);

        pendingLoadingDismiss = () -> {
            pendingLoadingDismiss = null;
            if (!isFinishing() && !isDestroyed() && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        };
        loadingHandler.postDelayed(pendingLoadingDismiss, remainingMs);
    }

    public void setSeasons() {

        if (toggleVariable == ToggleVariable.TOTAL) {
            seasons.clear();
            seasons.add("");
        } else {
            Set<String> uniqueSeasons = new TreeSet<>(Comparator.reverseOrder());

            ObservationModel[] observations = database.getAllObservations();
            for (ObservationModel observation : observations) {
                String timeStamp = observation.getObservation_time_stamp();
                if (toggleVariable == ToggleVariable.YEAR)
                    uniqueSeasons.add(timeStamp.substring(0, 4));
                else
                    uniqueSeasons.add(timeStamp.substring(0, 7));
            }

            seasons = new ArrayList<>(uniqueSeasons);
        }
        rvStatisticsCard.setAdapter(new StatisticsAdapter(this, seasons, toggleVariable));

        // Dismiss the dialog after the recycler view loads all its children
        rvStatisticsCard.post(this::dismissLoadingDialog);
    }

    private void setupStatisticsInsets(Toolbar toolbar) {
        View rootView = findViewById(android.R.id.content);
        InsetHandler.INSTANCE.setupStandardInsets(rootView, toolbar);
    }
}