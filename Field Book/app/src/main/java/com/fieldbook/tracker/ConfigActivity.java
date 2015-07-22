package com.fieldbook.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.fieldbook.tracker.Trait.TraitEditorActivity;
import com.fieldbook.tracker.Tutorial.TutorialSettingsActivity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import jxl.Workbook;
import jxl.WorkbookSettings;

/**
 * Settings Screen
 */
public class ConfigActivity extends Activity {

    Handler mHandler = new Handler();

    private static final int DIALOG_LOAD_FIELDFILECSV = 1000;
    private static final int DIALOG_LOAD_FIELDFILEEXCEL = 1001;

    public static boolean helpActive;
    public static Activity thisActivity;

    private SharedPreferences ep;

    private Dialog personDialog;
    private Dialog locationDialog;
    private Dialog saveDialog;

    private Dialog fieldDialog2;

    private Dialog advancedDialog;

    private Dialog setupDialog;

    private Dialog importFieldDialog;

    private Dialog dbSaveDialog;

    private String[] importColumns;

    private String mChosenFile = "";

    private ListView setupList;

    String versionName;

    private double lat;
    private double lng;

    private EditText exportFile;
    private String exportFileString = "";

    private CheckBox checkDB;
    private CheckBox checkExcel;
    private Boolean checkDbBool = false;
    private Boolean checkExcelBool = false;

    private boolean isCSV;
    private int idColPosition;
    public static boolean languageChange = false;

    private Workbook wb;

    Spinner unique;
    Spinner primary;
    Spinner secondary;

    private RadioButton onlyUnique;
    private RadioButton allColumns;
    private RadioButton allTraits;
    private RadioButton activeTraits;

    private int action;

    private boolean columnFail;

    private ArrayList<String> newRange;
    private ArrayList<String> exportTrait;

    private String local;
    private String region;

    private Menu systemMenu;

    @Override
    public void onDestroy() {
        try {
            TutorialSettingsActivity.thisActivity.finish();
        } catch (Exception e) {
            Log.e("Field Book","");
        }

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (systemMenu != null) {
            if (ep.getBoolean("Tips", false)) {
                systemMenu.findItem(R.id.help).setVisible(true);
            } else {
                systemMenu.findItem(R.id.help).setVisible(false);
            }
        }
        //Device default language : Locale.getDefault().getLanguage()
        // This allows dynamic language change without exiting the app
        local = ep.getString("language", Locale.getDefault().toString());
        region = ep.getString("region", "");
        Locale locale2 = new Locale(local, region);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2,
                getBaseContext().getResources().getDisplayMetrics());
        invalidateOptionsMenu();
        loadScreen();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        // Enforce internal language change
        local = "en";
        thisActivity = this;

