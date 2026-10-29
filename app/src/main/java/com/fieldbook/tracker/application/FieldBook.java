package com.fieldbook.tracker.application;

import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.multidex.MultiDexApplication;
import androidx.work.Configuration;

import com.fieldbook.tracker.BuildConfig;
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiReconnectNotifier;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class FieldBook extends MultiDexApplication implements Configuration.Provider {

    @Inject
    HiltWorkerFactory workerFactory;

    @Inject
    BrapiReconnectNotifier brapiReconnectNotifier;

    public FieldBook() {
        if (BuildConfig.DEBUG) {
            //StrictMode.enableDefaults();
            //un-comment to enable strict warnings in logcat
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        brapiReconnectNotifier.start();
    }

    // WorkManager is initialized on demand with Hilt's factory so the BrAPI reconnect worker gets its
    // dependencies injected, the default startup initializer is removed in the manifest.
    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build();
    }
}
