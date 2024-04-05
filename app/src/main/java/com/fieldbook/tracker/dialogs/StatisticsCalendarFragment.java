package com.fieldbook.tracker.dialogs;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.StatisticsActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.kizitonwose.calendar.core.CalendarDay;
import com.kizitonwose.calendar.core.CalendarMonth;
import com.kizitonwose.calendar.core.DayPosition;
import com.kizitonwose.calendar.view.CalendarView;
import com.kizitonwose.calendar.view.MonthDayBinder;
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder;
import com.kizitonwose.calendar.view.ViewContainer;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.kizitonwose.calendar.core.ExtensionsKt.daysOfWeek;

public class StatisticsCalendarFragment extends Fragment {
    DataHelper database;
    StatisticsActivity originActivity;
    Toolbar toolbar;
    CalendarView monthCalendarView;
    LocalDate firstDay;
    LocalDate lastDay;

    public StatisticsCalendarFragment(StatisticsActivity statisticsActivity) {
        this.originActivity = statisticsActivity;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        database = originActivity.getDatabase();
        View customView = inflater.inflate(R.layout.calendar_view, container, false);

        toolbar = customView.findViewById(R.id.toolbar);
        setupToolbar();

        Map<LocalDate, Integer> observationCount = getObservationCount();

        monthCalendarView = customView.findViewById(R.id.calendarView);
        monthCalendarView.setDayBinder(new MonthDayBinder<DayViewContainer>() {
            @NonNull
            @Override
            public DayViewContainer create(@NonNull View view) {
                return new DayViewContainer(view);
            }

            @Override
            public void bind(@NonNull DayViewContainer container, CalendarDay day) {
                container.calendarDayText.setText(String.valueOf(day.getDate().getDayOfMonth()));

                // Reset the text and background color
                container.calendarDayText.setTextColor(Color.TRANSPARENT);
                container.circleBackground.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));

                if (day.getPosition() == DayPosition.MonthDate) {
                    // Setting the background color for the date
                    container.calendarDayText.setTextColor(Color.BLACK);
                    int count = observationCount.getOrDefault(day.getDate(), 0);
                    if (count > 0) container.circleBackground.setBackgroundTintList(ColorStateList.valueOf(getColorForObservations(count)));
                }
            }
        });

        monthCalendarView.setMonthHeaderBinder(new MonthHeaderFooterBinder<MonthViewContainer>() {
            @NonNull
            @Override
            public MonthViewContainer create(@NonNull View view) {
                return new MonthViewContainer(view);
            }

            @Override
            public void bind(@NonNull MonthViewContainer container, @NonNull CalendarMonth month) {
                container.calendarMonthTitle.setText(month.getYearMonth().format(DateTimeFormatter.ofPattern("MMMM yyyy")));
            }
        });

        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(48);  // TODO: set start and end month of calendar
        YearMonth endMonth = currentMonth.plusMonths(0);
        List<DayOfWeek> daysOfWeek = daysOfWeek();

        monthCalendarView.setup(startMonth, endMonth, daysOfWeek.get(0));
        monthCalendarView.scrollToMonth(currentMonth);

        // Displaying the days of the week titles
        ViewGroup titlesContainer = customView.findViewById(R.id.calendarDayTitlesContainer);
        for (int i = 0; i < titlesContainer.getChildCount(); i++) {
            View childView = titlesContainer.getChildAt(i);
            if (childView instanceof TextView) {
                TextView textView = (TextView) childView;
                String title = daysOfWeek.get(i).getDisplayName(TextStyle.SHORT, Locale.getDefault());
                textView.setText(title);
            }
        }

        return customView;
    }

    private void setupToolbar() {
        originActivity.setSupportActionBar(toolbar);

        if (originActivity.getSupportActionBar() != null) {
            originActivity.getSupportActionBar().setTitle(getString(R.string.stats_heatmap_title));
            originActivity.getSupportActionBar().getThemedContext();
            originActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            originActivity.getSupportActionBar().setHomeButtonEnabled(true);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_statistics_calendar_heatmap, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        final int firstDay = R.id.stats_first_day;
        final int lastDay = R.id.stats_last_day;

        int itemId = item.getItemId();

        if (itemId == firstDay) {
            monthCalendarView.scrollToDate(this.firstDay);
        } else if (itemId == lastDay) {
            monthCalendarView.scrollToDate(this.lastDay);
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Calculates the number of observations collected on each day
     */
    private Map<LocalDate, Integer> getObservationCount() {
        ObservationModel[] observations = database.getAllObservations();

        Map<LocalDate, Integer> observationCount = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZZZZZ");

        if (observations.length > 0) {
            firstDay = LocalDate.parse(observations[0].getObservation_time_stamp(), formatter);
            lastDay = LocalDate.parse(observations[observations.length - 1].getObservation_time_stamp(), formatter);
        }

        for (ObservationModel observation : observations) {
            LocalDate date = LocalDate.parse(observation.getObservation_time_stamp(), formatter);
            observationCount.put(date, observationCount.getOrDefault(date, 0) + 1);
        }
        return observationCount;
    }

    public class DayViewContainer extends ViewContainer {
        public final TextView calendarDayText;
        public final View circleBackground;

        public DayViewContainer(View view) {
            super(view);
            calendarDayText = view.findViewById(R.id.calendarDayText);
            circleBackground = view.findViewById(R.id.circleBackground);
        }
    }

    public class MonthViewContainer extends ViewContainer {
        TextView calendarMonthTitle;

        MonthViewContainer(@NonNull View view) {
            super(view);
            calendarMonthTitle = view.findViewById(R.id.calendarMonthTitle);
        }
    }

    /**
     * Returns the color for the heatmap based on the number of observations
     * TODO: Change the logic of assigning colors
     * @return ID of the color
     */
    private int getColorForObservations(int observations) {
        if (observations == 1) {
            return 0xFFb2f2bb;
        } else if (observations < 5) {
            return 0xFF69db7c;
        } else if (observations < 8) {
            return 0xFF40c057;
        } else {
            return 0xFF2f9e44;
        }
    }
}