        local = ep.getString("language", Locale.getDefault().toString());
        Locale locale2 = new Locale(local);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2,
                getBaseContext().getResources().getDisplayMetrics());

        invalidateOptionsMenu();
        loadScreen();

        helpActive = false;
    }

    private void loadScreen() {
        if(getActionBar()!=null) {
            getActionBar().setHomeButtonEnabled(true);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        FrameLayout mFrame = new FrameLayout(this);
        mFrame.addView(LayoutInflater.from(getBaseContext()).inflate(R.layout.config, null));
        setContentView(mFrame);

        // Advanced Settings
        advancedDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        advancedDialog.setTitle(getString(R.string.advanced));
        advancedDialog.setContentView(R.layout.advanced);

        android.view.WindowManager.LayoutParams params = advancedDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        advancedDialog.getWindow().setAttributes(params);

        advancedDialog.setCancelable(true);
        advancedDialog.setCanceledOnTouchOutside(true);

        CheckBox tips = (CheckBox) advancedDialog.findViewById(R.id.tips);
        CheckBox cycle = (CheckBox) advancedDialog.findViewById(R.id.cycle);
        CheckBox ignoreEntries = (CheckBox) advancedDialog.findViewById(R.id.ignoreExisting);
        CheckBox useDay = (CheckBox) advancedDialog.findViewById(R.id.useDay);
        CheckBox rangeSound = (CheckBox) advancedDialog.findViewById(R.id.rangeSound);
        CheckBox jumpToPlot = (CheckBox) advancedDialog.findViewById(R.id.jumpToPlot);
        CheckBox barcodeScan = (CheckBox) advancedDialog.findViewById(R.id.barcodeScan);
        CheckBox nextEmptyPlot = (CheckBox) advancedDialog.findViewById(R.id.nextEmptyPlot);
        CheckBox quickGoTo = (CheckBox) advancedDialog.findViewById(R.id.quickGoTo);
        CheckBox disableShare = (CheckBox) advancedDialog.findViewById(R.id.disableShare);
        CheckBox disableEntryNavLeft = (CheckBox) advancedDialog.findViewById(R.id.disableEntryNavLeft);
        CheckBox disableEntryNavRight = (CheckBox) advancedDialog.findViewById(R.id.disableEntryNavRight);
        CheckBox dataGrid = (CheckBox) advancedDialog.findViewById(R.id.dataGrid);

        Button advCloseBtn = (Button) advancedDialog.findViewById(R.id.closeBtn);

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

        dataGrid.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton arg0,
                                         boolean checked) {
                Editor e = ep.edit();
                e.putBoolean("DataGrid", checked);
                e.apply();
                MainActivity.reloadData = true;
            }
        });

        // Export Field book
        saveDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        saveDialog.setTitle(getString(R.string.export));
        saveDialog.setContentView(R.layout.savefile);

        android.view.WindowManager.LayoutParams params2 = saveDialog.getWindow().getAttributes();
        params2.width = LayoutParams.MATCH_PARENT;
        saveDialog.getWindow().setAttributes(params2);

        saveDialog.setCancelable(true);
        saveDialog.setCanceledOnTouchOutside(true);

        Button closeBtn = (Button) saveDialog.findViewById(R.id.closeBtn);

        closeBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                saveDialog.dismiss();
            }
        });

        exportFile = (EditText) saveDialog.findViewById(R.id.fileName);

        checkDB = (CheckBox) saveDialog.findViewById(R.id.formatDB);
        checkExcel = (CheckBox) saveDialog.findViewById(R.id.formatExcel);

        allColumns = (RadioButton) saveDialog.findViewById(R.id.allColumns);
        onlyUnique = (RadioButton) saveDialog.findViewById(R.id.onlyUnique);
        allTraits = (RadioButton) saveDialog.findViewById(R.id.allTraits);
        activeTraits = (RadioButton) saveDialog.findViewById(R.id.activeTraits);

        Button exportButton = (Button) saveDialog.findViewById(R.id.saveBtn);

        exportButton.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                // Ensure at least one export type is checked
                if (!checkDB.isChecked() & !checkExcel.isChecked()) {
                    Toast.makeText(ConfigActivity.this, getString(R.string.noexportcheck),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                if(!onlyUnique.isChecked() & !allColumns.isChecked()) {
                    Toast.makeText(ConfigActivity.this, getString(R.string.nofieldcheck),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                if(!activeTraits.isChecked() & !allTraits.isChecked()) {
                    Toast.makeText(ConfigActivity.this, getString(R.string.notraitcheck),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                newRange = new ArrayList<>();

                if(onlyUnique.isChecked()) {
                    newRange.add(ep.getString("ImportUniqueName", ""));
                }

                if(allColumns.isChecked()) {
                    String[] columns = MainActivity.dt.getRangeColumns();
                    Collections.addAll(newRange, columns);
                }

                exportTrait = new ArrayList<>();

                if(activeTraits.isChecked()) {
                    String[] traits = MainActivity.dt.getVisibleTrait();
                    Collections.addAll(exportTrait, traits);
                }

                if(allTraits.isChecked()) {
                    String[] traits = MainActivity.dt.getAllTraits();
                    Collections.addAll(exportTrait, traits);
                }

                checkDbBool = checkDB.isChecked();
                checkExcelBool = checkExcel.isChecked();
                exportFileString = exportFile.getText().toString();

                saveDialog.dismiss();
                mHandler.post(exportData);
            }
        });

        //setup
        setupDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        setupDialog.setTitle(getString(R.string.setup));
        setupDialog.setContentView(R.layout.config);

        params = setupDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        setupDialog.getWindow().setAttributes(params);

        setupDialog.setCancelable(true);
        setupDialog.setCanceledOnTouchOutside(true);

        // This is the list of items shown on the settings screen itself
        setupList = (ListView) setupDialog.findViewById(R.id.myList);

        Button setupCloseBtn = (Button) setupDialog.findViewById(R.id.closeBtn);

        setupCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                setupDialog.dismiss();
            }
        });

        // To configure first name, last name
        personDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        personDialog.setTitle(getString(R.string.personsetup));
        personDialog.setContentView(R.layout.person);

        personDialog.setCancelable(true);
        personDialog.setCanceledOnTouchOutside(true);

        final EditText firstName = (EditText) personDialog
                .findViewById(R.id.firstName);
        final EditText lastName = (EditText) personDialog
                .findViewById(R.id.lastName);

        Button yesButton = (Button) personDialog.findViewById(R.id.saveBtn);

        yesButton.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                Editor e = ep.edit();

                e.putString("FirstName", firstName.getText().toString());
                e.putString("LastName", lastName.getText().toString());

                e.apply();

                updateSetupList();

                personDialog.dismiss();
            }
        });

        fieldDialog2 = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        fieldDialog2.setTitle(getString(R.string.fields));
        fieldDialog2.setContentView(R.layout.config);

        fieldDialog2.setCancelable(true);
        fieldDialog2.setCanceledOnTouchOutside(true);

        Button fdCloseBtn2 = (Button) fieldDialog2.findViewById(R.id.closeBtn);

        fdCloseBtn2.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                fieldDialog2.dismiss();
            }
        });

        ListView settingsList = (ListView) findViewById(R.id.myList);

        Button mainCloseBtn = (Button) findViewById(R.id.closeBtn);

        mainCloseBtn.setVisibility(View.GONE);

        String[] items2 = new String[]{ getString(R.string.fields),
                getString(R.string.traits), getString(R.string.setup),getString(R.string.export), getString(R.string.advanced),
                getString(R.string.language)};//, "API Test"}; TODO cleanup

        settingsList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int position, long arg3) {
                Intent intent = new Intent();
                switch (position) {
                    case 0:
                        intent.setClassName(ConfigActivity.this,
                                FileExploreActivity.class.getName());
                        intent.putExtra("path", Constants.FIELDIMPORTPATH);
                        startActivityForResult(intent, 1);
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
                        }else if (MainActivity.dt.getTraitColumnsAsString() == null) {
                            makeToast(getString(R.string.notraitloaded));
                            return;
                        }

                        showSaveDialog();
                        break;
                    case 4:
                        advancedDialog.show();
                        break;
                    case 5:
                        showLanguageDialog();
                        break;
                    /*case 6:
                        intent.setClassName(ConfigActivity.this,
                                ApiActivity.class.getName());
                        startActivity(intent);
                        break;*/
                }
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.listitem, items2);
        settingsList.setAdapter(adapter);

        SharedPreferences.Editor ed = ep.edit();

        if (ep.getInt("UpdateVersion", -1) < getVersion()) {
            ed.putInt("UpdateVersion", getVersion());
            ed.apply();
            Intent intent = new Intent();
            intent.setClass(ConfigActivity.this, ChangelogActivity.class);
            startActivity(intent);
        }
        if (!ep.getBoolean("TipsConfigured", false)) {
            ed.putBoolean("TipsConfigured", true);
            ed.apply();
            showTipsDialog();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                mChosenFile = data.getStringExtra("result");
                showFieldFileDialog();
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

    public int getVersion() {
        int v = 0;
        try {
            v = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("Field Book","" + e.getMessage());
        }
        return v;
    }

    private void showTipsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this);

        builder.setTitle(getString(R.string.tutorial));
        builder.setMessage(getString(R.string.tipsdesc));

        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                Editor ed = ep.edit();
                ed.putBoolean("Tips", true);
                ed.putBoolean("TipsConfigured", true);
                ed.commit();

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
                ed.commit();

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

    // Only used for truncating lat long values
    private String truncateDecimalString(String v) {
        int count = 0;

        boolean found = false;

        String truncated = "";

        for (int i = 0; i < v.length(); i++) {
            if (found) {
                count += 1;

                if (count == 5)
                    break;
            }

            if (v.charAt(i) == '.') {
                found = true;
            }

            truncated += v.charAt(i);
        }

        return truncated;
    }

    private void showAboutDialog() {
        final PackageManager packageManager = this.getPackageManager();

        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(this.getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = null;
        }

        final Dialog aboutDialog = new Dialog(ConfigActivity.this,
                android.R.style.Theme_Holo_Light_Dialog);

        aboutDialog.setTitle(R.string.about);

        aboutDialog.setContentView(R.layout.about);

        android.view.WindowManager.LayoutParams langParams = aboutDialog.getWindow().getAttributes();
        langParams.width = LayoutParams.MATCH_PARENT;
        aboutDialog.getWindow().setAttributes(langParams);

        aboutDialog.setCancelable(true);
        aboutDialog.setCanceledOnTouchOutside(true);

        TextView versionText = (TextView) aboutDialog.findViewById(R.id.tvVersion);
        versionText.setText(getString(R.string.updatemsg) + " " + versionName);

        versionText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(ConfigActivity.this, ChangelogActivity.class);
                startActivity(intent);
            }
        });

        Button closeBtn = (Button) aboutDialog
                .findViewById(R.id.closeBtn);

        closeBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                aboutDialog.dismiss();
            }
        });

        aboutDialog.show();
    }

    // Validate that column choices are different from one another
    private boolean checkImportColumnNames() {
        String idCol = unique.getSelectedItem().toString();
        String priCol = primary.getSelectedItem().toString();
        String secCol = secondary.getSelectedItem().toString();

        idColPosition = unique.getSelectedItemPosition();

        if (idCol.equals(priCol) || idCol.equals(secCol) || priCol.equals(secCol)) {
            makeToast(getString(R.string.colnamesdif));
        }

        return true;
    }

    private Runnable exportData = new Runnable() {
        public void run() {
            new ExportDataTask().execute(0);
        }
    };

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
                    .fromHtml(getString(R.string.exportmsg)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            String[] newRanges = newRange.toArray(new String[newRange.size()]);
            String[] exportTraits = exportTrait.toArray(new String[exportTrait.size()]);

            // Retrieves the data needed for export
            Cursor exportData = MainActivity.dt.getExportDBData(newRanges, exportTraits);

            if(exportData.getCount()==0) {
                noData = true;
                return(0);
            }

            if (checkDbBool & !checkExcelBool) {
                if (exportData.getCount() > 0) {
                    try {
                        File file = new File(Constants.FIELDEXPORTPATH,
                                exportFileString + "_database.csv");

                        if (file.exists()) {
                            file.delete();
                        }

                        FileWriter fw = new FileWriter(file);

                        CSVWriter csvWriter = new CSVWriter(fw, exportData);

                        csvWriter.writeDatabaseFormat(newRange, ep.getString("FirstName", "") + "_"
                                + ep.getString("LastName", ""), ep.getString("Location", ""));

                        System.out.println(exportFileString);
                        shareFile(file);
                    } catch (Exception e) {
                        ErrorLog("ExportDataError.txt", "" + e.getMessage());
                        fail = true;
                    }
                }
            } else if (checkExcelBool & !checkDbBool) {
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
                        ErrorLog("ExportDataError.txt", "" + e.getMessage());
                        fail = true;
                    }
                }
            } else {
                if (exportData.getCount() > 0) {
                    try {
                        File file = new File(Constants.FIELDEXPORTPATH,
                                exportFileString + "_database.csv");

                        if (file.exists()) {
                            file.delete();
                        }

                        FileWriter fw = new FileWriter(file);

                        CSVWriter csvWriter = new CSVWriter(fw, exportData);

                        csvWriter.writeDatabaseFormat(newRange, ep.getString("FirstName", "") + "_"
                                + ep.getString("LastName", ""), ep.getString("Location", ""));
                        shareFile(file);
                    } catch (Exception e) {
                        ErrorLog("ExportDataError.txt", "" + e.getMessage());
                        fail = true;
                    }
                }

                if (fail)
                    return 0;

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
                        ErrorLog("ExportDataError.txt", "" + e.getMessage());
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

            if (fail) {
                makeToast(getString(R.string.exporterror));
            }

            if (noData) {
                makeToast(getString(R.string.exporttraiterror));
            }
        }
    }

    private Runnable exportDB = new Runnable() {
        public void run() {
            new ExportDBTask().execute(0);
        }
    };

    private class ExportDBTask extends AsyncTask<Integer, Integer, Integer>
    {
        boolean fail;
        ProgressDialog dialog;
        String error;

        @Override
        protected void onPreExecute()
        {
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
        protected Integer doInBackground(Integer... params)
        {
            try {
                MainActivity.dt.exportDatabase(exportFileString);
            }
            catch (Exception e)
            {
                ErrorLog("ExportDatabaseError.txt", "" + e.getMessage());
                e.printStackTrace();
                error = "" + e.getMessage();
                fail = true;
            }

            File exportedDb = new File(Constants.BACKUPPATH + "/" + exportFileString + ".db");
            File exportedSp = new File(Constants.BACKUPPATH + "/" + exportFileString + "_sharedpref.xml");

            shareFile(exportedDb);
            shareFile(exportedSp);

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result)
        {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }

            if (fail) {
                ErrorLog("ExportDatabaseError.txt", error);
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
        protected void onPreExecute()
        {
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
                ErrorLog("ImportDatabase.txt", "" + e.getMessage());
                e.printStackTrace();

                error = "" + e.getMessage();

                fail = true;
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result)
        {
            if (dialog.isShowing())
                dialog.dismiss();

            if (fail) {
                ErrorLog("DBImportError.txt", error);
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

        if(!ep.getBoolean("DisableShare", true)) {
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(filePath));
            try {
                startActivity(Intent.createChooser(intent, "Sending File..."));
            } catch(Exception e) {
                Log.e("Field Book","" + e.getMessage());
            }
        }
    }

    private void scanFile(File filePath) {
        MediaScannerConnection.scanFile(this, new String[]{filePath.getAbsolutePath()}, null, null);
    }

    //TODO merge with verfiyuniquecolumncsv
    private boolean verifyUniqueColumnExcel(Workbook wb) {
        HashMap<String, String> check = new HashMap<String, String>();

        for (int s = 0; s < wb.getSheet(0).getRows(); s++) {
            String value = wb.getSheet(0).getCell(idColPosition, s).getContents();

            if (check.containsKey(value)) {
                return false;
            } else
                check.put(value, value);
        }

        return true;
    }

    private boolean verifyUniqueColumnCSV(String path) {
        try {
            HashMap<String, String> check = new HashMap<String, String>();

            FileReader fr = new FileReader(path);

            CSVReader cr = new CSVReader(fr);

            String[] columns = cr.readNext();

            System.out.println(idColPosition);

            while (columns != null) {
                columns = cr.readNext();

                if (columns != null) {
                    if (check.containsKey(columns[idColPosition])) {
                        cr.close();
                        return false;
                    } else
                        check.put(columns[idColPosition], columns[idColPosition]);
                }
            }

            return true;
        } catch (Exception n) {
            ErrorLog("VerifyUniqueError.txt", "" + n.getMessage());
            n.printStackTrace();
            return false;
        }
    }

    // Helper function to merge arrays
    String[] concat(String[] a1, String[] a2) {
        String[] n = new String[a1.length + a2.length];
        System.arraycopy(a1, 0, n, 0, a1.length);
        System.arraycopy(a2, 0, n, a1.length, a2.length);

        return n;
    }

    // Helper function to set spinner adapter and listener
    private void setSpinner(Spinner spinner, String[] data, String pref) {
        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<String>(this, R.layout.spinnerlayout, data);
        spinner.setAdapter(itemsAdapter);

        int spinnerPosition = itemsAdapter.getPosition(ep.getString(pref, itemsAdapter.getItem(0)));
        spinner.setSelection(spinnerPosition);
    }

    public void makeToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showLanguageDialog() {
        final Dialog languageDialog = new Dialog(ConfigActivity.this,
                android.R.style.Theme_Holo_Light_Dialog);
        languageDialog.setTitle(getString(R.string.language));
        languageDialog.setContentView(R.layout.config);

        android.view.WindowManager.LayoutParams params = languageDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        languageDialog.getWindow().setAttributes(params);

        languageDialog.setCancelable(true);
        languageDialog.setCanceledOnTouchOutside(true);

        ListView myList = (ListView) languageDialog
                .findViewById(R.id.myList);

        region = "";
        String[] langArray = new String[12];

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

        Integer image_id[] = {R.drawable.ic_us, R.drawable.ic_mx, R.drawable.ic_fr, R.drawable.ic_in,
                R.drawable.ic_de, R.drawable.ic_jp, R.drawable.ic_ar, R.drawable.ic_cn, R.drawable.ic_br,
                R.drawable.ic_ru, R.drawable.ic_et, R.drawable.ic_et};

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
                }
                Editor ed = ep.edit();
                ed.putString("language", local);
                ed.putString("region", region);
                ed.apply();

                Locale locale2 = new Locale(local, region);
                Locale.setDefault(locale2);
                Configuration config2 = new Configuration();
                config2.locale = locale2;
                getBaseContext().getResources().
                        updateConfiguration(config2, getBaseContext().getResources()
                                .getDisplayMetrics());

                invalidateOptionsMenu();
                loadScreen();

                languageChange = true;

                languageDialog.dismiss();
            }
        });

        CustomListAdapter adapterImg = new CustomListAdapter(this, image_id, langArray);
        myList.setAdapter(adapterImg);

        Button langCloseBtn = (Button) languageDialog
                .findViewById(R.id.closeBtn);

        langCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                languageDialog.dismiss();
            }
        });

        languageDialog.show();
    }

    public class CustomListAdapter extends ArrayAdapter<String> {
        String[] color_names;
        Integer[] image_id;
        Context context;

        public CustomListAdapter(Activity context, Integer[] image_id, String[] text) {
            super(context, R.layout.languageline, text);
            this.color_names = text;
            this.image_id = image_id;
            this.context = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View single_row = inflater.inflate(R.layout.languageline, null,
                    true);
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
        String tagDrop1 = "";
        String tagDrop2 = "";
        String tagDrop3 = "";

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

        if (ep.getString("DROP1", "").length() > 0) {
            tagDrop1 = getString(R.string.drop1) + ": " + ep.getString("DROP1", "");
        } else {
            tagDrop1 = getString(R.string.drop1) + ": " + getString(R.string.none);
        }

        if (ep.getString("DROP2", "").length() > 0) {
            tagDrop2 = getString(R.string.drop2) + ": " + ep.getString("DROP2", "");
        } else {
            tagDrop2 = getString(R.string.drop2) + ": " + getString(R.string.none);
        }

        if (ep.getString("DROP3", "").length() > 0) {
            tagDrop3 = getString(R.string.drop3) + ": " + ep.getString("DROP3", "");
        } else {
            tagDrop3 = getString(R.string.drop3) + ": " + getString(R.string.none);
        }

        return new String[]{tagName, tagLocation, tagDrop1, tagDrop2, tagDrop3, getString(R.string.clearsettings)};
    }

    private void updateSetupList() {
        ArrayAdapter<String> ga = (ArrayAdapter) setupList.getAdapter();

        if(ga!=null) {
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
        SimpleDateFormat timeStamp = new SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss", Locale.getDefault());

        String fFile = ep.getString("FieldFile", "");

        if (fFile.length() > 4 & fFile.toLowerCase().endsWith(".csv")) {
            fFile = fFile.substring(0, fFile.length() - 4);
        }

        exportFile.setText(timeStamp.format(Calendar.getInstance().getTime()) + "_" + fFile);
        saveDialog.show();
    }

    private void showSetupDialog() {
        String[] array = prepareSetup();
        ArrayList<String> lst = new ArrayList<String>();
        lst.addAll(Arrays.asList(array));
        final String[] traits = MainActivity.dt.getRangeColumns();

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
                        if (MainActivity.dt.getRangeColumns() == null)
                            return;

                        showTrait1Dialog(traits);
                        break;

                    case 3:
                        if (MainActivity.dt.getRangeColumns() == null)
                            return;

                        showTrait2Dialog(traits);
                        break;

                    case 4:
                        if (MainActivity.dt.getRangeColumns() == null)
                            return;

                        showTrait3Dialog(traits);
                        break;

                    case 5:
                        showClearSettingsDialog();
                        break;

                }
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.listitem, lst);

        setupList.setAdapter(adapter);
        setupDialog.show();
    }

    private void showPersonDialog() {
        personDialog.show();
    }

    private void showLocationDialog() {
        locationDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        locationDialog.setTitle(getString(R.string.locationsetup));
        locationDialog.setContentView(R.layout.location);

        android.view.WindowManager.LayoutParams langParams = locationDialog.getWindow().getAttributes();
        langParams.width = LayoutParams.MATCH_PARENT;
        locationDialog.getWindow().setAttributes(langParams);

        locationDialog.setCancelable(true);
        locationDialog.setCanceledOnTouchOutside(true);

        GPSTracker gps = new GPSTracker(this);
        if(gps.canGetLocation()) { //GPS enabled
            lat = gps.getLatitude(); // returns latitude
            lng = gps.getLongitude(); // returns longitude
        } else {
            Intent intent = new Intent(
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        Button findLocation = (Button) locationDialog
                .findViewById(R.id.getLctnBtn);
        Button yesLocation = (Button) locationDialog.findViewById(R.id.saveBtn);

        final EditText longitude = (EditText) locationDialog
                .findViewById(R.id.longitude);
        final EditText latitude = (EditText) locationDialog
                .findViewById(R.id.latitude);

        findLocation.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                latitude.setText(truncateDecimalString(String.valueOf(lat)));
                longitude.setText(truncateDecimalString(String.valueOf(lng)));
            }
        });

        yesLocation.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Editor e = ep.edit();
                e.putString("Location", latitude.getText().toString() + " ; " + longitude.getText().toString());
                e.apply();
                updateSetupList();
                locationDialog.dismiss();
            }
        });

        locationDialog.show();
    }

    private void showClearSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this);

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
                ed.putString("DROP1", "");
                ed.putString("DROP2", "");
                ed.putString("DROP3", "");
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

    private void showTrait3Dialog(final String[] traits) {
        final Dialog dialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);

        dialog.setTitle(getString(R.string.drop3) + ": " + ep.getString("DROP3", ""));
        dialog.setContentView(R.layout.config);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        ListView csvList = (ListView) dialog.findViewById(R.id.myList);
        Button csvButton = (Button) dialog.findViewById(R.id.closeBtn);

        csvButton.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        csvList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                dialog.dismiss();

                Editor e = ep.edit();

                e.putString("DROP3", traits[which]);
                e.commit();

                MainActivity.partialReload = true;

                updateSetupList();
            }
        });

        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<String>(this, R.layout.listitem, traits);
        csvList.setAdapter(itemsAdapter);
        dialog.show();
    }

    private void showTrait2Dialog(final String[] traits) {
        final Dialog dialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);

        dialog.setTitle(getString(R.string.drop2) + ": " + ep.getString("DROP2", ""));
        dialog.setContentView(R.layout.config);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        ListView csvList = (ListView) dialog.findViewById(R.id.myList);
        Button csvButton = (Button) dialog.findViewById(R.id.closeBtn);

        csvButton.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        csvList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                dialog.dismiss();

                Editor e = ep.edit();

                e.putString("DROP2", traits[which]);
                e.commit();

                MainActivity.partialReload = true;

                updateSetupList();
            }
        });

        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<String>(this, R.layout.listitem, traits);
        csvList.setAdapter(itemsAdapter);
        dialog.show();
    }

    private void showTrait1Dialog(final String[] traits) {
        final Dialog dialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);

        dialog.setTitle(getString(R.string.drop1) + ": " + ep.getString("DROP1", ""));
        dialog.setContentView(R.layout.config);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        ListView csvList = (ListView) dialog.findViewById(R.id.myList);
        Button csvButton = (Button) dialog.findViewById(R.id.closeBtn);

        csvButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        csvList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                dialog.dismiss();

                Editor e = ep.edit();
                e.putString("DROP1", traits[which]);
                e.commit();

                MainActivity.partialReload = true;
                updateSetupList();
            }
        });

        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<String>(this, R.layout.listitem, traits);
        csvList.setAdapter(itemsAdapter);
        dialog.show();
    }

    private void showFieldFileDialog() {
        //todo get URI instead of string
        Editor e = ep.edit();
        e.putString("FieldFile", mChosenFile.substring(mChosenFile.lastIndexOf("/") + 1, mChosenFile.lastIndexOf(".")));
        e.commit();

        columnFail = false;

        if (mChosenFile.toLowerCase().contains(".xls")) {
            isCSV = false;
            action = DIALOG_LOAD_FIELDFILEEXCEL;
        }

        if (mChosenFile.toLowerCase().contains(".csv")) {
            action = DIALOG_LOAD_FIELDFILECSV;
            isCSV = true;
        }

        if (!mChosenFile.toLowerCase().contains(".csv") && !mChosenFile.toLowerCase().contains(".xls")) {
            makeToast(getString(R.string.notsupported));
        } else {
            makeDirs();
            loadFile();
        }
    }

    private void makeDirs() {
        createDir(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", ""));
        createDir(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/audio");
        createDir(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/photos");
    }

    private void createDir(String path) {
        File dir = new File(path);
        File blankFile = new File(path + "/.fieldbook");

        if (!dir.exists()) {
            dir.mkdirs();

            try {
                blankFile.getParentFile().mkdirs();
                blankFile.createNewFile();
                scanFile(blankFile);
            } catch (IOException e) {
                ErrorLog("DirectoryError.txt", "" + e.getMessage());
            }
        }
    }

    private void loadFile() {
        if(action==DIALOG_LOAD_FIELDFILEEXCEL) {
            WorkbookSettings wbSettings = new WorkbookSettings();
            wbSettings.setUseTemporaryFileDuringWrite(true);

            try {
                wb = Workbook.getWorkbook(new File(mChosenFile), wbSettings);

                importColumns = new String[wb.getSheet(0).getColumns()];

                for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                    importColumns[s] = wb.getSheet(0).getCell(s, 0).getContents();
                }

            } catch (Exception n) {
                ErrorLog("ExcelError.txt", "" + n.getMessage());
            }

        }
        if(action==DIALOG_LOAD_FIELDFILECSV) {
            try {
                FileReader fr = new FileReader(mChosenFile);
                CSVReader cr = new CSVReader(fr);

                importColumns = cr.readNext();

            } catch (Exception n) {
                ErrorLog("CSVError.txt", "" + n.getMessage());
            }
        }

        String[] reservedNames = new String[]{"abort", "action", "add", "after", "all", "alter",
                "analyze", "and", "as", "asc", "attach", "autoincrement", "before", "begin",
                "between", "by", "cascade", "case", "cast", "check", "collate", "commit",
                "conflict", "constraint", "create", "cross", "current_date", "current_time",
                "current_timestamp", "database", "default", "deferrable", "deferred", "delete",
                "desc", "detach", "distinct", "drop", "each", "else", "end", "escape", "except",
                "exclusive", "exists", "explain", "fail", "for", "foreign", "from", "full", "glob",
                "group", "having", "if", "ignore", "immediate", "in", "index", "indexed", "initially",
                "inner", "insert", "instead", "intersect", "into", "is", "isnull", "join", "key",
                "left", "like", "limit", "match", "natural", "no", "not", "notnull", "null", "of",
                "offset", "on", "or", "order", "outer", "plan", "pragma", "primary", "query", "raise",
                "recursive", "references", "regexp", "reindex", "release", "rename", "replace",
                "restrict", "right", "rollback", "savepoint", "select", "set", "table", "then", "to",
                "transaction", "trigger", "union", "update", "using", "vacuum", "virtual", "when",
                "where", "with", "without"};

        List<String> list = Arrays.asList(reservedNames);

        for (String s : importColumns) {
            if (DataHelper.hasSpecialChars(s)) {
                columnFail = true;
                Toast.makeText(ConfigActivity.this, getString(R.string.columnfail) + " (\"" + s + "\")", Toast.LENGTH_LONG).show();
                break;
            }
            if (list.contains(s.toLowerCase())) {
                columnFail = true;
                Toast.makeText(ConfigActivity.this, getString(R.string.columnfail) + " (\"" + s + "\")", Toast.LENGTH_LONG).show();
                break;
            }
        }

        if (!columnFail) {
            importDialog(importColumns);
        }
    }

    private void importDialog(String[] columns) {
        importFieldDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        importFieldDialog.setTitle(getString(R.string.importfields));
        importFieldDialog.setContentView(R.layout.importdialog);

        android.view.WindowManager.LayoutParams params2 = importFieldDialog.getWindow().getAttributes();
        params2.width = LayoutParams.MATCH_PARENT;
        importFieldDialog.getWindow().setAttributes(params2);

        importFieldDialog.setCancelable(true);
        importFieldDialog.setCanceledOnTouchOutside(false);

        Button startImport = (Button) importFieldDialog.findViewById(R.id.okBtn);

        startImport.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                if (checkImportColumnNames()) {
                    importFieldDialog.dismiss();
                    if (isCSV)
                        mHandler.post(importCSV);
                    else
                        mHandler.post(importExcel);
                }
            }
        });

        unique = (Spinner) importFieldDialog.findViewById(R.id.uniqueSpin);
        primary = (Spinner) importFieldDialog.findViewById(R.id.primarySpin);
        secondary = (Spinner) importFieldDialog.findViewById(R.id.secondarySpin);

        setSpinner(unique, columns, "ImportUniqueName");
        setSpinner(primary, columns, "ImportFirstName");
        setSpinner(secondary, columns, "ImportSecondName");

        importFieldDialog.show();
    }

    // Creates a new thread to do importing
    private Runnable importCSV = new Runnable() {
        public void run() {
            new ImportCSVTask().execute(0);
        }
    };

    //TODO combine with excel
    private class ImportCSVTask extends AsyncTask<Integer, Integer, Integer> {
        ProgressDialog dialog;

        boolean fail;
        boolean uniqueFail;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

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
                String[] data;
                String[] columns;

                //verify unique
                if (!verifyUniqueColumnCSV(mChosenFile)) {
                    uniqueFail = true;
                    return 0;
                }

                FileReader fr = new FileReader(mChosenFile);

                CSVReader cr = new CSVReader(fr);

                columns = cr.readNext();

                for (int i = 0; i < columns.length; i++) {
                    columns[i] = columns[i].replaceAll(" ", "_");
                }

                MainActivity.dt.dropRange();
                MainActivity.dt.createRange(columns);

                Editor e = ep.edit();
                e.putString("DROP1", null);
                e.putString("DROP2", null);
                e.putString("DROP3", null);
                e.apply();

                data = columns;

                while (data != null) {
                    data = cr.readNext();

                    if (data != null) {
                        MainActivity.dt.insertRange(columns, data);
                    }
                }

                try {
                    cr.close();
                } catch (Exception f) {
                    ErrorLog("CSVError.txt", "" +f.getMessage());
                }

                try {
                    fr.close();
                } catch (Exception f) {
                    ErrorLog("CSVError.txt","" + f.getMessage());
                }

                // These 2 lines are necessary due to importing of range data.
                // As the table is dropped and recreated,
                // changes are not visible until you refresh the database
                // connection
                MainActivity.dt.close();
                MainActivity.dt.open();

                File newDir = new File(mChosenFile);
                newDir.mkdirs();

            } catch (Exception e) {
                ErrorLog("CSVError.txt", "" + e.getMessage());
                e.printStackTrace();
                fail = true;

                //recreate empty default table on fail
                MainActivity.dt.defaultFieldTable();

                MainActivity.dt.close();
                MainActivity.dt.open();
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (dialog.isShowing())
                dialog.dismiss();

            if (fail)
                Toast.makeText(ConfigActivity.this, getString(R.string.importerror),
                        Toast.LENGTH_LONG).show();
            else if (uniqueFail)
                Toast.makeText(ConfigActivity.this, getString(R.string.importuniqueerror),
                        Toast.LENGTH_LONG).show();
            else {
                Editor ed = ep.edit();
                ed.putString("ImportUniqueName", unique.getSelectedItem().toString());
                ed.putString("ImportFirstName", primary.getSelectedItem().toString());
                ed.putString("ImportSecondName", secondary.getSelectedItem().toString());
                ed.putBoolean("ImportFieldFinished", true);
                ed.apply();

                MainActivity.reloadData = true;
            }
        }
    }

    public void ErrorLog(String sFileName, String sErrMsg) {
        try {
            SimpleDateFormat lv_parser = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");

            File file = new File(Constants.ERRORPATH, sFileName);

            FileWriter filewriter = new FileWriter(file, true);
            BufferedWriter out = new BufferedWriter(filewriter);

            out.write(lv_parser.format(Calendar.getInstance().getTime()) + " " + sErrMsg + "\n");
            out.flush();
            out.close();

            scanFile(file);
        } catch (Exception e) {
            Log.e("Field Book","" + e.getMessage());
        }
    }

    //TODO combine with csv
    private Runnable importExcel = new Runnable() {
        public void run() {
            new ImportExcelTask().execute(0);
        }

    };

    private class ImportExcelTask extends AsyncTask<Integer, Integer, Integer> {
        ProgressDialog dialog;

        boolean fail;
        boolean uniqueFail;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

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
                String[] data;
                String[] columns;

                //verify unique
                if (!verifyUniqueColumnExcel(wb)) {
                    uniqueFail = true;
                    return 0;
                }

                columns = new String[wb.getSheet(0).getColumns()];

                for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                    columns[s] = wb.getSheet(0).getCell(s, 0).getContents();
                }

                MainActivity.dt.dropRange();
                MainActivity.dt.createRange(columns);

                Editor e = ep.edit();

                e.putString("DROP1", null);
                e.putString("DROP2", null);
                e.putString("DROP3", null);

                e.apply();

                int row = 1;

                while (row < wb.getSheet(0).getRows()) {
                    data = new String[wb.getSheet(0).getColumns()];

                    for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                        data[s] = wb.getSheet(0).getCell(s, row).getContents();
                    }

                    row += 1;

                    MainActivity.dt.insertRange(columns, data);
                }

                // These 2 lines are necessary due to importing of range data.
                // As the table is dropped and recreated,
                // changes are not visible until you refresh the database
                // connection
                MainActivity.dt.close();
                MainActivity.dt.open();

                File newDir = new File(mChosenFile);

                newDir.mkdirs();

            } catch (Exception e) {
                ErrorLog("ImportExcelError.txt", "" + e.getMessage());
                e.printStackTrace();
                fail = true;

                //recreate empty default table on fail
                MainActivity.dt.defaultFieldTable();

                MainActivity.dt.close();
                MainActivity.dt.open();
            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (dialog.isShowing())
                dialog.dismiss();

            if (fail)
                Toast.makeText(ConfigActivity.this, getString(R.string.importerror),
                        Toast.LENGTH_LONG).show();
            else if (uniqueFail)
                Toast.makeText(ConfigActivity.this, getString(R.string.importuniqueerror),
                        Toast.LENGTH_LONG).show();
            else {
                Editor ed = ep.edit();
                ed.putString("ImportUniqueName", unique.getSelectedItem().toString());
                ed.putString("ImportFirstName", primary.getSelectedItem().toString());
                ed.putString("ImportSecondName", secondary.getSelectedItem().toString());
                ed.putBoolean("ImportFieldFinished", true);
                ed.apply();

                MainActivity.reloadData = true;
            }
        }
    }

    private void showDatabaseDialog() {
        String[] items = new String[3];
        items[0] = getString(R.string.dbexport);
        items[1] = getString(R.string.dbimport);
        items[2] = getString(R.string.dbreset);

        final Dialog chooseBackupDialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);
        chooseBackupDialog.setTitle(getString(R.string.dbbackup));
        chooseBackupDialog.setContentView(R.layout.config);

        android.view.WindowManager.LayoutParams params = chooseBackupDialog.getWindow().getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        chooseBackupDialog.getWindow().setAttributes(params);

        chooseBackupDialog.setCancelable(true);
        chooseBackupDialog.setCanceledOnTouchOutside(true);

        setupList = (ListView) chooseBackupDialog.findViewById(R.id.myList);

        Button setupCloseBtn = (Button) chooseBackupDialog.findViewById(R.id.closeBtn);

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

        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<String>(this, R.layout.listitem, items);
        setupList.setAdapter(itemsAdapter);
        chooseBackupDialog.show();
    }

    private void showDatabaseImportDialog() {
        Intent intent = new Intent();

        intent.setClassName(ConfigActivity.this,
                FileExploreActivity.class.getName());
        intent.putExtra("path", Constants.BACKUPPATH);
        startActivityForResult(intent, 2);
    }

    private void showDatabaseExportDialog() {
        dbSaveDialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);
        dbSaveDialog.setTitle(getString(R.string.dbbackup));
        dbSaveDialog.setContentView(R.layout.savedb);

        android.view.WindowManager.LayoutParams params2 = dbSaveDialog.getWindow().getAttributes();
        params2.width = LayoutParams.MATCH_PARENT;
        dbSaveDialog.getWindow().setAttributes(params2);

        dbSaveDialog.setCancelable(true);
        dbSaveDialog.setCanceledOnTouchOutside(true);

        exportFile = (EditText) dbSaveDialog.findViewById(R.id.fileName);

        Button closeBtn = (Button) dbSaveDialog.findViewById(R.id.closeBtn);

        closeBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                dbSaveDialog.dismiss();
            }
        });

        Button exportButton = (Button) dbSaveDialog.findViewById(R.id.saveBtn);

        exportButton.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                dbSaveDialog.dismiss();
                exportFileString = exportFile.getText().toString();
                mHandler.post(exportDB);
            }
        });

        SimpleDateFormat timeStamp = new SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss", Locale.getDefault());

        exportFile.setText(timeStamp.format(Calendar.getInstance().getTime()) + "_" + "systemdb" + DataHelper.DATABASE_VERSION + ".db");

        dbSaveDialog.show();
    }

    // First confirmation
    private void showDatabaseResetDialog1() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this);

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
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this);

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

                Toast toast = Toast.makeText(ConfigActivity.this, getString(R.string.resetcomplete), Toast.LENGTH_LONG);
                toast.show();

                try {
                    ConfigActivity.thisActivity.finish();
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
        new MenuInflater(ConfigActivity.this).inflate(R.menu.configmenu, menu);

        systemMenu = menu;

        if (systemMenu != null) {

            if (ep.getBoolean("Tips", false)) {
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