package com.fieldbook.tracker.application;

import androidx.multidex.MultiDexApplication;

import com.fieldbook.tracker.BuildConfig;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class FieldBook extends MultiDexApplication {

    public FieldBook() {
        if (BuildConfig.DEBUG) {
            //StrictMode.enableDefaults();
            //un-comment to enable strict warnings in logcat
        }
    }
}
