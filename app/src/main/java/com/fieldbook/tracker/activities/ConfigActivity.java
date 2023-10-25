package com.fieldbook.tracker.activities;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.brapi.BrapiExportActivity;
import com.fieldbook.tracker.adapters.ImageListAdapter;
import com.fieldbook.tracker.brapi.BrapiAuthDialog;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.dao.StudyDao;
import com.fieldbook.tracker.database.models.ObservationUnitModel;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.AppLanguageUtil;
import com.fieldbook.tracker.utilities.CSVWriter;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.utilities.FieldSwitchImpl;
import com.fieldbook.tracker.utilities.OldPhotosMigrator;
import com.fieldbook.tracker.utilities.SoundHelperImpl;
import com.fieldbook.tracker.utilities.TapTargetUtil;
import com.fieldbook.tracker.utilities.Utils;
import com.fieldbook.tracker.utilities.VerifyPersonHelper;
import com.fieldbook.tracker.utilities.ZipUtil;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.michaelflisar.changelog.ChangelogBuilder;
import com.michaelflisar.changelog.classes.ImportanceChangelogSorter;
import com.michaelflisar.changelog.internal.ChangelogDialogFragment;

import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * The main page of FieldBook.
 * <p>
 * This contains a list of features that the app provides containing:
 * Fields -> the FieldEditorActivity, allows the user to import, delete and select a field
 * Traits -> the TraitEditorActivity, allows the user to edit, import, delete and select visible traits
 * Collect -> the main phenotyping component allowing user to navigate across plots and make
 * observations using the visible traits
 * Export -> creates a dialog that lets the user export data
 * Settings -> the app preferences activity
 * About -> a third party library that handles showing dependencies and other app data
 * <p>
 * Also this activity has a static member variable for the DataHelper (database) class that many other classes use
 * to make queries.
 */
@AndroidEntryPoint
public class ConfigActivity extends ThemedActivity {

    private static final int REQUEST_BARCODE = 100;
    private final static String TAG = ConfigActivity.class.getSimpleName();
    private final int PERMISSIONS_REQUEST_EXPORT_DATA = 9990;
    private final int PERMISSIONS_REQUEST_TRAIT_DATA = 9950;
    private final int PERMISSIONS_REQUEST_MAKE_DIRS = 9930;
    private final int REQUEST_STORAGE_DEFINER = 104;
    private final Runnable exportData = () -> new ExportDataTask().execute(0);
    @Inject
    public DataHelper database;
    @Inject
    public SoundHelperImpl soundHelper;
    @Inject
    VerifyPersonHelper verifyPersonHelper;
    public FieldSwitchImpl fieldSwitcher = null;
    Handler mHandler = new Handler();
    boolean doubleBackToExitPressedOnce = false;
    ListView settingsList;
    private SharedPreferences ep;
    private AlertDialog saveDialog;
    private EditText exportFile;
    private String exportFileString = "";
    private String fFile;
    private CheckBox checkDB;
    private CheckBox checkExcel;
    private Boolean checkDbBool = false;
    private Boolean checkExcelBool = false;
    private RadioButton onlyUnique;
    private RadioButton allColumns;
    private RadioButton allTraits;
    private RadioButton activeTraits;
    private ArrayList<String> newRange;
    private ArrayList<String> exportTrait;
    private Menu systemMenu;
    //barcode search fab
    private FloatingActionButton barcodeSearchFab;

