package com.fieldbook.tracker.utilities;

import android.Manifest;
import android.app.Application;
import android.os.Environment;

import java.io.File;

public class Constants extends Application {
    public static final String TAG = "Field Book";

    public static final String MPATH = Environment.getExternalStorageDirectory().toString() + "/fieldBook";

    public static final String RESOURCEPATH = "/resources";

    public static final String PLOTDATAPATH = "/plot_data";

    public static final String TRAITPATH = "/trait";

    public static final String FIELDIMPORTPATH = "/field_import";

    public static final String FIELDEXPORTPATH = "/field_export";

    public static final String GEONAV_LOG_PATH = "/geonav";

    public static final String BACKUPPATH = "/database";

    public static final String UPDATEPATH = "/updates";

    public static final String ARCHIVEPATH = "/archive";

    public final static String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO, Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.CAMERA};

    public final static int PERM_REQ = 100;

    public static final String BRAPI_PATH_V1 = "/brapi/v1";
    public static final String BRAPI_PATH_V2 = "/brapi/v2";

}