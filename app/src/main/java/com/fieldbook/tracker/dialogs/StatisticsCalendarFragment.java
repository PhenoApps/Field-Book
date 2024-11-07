package com.fieldbook.tracker.dialogs;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
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
import androidx.core.content.ContextCompat;
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

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.kizitonwose.calendar.core.ExtensionsKt.daysOfWeek;

public class StatisticsCalendarFragment extends Fragment implements DateRangePickerDialog.onDateRangeSelectedListener {
    DataHelper database;
    StatisticsActivity originActivity;
    Toolbar toolbar;
    CalendarView monthCalendarView;
    int dateToggle;
    YearMonth firstMonth, lastMonth;
    LocalDate heatMapStartDate, heatMapEndDate;
    Calendar dateSelectorStartRange, dateSelectorEndRange;
    DateTimeFormatter timeStampFormat;
    private static final String TIME_STAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSSZZZZZ";
    private static final String MONTH_HEADER_PATTERN = "MMMM yyyy";
    private static final int THRESHOLD_LOW = 1;
    private static final int THRESHOLD_MEDIUM = 5;
    private static final int THRESHOLD_HIGH = 8;

    public StatisticsCalendarFragment(StatisticsActivity statisticsActivity) {
        this.originActivity = statisticsActivity;
        this.dateToggle = 0;
        this.dateSelectorStartRange = Calendar.getInstance();
        this.dateSelectorEndRange = Calendar.getInstance();
        this.timeStampFormat = DateTimeFormatter.ofPattern(TIME_STAMP_PATTERN);
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
                int count = observationCount.getOrDefault(day.getDate(), 0);

                // Displays the date or the number of observations collected on the date based on the toggle
                if (dateToggle == 0)
                    container.calendarDayText.setText(String.valueOf(day.getDate().getDayOfMonth()));
                else container.calendarDayText.setText(String.valueOf(count));

                // Reset the text and background color
                container.calendarDayText.setTextColor(Color.TRANSPARENT);
                container.circleBackground.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));

                if (day.getPosition() == DayPosition.MonthDate) {
                    // Setting the color if the date is out of the selected range
                    if (day.getDate().isBefore(heatMapStartDate) || day.getDate().isAfter(heatMapEndDate)) {
                        container.calendarDayText.setTextColor(Color.GRAY);
                    } else {
                        // Setting the text and background color for the date
                        container.calendarDayText.setTextColor(Color.BLACK);
                        if (count > 0)
                            container.circleBackground.setBackgroundTintList(ColorStateList.valueOf(getColorForObservations(count)));
                    }
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
                container.calendarMonthTitle.setText(month.getYearMonth().format(DateTimeFormatter.ofPattern(MONTH_HEADER_PATTERN)));
            }
        });

        YearMonth currentMonth = YearMonth.now();
        List<DayOfWeek> daysOfWeek = daysOfWeek();

        monthCalendarView.setup(firstMonth, currentMonth, daysOfWeek.get(0));
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
        final int calendarRange = R.id.stats_calendar_range;
        final int counter = R.id.stats_counter;

        int itemId = item.getItemId();

        if (itemId == firstDay) {
            monthCalendarView.scrollToMonth(this.firstMonth);
        } else if (itemId == lastDay) {
            monthCalendarView.scrollToMonth(this.lastMonth);
        } else if (itemId == calendarRange) {
            DateRangePickerDialog dialog = new DateRangePickerDialog(originActivity, dateSelectorStartRange, dateSelectorEndRange, this);
            dialog.show(originActivity.getSupportFragmentManager(), "StatisticsActivity");
        } else if (itemId == counter) {
            dateToggle = 1 - dateToggle;
            monthCalendarView.notifyCalendarChanged();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Calculates the number of observations collected on each day
     */
    private Map<LocalDate, Integer> getObservationCount() {
        ObservationModel[] observations = database.getAllObservations();

        Map<LocalDate, Integer> observationCount = new HashMap<>();

        // Sets the heatmap date range when the page loads for the first time
        heatMapStartDate = LocalDate.parse(observations[0].getObservation_time_stamp(), timeStampFormat);
        heatMapEndDate = LocalDate.now();

        setFirstAndLastDates();

        SimpleDateFormat sdf = new SimpleDateFormat(TIME_STAMP_PATTERN, Locale.getDefault());

        // Sets the limits for the date range selection calendar
        try {
            dateSelectorStartRange.setTime(sdf.parse(observations[0].getObservation_time_stamp()));
            dateSelectorEndRange.setTime(sdf.parse(observations[observations.length - 1].getObservation_time_stamp()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (ObservationModel observation : observations) {
            LocalDate date = LocalDate.parse(observation.getObservation_time_stamp(), timeStampFormat);
            observationCount.put(date, observationCount.getOrDefault(date, 0) + 1);
        }
        return observationCount;
    }

    @Override
    public void onDateRangeSelected(LocalDate start, LocalDate end) {

        heatMapStartDate = start;
        heatMapEndDate = end;
        setFirstAndLastDates();
        monthCalendarView.updateMonthData(YearMonth.from(heatMapStartDate), YearMonth.from(heatMapEndDate));
    }

    /**
     * Sets the first and last dates with an observation within the heatmap date range.
     */
    public void setFirstAndLastDates() {

        ObservationModel[] observations = database.getAllObservations();

        for (ObservationModel observation : observations) {
            LocalDate date = LocalDate.parse(observation.getObservation_time_stamp(), timeStampFormat);
            if (date.isEqual(heatMapStartDate) || (date.isAfter(heatMapStartDate) && date.isBefore(heatMapEndDate))) {
                firstMonth = YearMonth.from(date);
                break;
            }
        }

        for (int i = observations.length - 1; i >= 0; i--) {
            ObservationModel observation = observations[i];
            LocalDate date = LocalDate.parse(observation.getObservation_time_stamp(), timeStampFormat);
            if (date.isEqual(heatMapEndDate) || (date.isAfter(heatMapStartDate) && date.isBefore(heatMapEndDate))) {
                lastMonth = YearMonth.from(date);
                break;
            }
        }
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
     * TODO: Change the threshold values (and possibly colors)
     * @return ID of the color
     */
    private int getColorForObservations(int observations) {
        TypedValue colorValue = new TypedValue();
        if (observations == THRESHOLD_LOW) {
            getActivity().getTheme().resolveAttribute(R.attr.fb_heatmap_color_low, colorValue, true);
        } else if (observations < THRESHOLD_MEDIUM) {
            getActivity().getTheme().resolveAttribute(R.attr.fb_heatmap_color_medium, colorValue, true);
        } else if (observations < THRESHOLD_HIGH) {
            getActivity().getTheme().resolveAttribute(R.attr.fb_heatmap_color_high, colorValue, true);
        } else {
            getActivity().getTheme().resolveAttribute(R.attr.fb_heatmap_color_max, colorValue, true);
        }
        return colorValue.data;
    }
}
