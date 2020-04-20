package com.fieldbook.tracker.utilities;

import android.Manifest;
import android.os.Environment;

import java.io.File;

public class Constants {
    public static final String TAG = "Field Book";

    public static final String RESOURCEPATH = Environment.getExternalStorageDirectory().toString()
            + "/fieldBook/resources";

    public static final String PLOTDATAPATH = Environment.getExternalStorageDirectory().toString()
            + "/fieldBook/plot_data";

    public static final String TRAITPATH = Environment.getExternalStorageDirectory().toString()
            + "/fieldBook/trait";

    public static final String FIELDIMPORTPATH = Environment.getExternalStorageDirectory().toString()
            + "/fieldBook/field_import";

    public static final String FIELDEXPORTPATH = Environment.getExternalStorageDirectory().toString()
            + "/fieldBook/field_export";

    public static final File MPATH = new File(Environment.getExternalStorageDirectory().toString()
            + "/fieldBook");

    public static final String BACKUPPATH = Environment.getExternalStorageDirectory().toString()
            + "/fieldBook/database";

    public static final String UPDATEPATH = Environment.getExternalStorageDirectory().toString()
            + "/fieldBook/updates";

    public static final String ARCHIVEPATH = Environment.getExternalStorageDirectory().toString()
            + "/fieldBook/archive";

    public final static String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO, Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.CAMERA};

    public final static int PERM_REQ = 100;

    public static final String BRAPI_PATH = "/brapi/v1";
}