    private void invokeDatabaseImport(DocumentFile doc) {

        mHandler.post(() -> {
            new ImportDBTask(doc).execute(0);
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if (systemMenu != null) {
            systemMenu.findItem(R.id.help).setVisible(ep.getBoolean(GeneralKeys.TIPS, false));
        }

        invalidateOptionsMenu();
        loadScreen();
    }

    private void setCrashlyticsUserId() {

        String id = ep.getString(GeneralKeys.CRASHLYTICS_ID, "");
        FirebaseCrashlytics instance = FirebaseCrashlytics.getInstance();
        instance.setUserId(id);
        instance.setCustomKey(GeneralKeys.CRASHLYTICS_KEY_USER_TOKEN, id);
    }

    /**
     *
     */
    private void checkBrapiToken() {
        String token = ep.getString(GeneralKeys.BRAPI_TOKEN, "");
        if (!token.isEmpty()) {
            ep.edit().putBoolean(GeneralKeys.BRAPI_ENABLED, true).apply();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        //important: this must be called before super.onCreate or else you get a black flicker
        AppLanguageUtil.Companion.refreshAppText(this);

        super.onCreate(savedInstanceState);

        ep = getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0);

        checkBrapiToken();

        setCrashlyticsUserId();
        invalidateOptionsMenu();
        loadScreen();

        // request permissions
        ActivityCompat.requestPermissions(this, Constants.permissions, Constants.PERM_REQ);

        int lastVersion = ep.getInt(GeneralKeys.UPDATE_VERSION, -1);
        int currentVersion = Utils.getVersion(this);
        if (lastVersion < currentVersion) {
            ep.edit().putInt(GeneralKeys.UPDATE_VERSION, Utils.getVersion(this)).apply();
            showChangelog(true, false);

            if (currentVersion >= 530 && lastVersion < 530) {

                OldPhotosMigrator.Companion.migrateOldPhotosDir(this, database);

                //clear field selection after updates
                ep.edit().putInt(GeneralKeys.SELECTED_FIELD_ID, -1).apply();
                ep.edit().putString(GeneralKeys.FIELD_FILE, null).apply();
                ep.edit().putString(GeneralKeys.FIELD_OBS_LEVEL, null).apply();
                ep.edit().putString(GeneralKeys.UNIQUE_NAME, null).apply();
                ep.edit().putString(GeneralKeys.PRIMARY_NAME, null).apply();
                ep.edit().putString(GeneralKeys.SECONDARY_NAME, null).apply();
            }
        }

        if (!ep.contains(GeneralKeys.FIRST_RUN)) {
            // do things on the first run
            //this will grant FB access to the chosen folder
            //preference and activity is handled through this utility call

            SharedPreferences.Editor ed = ep.edit();

            Set<String> entries = ep.getStringSet(GeneralKeys.TOOLBAR_CUSTOMIZE, new HashSet<>());
            entries.add("search");
            entries.add("resources");
            entries.add("summary");
            entries.add("lockData");

            ed.putStringSet(GeneralKeys.TOOLBAR_CUSTOMIZE, entries);
            ed.putBoolean(GeneralKeys.FIRST_RUN, false);
            ed.apply();
        }

        if (!BaseDocumentTreeUtil.Companion.isEnabled(this)) {

            startActivityForResult(new Intent(this, DefineStorageActivity.class),
                    REQUEST_STORAGE_DEFINER);
        }

        verifyPersonHelper.updateLastOpenedTime();
    }

    private void showChangelog(Boolean managedShow, Boolean rateButton) {
        ChangelogDialogFragment builder = new ChangelogBuilder()
                .withUseBulletList(true) // true if you want to show bullets before each changelog row, false otherwise
                .withManagedShowOnStart(managedShow)  // library will take care to show activity/dialog only if the changelog has new infos and will only show this new infos
                .withRateButton(rateButton) // enable this to show a "rate app" button in the dialog => clicking it will open the play store; the parent activity or target fragment can also implement IChangelogRateHandler to handle the button click
                .withSummary(false, true) // enable this to show a summary and a "show more" button, the second paramter describes if releases without summary items should be shown expanded or not
                .withTitle(getString(R.string.changelog_title)) // provide a custom title if desired, default one is "Changelog <VERSION>"
                .withOkButtonLabel(getString(android.R.string.ok)) // provide a custom ok button text if desired, default one is "OK"
                .withSorter(new ImportanceChangelogSorter())
                .buildAndShowDialog(this, false); // second parameter defines, if the dialog has a dark or light theme
    }

    private void initToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(null);
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setHomeButtonEnabled(false);
        }
    }

    private void loadScreen() {
        setContentView(R.layout.activity_config);
        initToolbar();

        settingsList = findViewById(R.id.myList);

        String[] configList = new String[]{getString(R.string.settings_fields),
                getString(R.string.settings_traits), getString(R.string.settings_collect), getString(R.string.settings_export), getString(R.string.settings_advanced), getString(R.string.about_title)};

        Integer[] image_id = {R.drawable.ic_nav_drawer_fields, R.drawable.ic_nav_drawer_traits, R.drawable.ic_nav_drawer_collect_data, R.drawable.trait_date_save, R.drawable.ic_nav_drawer_settings, R.drawable.ic_tb_info};

        settingsList.setOnItemClickListener((av, arg1, position, arg3) -> {
            Intent intent = new Intent();
            switch (position) {
                case 0:
                    intent.setClassName(ConfigActivity.this,
                            FieldEditorActivity.class.getName());
                    startActivity(intent);

                    break;
                case 1:
                    intent.setClassName(ConfigActivity.this,
                            TraitEditorActivity.class.getName());
                    startActivity(intent);

                    break;
                case 2:

                    if (checkTraitsExist() < 0) return;

                    collectDataFilePermission();
                    break;
                case 3:

                    if (checkTraitsExist() < 0) return;

                    String exporter = ep.getString(GeneralKeys.EXPORT_SOURCE_DEFAULT, "");

                    switch (exporter) {
                        case "local":
                            exportPermission();
                            break;
                        case "brapi":
                            exportBrAPI();
                            break;
                        default:
                            // Skip dialog if BrAPI is disabled
                            if (ep.getBoolean(GeneralKeys.BRAPI_ENABLED, false)) {
                                showExportDialog();
                            } else {
                                exportPermission();
                            }
                            break;
                    }

                    break;
                case 4:
                    intent.setClassName(ConfigActivity.this,
                            PreferencesActivity.class.getName());
                    startActivity(intent);
                    break;
                case 5:
                    intent.setClassName(ConfigActivity.this,
                            AboutActivity.class.getName());
                    startActivity(intent);
                    break;
            }
        });

        ImageListAdapter adapterImg = new ImageListAdapter(this, image_id, configList);
        settingsList.setAdapter(adapterImg);

        SharedPreferences.Editor ed = ep.edit();

        if (!ep.getBoolean(GeneralKeys.TIPS_CONFIGURED, false)) {
            ed.putBoolean(GeneralKeys.TIPS_CONFIGURED, true);
            ed.apply();
            showTipsDialog();
            loadSampleDataDialog();
        }

        barcodeSearchFab = findViewById(R.id.act_config_search_fab);
        barcodeSearchFab.setOnClickListener(v -> {
            new IntentIntegrator(this)
                    .setPrompt(getString(R.string.barcode_scanner_text))
                    .setBeepEnabled(false)
                    .setRequestCode(REQUEST_BARCODE)
                    .initiateScan();
        });

        //this must happen after migrations and can't be injected in config
        fieldSwitcher = new FieldSwitchImpl(this);

    }

