package com.fieldbook.tracker.dialogs;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.StatisticsActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.kizitonwose.calendar.core.CalendarDay;
import com.kizitonwose.calendar.core.DayPosition;
import com.kizitonwose.calendar.view.CalendarView;
import com.kizitonwose.calendar.view.MonthDayBinder;
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

import kotlin.Unit;

import static com.kizitonwose.calendar.core.ExtensionsKt.daysOfWeek;

public class StatisticsCalendarDialog extends DialogFragment {
    StatisticsActivity originActivity;
    CalendarView monthCalendarView;
    DataHelper database;

    public StatisticsCalendarDialog(StatisticsActivity context) {
        this.originActivity = context;
        this.database = originActivity.getDatabase();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(originActivity, R.style.AppAlertDialog);

        View customView = getLayoutInflater().inflate(R.layout.calendar_view, null);
        builder.setTitle(getString(R.string.stats_heatmap_title));
        builder.setView(customView);

        builder.setNegativeButton(R.string.dialog_close, (dialogInterface, i) -> dismiss());

        // Calculating the number of observations collected on each day
        ObservationModel[] observations = database.getAllObservations();
        Map<LocalDate, Integer> observationCount = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZZZZZ");

        for (ObservationModel observation : observations) {
            LocalDate date = LocalDate.parse(observation.getObservation_time_stamp(), formatter);
            observationCount.put(date, observationCount.getOrDefault(date, 0) + 1);
        }

        monthCalendarView = customView.findViewById(R.id.calendarView);
        monthCalendarView.setDayBinder(new MonthDayBinder<StatisticsCalendarDialog.DayViewContainer>() {
            @NonNull
            @Override
            public StatisticsCalendarDialog.DayViewContainer create(@NonNull View view) {
                return new DayViewContainer(view);
            }

            @Override
            public void bind(@NonNull StatisticsCalendarDialog.DayViewContainer container, CalendarDay day) {
                container.textView.setText(String.valueOf(day.getDate().getDayOfMonth()));
                if (day.getPosition() == DayPosition.MonthDate) {
                    container.textView.setTextColor(Color.BLACK);
                } else {
                    container.textView.setTextColor(Color.GRAY);
                }

                // Setting the background color for the date
                int count = observationCount.getOrDefault(day.getDate(), 0);
                if (count > 0)
                    container.circleBackground.setBackgroundTintList(ColorStateList.valueOf(getColorForObservations(count)));
//                container.circleBackground.setBackgroundColor(getColorForObservations(observationCount.getOrDefault(day.getDate(), 0)));
//                GradientDrawable circleBackground = new GradientDrawable();
//                circleBackground.setShape(GradientDrawable.OVAL);
//                circleBackground.setColor(getColorForObservations(observationCount.getOrDefault(day.getDate(), 0)));
//                container.textView.setBackground(circleBackground);
            }
        });

        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(48);  // TODO: set start and end month of calendar
        YearMonth endMonth = currentMonth.plusMonths(0);
        List<DayOfWeek> daysOfWeek = daysOfWeek();
        monthCalendarView.setMonthScrollListener(calendarMonth -> {
            // Setting the month and year header
            TextView monthTitle = customView.findViewById(R.id.month_title);
            monthTitle.setText(calendarMonth.getYearMonth().getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + calendarMonth.getYearMonth().getYear());
            return Unit.INSTANCE;
        });
        monthCalendarView.setup(startMonth, endMonth, daysOfWeek.get(0));
        monthCalendarView.scrollToMonth(currentMonth);

        // Displaying the days of the week titles
        ViewGroup titlesContainer = customView.findViewById(R.id.titlesContainer);
        for (int i = 0; i < titlesContainer.getChildCount(); i++) {
            View childView = titlesContainer.getChildAt(i);
            if (childView instanceof TextView) {
                TextView textView = (TextView) childView;
                String title = daysOfWeek.get(i).getDisplayName(TextStyle.SHORT, Locale.getDefault());
                textView.setText(title);
            }
        }

        return builder.create();
    }

    public class DayViewContainer extends ViewContainer {
        public final TextView textView;
        public final View circleBackground;

        public DayViewContainer(View view) {
            super(view);
            textView = view.findViewById(R.id.calendarDayText);
            circleBackground = view.findViewById(R.id.circleBackground);
        }
    }

    /**
     * Returns the color for the heatmap based on the number of observations
     *
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
