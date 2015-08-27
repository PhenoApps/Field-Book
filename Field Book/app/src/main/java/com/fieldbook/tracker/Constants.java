package com.fieldbook.tracker;

import android.os.Environment;

import java.io.File;

public class Constants {
    public static final String RESOURCEPATH = Environment.getExternalStorageDirectory()
            + "/FieldBook/resources";

    public static String PLOTDATAPATH = Environment.getExternalStorageDirectory()
            + "/FieldBook/plot_data";

    public static String TRAITPATH = Environment.getExternalStorageDirectory()
            + "/FieldBook/trait";

    public static String FIELDIMPORTPATH = Environment.getExternalStorageDirectory()
            + "/FieldBook/field_import";

    public static String FIELDEXPORTPATH = Environment.getExternalStorageDirectory()
            + "/FieldBook/field_export";

    public static final File MPATH = new File(Environment.getExternalStorageDirectory()
            + "/FieldBook");

    public static String BACKUPPATH = Environment.getExternalStorageDirectory()
            + "/FieldBook/database";

    public static String ERRORPATH = Environment.getExternalStorageDirectory()
            + "/FieldBook/errors";

    public static String UPDATEPATH = Environment.getExternalStorageDirectory()
            + "/FieldBook/updates";

}
