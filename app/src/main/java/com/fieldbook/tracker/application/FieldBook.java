package com.fieldbook.tracker.application;

import androidx.multidex.MultiDexApplication;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.BuildConfig;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.LogcatRecorder;

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

        if (PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(GeneralKeys.LOGCAT_CAPTURE_ENABLED, false)) {
            LogcatRecorder.INSTANCE.start(this);
        }
    }
}
