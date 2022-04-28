package com.fieldbook.tracker.traits;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.dialogs.DatePickerFragment;
import com.fieldbook.tracker.preferences.GeneralKeys;

import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class DateTraitLayout extends BaseTraitLayout {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Button addDayBtn;
    Button minusDayBtn;
    ImageButton saveDayBtn;
    private ImageButton calendarVisibilityBtn;
    private TextView month;
    private TextView day;
    private String date;

    public DateTraitLayout(Context context) {
        super(context);
    }

    public DateTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DateTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setNaTraitsText() {
        month.setText("");
        day.setText("NA");
    }

    @Override
    public String type() {
        return "date";
    }

    @Override
    public void init() {
        date = getPrefs().getString(GeneralKeys.CALENDAR_LAST_SAVED_DATE, "2000-01-01");
        month = findViewById(R.id.mth);
        day = findViewById(R.id.day);
        addDayBtn = findViewById(R.id.addDateBtn);
        minusDayBtn = findViewById(R.id.minusDateBtn);
        saveDayBtn = findViewById(R.id.enterBtn);

        calendarVisibilityBtn = findViewById(R.id.trait_date_calendar_visibility_btn);

        /*
         * When the calendar view visibility button is pressed it starts the date picker dialog.
         */
        calendarVisibilityBtn.setOnClickListener((View) -> {
            DialogFragment newFragment = new DatePickerFragment(dateFormat, (y, m, d) -> {

                Calendar calendar = Calendar.getInstance();

                calendar.set(y, m, d);

                updateViewDate(calendar);

                //set blue if updated, display color if new
                if (getNewTraits().containsKey(getCurrentTrait().getTrait())) {
                    month.setTextColor(Color.BLUE);
                    day.setTextColor(Color.BLUE);
                } else {
                    //set month/day text and color
                    month.setTextColor(Color.parseColor(getDisplayColor()));
                    day.setTextColor(Color.parseColor(getDisplayColor()));
                }

                //save date to db
                updateTrait(getCurrentTrait().getTrait(), "date", dateFormat.format(calendar.getTime()));

                return true;
            });

            newFragment.show(((CollectActivity) getContext()).getSupportFragmentManager(),
                    "datePicker");
        });

        // Add day
        addDayBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Calendar calendar = Calendar.getInstance();

                //Parse date
                try {
                    calendar.setTime(dateFormat.parse(date));
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                // Add day
                calendar.add(Calendar.DATE, 1);

                updateViewDate(calendar);
            }
        });

        // Minus day
        minusDayBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Calendar calendar = Calendar.getInstance();

                //Parse date
                try {
                    calendar.setTime(dateFormat.parse(date));
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                //Subtract day, rewrite date
                calendar.add(Calendar.DATE, -1);

                updateViewDate(calendar);
            }
        });

        // Saving date data
        saveDayBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Calendar calendar = Calendar.getInstance();

                //Parse date
                try {
                    calendar.setTime(dateFormat.parse(date));
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                if (getPrefs().getBoolean(GeneralKeys.USE_DAY_OF_YEAR, false)) {
                    updateTrait(getCurrentTrait().getTrait(), "date", String.valueOf(calendar.get(Calendar.DAY_OF_YEAR)));
                } else {
                    updateTrait(getCurrentTrait().getTrait(), "date", dateFormat.format(calendar.getTime()));
                }

                // Change the text color accordingly
                month.setTextColor(Color.parseColor(getDisplayColor()));
                day.setTextColor(Color.parseColor(getDisplayColor()));
            }
        });
    }

    private void updateViewDate(Calendar calendar) {

        date = dateFormat.format(calendar.getTime());

        String dayOfMonth = Integer.toString(calendar.get(Calendar.DAY_OF_MONTH));
        String monthText = Integer.toString(calendar.get(Calendar.MONTH) + 1);
        String yearText = Integer.toString(calendar.get(Calendar.YEAR));

        getPrefs().edit()
                .putString(GeneralKeys.CALENDAR_LAST_SAVED_DATE, yearText + "-" + monthText + "-" + dayOfMonth)
                .apply();

        //Set text
        day.setText(dayOfMonth);
        month.setText(getMonthForInt(calendar.get(Calendar.MONTH)));

        // Change text color
        if (getNewTraits().containsKey(getCurrentTrait().getTrait())) {
            month.setTextColor(Color.BLUE);
            day.setTextColor(Color.BLUE);
        } else {
            month.setTextColor(Color.BLACK);
            day.setTextColor(Color.BLACK);
        }

    }

    @Override
    public void loadLayout() {
        getEtCurVal().setEnabled(false);
        getEtCurVal().setVisibility(View.GONE);

        final Calendar c = Calendar.getInstance();
        date = dateFormat.format(c.getTime());

        if (getNewTraits().containsKey(getCurrentTrait().getTrait()) && !getNewTraits().get(getCurrentTrait().getTrait()).toString().equals("NA")) {
            if (getNewTraits().get(getCurrentTrait().getTrait()).toString().length() < 4 && getNewTraits().get(getCurrentTrait().getTrait()).toString().length() > 0) {
                Calendar calendar = Calendar.getInstance();

                //convert day of year to yyyy-mm-dd string
                date = getNewTraits().get(getCurrentTrait().getTrait()).toString();
                calendar.set(Calendar.DAY_OF_YEAR, Integer.parseInt(date));
                date = dateFormat.format(calendar.getTime());

                //set month/day text and color
                month.setTextColor(Color.parseColor(getDisplayColor()));
                day.setTextColor(Color.parseColor(getDisplayColor()));

                month.setText(getMonthForInt(calendar.get(Calendar.MONTH)));
                day.setText(String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)));

            } else if (getNewTraits().get(getCurrentTrait().getTrait()).toString().contains(".")) {
                //convert from yyyy.mm.dd to yyyy-mm-dd
                String[] oldDate = getNewTraits().get(getCurrentTrait().getTrait()).toString().split("\\.");
                date = oldDate[0] + "-" + String.format("%02d", Integer.parseInt(oldDate[1])) + "-" + String.format("%02d", Integer.parseInt(oldDate[2]));

                //set month/day text and color
                month.setText(getMonthForInt(Integer.parseInt(oldDate[1]) - 1));
                day.setText(oldDate[2]);
                month.setTextColor(Color.parseColor(getDisplayColor()));
                day.setTextColor(Color.parseColor(getDisplayColor()));

            } else {
                Calendar calendar = Calendar.getInstance();

                //new format
                date = getNewTraits().get(getCurrentTrait().getTrait()).toString();

                //Parse date
                try {
                    calendar.setTime(dateFormat.parse(date));
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                //set month/day text and color
                month.setText(getMonthForInt(calendar.get(Calendar.MONTH)));
                day.setText(String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)));

                month.setTextColor(Color.parseColor(getDisplayColor()));
                day.setTextColor(Color.parseColor(getDisplayColor()));
            }
        } else if (getNewTraits().containsKey(getCurrentTrait().getTrait()) && getNewTraits().get(getCurrentTrait().getTrait()).toString().equals("NA")) {
            month.setText("");
            day.setText("NA");
        } else {
            month.setTextColor(Color.BLACK);
            day.setTextColor(Color.BLACK);
            month.setText(getMonthForInt(c.get(Calendar.MONTH)));
            day.setText(String.format("%02d", c.get(Calendar.DAY_OF_MONTH)));
        }
    }

    @Override
    public void deleteTraitListener() {
        removeTrait(getCurrentTrait().getTrait());

        final Calendar c = Calendar.getInstance();
        date = dateFormat.format(c.getTime());

        month.setTextColor(Color.BLACK);
        day.setTextColor(Color.BLACK);

        //This is used to persist moving between months
        month.setText(getMonthForInt(c.get(Calendar.MONTH)));
        day.setText(String.format("%02d", c.get(Calendar.DAY_OF_MONTH)));
    }

    /**
     * Get month name based on numeric value
     */
    String getMonthForInt(int m) {
        String month = "invalid";
        DateFormatSymbols dfs = new DateFormatSymbols();
        String[] months = dfs.getShortMonths();

        if (m >= 0 && m <= 11) {
            month = months[m];
        }

        return month;
    }
}