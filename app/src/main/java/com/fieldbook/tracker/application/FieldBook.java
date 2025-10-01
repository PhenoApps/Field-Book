package com.fieldbook.tracker.application;

import androidx.multidex.MultiDexApplication;

import com.fieldbook.tracker.BuildConfig;
import com.fieldbook.shared.AndroidAppContextHolder;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class FieldBook extends MultiDexApplication {

    public FieldBook() {
        if (BuildConfig.DEBUG) {
            //StrictMode.enableDefaults();
            //un-comment to enable strict warnings in logcat
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidAppContextHolder.initialize(this);
    }
}
