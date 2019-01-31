package com.fieldbook.tracker;

import android.app.Activity;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.io.CSVWriter;
import com.fieldbook.tracker.fields.FieldEditorActivity;
import com.fieldbook.tracker.traits.TraitEditorActivity;
import com.fieldbook.tracker.tutorial.TutorialSettingsActivity;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.utilities.CustomListAdapter;
import com.fieldbook.tracker.utilities.GPSTracker;
import com.fieldbook.tracker.utilities.Utils;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;

/**
 * Settings Screen
 */
public class ConfigActivity extends AppCompatActivity {

    Handler mHandler = new Handler();

    public static boolean helpActive;

    private SharedPreferences ep;

    private AlertDialog personDialog;
    private AlertDialog locationDialog;
    private AlertDialog saveDialog;
    private AlertDialog setupDialog;
    private AlertDialog dbSaveDialog;

    private String mChosenFile = "";

    private ListView setupList;

    String versionName;

    private double lat;
    private double lng;

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

    @Override
    public void onDestroy() {
        try {
            TutorialSettingsActivity.thisActivity.finish();
        } catch (Exception e) {
            Log.e("Field Book", "");
        }

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (systemMenu != null) {
            if (ep.getBoolean(PreferencesActivity.TUTORIAL_MODE, false)) {
                systemMenu.findItem(R.id.help).setVisible(true);
            } else {
                systemMenu.findItem(R.id.help).setVisible(false);
            }
        }

        invalidateOptionsMenu();
        loadScreen();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        invalidateOptionsMenu();
        loadScreen();

        helpActive = false;

        // request permissions
        ActivityCompat.requestPermissions(this, Constants.permissions, Constants.PERM_REQ);

        checkIntent();
    }

    private void loadScreen() {
        setContentView(R.layout.activity_config);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setTitle(null);
        getSupportActionBar().getThemedContext();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        //setup
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list, null);

        builder.setTitle(R.string.profile)
                .setCancelable(true)
                .setView(layout);

        setupDialog = builder.create();

        android.view.WindowManager.LayoutParams params = setupDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        setupDialog.getWindow().setAttributes(params);

