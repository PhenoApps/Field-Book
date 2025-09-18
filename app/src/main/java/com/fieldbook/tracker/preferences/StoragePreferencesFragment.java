package com.fieldbook.tracker.preferences;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.DefineStorageActivity;
import com.fieldbook.tracker.activities.FileExploreActivity;
import com.fieldbook.tracker.activities.PreferencesActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.fragments.ExportDatabaseFragment;
import com.fieldbook.tracker.fragments.ImportDatabaseFragment;
import com.fieldbook.tracker.utilities.FileUtil;
import com.fieldbook.tracker.utilities.Utils;
import com.fieldbook.tracker.utilities.ZipUtil;

import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.File;
import java.io.IOException;
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
public class StoragePreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    @Inject
    SharedPreferences preferences;

    @Inject
    DataHelper database;

    private static final int REQUEST_FILE_EXPLORE_CODE = 2;
    private static final int REQUEST_STORAGE_DEFINER_CODE = 999;

    private final int PERMISSIONS_REQUEST_DATABASE_IMPORT = 9980;
    private final int PERMISSIONS_REQUEST_DATABASE_EXPORT = 9970;

    Context context;
    private AlertDialog dbSaveDialog;
    private EditText exportFile;
    private String exportFileString = "";
    private Preference defaultStorageLocation;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        setPreferencesFromResource(R.xml.preferences_storage, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.preferences_storage_title));

        defaultStorageLocation = findPreference("DEFAULT_STORAGE_LOCATION_PREFERENCE");


        String storageSummary = preferences.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, null);
        defaultStorageLocation.setSummary(storageSummary);

        defaultStorageLocation.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(context, DefineStorageActivity.class);
            startActivityForResult(intent, REQUEST_STORAGE_DEFINER_CODE);
            return true;
        });

        Preference databaseImport = findPreference("pref_database_import");
        Preference databaseExport = findPreference("pref_database_export");
        Preference databaseDelete = findPreference("pref_database_delete");

        databaseImport.setOnPreferenceClickListener(preference -> {
            if (checkDirectory()) {
                importDatabaseFilePermission();
            }
            return true;
        });

        databaseExport.setOnPreferenceClickListener(preference -> {
            if (checkDirectory()) {
                exportDatabaseFilePermission();
            }
            return true;
        });

        databaseDelete.setOnPreferenceClickListener(preference -> {
            showDatabaseResetDialog1();
            return true;
        });
    }

    @NonNull
    private Boolean checkDirectory() {
        if (BaseDocumentTreeUtil.Companion.getRoot(context) != null
                && BaseDocumentTreeUtil.Companion.isEnabled(context)
                && BaseDocumentTreeUtil.Companion.getDirectory(context, R.string.dir_database) != null) {
            return true;
        } else {
            Toast.makeText(context, R.string.error_storage_directory, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        StoragePreferencesFragment.this.context = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (BaseDocumentTreeUtil.Companion.isEnabled(context)) {
            DocumentFile root = BaseDocumentTreeUtil.Companion.getRoot(context);
            if (root != null && root.exists()) {
                String path = root.getUri().getLastPathSegment();
                if (path == null) {
                    path = BaseDocumentTreeUtil.Companion.getStem(root.getUri(), context);
                }
                defaultStorageLocation.setSummary(path);
            }
        }
    }

    private boolean validateBrapiEnabledBeforeSetting(String newValue) {
        if ("brapi".equals(newValue) && !preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)) {
            showBrapiDisabledAlertDialog();
            return false;
        }
        return true;
    }

    private void showBrapiDisabledAlertDialog() {
        new AlertDialog.Builder(getContext(), R.style.AppAlertDialog)
                .setTitle(R.string.brapi_disabled_alert_title)
                .setMessage(R.string.brapi_disabled_alert_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

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

        Bundle args = new Bundle();
        args.putString(ImportDatabaseFragment.FILE_EXTRA, docFile.getUri().toString());

        ImportDatabaseFragment importDatabaseFragment = new ImportDatabaseFragment();
        importDatabaseFragment.setArguments(args);

        getChildFragmentManager().beginTransaction()
                .add(importDatabaseFragment, "com.fieldbook.tracker.fragments.ImportDatabaseFragment")
                .addToBackStack(null)
                .commit();
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
                .setView(layout)
                .setPositiveButton(getString(R.string.dialog_save), (dialog, which) -> {
                    dbSaveDialog.dismiss();
                    exportFileString = exportFile.getText().toString();

                    if (!exportFileString.isBlank()) {

                        Bundle args = new Bundle();
                        args.putString(ExportDatabaseFragment.EXTRA_FILE_NAME, exportFileString);

                        ExportDatabaseFragment exportDatabaseFragment = new ExportDatabaseFragment();
                        exportDatabaseFragment.setArguments(args);

                        getChildFragmentManager().beginTransaction()
                                .add(exportDatabaseFragment, "com.fieldbook.tracker.fragments.ExportDatabaseFragment")
                                .addToBackStack(null)
                                .commit();
                    } else {

                        Utils.makeToast(context, getString(R.string.database_export_invalid_filename));

                    }
                })
                .setNegativeButton(getString(R.string.dialog_cancel), (dialog, which) -> dialog.dismiss());
        dbSaveDialog = builder.create();
        dbSaveDialog.show();
        android.view.WindowManager.LayoutParams params = dbSaveDialog.getWindow().getAttributes();
        params.width = LinearLayout.LayoutParams.MATCH_PARENT;
        dbSaveDialog.getWindow().setAttributes(params);
    }

    private void showDatabaseResetDialog1() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AppAlertDialog);
        builder.setTitle(getString(R.string.dialog_warning));
        builder.setMessage(getString(R.string.database_reset_warning1));
        builder.setPositiveButton(getString(R.string.dialog_delete), (dialog, which) -> {
            dialog.dismiss();
            showDatabaseResetDialog2();
        });
        builder.setNegativeButton(getString(R.string.dialog_cancel), (dialog, which) -> dialog.dismiss());
        AlertDialog alert = builder.create();
        alert.show();

        // Set the delete button text color to red
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(getContext(), R.color.main_value_saved_color));
    }

    private void showDatabaseResetDialog2() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AppAlertDialog);
        builder.setTitle(getString(R.string.dialog_warning));
        builder.setMessage(getString(R.string.database_reset_warning2));
        builder.setPositiveButton(getString(R.string.dialog_yes), (dialog, which) -> {
            database.deleteDatabase();
            SharedPreferences.Editor ed = preferences.edit();
            ed.clear();
            ed.apply();
            dialog.dismiss();
            Utils.makeToast(getContext(), getString(R.string.database_reset_message));
            try {
                getActivity().finishAffinity();
            } catch (Exception e) {
                Log.e("Field Book", "" + e.getMessage());
            }
        });
        builder.setNegativeButton(getString(R.string.dialog_no), (dialog, which) -> dialog.dismiss());
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FILE_EXPLORE_CODE && resultCode == RESULT_OK) {
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
        } else if (requestCode == REQUEST_STORAGE_DEFINER_CODE && resultCode == RESULT_OK) {
            String storageSummary = preferences.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, null);
            defaultStorageLocation.setSummary(storageSummary);
        }
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_DATABASE_EXPORT)
    public void exportDatabaseFilePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
            if (EasyPermissions.hasPermissions(getContext(), perms)) {
                showDatabaseExportDialog();
            } else {
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
                    EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_import),
                            PERMISSIONS_REQUEST_DATABASE_IMPORT, perms);
                }
            }
        } else showDatabaseImportDialog();
    }
}
