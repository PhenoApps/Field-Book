package com.fieldbook.tracker.activities;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.BrapiAuthDialog;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.dao.VisibleObservationVariableDao;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.utilities.CSVWriter;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.adapters.ImageListAdapter;
import com.fieldbook.tracker.utilities.DialogUtils;
import com.fieldbook.tracker.utilities.PrefsConstants;
import com.fieldbook.tracker.utilities.Utils;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.getkeepsafe.taptargetview.TapTargetView;

import com.michaelflisar.changelog.ChangelogBuilder;
import com.michaelflisar.changelog.classes.ImportanceChangelogSorter;
import com.michaelflisar.changelog.internal.ChangelogDialogFragment;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * Settings Screen
 */
public class ConfigActivity extends AppCompatActivity {

    public static DataHelper dt;
    private final int PERMISSIONS_REQUEST_EXPORT_DATA = 9990;
    private final int PERMISSIONS_REQUEST_TRAIT_DATA = 9950;
    private final int PERMISSIONS_REQUEST_MAKE_DIRS = 9930;
    Handler mHandler = new Handler();
    boolean doubleBackToExitPressedOnce = false;
    private SharedPreferences ep;
    private AlertDialog saveDialog;
    private AlertDialog dbSaveDialog;
    private String mChosenFile = "";
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
    ListView settingsList;

    private Runnable exportData = new Runnable() {
        public void run() {
            new ExportDataTask().execute(0);
        }
    };

    private Runnable importDB = new Runnable() {
        public void run() {
            new ImportDBTask().execute(0);
        }
    };

    @Override
    public void onDestroy() {
        //ConfigActivity.dt.close();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (systemMenu != null) {
            systemMenu.findItem(R.id.help).setVisible(ep.getBoolean("Tips", false));
        }

        invalidateOptionsMenu();
        loadScreen();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dt = new DataHelper(this);
        dt.open();

        ep = getSharedPreferences("Settings", 0);

        invalidateOptionsMenu();
        loadScreen();

        // request permissions
        ActivityCompat.requestPermissions(this, Constants.permissions, Constants.PERM_REQ);

        if (ep.getInt("UpdateVersion", -1) < Utils.getVersion(this)) {
            ep.edit().putInt("UpdateVersion", Utils.getVersion(this)).apply();
            showChangelog(true, false);
        }

