package com.fieldbook.tracker.application;

import androidx.multidex.MultiDexApplication;

import com.fieldbook.tracker.BuildConfig;

public class FieldBook extends MultiDexApplication {

    public FieldBook() {
        if (BuildConfig.DEBUG) {
            //StrictMode.enableDefaults();
            //un-comment to enable strict warnings in logcat
        }
    }
}
