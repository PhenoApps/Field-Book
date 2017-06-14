package com.fieldbook.tracker.utilities;

import android.content.Context;
import android.media.MediaScannerConnection;

import java.io.File;

public class Utils {

    // scanFile
    public static void scanFile(Context context, File filePath) {
        MediaScannerConnection.scanFile(context, new String[]{filePath.getAbsolutePath()}, null, null);
    }

    //TODO language


    //TODO sharefile

}
