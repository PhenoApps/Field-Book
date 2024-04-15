package com.fieldbook.tracker.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.archit.calendardaterangepicker.customviews.CalendarListener;
import com.archit.calendardaterangepicker.customviews.DateRangeCalendarView;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.StatisticsActivity;

import java.time.LocalDate;
import java.util.Calendar;

public class DateRangePickerDialog extends DialogFragment {
    StatisticsActivity originActivity;
    Calendar dateSelectorStartRange;
    Calendar dateSelectorEndRange;
    Calendar heatmapStartRange;
    Calendar heatmapEndRange;
    onDateRangeSelectedListener listener;

    public DateRangePickerDialog(StatisticsActivity statisticsActivity, Calendar dateSelectorStartRange, Calendar dateSelectorEndRange, onDateRangeSelectedListener listener) {
        this.originActivity = statisticsActivity;
        this.dateSelectorStartRange = dateSelectorStartRange;
        this.dateSelectorEndRange = dateSelectorEndRange;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(originActivity, R.style.AppAlertDialog);

        View customView = getLayoutInflater().inflate(R.layout.dialog_calendar_range_picker, null);
        builder.setTitle(getString(R.string.stats_date_range_picker_title)).setCancelable(true).setView(customView);

        DateRangeCalendarView calendar = customView.findViewById(R.id.calendarRange);

        calendar.setCalendarListener(new CalendarListener() {
            @Override
            public void onFirstDateSelected(@NonNull Calendar calendar) {
            }

            @Override
            public void onDateRangeSelected(@NonNull Calendar start, @NonNull Calendar end) {
                heatmapStartRange = start;
                heatmapEndRange = end;
            }
        });

        calendar.setSelectableDateRange(dateSelectorStartRange, dateSelectorEndRange);

        builder.setPositiveButton(R.string.dialog_ok, (dialogInterface1, id1) -> {
            LocalDate start = LocalDate.of(heatmapStartRange.get(Calendar.YEAR), heatmapStartRange.get(Calendar.MONTH) + 1, heatmapStartRange.get(Calendar.DAY_OF_MONTH));
            LocalDate end = LocalDate.of(heatmapEndRange.get(Calendar.YEAR), heatmapEndRange.get(Calendar.MONTH) + 1, heatmapEndRange.get(Calendar.DAY_OF_MONTH));
            listener.onDateRangeSelected(start, end);
        });

        final AlertDialog dialog = builder.create();
        return dialog;
    }

    public interface onDateRangeSelectedListener {
        /**
         * Sets the heatmap date range
         */
        void onDateRangeSelected(LocalDate start, LocalDate end);

    }

}
