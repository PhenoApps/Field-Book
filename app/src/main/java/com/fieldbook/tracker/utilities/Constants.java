package com.fieldbook.tracker.utilities;

import android.os.Environment;

import java.io.File;

public class Constants {
    public static final String RESOURCEPATH = Environment.getExternalStorageDirectory()
            + "/fieldBook/resources";

    public static String PLOTDATAPATH = Environment.getExternalStorageDirectory()
            + "/fieldBook/plot_data";

    public static String TRAITPATH = Environment.getExternalStorageDirectory()
            + "/fieldBook/trait";

    public static String TEMPLATEPATH = Environment.getExternalStorageDirectory()
            + "/fieldBook/templates";

    public static String FIELDIMPORTPATH = Environment.getExternalStorageDirectory()
            + "/fieldBook/field_import";

    public static String FIELDEXPORTPATH = Environment.getExternalStorageDirectory()
            + "/fieldBook/field_export";

    public static final File MPATH = new File(Environment.getExternalStorageDirectory()
            + "/fieldBook");

    public static String BACKUPPATH = Environment.getExternalStorageDirectory()
            + "/fieldBook/database";

    public static String UPDATEPATH = Environment.getExternalStorageDirectory()
            + "/fieldBook/updates";

    public static String ARCHIVEPATH = Environment.getExternalStorageDirectory()
            + "/fieldBook/archive";

}
