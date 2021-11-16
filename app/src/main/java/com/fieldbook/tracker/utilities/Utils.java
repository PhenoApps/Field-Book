package com.fieldbook.tracker.utilities;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.ConfigActivity;
import com.getkeepsafe.taptargetview.TapTarget;

import java.io.File;
import java.io.IOException;

import static com.fieldbook.tracker.activities.ConfigActivity.dt;

public class Utils extends Application {

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

    public static boolean isConnected(Context context) {

        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;
    }

    public static void makeToast(Context context, String message) {
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.TOP,0,0);
        toast.show();
    }

    public static void createDir(Context context, String path) {

        if (path != null) {
            File dir = new File(path);
            File blankFile = new File(path + "/.fieldbook");

            if (!dir.exists()) {
                dir.mkdirs();

                try {
                    blankFile.getParentFile().mkdirs();
                    blankFile.createNewFile();
                    Utils.scanFile(context, blankFile);
                } catch (IOException e) {
                    Log.d("CreateDir", e.toString());
                }
            }
        } else {

            makeToast(context, context.getString(R.string.error_making_directory));
        }

    }

    public static void createDirs(Context context, String basePath) {
        if(basePath==null) {
            Utils.createDir(context, Constants.MPATH);
            basePath = Constants.MPATH;
        }

        Utils.createDir(context, basePath + Constants.RESOURCEPATH);
        Utils.createDir(context, basePath + Constants.PLOTDATAPATH);
        Utils.createDir(context, basePath + Constants.TRAITPATH);
        Utils.createDir(context, basePath + Constants.FIELDIMPORTPATH);
        Utils.createDir(context, basePath + Constants.FIELDEXPORTPATH);
        Utils.createDir(context, basePath + Constants.BACKUPPATH);
        Utils.createDir(context, basePath + Constants.UPDATEPATH);
        Utils.createDir(context, basePath + Constants.ARCHIVEPATH);

        updateAssets(basePath);
        scanSampleFiles(context, basePath);
    }

    private static void scanSampleFiles(Context context, String basePath) {
        String[] fileList = {basePath + Constants.TRAITPATH + "/trait_sample.trt",
                basePath + Constants.FIELDIMPORTPATH + "/field_sample.csv",
                basePath + Constants.FIELDIMPORTPATH + "/field_sample2.csv",
                basePath + Constants.FIELDIMPORTPATH + "/field_sample3.csv",
                basePath + Constants.TRAITPATH + "/severity.txt"};

        for (String aFileList : fileList) {
            File temp = new File(aFileList);
            if (temp.exists()) {
                Utils.scanFile(context, temp);
            }
        }
    }

    private static void updateAssets(String basePath) {
        dt.copyFileOrDir(basePath, "field_import");
        dt.copyFileOrDir(basePath, "resources");
        dt.copyFileOrDir(basePath, "trait");
        dt.copyFileOrDir(basePath, "database");
    }
}