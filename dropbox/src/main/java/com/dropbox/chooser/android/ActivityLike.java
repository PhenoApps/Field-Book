package com.dropbox.chooser.android;

import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Used to compensate for the lack of a shared interface between Activity, Fragment, and android.support.v4.app.Fragment
 */
interface ActivityLike {
    void startActivity(Intent intent) throws ActivityNotFoundException;
    void startActivityForResult(Intent intent, int requestCode) throws ActivityNotFoundException;
    /** Might be null */
    ContentResolver getContentResolver();
    /** Might be null */
    PackageManager getPackageManager();
    /** Might be null */
    FragmentManager getFragmentManager();
    /** Might be null */
    android.support.v4.app.FragmentManager getSupportFragmentManager();
}