    /**
     * Checks if there are any visible traits in trait editor.
     * Also checks if a field is selected.
     *
     * @return -1 when the conditions fail, otherwise it returns 1
     */
    private int checkTraitsExist() {

        String[] traits = database.getVisibleTrait();

        if (!ep.getBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false)
                || ep.getInt(GeneralKeys.SELECTED_FIELD_ID, -1) == -1) {
            Utils.makeToast(getApplicationContext(), getString(R.string.warning_field_missing));
            return -1;
        } else if (traits.length == 0) {
            Utils.makeToast(getApplicationContext(), getString(R.string.warning_traits_missing));
            return -1;
        }

        return 1;
    }

    private String getOverwriteFile(String filename) {
        String[] fileArray;

        DocumentFile exportDir = BaseDocumentTreeUtil.Companion.getDirectory(this, R.string.dir_field_export);

        if (exportDir != null && exportDir.exists()) {

            DocumentFile[] files = exportDir.listFiles();

            fileArray = new String[files.length];
            for (int i = 0; i < files.length; ++i) {
                fileArray[i] = files[i].getName();
            }

            if (filename.contains(fFile)) {
                for (String aFileArray : fileArray) {

                    DocumentFile oldDoc = BaseDocumentTreeUtil.Companion.getFile(this, R.string.dir_field_export, aFileArray);
                    DocumentFile newDoc = BaseDocumentTreeUtil.Companion.getFile(this, R.string.dir_archive, aFileArray);

                    if (checkDbBool) {
                        if (aFileArray.contains(fFile) && aFileArray.contains("database")) {
                            if (newDoc != null && oldDoc != null) {
                                if (newDoc.getName() != null) {
                                    oldDoc.renameTo(newDoc.getName());
                                }
                            }
                        }
                    }

                    if (checkExcelBool) {
                        if (aFileArray.contains(fFile) && aFileArray.contains("table")) {
                            if (newDoc != null && oldDoc != null) {
                                if (newDoc.getName() != null) {
                                    oldDoc.renameTo(newDoc.getName());
                                }
                            }
                        }
                    }
                }
            }
        }


        return filename;
    }

    private void showCitationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this, R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.citation_title))
                .setMessage(getString(R.string.citation_string) + "\n\n" + getString(R.string.citation_text))
                .setCancelable(false);

        builder.setPositiveButton(getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                invalidateOptionsMenu();
                Intent intent = new Intent();
                intent.setClassName(ConfigActivity.this,
                        ConfigActivity.class.getName());
                startActivity(intent);
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void showTipsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this, R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.tutorial_dialog_title));
        builder.setMessage(getString(R.string.tutorial_dialog_description));

        builder.setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Editor ed = ep.edit();
                ed.putBoolean(GeneralKeys.TIPS, true);
                ed.putBoolean(GeneralKeys.TIPS_CONFIGURED, true);
                ed.apply();

                dialog.dismiss();

                invalidateOptionsMenu();

                Intent intent = new Intent();
                intent.setClassName(ConfigActivity.this,
                        ConfigActivity.class.getName());
                startActivity(intent);
            }
        });

        builder.setNegativeButton(getString(R.string.dialog_no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Editor ed = ep.edit();
                ed.putBoolean(GeneralKeys.TIPS_CONFIGURED, true);
                ed.apply();

                dialog.dismiss();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void loadSampleDataDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this, R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.startup_sample_data_title));
        builder.setMessage(getString(R.string.startup_sample_data_message));

        builder.setPositiveButton(getString(R.string.dialog_yes), (dialog, which) -> {
            // Load database with sample data
            DocumentFile sampleDatabase = BaseDocumentTreeUtil.Companion.getFile(ConfigActivity.this,
                    R.string.dir_database, "sample.db");
            if (sampleDatabase != null && sampleDatabase.exists()) {
                invokeDatabaseImport(sampleDatabase);
            }
        });

        builder.setNegativeButton(getString(R.string.dialog_no), (dialog, which) -> dialog.dismiss());

        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Scan file to update file list and share exported file
     */
    private void shareFile(DocumentFile docFile) {
        if (!ep.getBoolean(GeneralKeys.DISABLE_SHARE, false)) {
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, docFile.getUri());
            try {
                startActivity(Intent.createChooser(intent, "Sending File..."));
            } catch (Exception e) {
                Log.e("Field Book", "" + e.getMessage());
            }
        }
    }

    // Helper function to merge arrays
    String[] concat(String[] a1, String[] a2) {
        String[] n = new String[a1.length + a2.length];
        System.arraycopy(a1, 0, n, 0, a1.length);
        System.arraycopy(a2, 0, n, a1.length, a2.length);

        return n;
    }

    private void showExportDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list_buttonless, null);
        ListView exportSourceList = layout.findViewById(R.id.myList);

        String[] exportArray = new String[2];
        exportArray[0] = getString(R.string.export_source_local);
        exportArray[1] = ep.getString(GeneralKeys.BRAPI_DISPLAY_NAME, getString(R.string.preferences_brapi_server_test));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item_dialog_list, exportArray);
        exportSourceList.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        builder.setTitle(R.string.export_dialog_title)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_cancel), (dialogInterface, i) -> { /* do nothing */ });

        final AlertDialog exportDialog = builder.create();
        exportDialog.show();

        exportSourceList.setOnItemClickListener((av, arg1, which, arg3) -> {
            switch (which) {
                case 0:
                    exportPermission();
                    break;
                case 1:
                    exportBrAPI();
                    break;
            }
            exportDialog.dismiss();
        });
    }

    private void exportBrAPI() {
        // Get our active field
        int activeFieldId = ep.getInt(GeneralKeys.SELECTED_FIELD_ID, -1);
        FieldObject activeField;
        if (activeFieldId != -1) {
            activeField = database.getFieldObject(activeFieldId);
        } else {
            activeField = null;
            Toast.makeText(ConfigActivity.this, R.string.warning_field_missing, Toast.LENGTH_LONG).show();
            return;
        }

        // Check that our field is a brapi field
        if (activeField.getExp_source() == null ||
                activeField.getExp_source().equals("") ||
                activeField.getExp_source().equals("local")) {

            Toast.makeText(ConfigActivity.this, R.string.brapi_field_not_selected, Toast.LENGTH_LONG).show();
            return;
        }

        // Check that the field data source is the same as the current target
        if (!BrAPIService.checkMatchBrapiUrl(ConfigActivity.this, activeField.getExp_source())) {

            String hostURL = BrAPIService.getHostUrl(ConfigActivity.this);
            String badSourceMsg = getResources().getString(R.string.brapi_field_non_matching_sources, activeField.getExp_source(), hostURL);
            Toast.makeText(ConfigActivity.this, badSourceMsg, Toast.LENGTH_LONG).show();
            return;
        }

        // Check if we are authorized and force authorization if not.
        if (BrAPIService.isLoggedIn(getApplicationContext())) {
            Intent exportIntent = new Intent(ConfigActivity.this, BrapiExportActivity.class);
            startActivity(exportIntent);
        } else {
            // Show our login dialog
            BrapiAuthDialog brapiAuth = new BrapiAuthDialog(ConfigActivity.this);
            brapiAuth.show();
        }
    }

    private void showSaveDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_export, null);

        exportFile = layout.findViewById(R.id.fileName);
        checkDB = layout.findViewById(R.id.formatDB);
        checkExcel = layout.findViewById(R.id.formatExcel);
        allColumns = layout.findViewById(R.id.allColumns);
        onlyUnique = layout.findViewById(R.id.onlyUnique);
        allTraits = layout.findViewById(R.id.allTraits);
        activeTraits = layout.findViewById(R.id.activeTraits);
        CheckBox checkOverwrite = layout.findViewById(R.id.overwrite);
        CheckBox checkBundle = layout.findViewById(R.id.dialog_export_bundle_data_cb);

        checkBundle.setChecked(ep.getBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED, false));
        checkOverwrite.setChecked(ep.getBoolean(GeneralKeys.EXPORT_OVERWRITE, false));
        checkDB.setChecked(ep.getBoolean(GeneralKeys.EXPORT_FORMAT_DATABSE, false));
        checkExcel.setChecked(ep.getBoolean(GeneralKeys.EXPORT_FORMAT_TABLE, false));
        onlyUnique.setChecked(ep.getBoolean(GeneralKeys.EXPORT_COLUMNS_UNIQUE, false));
        allColumns.setChecked(ep.getBoolean(GeneralKeys.EXPORT_COLUMNS_ALL, false));
        allTraits.setChecked(ep.getBoolean(GeneralKeys.EXPORT_TRAITS_ALL, false));
        activeTraits.setChecked(ep.getBoolean(GeneralKeys.EXPORT_TRAITS_ACTIVE, false));

        SimpleDateFormat timeStamp = new SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss", Locale.getDefault());

        fFile = ep.getString(GeneralKeys.FIELD_FILE, "");

        if (fFile.length() > 4 & fFile.toLowerCase().endsWith(".csv")) {
            fFile = fFile.substring(0, fFile.length() - 4);
        }

        String exportString = timeStamp.format(Calendar.getInstance().getTime()) + "_" + fFile;
        exportFile.setText(exportString);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.settings_export)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_save), null);

        builder.setNegativeButton(getString(R.string.dialog_cancel), (dialog, i) -> dialog.dismiss());

        saveDialog = builder.create();
        saveDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        saveDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        saveDialog.show();

        android.view.WindowManager.LayoutParams params2 = saveDialog.getWindow().getAttributes();
        params2.width = LayoutParams.MATCH_PARENT;
        saveDialog.getWindow().setAttributes(params2);

        // Override positive button so it doesnt automatically dismiss dialog
        Button positiveButton = saveDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(arg0 -> {
            if (!checkDB.isChecked() & !checkExcel.isChecked()) {
                Utils.makeToast(getApplicationContext(), getString(R.string.export_error_missing_format));
                return;
            }

            if (!onlyUnique.isChecked() & !allColumns.isChecked()) {
                Utils.makeToast(getApplicationContext(), getString(R.string.export_error_missing_column));
                return;
            }

            if (!activeTraits.isChecked() & !allTraits.isChecked()) {
                Utils.makeToast(getApplicationContext(), getString(R.string.export_error_missing_trait));
                return;
            }

            Editor ed = ep.edit();
            ed.putBoolean(GeneralKeys.EXPORT_COLUMNS_UNIQUE, onlyUnique.isChecked());
            ed.putBoolean(GeneralKeys.EXPORT_COLUMNS_ALL, allColumns.isChecked());
            ed.putBoolean(GeneralKeys.EXPORT_TRAITS_ALL, allTraits.isChecked());
            ed.putBoolean(GeneralKeys.EXPORT_TRAITS_ACTIVE, activeTraits.isChecked());
            ed.putBoolean(GeneralKeys.EXPORT_FORMAT_TABLE, checkExcel.isChecked());
            ed.putBoolean(GeneralKeys.EXPORT_FORMAT_DATABSE, checkDB.isChecked());
            ed.putBoolean(GeneralKeys.EXPORT_OVERWRITE, checkOverwrite.isChecked());
            ed.putBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED, checkBundle.isChecked());
            ed.apply();

            BaseDocumentTreeUtil.Companion.getDirectory(ConfigActivity.this, R.string.dir_field_export);

            newRange = new ArrayList<>();

            if (onlyUnique.isChecked()) {
                newRange.add(ep.getString(GeneralKeys.UNIQUE_NAME, ""));
            }

            if (allColumns.isChecked()) {
                String[] columns = database.getRangeColumns();
                Collections.addAll(newRange, columns);
            }

            exportTrait = new ArrayList<>();

            if (activeTraits.isChecked()) {
                String[] traits = database.getVisibleTrait();
                Collections.addAll(exportTrait, traits);
            }

            if (allTraits.isChecked()) {
                String[] traits = database.getAllTraits();
                Collections.addAll(exportTrait, traits);
            }

            checkDbBool = checkDB.isChecked();
            checkExcelBool = checkExcel.isChecked();

            if (ep.getBoolean(GeneralKeys.EXPORT_OVERWRITE, false)) {
                exportFileString = getOverwriteFile(exportFile.getText().toString());
            } else {
                exportFileString = exportFile.getText().toString();
            }

            saveDialog.dismiss();
            mHandler.post(exportData);
        });
    }

    private void resolveFuzzySearchResult(FieldObject f, @Nullable String plotId) {

        soundHelper.playCelebrate();

        int studyId = ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0);

        int newStudyId = f.getExp_id();

        if (studyId != newStudyId) {

            switchField(newStudyId);

        }

        Intent intent = new Intent(this, CollectActivity.class);
        CollectActivity.reloadData = true;

        if (plotId != null) {

            ep.edit().putString(GeneralKeys.LAST_PLOT, plotId).apply();

        }

        startActivity(intent);

    }
    @Nullable
    private FieldObject searchStudiesForBarcode(String barcode) {

        // first, search to try and match study alias (brapi stores study_db_id here)
        ArrayList<FieldObject> fields = database.getAllFieldObjects();

        // start by searching for alias
        for (FieldObject f : fields) {

            if (f != null && f.getExp_alias() != null && f.getExp_alias().equals(barcode)) {

                return f;

            }
        }

        // second, if field is not found search for study name
        for (FieldObject f : fields) {

            if (f != null && f.getExp_name() != null && f.getExp_name().equals(barcode)) {

                return f;

            }
        }

        return null;
    }

    @Nullable
    private ObservationUnitModel searchPlotsForBarcode(String barcode) {

        // search for barcode in database
        ObservationUnitModel[] models = database.getAllObservationUnits();
        for (ObservationUnitModel m : models) {
            if (m.getObservation_unit_db_id().equals(barcode)) {

                return m;
            }
        }

        return null;
    }

    //1) study alias, 2) study names, 3) plotdbids
    private void fuzzyBarcodeSearch(String barcode) {

        // search for studies
        FieldObject f = searchStudiesForBarcode(barcode);

        if (f == null) {

            // search for plots
            ObservationUnitModel m = searchPlotsForBarcode(barcode);

            if (m != null && m.getStudy_id() != -1) {

                FieldObject study = database.getFieldObject(m.getStudy_id());

                resolveFuzzySearchResult(study, barcode);

            } else {

                soundHelper.playError();

                Utils.makeToast(this, getString(R.string.act_config_fuzzy_search_failed, barcode));
            }

        } else {

            resolveFuzzySearchResult(f, null);

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_STORAGE_DEFINER) {
            if (resultCode != Activity.RESULT_OK) finish();
        } else if (requestCode == REQUEST_BARCODE) {
            if (resultCode == RESULT_OK) {

                // get barcode from scan result
                IntentResult plotDataResult = IntentIntegrator.parseActivityResult(resultCode, data);
                String scannedBarcode = plotDataResult.getContents();

                try {

                    fuzzyBarcodeSearch(scannedBarcode);

                } catch (Exception e) {

                    e.printStackTrace();

                    Utils.makeToast(this, getString(R.string.act_config_fuzzy_search_error, scannedBarcode));

                    soundHelper.playError();
                }
            }
        }
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_EXPORT_DATA)
    private void exportPermission() {
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (EasyPermissions.hasPermissions(this, perms)) {
                showSaveDialog();
            } else {
                EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_export),
                        PERMISSIONS_REQUEST_EXPORT_DATA, perms);
            }
        } else showSaveDialog();
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_TRAIT_DATA)
    public void collectDataFilePermission() {
        String[] perms = {Manifest.permission.VIBRATE, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            perms = new String[] {Manifest.permission.VIBRATE, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA};
        }

        if (EasyPermissions.hasPermissions(this, perms)) {
            Intent intent = new Intent();

            intent.setClassName(ConfigActivity.this,
                    CollectActivity.class.getName());
            startActivity(intent);
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_trait_features),
                    PERMISSIONS_REQUEST_TRAIT_DATA, perms);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(ConfigActivity.this).inflate(R.menu.menu_settings, menu);
        systemMenu = menu;
        systemMenu.findItem(R.id.help).setVisible(ep.getBoolean(GeneralKeys.TIPS, false));
        return true;
    }

    private Rect settingsListItemLocation(int item) {
        View v = settingsList.getChildAt(item);
        final int[] location = new int[2];
        v.getLocationOnScreen(location);
        Rect droidTarget = new Rect(location[0], location[1], location[0] + v.getWidth() / 5, location[1] + v.getHeight());
        return droidTarget;
    }

    private TapTarget settingsTapTargetRect(Rect item, String title, String desc) {
        return TapTargetUtil.Companion.getTapTargetSettingsRect(this, item, title, desc);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = new Intent(Intent.ACTION_VIEW);

        switch (item.getItemId()) {
            case R.id.help:
                TapTargetSequence sequence = new TapTargetSequence(this)
                        .targets(settingsTapTargetRect(settingsListItemLocation(0), getString(R.string.tutorial_settings_fields_title), getString(R.string.tutorial_settings_fields_description)),
                                settingsTapTargetRect(settingsListItemLocation(1), getString(R.string.tutorial_settings_traits_title), getString(R.string.tutorial_settings_traits_description)),
                                settingsTapTargetRect(settingsListItemLocation(2), getString(R.string.tutorial_settings_collect_title), getString(R.string.tutorial_settings_collect_description)),
                                settingsTapTargetRect(settingsListItemLocation(3), getString(R.string.tutorial_settings_export_title), getString(R.string.tutorial_settings_export_description)),
                                settingsTapTargetRect(settingsListItemLocation(4), getString(R.string.tutorial_settings_settings_title), getString(R.string.tutorial_settings_settings_description))
                        )
                        .listener(new TapTargetSequence.Listener() {
                            // This listener will tell us when interesting(tm) events happen in regards to the sequence
                            @Override
                            public void onSequenceFinish() {
                                TapTargetView.showFor(ConfigActivity.this, settingsTapTargetRect(settingsListItemLocation(0), getString(R.string.tutorial_settings_fields_title), getString(R.string.tutorial_settings_fields_import)),
                                        new TapTargetView.Listener() {          // The listener can listen for regular clicks, long clicks or cancels
                                            @Override
                                            public void onTargetClick(TapTargetView view) {
                                                super.onTargetClick(view);      // This call is optional
                                                intent.setClassName(ConfigActivity.this,
                                                        FieldEditorActivity.class.getName());
                                                startActivity(intent);
                                            }
                                        });
                            }

                            @Override
                            public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                                Log.d("TapTargetView", "Clicked on " + lastTarget.id());
                            }

                            @Override
                            public void onSequenceCanceled(TapTarget lastTarget) {

                            }
                        });
                sequence.start();
                break;
            case R.id.changelog:
                showChangelog(false, false);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }

    /**
     * Queries the database for saved studies and calls switch field for the first one.
     */
    public void selectFirstField() {

        try {

            FieldObject[] fs = StudyDao.Companion.getAllFieldObjects().toArray(new FieldObject[0]);

            if (fs.length > 0) {

                switchField(fs[0].getExp_id());
            }

        } catch (Exception e) {

            e.printStackTrace();

        }
    }

    /**
     * Calls database switch field on the given studyId.
     *
     * @param studyId the study id to switch to
     */
    private void switchField(int studyId) {

        if (fieldSwitcher == null) {
            fieldSwitcher = new FieldSwitchImpl(this);
        }

        fieldSwitcher.switchField(studyId);

    }

    private class ExportDataTask extends AsyncTask<Integer, Integer, Integer> {
        boolean fail;
        boolean noData = false;

        ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            fail = false;

            dialog = new ProgressDialog(ConfigActivity.this);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html
                    .fromHtml(getString(R.string.export_progress)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {

            //flag telling if the user checked the media bundle option
            boolean bundleChecked = ep.getBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED, false);

            newRange.clear();

            if (onlyUnique.isChecked()) {
                newRange.add(ep.getString(GeneralKeys.UNIQUE_NAME, ""));
            }

            if (allColumns.isChecked()) {
                String[] columns = database.getRangeColumns();
                Collections.addAll(newRange, columns);
            }

            String[] newRanges = newRange.toArray(new String[newRange.size()]);
            String[] exportTraits = exportTrait.toArray(new String[exportTrait.size()]);

            // Retrieves the data needed for export
            Cursor exportData = database.getExportDBData(newRanges, exportTraits);

            for (String i : newRanges) {
                Log.i("Field Book : Ranges : ", i);
            }

            for (String j : exportTraits) {
                Log.i("Field Book : Traits : ", j);
            }

            if (exportData.getCount() == 0) {
                noData = true;
                return (0);
            }

            //separate files for table and database
            DocumentFile dbFile = null;
            DocumentFile tableFile = null;

            ArrayList<TraitObject> traits = database.getAllTraitObjects();

            //check if export database has been selected
            if (checkDbBool) {
                if (exportData.getCount() > 0) {
                    try {

                        String exportFileName = exportFileString + "_database.csv";

                        dbFile = BaseDocumentTreeUtil.Companion.getFile(ConfigActivity.this, R.string.dir_field_export, exportFileName);

                        DocumentFile exportDir = BaseDocumentTreeUtil.Companion.getDirectory(ConfigActivity.this, R.string.dir_field_export);

                        if (dbFile != null && dbFile.exists()) {
                            dbFile.delete();
                        }
                        if (exportDir != null && exportDir.exists()) {
                            dbFile = exportDir.createFile("*/*", exportFileName);
                        }

                        OutputStream output = BaseDocumentTreeUtil.Companion.getFileOutputStream(ConfigActivity.this, R.string.dir_field_export, exportFileName);

                        if (output != null) {
                            OutputStreamWriter fw = new OutputStreamWriter(output);
                            CSVWriter csvWriter = new CSVWriter(fw, exportData);
                            csvWriter.writeDatabaseFormat(newRange);

                            System.out.println(exportFileString);
                        }

                    } catch (Exception e) {
                        fail = true;
                    }
                }
            }

            //check if export table has been selected
            if (checkExcelBool) {
                if (exportData.getCount() > 0) {
                    try {

                        String tableFileName = exportFileString + "_table.csv";

                        tableFile = BaseDocumentTreeUtil.Companion.getFile(ConfigActivity.this, R.string.dir_field_export, tableFileName);

                        DocumentFile exportDir = BaseDocumentTreeUtil.Companion.getDirectory(ConfigActivity.this, R.string.dir_field_export);

                        if (tableFile != null && tableFile.exists()) {
                            tableFile.delete();
                        }
                        if (exportDir != null && exportDir.exists()) {
                            tableFile = exportDir.createFile("*/*", tableFileName);
                        }

                        OutputStream output = BaseDocumentTreeUtil.Companion.getFileOutputStream(ConfigActivity.this, R.string.dir_field_export, tableFileName);
                        OutputStreamWriter fw = new OutputStreamWriter(output);

                        exportData = database.convertDatabaseToTable(newRanges, exportTraits);
                        CSVWriter csvWriter = new CSVWriter(fw, exportData);

                        csvWriter.writeTableFormat(concat(newRanges, exportTraits), newRanges.length, traits);

                    } catch (Exception e) {
                        fail = true;
                    }
                }
            }

            //if the export did not fail, create a zip file with the exported files
            if (!fail) {

                String zipFileName = exportFileString + ".zip";

                //if bundle is checked, zip the files with the media dir for the given study
                if (bundleChecked) {

                    //query selected study to get the study name
                    int studyId = ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0);

                    //study name is the same as the media directory in plot_data
                    FieldObject fieldObject = StudyDao.Companion.getFieldObject(studyId);

                    if (fieldObject != null) {

                        String studyName = fieldObject.getExp_name();

                        //create media dir directory from fieldBook/plot_data/studyName path
                        DocumentFile mediaDir = BaseDocumentTreeUtil.Companion.getFile(
                                ConfigActivity.this, R.string.dir_plot_data, studyName);

                        if (mediaDir != null && mediaDir.exists()) {

                            //create zip file output from user created name
                            DocumentFile exportDir = BaseDocumentTreeUtil.Companion.getDirectory(
                                    ConfigActivity.this, R.string.dir_field_export);

                            if (exportDir != null && exportDir.exists()) {

                                DocumentFile zipFile = exportDir.createFile("*/*", zipFileName);

                                OutputStream output = BaseDocumentTreeUtil.Companion.getFileOutputStream(
                                        ConfigActivity.this, R.string.dir_field_export, zipFileName);

                                if (output != null) {

                                    //add media folders and database or export files to zip file
                                    ArrayList<DocumentFile> paths = new ArrayList<>();

                                    if (dbFile != null) paths.add(dbFile);
                                    if (tableFile != null) paths.add(tableFile);
                                    paths.add(mediaDir);

                                    zipFiles(ConfigActivity.this, paths, output);

                                    if (dbFile != null && dbFile.exists()) dbFile.delete();
                                    if (tableFile != null && tableFile.exists()) tableFile.delete();

                                    //share the zip file
                                    shareFile(zipFile);
                                }
                            }
                        }
                    }

                } else { //if not bundling media data, create zip of the table or database files

                    DocumentFile exportDir = BaseDocumentTreeUtil.Companion.getDirectory(
                            ConfigActivity.this, R.string.dir_field_export);

                    if (exportDir != null && exportDir.exists()) {

                        if (checkDbBool && checkExcelBool) {

                            DocumentFile zipFile = exportDir.createFile("*/*", zipFileName);

                            OutputStream output = BaseDocumentTreeUtil.Companion.getFileOutputStream(
                                    ConfigActivity.this, R.string.dir_field_export, zipFileName);

                            ArrayList<DocumentFile> paths = new ArrayList<>();

                            if (dbFile != null) paths.add(dbFile);
                            if (tableFile != null) paths.add(tableFile);

                            zipFiles(ConfigActivity.this, paths, output);

                            if (dbFile != null) dbFile.delete();
                            if (tableFile != null) tableFile.delete();

                            shareFile(zipFile);

                        } else if (checkDbBool) {

                            shareFile(dbFile);

                        } else if (checkExcelBool) {

                            shareFile(tableFile);
                        }
                    }
                }
            }

            return 0;
        }

        private void zipFiles(Context ctx, ArrayList<DocumentFile> paths, OutputStream outputStream) {

            try {

                ZipUtil.Companion.zip(ctx, paths.toArray(new DocumentFile[]{}), outputStream);

                outputStream.close();

            } catch (IOException io) {

                io.printStackTrace();
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            newRange.clear();

            if (dialog.isShowing()) {
                dialog.dismiss();
            }

            if (!fail) {
                showCitationDialog();
                database.updateExpTable(false, false, true, ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));
            }

            if (fail) {
                Utils.makeToast(getApplicationContext(), getString(R.string.export_error_general));
            }

            if (noData) {
                Utils.makeToast(getApplicationContext(), getString(R.string.export_error_data_missing));
            }
        }
    }

    private class ImportDBTask extends AsyncTask<Integer, Integer, Integer> {
        boolean fail;
        ProgressDialog dialog;
        DocumentFile file = null;

        public ImportDBTask(DocumentFile file) {
            this.file = file;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            fail = false;

            dialog = new ProgressDialog(ConfigActivity.this, R.style.AppAlertDialog);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html
                    .fromHtml(getString(R.string.import_dialog_importing)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            try {
                if (this.file != null) {
                    database.importDatabase(this.file);
                }
            } catch (Exception e) {
                Log.d("Database", e.toString());
                e.printStackTrace();
                fail = true;
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (dialog.isShowing())
                dialog.dismiss();

            if (fail) {
                Utils.makeToast(getApplicationContext(), getString(R.string.import_error_general));
            }

            SharedPreferences prefs = getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_MULTI_PROCESS);
            SharedPreferences.Editor editor = prefs.edit();
            editor.apply();

            //if sample db is imported, automatically select the first study
            try {

                selectFirstField();

            } catch (Exception e) {

                e.printStackTrace();

            }

            CollectActivity.reloadData = true;
        }
    }
}