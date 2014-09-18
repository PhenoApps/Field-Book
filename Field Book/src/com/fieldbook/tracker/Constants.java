package com.fieldbook.tracker;

import android.os.Environment;

import java.io.File;

/**
 * Created by trife on 9/9/2014.
 */
public class Constants {
    public static final String RESOURCEPATH = Environment.getExternalStorageDirectory()
            + "/fieldBook/resources";

    public static String PLOTDATAPATH = Environment.getExternalStorageDirectory()
            + "/fieldBook/plot_data";

    public static String TRAITPATH = Environment.getExternalStorageDirectory()
            + "/fieldbook/trait";

    public static String FIELDIMPORTPATH = Environment.getExternalStorageDirectory()
            + "/fieldbook/field_import";

    public static String FIELDEXPORTPATH = Environment.getExternalStorageDirectory()
            + "/fieldbook/field_export";

    public static final File MPATH = new File(Environment.getExternalStorageDirectory()
            + "/fieldBook");

    public static String BACKUPPATH = Environment.getExternalStorageDirectory()
            + "/fieldbook/database";

}
