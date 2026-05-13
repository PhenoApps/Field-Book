package com.fieldbook.tracker.application;

import androidx.multidex.MultiDexApplication;

import com.fieldbook.tracker.BuildConfig;
import com.fieldbook.tracker.activities.brapi.io.sync.SyncNotifications;
import com.fieldbook.tracker.activities.brapi.io.sync.SyncScheduler;

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
        SyncNotifications.INSTANCE.createChannel(this);
        SyncScheduler.INSTANCE.schedule(this);
    }
}
