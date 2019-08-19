package com.fieldbook.tracker.traitLayouts;

import android.widget.TextView;

public class DataWrapper {
    private String date;
    private TextView day;
    private TextView month;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public TextView getDay() {
        return day;
    }

    public void setDay(TextView day) {
        this.day = day;
    }

    public TextView getMonth() {
        return month;
    }

    public void setMonth(TextView month) {
        this.month = month;
    }
}
