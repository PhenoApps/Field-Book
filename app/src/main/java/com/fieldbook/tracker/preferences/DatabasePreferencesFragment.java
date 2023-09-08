package com.fieldbook.tracker.preferences;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.activities.FileExploreActivity;
import com.fieldbook.tracker.activities.PreferencesActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.utilities.DialogUtils;
import com.fieldbook.tracker.utilities.Utils;
import com.fieldbook.tracker.utilities.ZipUtil;

import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

@AndroidEntryPoint
public class DatabasePreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    private static int REQUEST_FILE_EXPLORE_CODE = 2;

    PreferenceManager prefMgr;
    Context context;
    private AlertDialog dbSaveDialog;
    private EditText exportFile;
    private String exportFileString = "";
    public static Handler mHandler = new Handler();
    private final int PERMISSIONS_REQUEST_DATABASE_IMPORT = 9980;
    private final int PERMISSIONS_REQUEST_DATABASE_EXPORT = 9970;
    private SharedPreferences ep;

    @Inject
    DataHelper database;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(GeneralKeys.SHARED_PREF_FILE_NAME);
        ep = getContext().getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0);

        setPreferencesFromResource(R.xml.preferences_database, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.database_dialog_title));

        Preference databaseImport = findPreference("pref_database_import");
        Preference databaseExport = findPreference("pref_database_export");
        Preference databaseDelete = findPreference("pref_database_delete");

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

    //TODO make static function that creates intent in FileExplorer to check that uri is valid and sent to result key
    private void showDatabaseImportDialog() {
        DocumentFile databaseDir = BaseDocumentTreeUtil.Companion.getDirectory(context, R.string.dir_database);
        if (databaseDir != null && databaseDir.exists()) {
            Intent intent = new Intent();
            intent.setClassName(getActivity(), FileExploreActivity.class.getName());
            intent.putExtra("path", databaseDir.getUri().toString());
            intent.putExtra("include", new String[]{"db", "zip"});
            intent.putExtra("title", getString(R.string.database_import));
            startActivityForResult(intent, REQUEST_FILE_EXPLORE_CODE);
        }
    }

    private void invokeImportDatabase(DocumentFile docFile) {
        mHandler.post(() -> {
           new ImportDBTask(docFile).execute(0);
        });
    }

    public class ImportDBTask extends AsyncTask<Integer, Integer, Integer> {
        boolean fail;
        ProgressDialog dialog;
        DocumentFile file = null;
        //todo add a success toast/dialog/something

        public ImportDBTask(DocumentFile file) {
            this.file = file;
        }

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

            if (file != null && file.getName() != null) {

                database.close();

                //first check if the file to import is just a .db file
                if (file.getName().endsWith(".db")) { //if it is import it old-style

                    try {

                        database.importDatabase(file);

                    } catch (Exception e) {

                        Log.d("Database", e.toString());

                        e.printStackTrace();

                        fail = true;
                    }
                } else if (file.getName().endsWith(".zip")) { //otherwise unzip and import prefs as well

                    String internalDbPath = DataHelper.getDatabasePath(context);

                    try (InputStream input = context.getContentResolver().openInputStream(file.getUri())) {

                        try (OutputStream output = new FileOutputStream(internalDbPath)) {

                            ZipUtil.Companion.unzip(context, input, output);

                            SharedPreferences.Editor edit = ep.edit();

                            edit.putInt(GeneralKeys.SELECTED_FIELD_ID, -1);
                            edit.putString(GeneralKeys.UNIQUE_NAME, "");
                            edit.putString(GeneralKeys.PRIMARY_NAME, "");
                            edit.putString(GeneralKeys.SECONDARY_NAME, "");
                            edit.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false);
                            edit.apply();

                            database.open();

                        } catch (Exception e) {

                            e.printStackTrace();

                            throw new Exception();
                        }

                    } catch (Exception e) {

                        e.printStackTrace();

                    }
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

            DocumentFile databaseDir = BaseDocumentTreeUtil.Companion.getDirectory(context, R.string.dir_database);

            if (databaseDir != null && databaseDir.exists()) {

                //create the file the contents will be zipped to
                DocumentFile zipFile = databaseDir.createFile("*/*", exportFileString + ".zip");

                if (zipFile != null && zipFile.exists()) {

                    //zip files into stream
                    try {

                        String tempName = UUID.randomUUID().toString();
                        DocumentFile tempOutput = databaseDir.createFile("*/*", tempName);
                        OutputStream tempStream = BaseDocumentTreeUtil.Companion.getFileOutputStream(context, R.string.dir_database, tempName);

                        ObjectOutputStream objectStream = new ObjectOutputStream(tempStream);

                        OutputStream zipOutput = context.getContentResolver().openOutputStream(zipFile.getUri());

                        SharedPreferences prefs = context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE);

                        objectStream.writeObject(prefs.getAll());

                        objectStream.close();

                        if (tempStream != null) {
                            tempStream.close();
                        }

                        ZipUtil.Companion.zip(context,
                                new DocumentFile[] { DocumentFile.fromFile(new File(dbPath)), tempOutput },
                                zipOutput);

                        if (tempOutput != null && !tempOutput.delete()) {

                            throw new IOException();
                        }

                    } catch (IOException e) {

                        e.printStackTrace();

                    }
                }
            }

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
                database.deleteDatabase();

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
        if (requestCode == REQUEST_FILE_EXPLORE_CODE) {
            if (resultCode == RESULT_OK) {
                if (getContext() != null) {

                    DocumentFile dbDir = BaseDocumentTreeUtil.Companion.getDirectory(context, R.string.dir_database);
                    DocumentFile dbInput = DocumentFile.fromSingleUri(getContext(),
                            Uri.parse(data.getStringExtra(FileExploreActivity.EXTRA_RESULT_KEY)));

                    if (dbDir != null && dbInput != null && dbInput.getName() != null) {

                        DocumentFile shared = dbDir.findFile(dbInput.getName());

                        if (shared != null) {

                            invokeImportDatabase(shared);

                        }
                    }
                }
            }
        }
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_DATABASE_EXPORT)
    public void exportDatabaseFilePermission() {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
            if (EasyPermissions.hasPermissions(getContext(), perms)) {
                showDatabaseExportDialog();
            } else {
                // Do not have permissions, request them now
                EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_export),
                        PERMISSIONS_REQUEST_DATABASE_EXPORT, perms);
            }
        } else showDatabaseExportDialog();
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_DATABASE_IMPORT)
    public void importDatabaseFilePermission() {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
            if (getContext() != null) {
                if (EasyPermissions.hasPermissions(getContext(), perms)) {
                    showDatabaseImportDialog();
                } else {
                    // Do not have permissions, request them now
                    EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_import),
                            PERMISSIONS_REQUEST_DATABASE_IMPORT, perms);
                }
            }
        } else showDatabaseImportDialog();
    }
}