        if (!ep.contains("FirstRun")) {
            // do things on the first run
            Utils.createDirs(this, Constants.MPATH);

            SharedPreferences.Editor ed = ep.edit();

            Set<String> entries = ep.getStringSet(GeneralKeys.TOOLBAR_CUSTOMIZE, new HashSet<String>());
            entries.add("search");
            entries.add("resources");
            entries.add("summary");
            entries.add("lockData");

            ed.putStringSet(GeneralKeys.TOOLBAR_CUSTOMIZE,entries);
            ed.putString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY,Constants.MPATH);
            ed.putBoolean("FirstRun",false);
            ed.apply();
        }
    }

    private void showChangelog(Boolean managedShow, Boolean rateButton) {
        ChangelogDialogFragment builder = new ChangelogBuilder()
                .withUseBulletList(true) // true if you want to show bullets before each changelog row, false otherwise
                .withManagedShowOnStart(managedShow)  // library will take care to show activity/dialog only if the changelog has new infos and will only show this new infos
                .withRateButton(rateButton) // enable this to show a "rate app" button in the dialog => clicking it will open the play store; the parent activity or target fragment can also implement IChangelogRateHandler to handle the button click
                .withSummary(false, true) // enable this to show a summary and a "show more" button, the second paramter describes if releases without summary items should be shown expanded or not
                .withTitle(getString(R.string.changelog_title)) // provide a custom title if desired, default one is "Changelog <VERSION>"
                .withOkButtonLabel("OK") // provide a custom ok button text if desired, default one is "OK"
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
                getString(R.string.settings_traits), getString(R.string.settings_collect),  getString(R.string.settings_export), getString(R.string.settings_advanced), getString(R.string.about_title)};

        Integer[] image_id = {R.drawable.ic_nav_drawer_fields, R.drawable.ic_nav_drawer_traits, R.drawable.ic_nav_drawer_collect_data, R.drawable.trait_date_save, R.drawable.ic_nav_drawer_settings, R.drawable.ic_tb_info};

        settingsList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int position, long arg3) {
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

                        String exporter = ep.getString("EXPORT_SOURCE_DEFAULT", "ask");

                        switch (exporter) {
                            case "ask":
                                showExportDialog();
                                break;
                            case "local":
                                exportPermission();
                                break;
                            case "brapi":
                                exportBrAPI();
                                break;
                            default:
                                showExportDialog();
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
            }
        });

        ImageListAdapter adapterImg = new ImageListAdapter(this, image_id, configList);
        settingsList.setAdapter(adapterImg);

        SharedPreferences.Editor ed = ep.edit();

        if (!ep.getBoolean("TipsConfigured", false)) {
            ed.putBoolean("TipsConfigured", true);
            ed.apply();
            showTipsDialog();
            loadSampleDataDialog();
        }
    }

    /**
     * Checks if there are any visible traits in trait editor.
     * Also checks if a field is selected.
     * @return -1 when the conditions fail, otherwise it returns 1
     */
    private int checkTraitsExist() {

        String[] traits = VisibleObservationVariableDao.Companion.getVisibleTrait();

        if (!ep.getBoolean("ImportFieldFinished", false) || ep.getInt(PrefsConstants.SELECTED_FIELD_ID, -1) == -1) {
            Utils.makeToast(getApplicationContext(),getString(R.string.warning_field_missing));
            return -1;
        } else if (traits.length == 0) {
            Utils.makeToast(getApplicationContext(),getString(R.string.warning_traits_missing));
            return -1;
        }

        return 1;
    }

    private String getOverwriteFile(String filename) {
        String[] fileArray;
        File dir = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.FIELDEXPORTPATH);
        File[] files = dir.listFiles();
        fileArray = new String[files.length];
        for (int i = 0; i < files.length; ++i) {
            fileArray[i] = files[i].getName();
        }

        if (filename.contains(fFile)) {
            for (String aFileArray : fileArray) {
                if (checkDbBool) {
                    if (aFileArray.contains(fFile) && aFileArray.contains("database")) {
                        File oldFile = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.FIELDEXPORTPATH, aFileArray);
                        File newFile = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.ARCHIVEPATH, aFileArray);
                        oldFile.renameTo(newFile);
                        Utils.scanFile(ConfigActivity.this, oldFile);
                        Utils.scanFile(ConfigActivity.this, newFile);
                    }
                }

                if (checkExcelBool) {
                    if (aFileArray.contains(fFile) && aFileArray.contains("table")) {
                        File oldFile = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.FIELDEXPORTPATH, aFileArray);
                        File newFile = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.ARCHIVEPATH, aFileArray);
                        oldFile.renameTo(newFile);
                        Utils.scanFile(ConfigActivity.this, oldFile);
                        Utils.scanFile(ConfigActivity.this, newFile);
                    }
                }
            }
        }

        return filename;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //TODO change all request codes
        super.onActivityResult(requestCode, resultCode, data);

        // Brapi authentication for exporting fields
        if (requestCode == 5) {

        }

        if (requestCode == 4) {
            if (resultCode == RESULT_OK) {

            }
        }

        if (requestCode == 2) {
            if (resultCode == RESULT_OK) {
                mChosenFile = data.getStringExtra("result");
                mChosenFile = mChosenFile.substring(mChosenFile.lastIndexOf("/") + 1, mChosenFile.length());
                mHandler.post(importDB);
            }
        }
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
        DialogUtils.styleDialogs(alert);
    }

    private void showTipsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this, R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.tutorial_dialog_title));
        builder.setMessage(getString(R.string.tutorial_dialog_description));

        builder.setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Editor ed = ep.edit();
                ed.putBoolean("Tips", true);
                ed.putBoolean("TipsConfigured", true);
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
                ed.putBoolean("TipsConfigured", true);
                ed.apply();

                dialog.dismiss();

                Intent intent = new Intent();
                intent.setClassName(ConfigActivity.this,
                        ConfigActivity.class.getName());
                startActivity(intent);
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
        DialogUtils.styleDialogs(alert);
    }

    private void loadSampleDataDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this, R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.startup_sample_data_title));
        builder.setMessage(getString(R.string.startup_sample_data_message));

        builder.setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Load database with sample data
                mChosenFile = "sample.db";
                mHandler.post(importDB);
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


    /**
     * Scan file to update file list and share exported file
     */
    private void shareFile(File filePath) {
        MediaScannerConnection.scanFile(this, new String[]{filePath.getAbsolutePath()}, null, null);

        if (!ep.getBoolean(GeneralKeys.DISABLE_SHARE, false)) {
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".fileprovider", filePath));
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
        exportArray[1] = getString(R.string.export_source_brapi);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, exportArray);
        exportSourceList.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        builder.setTitle(R.string.export_dialog_title)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });

        final AlertDialog exportDialog = builder.create();
        exportDialog.show();
        DialogUtils.styleDialogs(exportDialog);

        android.view.WindowManager.LayoutParams params = exportDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        exportDialog.getWindow().setAttributes(params);

        exportSourceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                Intent intent = new Intent();
                switch (which) {
                    case 0:
                        exportPermission();
                        break;
                    case 1:
                        exportBrAPI();
                        break;
                }
                exportDialog.dismiss();
            }
        });
    }

    private void exportBrAPI() {
        // Get our active field
        Integer activeFieldId = ep.getInt(PrefsConstants.SELECTED_FIELD_ID, -1);
        FieldObject activeField;
        if (activeFieldId != -1) {
            activeField = dt.getFieldObject(activeFieldId);
        } else {
            activeField = null;
            Toast.makeText(ConfigActivity.this, R.string.warning_field_missing, Toast.LENGTH_LONG).show();
            return;
        }

        // Check that our field is a brapi field
        if (activeField.getExp_source() == null ||
                activeField.getExp_source() == "" ||
                activeField.getExp_source() == "local") {

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

        checkOverwrite.setChecked(ep.getBoolean("Overwrite", false));
        checkDB.setChecked(ep.getBoolean("EXPORT_FORMAT_DATABASE", false));
        checkExcel.setChecked(ep.getBoolean("EXPORT_FORMAT_TABLE", false));
        onlyUnique.setChecked(ep.getBoolean("EXPORT_COLUMNS_UNIQUE",false));
        allColumns.setChecked(ep.getBoolean("EXPORT_COLUMNS_ALL",false));
        allTraits.setChecked(ep.getBoolean("EXPORT_TRAITS_ALL",false));
        activeTraits.setChecked(ep.getBoolean("EXPORT_TRAITS_ACTIVE",false));

        SimpleDateFormat timeStamp = new SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss", Locale.getDefault());

        fFile = ep.getString("FieldFile", "");

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

        builder.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                dialog.dismiss();
            }
        });

        saveDialog = builder.create();
        saveDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        saveDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        saveDialog.show();
        DialogUtils.styleDialogs(saveDialog);

        android.view.WindowManager.LayoutParams params2 = saveDialog.getWindow().getAttributes();
        params2.width = LayoutParams.MATCH_PARENT;
        saveDialog.getWindow().setAttributes(params2);

        // Override positive button so it doesnt automatically dismiss dialog
        Button positiveButton = saveDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (!checkDB.isChecked() & !checkExcel.isChecked()) {
                    Utils.makeToast(getApplicationContext(),getString(R.string.export_error_missing_format));
                    return;
                }

                if (!onlyUnique.isChecked() & !allColumns.isChecked()) {
                    Utils.makeToast(getApplicationContext(),getString(R.string.export_error_missing_column));
                    return;
                }

                if (!activeTraits.isChecked() & !allTraits.isChecked()) {
                    Utils.makeToast(getApplicationContext(),getString(R.string.export_error_missing_trait));
                    return;
                }

                Editor ed = ep.edit();
                ed.putBoolean("EXPORT_COLUMNS_UNIQUE", onlyUnique.isChecked());
                ed.putBoolean("EXPORT_COLUMNS_ALL", allColumns.isChecked());
                ed.putBoolean("EXPORT_TRAITS_ALL", allTraits.isChecked());
                ed.putBoolean("EXPORT_TRAITS_ACTIVE", activeTraits.isChecked());
                ed.putBoolean("EXPORT_FORMAT_TABLE", checkExcel.isChecked());
                ed.putBoolean("EXPORT_FORMAT_DATABASE", checkDB.isChecked());
                ed.putBoolean("Overwrite", checkOverwrite.isChecked());
                ed.apply();

                File file = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.FIELDEXPORTPATH);
                if (!file.exists()) {
                    Utils.createDir(getBaseContext(), ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.FIELDEXPORTPATH);
                }

                newRange = new ArrayList<>();

                if (onlyUnique.isChecked()) {
                    newRange.add(ep.getString("ImportUniqueName", ""));
                }

                if (allColumns.isChecked()) {
                    String[] columns = dt.getRangeColumns();
                    Collections.addAll(newRange, columns);
                }

                exportTrait = new ArrayList<>();

                if (activeTraits.isChecked()) {
                    String[] traits = dt.getVisibleTrait();
                    Collections.addAll(exportTrait, traits);
                }

                if (allTraits.isChecked()) {
                    String[] traits = dt.getAllTraits();
                    Collections.addAll(exportTrait, traits);
                }

                checkDbBool = checkDB.isChecked();
                checkExcelBool = checkExcel.isChecked();

                if (ep.getBoolean("Overwrite", false)) {
                    exportFileString = getOverwriteFile(exportFile.getText().toString());
                } else {
                    exportFileString = exportFile.getText().toString();
                }

                saveDialog.dismiss();
                mHandler.post(exportData);
            }
        });
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_EXPORT_DATA)
    private void exportPermission() {
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            showSaveDialog();
        } else {
            EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_export),
                    PERMISSIONS_REQUEST_EXPORT_DATA, perms);
        }
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_TRAIT_DATA)
    public void collectDataFilePermission() {
        String[] perms = {Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA};
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

    public void makeDirsPermission() {
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            Utils.createDirs(this, null);
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_file_creation),
                    PERMISSIONS_REQUEST_MAKE_DIRS, perms);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(ConfigActivity.this).inflate(R.menu.menu_settings, menu);
        systemMenu = menu;
        systemMenu.findItem(R.id.help).setVisible(ep.getBoolean("Tips", false));
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
        return TapTarget.forBounds(item, title, desc)
                // All options below are optional
                .outerCircleColor(R.color.main_primaryDark)      // Specify a color for the outer circle
                .outerCircleAlpha(0.95f)            // Specify the alpha amount for the outer circle
                .targetCircleColor(R.color.black)   // Specify a color for the target circle
                .titleTextSize(30)                  // Specify the size (in sp) of the title text
                .descriptionTextSize(20)            // Specify the size (in sp) of the description text
                .descriptionTypeface(Typeface.DEFAULT_BOLD)
                .descriptionTextColor(R.color.black)  // Specify the color of the description text
                .textColor(R.color.black)            // Specify a color for both the title and description text
                .dimColor(R.color.black)            // If set, will dim behind the view with 30% opacity of the given color
                .drawShadow(true)                   // Whether to draw a drop shadow or not
                .cancelable(false)                  // Whether tapping outside the outer circle dismisses the view
                .tintTarget(true)                   // Whether to tint the target view's color
                .transparentTarget(true)           // Specify whether the target is transparent (displays the content underneath)
                .targetRadius(60);
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                String state = Environment.getExternalStorageState();
                if (Environment.MEDIA_MOUNTED.equals(state)) {
                    Utils.createDirs(this, null);
                }
            }
        }

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

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    private class ExportDataTask extends AsyncTask<Integer, Integer, Integer> {
        boolean fail;
        boolean noData = false;
        boolean tooManyTraits = false;

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
            String[] newRanges = newRange.toArray(new String[newRange.size()]);
            String[] exportTraits = exportTrait.toArray(new String[exportTrait.size()]);

            // Retrieves the data needed for export
            Cursor exportData = dt.getExportDBData(newRanges, exportTraits);

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

//            if (exportTraits.length > 64) {
//                tooManyTraits = true;
//                return (0);
//            }

            if (checkDbBool) {
                if (exportData.getCount() > 0) {
                    try {
                        File file = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.FIELDEXPORTPATH,
                                exportFileString + "_database.csv");

                        if (file.exists()) {
                            file.delete();
                        }

                        FileWriter fw = new FileWriter(file);
                        CSVWriter csvWriter = new CSVWriter(fw, exportData);
                        csvWriter.writeDatabaseFormat(newRange);

                        System.out.println(exportFileString);
                        shareFile(file);
                    } catch (Exception e) {
                        fail = true;
                    }
                }
            }

            if (checkExcelBool) {
                if (exportData.getCount() > 0) {
                    try {
                        File file = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.FIELDEXPORTPATH,
                                exportFileString + "_table.csv");

                        if (file.exists()) {
                            file.delete();
                        }

                        FileWriter fw = new FileWriter(file);

                        exportData = dt.convertDatabaseToTable(newRanges, exportTraits);
                        CSVWriter csvWriter = new CSVWriter(fw, exportData);

                        csvWriter.writeTableFormat(concat(newRanges, exportTraits), newRanges.length);
                        shareFile(file);
                    } catch (Exception e) {
                        fail = true;
                    }
                }
            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            newRange.clear();

            if (dialog.isShowing()) {
                dialog.dismiss();
            }

            if (!fail) {
                showCitationDialog();
                dt.updateExpTable(false, false, true, ep.getInt(PrefsConstants.SELECTED_FIELD_ID, 0));
            }

            if (fail) {
                Utils.makeToast(getApplicationContext(),getString(R.string.export_error_general));
            }

            if (noData) {
                Utils.makeToast(getApplicationContext(),getString(R.string.export_error_data_missing));
            }

            if (tooManyTraits) {
                //TODO add to strings
                Utils.makeToast(getApplicationContext(),"Unfortunately, an SQLite limitation only allows 64 traits to be exported from Field Book at a time. Select fewer traits to export.");
            }
        }
    }

    private class ImportDBTask extends AsyncTask<Integer, Integer, Integer> {
        boolean fail;
        ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            fail = false;

            dialog = new ProgressDialog(ConfigActivity.this);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html
                    .fromHtml(getString(R.string.import_dialog_importing)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            try {
                dt.importDatabase(mChosenFile);
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
                Utils.makeToast(getApplicationContext(),getString(R.string.import_error_general));
            }

            SharedPreferences prefs = getSharedPreferences("Settings", Context.MODE_MULTI_PROCESS);
            SharedPreferences.Editor editor = prefs.edit();
            editor.apply();

            CollectActivity.reloadData = true;
        }
    }
}