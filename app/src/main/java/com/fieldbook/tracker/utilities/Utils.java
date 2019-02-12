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

    // truncate string
    public static String truncateDecimalString(String v, int digits) {
        int count = 0;

        boolean found = false;

        StringBuilder truncated = new StringBuilder();

        for (int i = 0; i < v.length(); i++) {
            if (found) {
                count += 1;

                if (count == digits)
                    break;
            }

            if (v.charAt(i) == '.') {
                found = true;
            }

            truncated.append(v.charAt(i));
        }

        return truncated.toString();
    }

    //TODO language


    //TODO sharefile

}
