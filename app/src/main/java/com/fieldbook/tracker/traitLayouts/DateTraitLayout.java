package com.fieldbook.tracker.traitLayouts;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.fieldbook.tracker.traits.TraitObject;

import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

public class DateTraitLayout extends TraitLayout {

    public DateTraitLayout(Context context) {
        super(context);
        throw new RuntimeException("Stub!");
    }

    public void loadLayout(EditText etCurVal, DataWrapper dataWrapper, HashMap newTraits,
                           TraitObject currentTrait, String displayColor, TextWatcher cvNum,
                           TextWatcher cvText, SeekBar seekBar, SeekBar.OnSeekBarChangeListener seekListener, Handler mHandler){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String date = dataWrapper.getDate();
        TextView day = dataWrapper.getDay();
        TextView month = dataWrapper.getMonth();

        etCurVal.setEnabled(false);
        etCurVal.setVisibility(View.GONE);

        final Calendar c = Calendar.getInstance();
        date = dateFormat.format(c.getTime());

        if (newTraits.containsKey(currentTrait.trait) && !newTraits.get(currentTrait.trait).toString().equals("NA")) {
            if(newTraits.get(currentTrait.trait).toString().length() < 4 && newTraits.get(currentTrait.trait).toString().length() > 0) {
                Calendar calendar = Calendar.getInstance();

                //convert day of year to yyyy-mm-dd string
                date = newTraits.get(currentTrait.trait).toString();
                calendar.set(Calendar.DAY_OF_YEAR, Integer.parseInt(date));
                date = dateFormat.format(calendar.getTime());

                //set month/day text and color
                month.setTextColor(Color.parseColor(displayColor));
                day.setTextColor(Color.parseColor(displayColor));

                month.setText(getMonthForInt(calendar.get(Calendar.MONTH)));
                day.setText(String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)));

            } else if (newTraits.get(currentTrait.trait).toString().contains(".")) {
                //convert from yyyy.mm.dd to yyyy-mm-dd
                String[] oldDate = newTraits.get(currentTrait.trait).toString().split("\\.");
                date = oldDate[0] + "-" + String.format("%02d", Integer.parseInt(oldDate[1])) + "-" + String.format("%02d", Integer.parseInt(oldDate[2]));

                //set month/day text and color
                month.setText(getMonthForInt(Integer.parseInt(oldDate[1])-1));
                day.setText(oldDate[2]);
                month.setTextColor(Color.parseColor(displayColor));
                day.setTextColor(Color.parseColor(displayColor));

            } else {
                Calendar calendar = Calendar.getInstance();

                //new format
                date = newTraits.get(currentTrait.trait).toString();

                //Parse date
                try {
                    calendar.setTime(dateFormat.parse(date));
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                //set month/day text and color
                month.setText(getMonthForInt(calendar.get(Calendar.MONTH)));
                day.setText(String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)));

                month.setTextColor(Color.parseColor(displayColor));
                day.setTextColor(Color.parseColor(displayColor));
            }
        } else if(newTraits.containsKey(currentTrait.trait) && newTraits.get(currentTrait.trait).toString().equals("NA")) {
            month.setText("");
            day.setText("NA");
        } else {
            month.setTextColor(Color.BLACK);
            day.setTextColor(Color.BLACK);
            month.setText(getMonthForInt(c.get(Calendar.MONTH)));
            day.setText(String.format("%02d", c.get(Calendar.DAY_OF_MONTH)));
        }
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