        // This is the list of items shown on the settings screen itself
        setupList = layout.findViewById(R.id.myList);
        Button setupCloseBtn = layout.findViewById(R.id.closeBtn);
        setupCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                setupDialog.dismiss();
            }
        });

        ListView settingsList = findViewById(R.id.myList);

        String[] items2 = new String[]{getString(R.string.fields),
                getString(R.string.traits), getString(R.string.profile), getString(R.string.export), getString(R.string.advanced),
                getString(R.string.language)}; //, "API Test"};

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
                        if (!ep.getBoolean("ImportFieldFinished", false)) {
                            makeToast(getString(R.string.importtraitwarning));
                            return;
                        }

                        intent.setClassName(ConfigActivity.this,
                                TraitEditorActivity.class.getName());
                        startActivity(intent);
                        break;
                    case 2:
                        if (!ep.getBoolean("ImportFieldFinished", false)) {
                            makeToast(getString(R.string.nofieldloaded));
                            return;
                        }

                        showSetupDialog();
                        break;
                    case 3:
                        if (!ep.getBoolean("ImportFieldFinished", false)) {
                            makeToast(getString(R.string.nofieldloaded));
                            return;
                        } else if (MainActivity.dt.getTraitColumnsAsString() == null) {
                            makeToast(getString(R.string.notraitloaded));
                            return;
                        }

                        showSaveDialog();
                        break;
                    case 4:
                        intent.setClassName(ConfigActivity.this,
                                PreferencesActivity.class.getName());
                        startActivity(intent);
                        break;
                    case 5:
                        //showLanguageDialog();

                        Intent i = new Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS);
                        startActivity(i);
                        break;
                    //case 6:
                    //    intent.setClassName(ConfigActivity.this,
                    //            BrapiActivity.class.getName());
                    //    startActivity(intent);
                    //    break;
                }
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, items2);
        settingsList.setAdapter(adapter);

        SharedPreferences.Editor ed = ep.edit();

        if (ep.getInt("UpdateVersion", -1) < Utils.getVersion(this)) {
            ed.putInt("UpdateVersion", Utils.getVersion(this));
            ed.apply();
            Intent intent = new Intent();
            intent.setClass(ConfigActivity.this, ChangelogActivity.class);
            startActivity(intent);
        }
        if (!ep.getBoolean("TipsConfigured", false)) {
            ed.putBoolean("TipsConfigured", true);
            ed.apply();
            showTipsDialog();
            loadSampleDataDialog();
        }
    }

    private String getOverwriteFile(String filename) {
        String[] fileArray;
        File dir = new File(Constants.FIELDEXPORTPATH);
        File[] files = dir.listFiles();
        fileArray = new String[files.length];
        for (int i = 0; i < files.length; ++i) {
            fileArray[i] = files[i].getName();
        }

        if (filename.contains(fFile)) {
            for (String aFileArray : fileArray) {
                if (checkDbBool) {
                    if (aFileArray.contains(fFile) && aFileArray.contains("database")) {
                        File oldFile = new File(Constants.FIELDEXPORTPATH, aFileArray);
                        File newFile = new File(Constants.ARCHIVEPATH, aFileArray);
                        oldFile.renameTo(newFile);
                        Utils.scanFile(ConfigActivity.this,oldFile);
                        Utils.scanFile(ConfigActivity.this,newFile);
                    }
                }

                if (checkExcelBool) {
                    if (aFileArray.contains(fFile) && aFileArray.contains("table")) {
                        File oldFile = new File(Constants.FIELDEXPORTPATH, aFileArray);
                        File newFile = new File(Constants.ARCHIVEPATH, aFileArray);
                        oldFile.renameTo(newFile);
                        Utils.scanFile(ConfigActivity.this,oldFile);
                        Utils.scanFile(ConfigActivity.this,newFile);
                    }
                }
            }
        }

        return filename;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //TODO change all request codes
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

        builder.setTitle(getString(R.string.citation_title));
        builder.setMessage(getString(R.string.citation_string) + "\n\n" + getString(R.string.citation_text));
        builder.setCancelable(false);

        builder.setPositiveButton(getString(R.string.okay), new DialogInterface.OnClickListener() {

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

        builder.setTitle(getString(R.string.tutorial));
        builder.setMessage(getString(R.string.tipsdesc));

        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {

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

        builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {

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
    }

    private void loadSampleDataDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this,R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.sampledata));
        builder.setMessage(getString(R.string.loadsampledata));

        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Load database with sample data
                mChosenFile = "sample.db";
                mHandler.post(importDB);
            }
        });

        builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    // Only used for truncating lat long values
    public String truncateDecimalString(String v) {
        int count = 0;

        boolean found = false;

        StringBuilder truncated = new StringBuilder();

        for (int i = 0; i < v.length(); i++) {
            if (found) {
                count += 1;

                if (count == 5)
                    break;
            }

            if (v.charAt(i) == '.') {
                found = true;
            }

            truncated.append(v.charAt(i));
        }

        return truncated.toString();
    }

    private void showAboutDialog() {
        final PackageManager packageManager = this.getPackageManager();

        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(this.getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_about, null);

        builder.setTitle(R.string.about)
                .setCancelable(true)
                .setView(layout);

        final AlertDialog aboutDialog = builder.create();

        android.view.WindowManager.LayoutParams langParams = aboutDialog.getWindow().getAttributes();
        langParams.width = LayoutParams.MATCH_PARENT;
        aboutDialog.getWindow().setAttributes(langParams);

        TextView versionText = (TextView) layout.findViewById(R.id.tvVersion);
        versionText.setText(getString(R.string.version) + " " + versionName);

        TextView otherApps = layout.findViewById(R.id.tvOtherApps);

        versionText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(ConfigActivity.this, ChangelogActivity.class);
                startActivity(intent);
            }
        });

        otherApps.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showOtherAppsDialog();
            }
        });

        Button closeBtn = layout.findViewById(R.id.closeBtn);

        closeBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                aboutDialog.dismiss();
            }
        });

        aboutDialog.show();
    }

    private void showOtherAppsDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list, null);

        builder.setTitle(R.string.otherapps)
                .setCancelable(true)
                .setView(layout);

        final AlertDialog otherAppsDialog = builder.create();

        android.view.WindowManager.LayoutParams params = otherAppsDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        otherAppsDialog.getWindow().setAttributes(params);

        ListView myList = layout.findViewById(R.id.myList);

        String[] appsArray = new String[3];

        appsArray[0] = "Inventory";
        appsArray[1] = "Coordinate";
        appsArray[2] = "1KK";

        Integer app_images[] = {R.drawable.other_ic_inventory, R.drawable.other_ic_coordinate, R.drawable.other_ic_1kk};
        final String[] links = {"https://play.google.com/store/apps/details?id=org.wheatgenetics.inventory",
                "https://play.google.com/store/apps/details?id=org.wheatgenetics.coordinate",
                "https://play.google.com/store/apps/details?id=org.wheatgenetics.onekk"};

        myList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                Uri uri = Uri.parse(links[which]);
                Intent intent;

                switch (which) {
                    case 0:
                        intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                        break;
                    case 1:
                        intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                        break;
                    case 2:
                        intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                        break;
                }
            }
        });

        CustomListAdapter adapterImg = new CustomListAdapter(this, app_images, appsArray);
        myList.setAdapter(adapterImg);

        Button langCloseBtn = layout.findViewById(R.id.closeBtn);

        langCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                otherAppsDialog.dismiss();
            }
        });

        otherAppsDialog.show();
    }

    private Runnable exportData = new Runnable() {
        public void run() {
            new ExportDataTask().execute(0);
        }
    };

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
                    .fromHtml(getString(R.string.exportmsg)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            String[] newRanges = newRange.toArray(new String[newRange.size()]);
            String[] exportTraits = exportTrait.toArray(new String[exportTrait.size()]);

            // Retrieves the data needed for export
            Cursor exportData = MainActivity.dt.getExportDBData(newRanges, exportTraits);

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

            if (exportTraits.length > 64) {
                tooManyTraits = true;
                return(0);
            }

            if (checkDbBool) {
                if (exportData.getCount() > 0) {
                    try {
                        File file = new File(Constants.FIELDEXPORTPATH,
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
                        File file = new File(Constants.FIELDEXPORTPATH,
                                exportFileString + "_table.csv");

                        if (file.exists()) {
                            file.delete();
                        }

                        FileWriter fw = new FileWriter(file);

                        exportData = MainActivity.dt.convertDatabaseToTable(newRanges, exportTraits);
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
                MainActivity.dt.updateExpTable(false,false,true,ep.getInt("ExpID", 0));
            }

            if (fail) {
                makeToast(getString(R.string.exporterror));
            }

            if (noData) {
                makeToast(getString(R.string.exporttraiterror));
            }

            if (tooManyTraits) {
                makeToast("Unfortunately, an SQLite limitation only allows 64 traits to be exported from Field Book at a time. Select fewer traits to export.");
            }
        }
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

            dialog = new ProgressDialog(ConfigActivity.this);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html
                    .fromHtml(getString(R.string.exportmsg)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            try {
                MainActivity.dt.exportDatabase(exportFileString);
            } catch (Exception e) {
                e.printStackTrace();
                error = "" + e.getMessage();
                fail = true;
            }

            File exportedDb = new File(Constants.BACKUPPATH + "/" + exportFileString + ".db");
            File exportedSp = new File(Constants.BACKUPPATH + "/" + exportFileString + ".db_sharedpref.xml");

            Utils.scanFile(ConfigActivity.this,exportedDb);
            Utils.scanFile(ConfigActivity.this,exportedSp);

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }

            if (fail) {
                makeToast(getString(R.string.exporterror));
            } else {
                makeToast(getString(R.string.exportcomplete));
            }

        }
    }

    private Runnable importDB = new Runnable() {
        public void run() {
            new ImportDBTask().execute(0);
        }
    };

    private class ImportDBTask extends AsyncTask<Integer, Integer, Integer> {
        boolean fail;
        ProgressDialog dialog;
        String error;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            fail = false;

            dialog = new ProgressDialog(ConfigActivity.this);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html
                    .fromHtml(getString(R.string.importmsg)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            try {
                MainActivity.dt.importDatabase(mChosenFile);
            } catch (Exception e) {
                e.printStackTrace();
                error = "" + e.getMessage();
                fail = true;
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (dialog.isShowing())
                dialog.dismiss();

            if (fail) {
                makeToast(getString(R.string.importerror));
            }

            SharedPreferences prefs = getSharedPreferences("Settings", Context.MODE_MULTI_PROCESS);
            SharedPreferences.Editor editor = prefs.edit();
            editor.apply();

            MainActivity.reloadData = true;
        }
    }

    /**
     * Scan file to update file list and share exported file
     */
    private void shareFile(File filePath) {
        MediaScannerConnection.scanFile(this, new String[]{filePath.getAbsolutePath()}, null, null);

        if (!ep.getBoolean(PreferencesActivity.DISABLE_SHARE, false)) {
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(filePath));
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

    public void makeToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void showAdvancedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_advanced_settings, null);

        builder.setTitle(R.string.advanced)
                .setCancelable(true)
                .setView(layout);

        advancedDialog = builder.create();

        android.view.WindowManager.LayoutParams params = advancedDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        advancedDialog.getWindow().setAttributes(params);

        CheckBox tips = (CheckBox) layout.findViewById(R.id.tipsCheckbox);
        CheckBox cycle = (CheckBox) layout.findViewById(R.id.cycleTraitsCheckbox);
        CheckBox ignoreEntries = (CheckBox) layout.findViewById(R.id.ignoreExistingCheckbox);
        CheckBox useDay = (CheckBox) layout.findViewById(R.id.useDayCheckbox);
        CheckBox rangeSound = (CheckBox) layout.findViewById(R.id.rangeSoundCheckbox);
        CheckBox jumpToPlot = (CheckBox) layout.findViewById(R.id.jumpToPlotCheckbox);
        CheckBox barcodeScan = (CheckBox) layout.findViewById(R.id.barcodeScanCheckbox);
        CheckBox printLabel = (CheckBox) layout.findViewById(R.id.printLabelCheckbox);
        CheckBox nextEmptyPlot = (CheckBox) layout.findViewById(R.id.nextEmptyPlotCheckbox);
        CheckBox quickGoTo = (CheckBox) layout.findViewById(R.id.quickGoToCheckbox);
        CheckBox disableShare = (CheckBox) layout.findViewById(R.id.disableShareCheckbox);
        CheckBox disableEntryNavLeft = (CheckBox) layout.findViewById(R.id.disableEntryNavLeftCheckbox);
        CheckBox disableEntryNavRight = (CheckBox) layout.findViewById(R.id.disableEntryNavRightCheckbox);
        CheckBox dataGrid = (CheckBox) layout.findViewById(R.id.dataGridCheckbox);

        Button advCloseBtn = (Button) layout.findViewById(R.id.closeBtn);

        advCloseBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                advancedDialog.dismiss();
            }
        });

        // Set default values for advanced settings
        tips.setChecked(ep.getBoolean("Tips", false));
        cycle.setChecked(ep.getBoolean("CycleTraits", false));
        ignoreEntries.setChecked(ep.getBoolean("IgnoreExisting", false));
        useDay.setChecked(ep.getBoolean("UseDay", false));
        rangeSound.setChecked(ep.getBoolean("RangeSound", false));
        jumpToPlot.setChecked(ep.getBoolean("JumpToPlot", false));
        barcodeScan.setChecked(ep.getBoolean("BarcodeScan", false));
        nextEmptyPlot.setChecked(ep.getBoolean("NextEmptyPlot", false));
        quickGoTo.setChecked(ep.getBoolean("QuickGoTo", false));
        disableShare.setChecked(ep.getBoolean("DisableShare", false));
        disableEntryNavLeft.setChecked(ep.getBoolean("DisableEntryNavLeft", false));
        disableEntryNavRight.setChecked(ep.getBoolean("DisableEntryNavRight", false));
        dataGrid.setChecked(ep.getBoolean("DataGrid", false));

        tips.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("Tips", checked);
                e.apply();
                invalidateOptionsMenu();
                MainActivity.reloadData = true;
            }
        });

        cycle.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("CycleTraits", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        ignoreEntries.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("IgnoreExisting", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        useDay.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("UseDay", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        rangeSound.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("RangeSound", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        jumpToPlot.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("JumpToPlot", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        nextEmptyPlot.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton arg0, boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("NextEmptyPlot", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        quickGoTo.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("QuickGoTo", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        disableShare.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("DisableShare", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        disableEntryNavLeft.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("DisableEntryNavLeft", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        disableEntryNavRight.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("DisableEntryNavRight", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        barcodeScan.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("BarcodeScan", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        printLabel.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("PrintLabel", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        dataGrid.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("DataGrid", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        advancedDialog.show();
    }

    private void showLanguageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list, null);

        builder.setTitle(R.string.language)
                .setCancelable(true)
                .setView(layout);

        final AlertDialog languageDialog = builder.create();

        final float scale = this.getResources().getDisplayMetrics().density;
        int pixels = (int) (500 * scale + 0.5f);

        android.view.WindowManager.LayoutParams params = languageDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        languageDialog.getWindow().setAttributes(params);

        ListView myList = (ListView) layout
                .findViewById(R.id.myList);

        ViewGroup.LayoutParams params2 = myList.getLayoutParams();
        params2.height = pixels;
        myList.setLayoutParams(params2);

        region = "";
        String[] langArray = new String[14];

        langArray[0] = getString(R.string.english);
        langArray[1] = getString(R.string.spanish);
        langArray[2] = getString(R.string.french);
        langArray[3] = getString(R.string.hindi);
        langArray[4] = getString(R.string.german);
        langArray[5] = getString(R.string.japanese);
        langArray[6] = getString(R.string.arabic);
        langArray[7] = getString(R.string.chinese);
        langArray[8] = getString(R.string.portuguesebr);
        langArray[9] = getString(R.string.russian);
        langArray[10] = getString(R.string.oromo);
        langArray[11] = getString(R.string.amharic);
        langArray[12] = getString(R.string.bengali);
        langArray[13] = getString(R.string.italian);

        Integer image_id[] = {R.drawable.locale_ic_us, R.drawable.locale_ic_mx, R.drawable.locale_ic_fr, R.drawable.locale_ic_in,
                R.drawable.locale_ic_de, R.drawable.locale_ic_jp, R.drawable.locale_ic_ar, R.drawable.locale_ic_cn, R.drawable.locale_ic_br,
                R.drawable.locale_ic_ru, R.drawable.locale_ic_et, R.drawable.locale_ic_et, R.drawable.locale_ic_bn, R.drawable.locale_ic_it};

        myList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                switch (which) {
                    case 0:
                        local = "en";
                        break;
                    case 1:
                        local = "es";
                        break;
                    case 2:
                        local = "fr";
                        break;
                    case 3:
                        local = "hi";
                        break;
                    case 4:
                        local = "de";
                        break;
                    case 5:
                        local = "ja";
                        break;
                    case 6:
                        local = "ar";
                        break;
                    case 7:
                        local = "zh";
                        region = "CN";
                        break;
                    case 8:
                        local = "pt";
                        region = "BR";
                        break;
                    case 9:
                        local = "ru";
                        break;
                    case 10:
                        local = "om";
                        region = "ET";
                        break;
                    case 11:
                        local = "am";
                        break;
                    case 12:
                        local = "bn";
                        break;
                    case 13:
                        local = "it";
                        break;
                }
                Editor ed = ep.edit();
                ed.putString("language", local);
                ed.putString("region", region);
                ed.apply();

                updateLanguage(local, region);
                invalidateOptionsMenu();
                loadScreen();

                languageChange = true;

                languageDialog.dismiss();
            }
        });

        CustomListAdapter adapterImg = new CustomListAdapter(this, image_id, langArray);
        myList.setAdapter(adapterImg);

        Button langCloseBtn = (Button) layout.findViewById(R.id.closeBtn);

        langCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                languageDialog.dismiss();
            }
        });

        languageDialog.show();
    }

    private void updateLanguage(String loc, String reg) {
        Locale locale2 = new Locale(loc, reg);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().
                updateConfiguration(config2, getBaseContext().getResources()
                        .getDisplayMetrics());
    }

    private class CustomListAdapter extends ArrayAdapter<String> {
        String[] color_names;
        Integer[] image_id;
        Context context;

        CustomListAdapter(Activity context, Integer[] image_id, String[] text) {
            super(context, R.layout.listitem_language, text);
            this.color_names = text;
            this.image_id = image_id;
            this.context = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View single_row = inflater.inflate(R.layout.listitem_language, null, true);
            TextView textView = (TextView) single_row.findViewById(R.id.txt);
            ImageView imageView = (ImageView) single_row.findViewById(R.id.img);
            textView.setText(color_names[position]);
            imageView.setImageResource(image_id[position]);
            return single_row;
        }
    }

    private String[] prepareSetup() {
        String tagName = "";
        String tagLocation = "";

        if (ep.getString("FirstName", "").length() > 0 | ep.getString("LastName", "").length() > 0) {
            tagName += getString(R.string.person) + ": " + ep.getString("FirstName", "")
                    + " " + ep.getString("LastName", "");
        } else {
            tagName += getString(R.string.person) + ": " + getString(R.string.none);
        }

        if (ep.getString("Location", "").length() > 0) {
            tagLocation += getString(R.string.location) + ": " + ep.getString("Location", "");
        } else {
            tagLocation += getString(R.string.location) + ": " + getString(R.string.none);
        }

        return new String[]{tagName, tagLocation, getString(R.string.clearsettings)};
    }

    private void updateSetupList() {
        ArrayAdapter<String> ga = (ArrayAdapter) setupList.getAdapter();

        if (ga != null) {
            ga.clear();
        }

        String[] arrayData = prepareSetup();

        if (arrayData != null) {
            for (String string : arrayData) {
                ga.insert(string, ga.getCount());
            }
        }

        ga.notifyDataSetChanged();
    }

    private void showSaveDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_export, null);

        builder.setTitle(R.string.export)
                .setCancelable(true)
                .setView(layout);

        saveDialog = builder.create();

        android.view.WindowManager.LayoutParams params2 = saveDialog.getWindow().getAttributes();
        params2.width = LayoutParams.MATCH_PARENT;
        saveDialog.getWindow().setAttributes(params2);


        Button closeBtn = layout.findViewById(R.id.closeBtn);

        closeBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                saveDialog.dismiss();
            }
        });

        exportFile = layout.findViewById(R.id.fileName);
        checkDB = layout.findViewById(R.id.formatDB);
        checkExcel = layout.findViewById(R.id.formatExcel);
        CheckBox checkOverwrite = layout.findViewById(R.id.overwrite);

        if (ep.getBoolean("Overwrite", false) ) {
            checkOverwrite.setChecked(true);
        }

        checkOverwrite.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    Editor ed = ep.edit();
                    ed.putBoolean("Overwrite", true);
                    ed.apply();
                } else {
                    Editor ed = ep.edit();
                    ed.putBoolean("Overwrite", false);
                    ed.apply();
                }
            }
        });

        allColumns = layout.findViewById(R.id.allColumns);
        onlyUnique = layout.findViewById(R.id.onlyUnique);
        allTraits = layout.findViewById(R.id.allTraits);
        activeTraits = layout.findViewById(R.id.activeTraits);

        Button exportButton = layout.findViewById(R.id.saveBtn);

        exportButton.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                // Ensure at least one export type is checked
                if (!checkDB.isChecked() & !checkExcel.isChecked()) {
                    makeToast(getString(R.string.noexportcheck));
                    return;
                }

                if (!onlyUnique.isChecked() & !allColumns.isChecked()) {
                    makeToast(getString(R.string.nofieldcheck));
                    return;
                }

                if (!activeTraits.isChecked() & !allTraits.isChecked()) {
                    makeToast(getString(R.string.notraitcheck));
                    return;
                }

                newRange = new ArrayList<>();

                if (onlyUnique.isChecked()) {
                    newRange.add(ep.getString("ImportUniqueName", ""));
                }

                if (allColumns.isChecked()) {
                    String[] columns = MainActivity.dt.getRangeColumns();
                    Collections.addAll(newRange, columns);
                }

                exportTrait = new ArrayList<>();

                if (activeTraits.isChecked()) {
                    String[] traits = MainActivity.dt.getVisibleTrait();
                    Collections.addAll(exportTrait, traits);
                }

                if (allTraits.isChecked()) {
                    String[] traits = MainActivity.dt.getAllTraits();
                    Collections.addAll(exportTrait, traits);
                }

                checkDbBool = checkDB.isChecked();
                checkExcelBool = checkExcel.isChecked();

                if (ep.getBoolean("Overwrite", false) ) {
                    exportFileString = getOverwriteFile(exportFile.getText().toString());
                } else {
                    exportFileString = exportFile.getText().toString();
                }

                saveDialog.dismiss();
                mHandler.post(exportData);
            }
        });

        SimpleDateFormat timeStamp = new SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss", Locale.getDefault());

        fFile = ep.getString("FieldFile", "");

        if (fFile.length() > 4 & fFile.toLowerCase().endsWith(".csv")) {
            fFile = fFile.substring(0, fFile.length() - 4);
        }

        exportFile.setText(timeStamp.format(Calendar.getInstance().getTime()) + "_" + fFile);

        saveDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        saveDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        saveDialog.show();
    }

    private void showSetupDialog() {
        String[] array = prepareSetup();
        ArrayList<String> lst = new ArrayList<>(Arrays.asList(array));

        setupList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                switch (which) {
                    case 0:
                        showPersonDialog();
                        break;

                    case 1:
                        showLocationDialog();
                        break;

                    case 2:
                        showClearSettingsDialog();
                        break;

                }
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, lst);

        setupList.setAdapter(adapter);
        setupDialog.show();
    }

    private void showPersonDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_person, null);

        builder.setTitle(R.string.personsetup)
                .setCancelable(true)
                .setView(layout);

        personDialog = builder.create();

        android.view.WindowManager.LayoutParams langParams = personDialog.getWindow().getAttributes();
        langParams.width = LayoutParams.MATCH_PARENT;
        personDialog.getWindow().setAttributes(langParams);

        final EditText firstName = layout.findViewById(R.id.firstName);
        final EditText lastName = layout.findViewById(R.id.lastName);

        firstName.setText(ep.getString("FirstName",""));
        lastName.setText(ep.getString("LastName",""));

        firstName.setSelectAllOnFocus(true);
        lastName.setSelectAllOnFocus(true);

        Button yesButton = layout.findViewById(R.id.saveBtn);

        yesButton.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                Editor e = ep.edit();

                e.putString("FirstName", firstName.getText().toString());
                e.putString("LastName", lastName.getText().toString());

                e.apply();

                if (setupDialog.isShowing()) {
                    updateSetupList();
                }

                personDialog.dismiss();
            }
        });
        personDialog.show();
    }

    private void showLocationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_location, null);

        builder.setTitle(R.string.locationsetup)
                .setCancelable(true)
                .setView(layout);

        locationDialog = builder.create();

        android.view.WindowManager.LayoutParams langParams = locationDialog.getWindow().getAttributes();
        langParams.width = LayoutParams.MATCH_PARENT;
        locationDialog.getWindow().setAttributes(langParams);

        GPSTracker gps = new GPSTracker(this);
        if (gps.canGetLocation()) { //GPS enabled
            lat = gps.getLatitude(); // returns latitude
            lng = gps.getLongitude(); // returns longitude
        } else {
            Intent intent = new Intent(
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        Button findLocation = layout
                .findViewById(R.id.getLctnBtn);
        Button yesLocation = layout.findViewById(R.id.saveBtn);

        final EditText longitude = layout
                .findViewById(R.id.longitude);
        final EditText latitude = layout
                .findViewById(R.id.latitude);

        longitude.setText(ep.getString("Longitude", ""));
        latitude.setText(ep.getString("Latitude", ""));

        findLocation.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                latitude.setText(truncateDecimalString(String.valueOf(lat)));
                longitude.setText(truncateDecimalString(String.valueOf(lng)));
            }
        });

        yesLocation.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Editor e = ep.edit();
                if (latitude.getText().toString().length() > 0 && longitude.getText().toString().length() > 0) {
                    e.putString("Location", latitude.getText().toString() + " ; " + longitude.getText().toString());
                    e.putString("Latitude",latitude.getText().toString());
                    e.putString("Longitude",longitude.getText().toString());
                } else {
                    e.putString("Location", "null");
                }

                e.apply();
                if (setupDialog.isShowing()) {
                    updateSetupList();
                }
                locationDialog.dismiss();
            }
        });

        locationDialog.show();
    }

    private void showClearSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this, R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.clearsettings));
        builder.setMessage(getString(R.string.areyousure));

        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                setupDialog.dismiss();
                dialog.dismiss();

                Editor ed = ep.edit();
                ed.putString("FirstName", "");
                ed.putString("LastName", "");
                ed.putString("Location", "");
                ed.apply();
            }
        });

        builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void checkIntent() {
        Bundle extras = getIntent().getExtras();
        String dialog = "";

        if (extras != null) {
            dialog = extras.getString("dialog");
        }

        if (dialog != null) {
            if (dialog.equals("person")) {
                showPersonDialog();
            }

            if (dialog.equals("location")) {
                showLocationDialog();
            }
        }
    }

    private void showDatabaseDialog() {
        String[] items = new String[3];
        items[0] = getString(R.string.dbexport);
        items[1] = getString(R.string.dbimport);
        items[2] = getString(R.string.dbreset);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list, null);

        builder.setTitle(R.string.dbbackup)
                .setCancelable(true)
                .setView(layout);

        final AlertDialog chooseBackupDialog = builder.create();

        android.view.WindowManager.LayoutParams params = chooseBackupDialog.getWindow().getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        chooseBackupDialog.getWindow().setAttributes(params);

        setupList = layout.findViewById(R.id.myList);
        Button setupCloseBtn = layout.findViewById(R.id.closeBtn);

        setupCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                chooseBackupDialog.dismiss();
            }
        });

        setupList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                chooseBackupDialog.dismiss();
                switch (which) {
                    case 0:
                        showDatabaseExportDialog();
                        break;
                    case 1:
                        showDatabaseImportDialog();
                        break;
                    case 2:
                        showDatabaseResetDialog1();
                        break;
                }
            }


        });

        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(this, R.layout.listitem, items);
        setupList.setAdapter(itemsAdapter);
        chooseBackupDialog.show();
    }

    private void showDatabaseImportDialog() {
        Intent intent = new Intent();

        intent.setClassName(ConfigActivity.this,
                FileExploreActivity.class.getName());
        intent.putExtra("path", Constants.BACKUPPATH);
        intent.putExtra("include", new String[]{"db"});
        intent.putExtra("title",getString(R.string.dbimport));
        startActivityForResult(intent, 2);
    }

    private void showDatabaseExportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_save_database, null);

        builder.setTitle(R.string.dbbackup)
                .setCancelable(true)
                .setView(layout);

        dbSaveDialog = builder.create();

        android.view.WindowManager.LayoutParams params2 = dbSaveDialog.getWindow().getAttributes();
        params2.width = LayoutParams.MATCH_PARENT;
        dbSaveDialog.getWindow().setAttributes(params2);

        exportFile = layout.findViewById(R.id.fileName);

        Button closeBtn = layout.findViewById(R.id.closeBtn);

        closeBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                dbSaveDialog.dismiss();
            }
        });

        Button exportButton = layout.findViewById(R.id.saveBtn);

        exportButton.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                dbSaveDialog.dismiss();
                exportFileString = exportFile.getText().toString();
                mHandler.post(exportDB);
            }
        });

        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", Locale.getDefault());

        exportFile.setText(timeStamp.format(Calendar.getInstance().getTime()) + "_" + "systemdb" + DataHelper.DATABASE_VERSION);
        dbSaveDialog.show();
    }

    // First confirmation
    private void showDatabaseResetDialog1() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this, R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.warning));
        builder.setMessage(getString(R.string.resetwarning1));

        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                showDatabaseResetDialog2();
            }

        });

        builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        });

        AlertDialog alert = builder.create();
        alert.show();

    }

    // Second confirmation
    private void showDatabaseResetDialog2() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this, R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.warning));
        builder.setMessage(getString(R.string.resetwarning2));

        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                // Delete database
                MainActivity.dt.deleteDatabase();

                // Clear all existing settings
                Editor ed = ep.edit();
                ed.clear();
                ed.apply();

                dialog.dismiss();
                makeToast(getString(R.string.resetcomplete));

                try {
                    ConfigActivity.this.finish();
                } catch (Exception e) {
                    Log.e("Field Book", "" + e.getMessage());
                }

                try {
                    MainActivity.thisActivity.finish();
                } catch (Exception e) {
                    Log.e("Field Book", "" + e.getMessage());
                }
            }

        });

        builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(ConfigActivity.this).inflate(R.menu.menu_settings, menu);

        systemMenu = menu;

        if (systemMenu != null) {

            if (ep.getBoolean(PreferencesActivity.TUTORIAL_MODE, false)) {
                systemMenu.findItem(R.id.help).setVisible(true);
            } else {
                systemMenu.findItem(R.id.help).setVisible(false);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = new Intent(Intent.ACTION_VIEW);

        switch (item.getItemId()) {
            case R.id.help:
                intent.setClassName(ConfigActivity.this,
                        TutorialSettingsActivity.class.getName());
                startActivity(intent);
                break;

            case R.id.resources:
                intent.setClassName(ConfigActivity.this,
                        FileExploreActivity.class.getName());
                startActivity(intent);
                break;

            case R.id.about:
                showAboutDialog();
                break;

            case R.id.database:
                showDatabaseDialog();
                break;

            case android.R.id.home:
                if (!ep.getBoolean("ImportFieldFinished", false)) {
                    makeToast(getString(R.string.nofieldloaded));
                } else if (MainActivity.dt.getTraitColumnsAsString() == null) {
                    makeToast(getString(R.string.notraitloaded));
                } else
                    finish();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (!ep.getBoolean("ImportFieldFinished", false)) {
            makeToast(getString(R.string.nofieldloaded));
        } else if (MainActivity.dt.getTraitColumnsAsString() == null) {
            makeToast(getString(R.string.notraitloaded));
        } else {
            finish();
        }
    }
}
