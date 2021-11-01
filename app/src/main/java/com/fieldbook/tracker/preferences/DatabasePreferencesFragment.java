package com.fieldbook.tracker.preferences;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.activities.ConfigActivity;
import com.fieldbook.tracker.activities.FileExploreActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.utilities.DialogUtils;
import com.fieldbook.tracker.utilities.Utils;
import com.fieldbook.tracker.utilities.ZipUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static android.app.Activity.RESULT_OK;
import static com.fieldbook.tracker.activities.ConfigActivity.dt;

public class DatabasePreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    PreferenceManager prefMgr;
    Context context;
    private Preference databaseImport;
    private Preference databaseExport;
    private Preference databaseDelete;
    private AlertDialog dbSaveDialog;
    private EditText exportFile;
    private String mChosenFile = "";
    private String exportFileString = "";
    public static Handler mHandler = new Handler();
    private final int PERMISSIONS_REQUEST_DATABASE_IMPORT = 9980;
    private final int PERMISSIONS_REQUEST_DATABASE_EXPORT = 9970;
    private SharedPreferences ep;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");
        ep = getContext().getSharedPreferences("Settings", 0);

        setPreferencesFromResource(R.xml.preferences_database, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.database_dialog_title));

        databaseImport = findPreference("pref_database_import");
        databaseExport = findPreference("pref_database_export");
        databaseDelete = findPreference("pref_database_delete");

        databaseImport.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                importDatabaseFilePermission();
                return true;
            }
        });

        databaseExport.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                exportDatabaseFilePermission();
                return true;
            }
        });

        databaseDelete.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                showDatabaseResetDialog1();
                return true;
            }
        });
    }

    private void showDatabaseImportDialog() {
        Intent intent = new Intent();

        intent.setClassName(getActivity(),
                FileExploreActivity.class.getName());
        intent.putExtra("path", ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.BACKUPPATH);
        intent.putExtra("include", new String[]{"db", "zip"});
        intent.putExtra("title", getString(R.string.database_import));
        startActivityForResult(intent, 2);
    }


    public Runnable importDB = new Runnable() {
        public void run() {
            new ImportDBTask().execute(0);
        }
    };

    public class ImportDBTask extends AsyncTask<Integer, Integer, Integer> {
        boolean fail;
        ProgressDialog dialog;
        //todo add a success toast/dialog/something

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            fail = false;

            dialog = new ProgressDialog(getContext());
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html
                    .fromHtml(getString(R.string.import_dialog_importing)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {

            //first check if the file to import is just a .db file

            if (mChosenFile.endsWith(".db")) { //if it is import it old-style

                try {

                    dt.importDatabase(mChosenFile);

                } catch (Exception e) {

                    Log.d("Database", e.toString());

                    e.printStackTrace();

                    fail = true;
                }
            } else if (mChosenFile.endsWith(".zip")) { //otherwise unzip and import prefs as well

                String internalDbPath = DataHelper.getDatabasePath(context);

                File zipInputFile = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH)
                        + Constants.BACKUPPATH + "/" + mChosenFile);

                try {

                    ZipUtil.Companion.unzip(context,
                            context.getContentResolver().openInputStream(Uri.fromFile(zipInputFile)),
                            new FileOutputStream(internalDbPath));

                } catch (IOException io) {

                    io.printStackTrace();

                }

            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (dialog.isShowing())
                dialog.dismiss();

            if (fail) {
                Utils.makeToast(getContext(), getString(R.string.import_error_general));
            }

            CollectActivity.reloadData = true;
        }
    }

    private void showDatabaseExportDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_save_database, null);

        exportFile = layout.findViewById(R.id.fileName);
        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", Locale.getDefault());
        String autoFillName = timeStamp.format(Calendar.getInstance().getTime()) + "_" + "systemdb" + DataHelper.DATABASE_VERSION;
        exportFile.setText(autoFillName);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AppAlertDialog);
        builder.setTitle(R.string.database_dialog_title)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_save), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dbSaveDialog.dismiss();
                exportFileString = exportFile.getText().toString();
                mHandler.post(exportDB);
            }
        });

        builder.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        dbSaveDialog = builder.create();
        dbSaveDialog.show();
        DialogUtils.styleDialogs(dbSaveDialog);

        android.view.WindowManager.LayoutParams params = dbSaveDialog.getWindow().getAttributes();
        params.width = LinearLayout.LayoutParams.MATCH_PARENT;
        dbSaveDialog.getWindow().setAttributes(params);
    }

    private Runnable exportDB = new Runnable() {
        public void run() {
            new ExportDBTask().execute(0);
        }
    };

    private class ExportDBTask extends AsyncTask<Integer, Integer, Integer> {
        boolean fail;
        ProgressDialog dialog;
        String error;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            fail = false;

            dialog = new ProgressDialog(getContext());
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html
                    .fromHtml(getString(R.string.export_progress)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {

            //get database path and shared preferences path
            String dbPath = DataHelper.getDatabasePath(context);

            //create the file the contents will be zipped to
            File zipFile = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.BACKUPPATH + "/" + exportFileString + ".zip");

            //zip files into stream
            try {

                File tempOutput = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.BACKUPPATH + "/" + UUID.randomUUID());

                FileOutputStream fileStream = new FileOutputStream(tempOutput);

                ObjectOutputStream objectStream = new ObjectOutputStream(fileStream);

                OutputStream zipOutput = context.getContentResolver().openOutputStream(Uri.fromFile(zipFile));

                SharedPreferences prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);

                objectStream.writeObject(prefs.getAll());

                ZipUtil.Companion.zip(new String[] {dbPath, tempOutput.getPath()}, zipOutput);

                objectStream.close();

                fileStream.close();

                if (!tempOutput.delete()) {

                    throw new IOException();
                }

            } catch (IOException e) {

                e.printStackTrace();

            }

            //use media scanner on the output
            Utils.scanFile(getContext(), zipFile);

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }

            if (fail) {
                Utils.makeToast(getContext(), getString(R.string.export_error_general));
            } else {
                Utils.makeToast(getContext(), getString(R.string.export_complete));
            }
        }
    }


    // First confirmation
    private void showDatabaseResetDialog1() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.dialog_warning));
        builder.setMessage(getString(R.string.database_reset_warning1));

        builder.setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                showDatabaseResetDialog2();
            }
        });

        builder.setNegativeButton(getString(R.string.dialog_no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
        DialogUtils.styleDialogs(alert);
    }

    // Second confirmation
    private void showDatabaseResetDialog2() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.dialog_warning));
        builder.setMessage(getString(R.string.database_reset_warning2));

        builder.setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                // Delete database
                dt.deleteDatabase();

                // Clear all existing settings
                SharedPreferences.Editor ed = ep.edit();
                ed.clear();
                ed.apply();

                dialog.dismiss();
                Utils.makeToast(getContext(), getString(R.string.database_reset_message));

                try {
                    getActivity().finishAffinity();
                } catch (Exception e) {
                    Log.e("Field Book", "" + e.getMessage());
                }
            }

        });

        builder.setNegativeButton(getString(R.string.dialog_no), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        });

        AlertDialog alert = builder.create();
        alert.show();
        DialogUtils.styleDialogs(alert);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        DatabasePreferencesFragment.this.context = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2) {
            if (resultCode == RESULT_OK) {
                mChosenFile = data.getStringExtra("result");
                mChosenFile = mChosenFile.substring(mChosenFile.lastIndexOf("/") + 1, mChosenFile.length());
                mHandler.post(importDB);
            }
        }
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_DATABASE_EXPORT)
    public void exportDatabaseFilePermission() {
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(getContext(), perms)) {
            showDatabaseExportDialog();
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_export),
                    PERMISSIONS_REQUEST_DATABASE_EXPORT, perms);
        }
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_DATABASE_IMPORT)
    public void importDatabaseFilePermission() {
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(getContext(), perms)) {
            showDatabaseImportDialog();
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_import),
                    PERMISSIONS_REQUEST_DATABASE_IMPORT, perms);
        }
    }
}