package com.fieldbook.tracker.utilities;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.util.Log;

import java.io.File;

public class Utils {

    // scanFile
    public static void scanFile(Context context, File filePath) {
        MediaScannerConnection.scanFile(context, new String[]{filePath.getAbsolutePath()}, null, null);
    }

    // get current app version
    public static int getVersion(Context context) {
        int v = 0;
        try {
            v = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("Field Book", "" + e.getMessage());
        }
        return v;
    }

    //TODO language


    //TODO sharefile

}
