package com.fieldbook.tracker.analytics;

public class AnalyticsEvents {
    private AnalyticsEvents() {
    }

    /**
     * Track changes to the server BRAPI URL setting.
     */
    public static final String SET_BRAPI_SERVER = "SetBrapi";

    /**
     * Track displays of the tutorial screen being displayed.
     */
    public static final String SHOW_TUTORIAL = "ShowTutorial";

    /**
     * Track scoped storage migration attempts. The action should be the result of the attempt.
     */
    public static final String SCOPED_STORAGE_MIGRATION = "ScopedStorageMigration";
}