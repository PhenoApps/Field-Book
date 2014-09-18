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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.fieldbook.tracker.Trait.TraitEditorActivity;
import com.fieldbook.tracker.Tutorial.TutorialSettingsActivity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
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

    private static final int DIALOG_LOAD_TRAITFILE = 1002;
    private static final int DIALOG_LOAD_TRAITS1 = 1003;
    private static final int DIALOG_LOAD_TRAITS2 = 1004;
    private static final int DIALOG_LOAD_TRAITS3 = 1005;

    private static final String CSV = ".csv";
    private static final String XLS = ".xls";
    private static final int DIALOG_LOAD_FIELDFILECSV = 1000;
    private static final int DIALOG_LOAD_FIELDFILEEXCEL = 1001;

    public static boolean helpActive;
    public static Activity thisActivity;

    private SharedPreferences ep;

    private Dialog personDialog;
    private Dialog locationDialog;
    private Dialog rateDialog;
    private Dialog saveDialog;

    private Dialog fieldDialog;
    private Dialog fieldDialog2;

    private Dialog fieldCurrentDialog;

    private Dialog advancedDialog;

    private Dialog setupDialog;

    private Dialog importFieldMapDialog;

    private Dialog dbSaveDialog;

    private String[] mFileList;
    private String[] importColumns;

    private String mUserChoice = "";
    private String mChosenFile = "";
    private String importDirectory = "";

    private ListView rateList;
    private ListView saveList;
    private ListView setupList;

    private Location fieldLocation;

    private double lat;
    private double lng;

    private EditText exportFile;

    private CheckBox checkDB;
    private CheckBox checkExcel;

    private boolean isCSV;
    private int idColPosition;

    private Workbook wb;

    private int uPosition;
    private int fPosition;
    private int sPosition;
    private int ePosition;

    private int action;

    private LinearLayout importMain;

    private OnItemClickListener mainSettingsListener;

    private boolean columnFail;

    private ArrayList<String> newRange;

    private String local;
    private String region;

    private FrameLayout mFrame;

    private ListView settingsList;

    private Menu systemMenu;

    @Override
    public void onDestroy() {

        try {
            // Always close the tutorial along with the activity
            TutorialSettingsActivity.thisActivity.finish();
        } catch (Exception e) {

        }

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (systemMenu != null) {
            // Always reload item visibility in the menu in the event settings has changed
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

        importFieldMapDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        importFieldMapDialog.setContentView(R.layout.importchoose);
        importFieldMapDialog.setTitle(getString(R.string.importfields));
        importFieldMapDialog.setCancelable(true);
        importFieldMapDialog.setCanceledOnTouchOutside(true);

        importMain = (LinearLayout) importFieldMapDialog.findViewById(R.id.main);


        invalidateOptionsMenu();
        loadScreen();

        helpActive = false;
    }

    private void loadScreen() {
        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        mFrame = new FrameLayout(this);

        mFrame.addView(LayoutInflater.from(getBaseContext()).inflate(R.layout.config, null));
        setContentView(mFrame);

        // Import Field book


        Button startImport = (Button) importFieldMapDialog.findViewById(R.id.okBtn);

        startImport.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {

                if (checkImportColumnNames()) {
                    importFieldMapDialog.dismiss();

                    if (isCSV)
                        mHandler.post(importCSV);
                    else
                        mHandler.post(importExcel);
                }
            }
        });

        // Advanced Settings
        advancedDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        advancedDialog.setTitle(getString(R.string.advanced));
        advancedDialog.setContentView(R.layout.advanced);

        android.view.WindowManager.LayoutParams params = advancedDialog.getWindow().getAttributes();
        params.width = LayoutParams.FILL_PARENT;
        advancedDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);

        advancedDialog.setCancelable(true);
        advancedDialog.setCanceledOnTouchOutside(true);

        CheckBox tips = (CheckBox) advancedDialog.findViewById(R.id.tips);
        CheckBox cycle = (CheckBox) advancedDialog.findViewById(R.id.cycle);
        CheckBox enableMap = (CheckBox) advancedDialog.findViewById(R.id.enableMap);
        CheckBox ignoreEntries = (CheckBox) advancedDialog.findViewById(R.id.ignoreExisting);
        CheckBox useDay = (CheckBox) advancedDialog.findViewById(R.id.useDay);
        CheckBox rangeSound = (CheckBox) advancedDialog.findViewById(R.id.rangeSound);
        CheckBox jumpToPlot = (CheckBox) advancedDialog.findViewById(R.id.jumpToPlot);
        CheckBox nextEmptyPlot = (CheckBox) advancedDialog.findViewById(R.id.nextEmptyPlot);

        Button advCloseBtn = (Button) advancedDialog.findViewById(R.id.closeBtn);

        advCloseBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                advancedDialog.dismiss();
            }
        });

        // Set default values for advanced settings
        tips.setChecked(ep.getBoolean("Tips", false));
        cycle.setChecked(ep.getBoolean("CycleTraits", false));
        enableMap.setChecked(ep.getBoolean("EnableMap", false));
        ignoreEntries.setChecked(ep.getBoolean("IgnoreExisting", false));
        useDay.setChecked(ep.getBoolean("UseDay", false));
        rangeSound.setChecked(ep.getBoolean("RangeSound", false));
        jumpToPlot.setChecked(ep.getBoolean("JumpToPlot", false));
        nextEmptyPlot.setChecked(ep.getBoolean("NextEmptyPlot", false));

        tips
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    public void onCheckedChanged(CompoundButton arg0,
                                                 boolean checked) {
                        Editor e = ep.edit();
                        e.putBoolean("Tips", checked);
                        e.commit();

                        // This is important - menu items won't change their language if
                        // this isn't called
                        invalidateOptionsMenu();
                    }
                });

        cycle
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    public void onCheckedChanged(CompoundButton arg0,
                                                 boolean checked) {
                        Editor e = ep.edit();
                        e.putBoolean("CycleTraits", checked);
                        e.commit();
                    }
                });

        enableMap
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    public void onCheckedChanged(CompoundButton arg0,
                                                 boolean checked) {
                        Editor e = ep.edit();
                        e.putBoolean("EnableMap", checked);
                        e.commit();
                    }
                });

        ignoreEntries
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    public void onCheckedChanged(CompoundButton arg0,
                                                 boolean checked) {
                        Editor e = ep.edit();
                        e.putBoolean("IgnoreExisting", checked);
                        e.commit();
                    }
                });

        useDay
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    public void onCheckedChanged(CompoundButton arg0,
                                                 boolean checked) {
                        Editor e = ep.edit();
                        e.putBoolean("UseDay", checked);
                        e.commit();
                    }
                });

        rangeSound
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    public void onCheckedChanged(CompoundButton arg0,
                                                 boolean checked) {
                        Editor e = ep.edit();
                        e.putBoolean("RangeSound", checked);
                        e.commit();
                    }
                });

        jumpToPlot
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    public void onCheckedChanged(CompoundButton arg0,
                                                 boolean checked) {
                        Editor e = ep.edit();
                        e.putBoolean("JumpToPlot", checked);
                        e.commit();
                    }
                });

        nextEmptyPlot
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    public void onCheckedChanged(CompoundButton arg0,
                                                 boolean checked) {
                        Editor e = ep.edit();
                        e.putBoolean("NextEmptyPlot", checked);
                        e.commit();
                    }
                });


        // Export Field book
        saveDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        saveDialog.setTitle(getString(R.string.exportas));
        saveDialog.setContentView(R.layout.savefile);

        android.view.WindowManager.LayoutParams params2 = saveDialog.getWindow().getAttributes();
        params2.width = LayoutParams.FILL_PARENT;
        saveDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params2);

        saveDialog.setCancelable(true);
        saveDialog.setCanceledOnTouchOutside(true);

        Button closeBtn = (Button) saveDialog.findViewById(R.id.closeBtn);

        closeBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                saveDialog.dismiss();
            }
        });

        exportFile = (EditText) saveDialog.findViewById(R.id.fileName);

        saveList = (ListView) saveDialog.findViewById(R.id.myList);

        saveList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        saveList.setItemsCanFocus(false);

        // Export DB Format
        checkDB = (CheckBox) saveDialog.findViewById(R.id.formatDB);

        // Export Excel Format
        checkExcel = (CheckBox) saveDialog.findViewById(R.id.formatExcel);

        checkDB.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
            }
        });

        checkExcel.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
            }
        });

        Button exportButton = (Button) saveDialog.findViewById(R.id.saveBtn);

        exportButton.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {

                int count = saveList.getAdapter().getCount();

                // Ensure at least one export type is checked
                if (!checkDB.isChecked() & !checkExcel.isChecked()) {
                    Toast.makeText(ConfigActivity.this, getString(R.string.noexportcheck),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                newRange = new ArrayList<String>();

                // Get the columns selected by the user
                for (int i = 0; i < count; i++) {
                    String currentItem = (String) saveList.getAdapter()
                            .getItem(i);

                    if (saveList.isItemChecked(i)) {
                        newRange.add(currentItem);
                    }
                }

                // If there are no columns selected, inform the user
                if (newRange.size() == 0) {
                    Toast.makeText(ConfigActivity.this, getString(R.string.nocolumns),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                saveDialog.dismiss();

                mHandler.post(exportData);
            }
        });

        //setup
        setupDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        setupDialog.setTitle(getString(R.string.setup));
        setupDialog.setContentView(R.layout.config);

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

                e.commit();

                updateSetupList();

                personDialog.dismiss();
            }
        });

        // choose type of field import
        fieldDialog2 = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        fieldDialog2.setTitle(getString(R.string.fields));
        fieldDialog2.setContentView(R.layout.genericdialog);

        fieldDialog2.setCancelable(true);
        fieldDialog2.setCanceledOnTouchOutside(true);

        ListView fieldList2 = (ListView) fieldDialog2.findViewById(R.id.myList);

        Button fdCloseBtn2 = (Button) fieldDialog2.findViewById(R.id.closeBtn);

        fdCloseBtn2.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                fieldDialog2.dismiss();
            }
        });


        // list current field values
        fieldCurrentDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        fieldCurrentDialog.setTitle(getString(R.string.fields));
        fieldCurrentDialog.setContentView(R.layout.currentsettings);

        fieldCurrentDialog.setCancelable(true);
        fieldCurrentDialog.setCanceledOnTouchOutside(true);

        Button fdCloseBtn3 = (Button) fieldCurrentDialog.findViewById(R.id.closeBtn);

        fdCloseBtn3.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                fieldCurrentDialog.dismiss();
            }
        });

        final TextView fieldColumns = (TextView) fieldCurrentDialog.findViewById(R.id.text);

        // field dialog

        fieldDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        fieldDialog.setTitle(getString(R.string.fields));
        fieldDialog.setContentView(R.layout.genericdialog);

        fieldDialog.setCancelable(true);
        fieldDialog.setCanceledOnTouchOutside(true);

        Button fdCloseBtn = (Button) fieldDialog.findViewById(R.id.closeBtn);

        fdCloseBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                fieldDialog.dismiss();
            }
        });

        ListView fieldList = (ListView) fieldDialog.findViewById(R.id.myList);

        String[] items5 = new String[]{getString(R.string.importfields),
                getString(R.string.listfieldimport)};
        fieldList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {

                fieldDialog.dismiss();

                switch (which) {
                    case 0:
                        fieldDialog2.show();
                        break;

                    case 1:
                        if (MainActivity.dt.getRangeColumnsAsString() != null)
                            fieldColumns.setText(MainActivity.dt.getRangeColumnsAsString());
                        else
                            fieldColumns.setText(getString(R.string.none));

                        fieldCurrentDialog.show();
                        break;
                }

            }
        });
        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<String>(this, R.layout.listitem, items5);
        fieldList.setAdapter(itemsAdapter);

        // To configure location
        locationDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        locationDialog.setTitle(getString(R.string.locationsetup));
        locationDialog.setContentView(R.layout.location);

        android.view.WindowManager.LayoutParams langParams = locationDialog.getWindow().getAttributes();
        langParams.width = LayoutParams.FILL_PARENT;
        locationDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) langParams);

        locationDialog.setCancelable(true);
        locationDialog.setCanceledOnTouchOutside(true);

        Button findLocation = (Button) locationDialog
                .findViewById(R.id.getLctnBtn);
        Button yesLocation = (Button) locationDialog.findViewById(R.id.saveBtn);

        final EditText longitude = (EditText) locationDialog
                .findViewById(R.id.longitude);
        final EditText latitude = (EditText) locationDialog
                .findViewById(R.id.latitude);

        final LocationListener locationListener = new LocationListener() {
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
                Toast.makeText(ConfigActivity.this,
                        "Provider enabled: " + provider, Toast.LENGTH_SHORT)
                        .show();
            }

            @Override
            public void onProviderDisabled(String provider) {
                Toast.makeText(ConfigActivity.this,
                        "Provider disabled: " + provider, Toast.LENGTH_SHORT)
                        .show();
            }

            @Override
            public void onLocationChanged(Location location) {
                fieldLocation = location;
            }
        };

        findLocation.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                LocationManager locationManager = (LocationManager) thisActivity.getSystemService(Context.LOCATION_SERVICE);
                fieldLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                lat = fieldLocation.getLatitude();
                lng = fieldLocation.getLongitude();

                latitude.setText(truncateDecimalString(String.valueOf(lat)));
                longitude.setText(truncateDecimalString(String.valueOf(lng)));
                locationManager.removeUpdates(locationListener);
            }
        });

        yesLocation.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Editor e = ep.edit();
                e.putString("Location", latitude.getText().toString() + " ; " + longitude.getText().toString());
                e.commit();
                updateSetupList();
                locationDialog.dismiss();
            }
        });


        // This is the list of items shown on the settings screen itself
        settingsList = (ListView) findViewById(R.id.myList);

        Button mainCloseBtn = (Button) findViewById(R.id.closeBtn);

        mainCloseBtn.setVisibility(View.GONE);

        String[] items2 = new String[]{getString(R.string.setup), getString(R.string.fields),
                getString(R.string.traits), getString(R.string.export), getString(R.string.advanced),
                getString(R.string.language)};

        settingsList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int position, long arg3) {
                Intent intent = new Intent();
                switch (position) {
                    case 0:
                        if (!ep.getBoolean("ImportFieldFinished", false)) {
                            showNoFieldDialog();
                            return;
                        }

                        showSetupDialog();
                        break;

                    case 1:
                        intent.setClassName(ConfigActivity.this,
                                FileExploreActivity.class.getName());
                        intent.putExtra("path", Constants.FIELDIMPORTPATH);
                        startActivityForResult(intent, 1);
                        break;

                    case 2:
                        if (!ep.getBoolean("ImportFieldFinished", false)) {
                            Toast toast = Toast.makeText(ConfigActivity.this, getString(R.string.importtraitwarning), Toast.LENGTH_LONG);
                            toast.show();
                            return;
                        }

                        intent.setClassName(ConfigActivity.this,
                                TraitEditorActivity.class.getName());
                        startActivity(intent);
                        break;

                    case 3:
                        if (!ep.getBoolean("ImportFieldFinished", false)) {
                            showNoFieldDialog();
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
                }
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.listitem, items2);
        settingsList.setAdapter(adapter);

        SharedPreferences.Editor ed = ep.edit();

        if (ep.getInt("UpdateVersion", -1) < getVersion()) {
            ed.putInt("UpdateVersion", getVersion());
            ed.commit();
            Intent intent = new Intent();
            intent.setClass(ConfigActivity.this, ChangelogActivity.class);
            startActivity(intent);
        }
        if (!ep.getBoolean("TipsConfigured", false)) {
            ed.putBoolean("TipsConfigured", true);
            ed.commit();
            showTipsDialog();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                mChosenFile = data.getStringExtra("result");
                showFieldFileDialog();
            }
            if (resultCode == RESULT_CANCELED) {
            }
        }
    }

    public int getVersion() {
        int v = 0;
        try {
            v = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
        }
        return v;
    }

    private void showTipsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this);

        builder.setTitle(getString(R.string.tipshort));
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
        String versionName;
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
        langParams.width = LayoutParams.FILL_PARENT;
        aboutDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) langParams);

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

    // Validate that columns are unique
    private boolean checkImportColumnNames() {
        idColPosition = findColValue(importMain, 0, false);

        if (idColPosition == -1) {
            Toast.makeText(ConfigActivity.this, getString(R.string.chooseunique),
                    Toast.LENGTH_LONG).show();
            return false;
        }

        uPosition = findColValue(importMain, 0, false);
        fPosition = findColValue(importMain, 1, false);

        if (fPosition == -1) {
            Toast.makeText(ConfigActivity.this, getString(R.string.choosefirst),
                    Toast.LENGTH_LONG).show();
            return false;
        }

        sPosition = findColValue(importMain, 2, false);

        if (sPosition == -1) {
            Toast.makeText(ConfigActivity.this, getString(R.string.choosesecond),
                    Toast.LENGTH_LONG).show();
            return false;
        }

        ePosition = findColValue(importMain, 3, false);

        return true;
    }

    private Runnable exportData = new Runnable() {
        public void run() {
            new ExportDataTask().execute(0);
        }
    };

    private class ExportDataTask extends AsyncTask<Integer, Integer, Integer> {
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
                    .fromHtml(getString(R.string.exportmsg)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            String[] newRanges = newRange.toArray(new String[newRange.size()]);

            if (checkDB.isChecked() & !checkExcel.isChecked()) {
                // Retrieves the data needed for export
                Cursor exportData = MainActivity.dt.getExportDBData(newRanges);

                // Do not proceed if there is no data
                // So it will not override existing files
                if (exportData.getCount() > 0) {
                    try {
                        File file = new File(Constants.FIELDEXPORTPATH,
                                exportFile.getText().toString() + "_database.csv");

                        if (file.exists())
                            file.delete();

                        FileWriter fw = new FileWriter(file);

                        CSVWriter csvWriter = new CSVWriter(fw, exportData);

                        csvWriter.writeFile(newRange, ep.getString("FirstName", "") + "_"
                                + ep.getString("LastName", ""), ep.getString("Location", ""), ep.getBoolean("UseDay", false));
                        shareFile(file);
                    } catch (Exception e) {
                        fail = true;
                    }
                }
            } else if (checkExcel.isChecked() & !checkDB.isChecked()) {
                // Retrieves the data needed for export
                Cursor exportData = MainActivity.dt.getExportExcelData(newRanges);

                // Do not proceed if there is no data
                // So it will not override existing files
                if (exportData.getCount() > 0) {
                    try {
                        File file = new File(Constants.FIELDEXPORTPATH,
                                exportFile.getText().toString() + "_table.csv");

                        if (file.exists())
                            file.delete();

                        FileWriter fw = new FileWriter(file);

                        CSVWriter csvWriter = new CSVWriter(fw, exportData);

                        // Total number of columns to write
                        String[] range = newRanges;

                        String[] traits = MainActivity.dt.getAllTraits();

                        csvWriter.writeFile2(concat(range, traits), range.length,
                                MainActivity.dt.findRangeColumns(ep.getString("ImportUniqueName", ""), range),
                                traits, ep.getBoolean("UseDay", false));
                        shareFile(file);
                    } catch (Exception e) {
                        fail = true;
                    }
                }
            } else {
                // Retrieves the data needed for export
                Cursor exportData = MainActivity.dt.getExportDBData(newRanges);

                // Do not proceed if there is no data
                // So it will not override existing files
                if (exportData.getCount() > 0) {
                    try {
                        File file = new File(Constants.FIELDEXPORTPATH,
                                exportFile.getText().toString() + "_database.csv");

                        if (file.exists())
                            file.delete();

                        FileWriter fw = new FileWriter(file);

                        CSVWriter csvWriter = new CSVWriter(fw, exportData);

                        csvWriter.writeFile(newRange, ep.getString("FirstName", "") + "_"
                                + ep.getString("LastName", ""), ep.getString("Location", ""), ep.getBoolean("UseDay", false));
                        shareFile(file);
                    } catch (Exception e) {
                        fail = true;
                    }
                }

                if (fail)
                    return 0;

                // Retrieves the data needed for export
                exportData = MainActivity.dt.getExportExcelData(newRanges);

                // Do not proceed if there is no data
                // So it will not override existing files
                if (exportData.getCount() > 0) {
                    try {
                        File file = new File(Constants.FIELDEXPORTPATH,
                                exportFile.getText().toString() + "_table.csv");

                        if (file.exists())
                            file.delete();

                        FileWriter fw = new FileWriter(file);

                        CSVWriter csvWriter = new CSVWriter(fw, exportData);

                        // Total number of columns to write
                        //String[] range = MainActivity.dt.getRangeColumns();
                        String[] range = newRanges;

                        String[] traits = MainActivity.dt.getAllTraits();

                        csvWriter.writeFile2(concat(range, traits), range.length,
                                MainActivity.dt.findRangeColumns(ep.getString("ImportUniqueName", ""), range),
                                traits, ep.getBoolean("UseDay", false));
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

            if (dialog.isShowing())
                dialog.dismiss();

            if (fail) {
                Toast.makeText(ConfigActivity.this,
                        getString(R.string.exporterror), Toast.LENGTH_LONG)
                        .show();
            }

        }
    }

    /**
     * Scan file to update file list and share exported file
     */
    private void shareFile(File filePath) {
        MediaScannerConnection.scanFile(this, new String[]{filePath.getAbsolutePath()}, null, null);
        Intent intent = new Intent();
        intent.setAction(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(filePath));
        try {
            startActivity(Intent.createChooser(intent, "Sending File..."));
        } finally {
        }
    }

    // Because the import dialog layout is created dynamically, this function
    // loops through child views and checks spinner values for duplicate values
    private int findColValue(LinearLayout parent, int value, boolean allowDuplicate) {
        int count = 0;
        int found = -1;

        for (int i = 0; i < parent.getChildCount(); i++) {
            LinearLayout child = (LinearLayout) parent.getChildAt(i);

            Spinner c = (Spinner) child.findViewById(R.id.map);
            TextView v = (TextView) child.findViewById(R.id.column);

            if (allowDuplicate) {
                if (c.getSelectedItemPosition() == value)
                    return i;
            } else {
                if (c.getSelectedItemPosition() == value) {
                    if (found == -1)
                        found = i;

                    count += 1;
                }
            }
        }

        if (allowDuplicate)
            return -1;
        else {
            if (count > 1)
                return -1;
            else
                return found;
        }
    }

    // Helper function to see if the import column matches any of the spinner values
    // If it does, set it to the appropriate value
    private void findMatchingColumn(Spinner s, String col) {
        if (col.toLowerCase().trim().contains("plot_id"))
            s.setSelection(0);
        else if (col.toLowerCase().trim().contains("range"))
            s.setSelection(1);
        else if (col.toLowerCase().trim().contains("plot"))
            s.setSelection(2);
        else
            s.setSelection(3);
    }

    // Ensure the values in the key column are unique for Excel workbooks
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

    // Ensure the key column has unique values for CSV files
    private boolean verifyUniqueColumnCSV(String path) {
        try {
            HashMap<String, String> check = new HashMap<String, String>();

            FileReader fr = new FileReader(path);

            CSVReader cr = new CSVReader(fr);

            String[] columns = cr.readNext();

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
    private void setSpinner(Spinner spinner, String[] data) {
        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<String>(this, R.layout.spinnerlayout, data);
        spinner.setAdapter(itemsAdapter);
    }

    /*
     * Helper function to create the necessary dialog screens. This is only
     * useful for simple dialogs. Complex dialogs such as choosing traits to
     * rate must be customized and so is not located here.
     */
    private void createDialog(int id) {

        columnFail = false;

        switch (id) {

            case DIALOG_LOAD_FIELDFILECSV:
                isCSV = true;
                showFieldFileDialog();
                break;

            case DIALOG_LOAD_FIELDFILEEXCEL:
                isCSV = false;
                showFieldFileExcelDialog();
                break;

            case DIALOG_LOAD_TRAITS1:
                final String[] traits1 = MainActivity.dt.getRangeColumns();
                showTrait1Dialog(traits1);
                break;

            case DIALOG_LOAD_TRAITS2:
                final String[] traits2 = MainActivity.dt.getRangeColumns();
                showTrait2Dialog(traits2);
                break;

            case DIALOG_LOAD_TRAITS3:
                final String[] traits3 = MainActivity.dt.getRangeColumns();
                showTrait3Dialog(traits3);
                break;

        }

    }

    public void makeToast(String message) {

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showLanguageDialog() {
        final Dialog languageDialog = new Dialog(ConfigActivity.this,
                android.R.style.Theme_Holo_Light_Dialog);
        languageDialog.setTitle(getString(R.string.language));
        languageDialog.setContentView(R.layout.genericdialog);

        android.view.WindowManager.LayoutParams params = languageDialog.getWindow().getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        languageDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);

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
                makeToast(local);
                ed.putString("language", local);
                ed.putString("region", region);
                ed.commit();

                Locale locale2 = new Locale(local, region);
                Locale.setDefault(locale2);
                Configuration config2 = new Configuration();
                config2.locale = locale2;
                getBaseContext().getResources().
                        updateConfiguration(config2, getBaseContext().getResources()
                                .getDisplayMetrics());

                invalidateOptionsMenu();

                loadScreen();
                languageDialog.dismiss();
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.listitem, langArray);
        myList.setAdapter(adapter);
        Button langCloseBtn = (Button) languageDialog
                .findViewById(R.id.closeBtn);

        langCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                languageDialog.dismiss();
            }
        });

        languageDialog.show();
    }

    private String[] prepareSetup() {
        String tagName = "";
        String tagLocation = "";
        String tagDrop1 = "";
        String tagDrop2 = "";
        String tagDrop3 = "";

        if (ep.getString("FirstName", "").length() > 0 | ep.getString("LastName", "").length() > 0)
            tagName += getString(R.string.person) + ": " + ep.getString("FirstName", "")
                    + " " + ep.getString("LastName", "");
        else
            tagName += getString(R.string.person) + ": " + getString(R.string.none);

        if (ep.getString("Location", "").length() > 0)
            tagLocation += getString(R.string.location) + ": " + ep.getString("Location", "");
        else
            tagLocation += getString(R.string.location) + ": " + getString(R.string.none);

        if (ep.getString("DROP1", "").length() > 0)
            tagDrop1 = getString(R.string.drop1) + ": " + ep.getString("DROP1", "");
        else
            tagDrop1 = getString(R.string.drop1) + ": " + getString(R.string.none);

        if (ep.getString("DROP2", "").length() > 0)
            tagDrop2 = getString(R.string.drop2) + ": " + ep.getString("DROP2", "");
        else
            tagDrop2 = getString(R.string.drop2) + ": " + getString(R.string.none);

        if (ep.getString("DROP3", "").length() > 0)
            tagDrop3 = getString(R.string.drop3) + ": " + ep.getString("DROP3", "");
        else
            tagDrop3 = getString(R.string.drop3) + ": " + getString(R.string.none);

        return new String[]{tagName, tagLocation, tagDrop1, tagDrop2, tagDrop3, getString(R.string.clearsettings)};
    }

    private void updateSetupList() {
        ArrayAdapter<String> ga = (ArrayAdapter) setupList.getAdapter();

        ga.clear();

        String[] arrayData = prepareSetup();

        if (arrayData != null) {
            for (String string : arrayData) {
                ga.insert(string, ga.getCount());
            }
        }

        ga.notifyDataSetChanged();
    }

    private void showSaveDialog() {
        final OnItemClickListener saveListener = new OnItemClickListener() {

            public void onItemClick(AdapterView<?> av, View v, int position,
                                    long arg3) {

                if (checkExcel.isChecked()) {
                    if (saveList.getAdapter().getItem(position).toString().toUpperCase().
                            equals(ep.getString("ImportUniqueName", "").toUpperCase())) {
                        saveList.setItemChecked(position, true);
                        return;
                    }
                }
            }
        };

        // As the export filename uses the import file name as well,
        // we parse it out here
        String fName = "";
        SimpleDateFormat timeStamp = new SimpleDateFormat(
                "yyyy.MM.dd", Locale.getDefault());

        if (ep.getString("FieldFile", "").length() > 0) {
            int index = ep.getString("FieldFile", "").lastIndexOf(
                    '.');

            if (index > 0
                    && index <= ep.getString("FieldFile", "")
                    .length() - 2) {
                fName = ep.getString("FieldFile", "").substring(0,
                        index);
            }

        }

        String fFile = ep.getString("FieldFile", "");

        if (fFile.length() > 4 & fFile.toLowerCase().endsWith(".csv")) {
            fFile = fFile.substring(0, fFile.length() - 4);
        }

        exportFile.setText(timeStamp.format(Calendar.getInstance().getTime()) + "_" + fFile);

        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<String>(this, R.layout.listitem_checked, MainActivity.dt.getRangeColumns());
        saveList.setAdapter(itemsAdapter);

        for (int i = 0; i < saveList.getAdapter().getCount(); i++) {
            saveList.setItemChecked(i, true);
        }

        saveDialog.show();
    }

    private void showSetupDialog() {
        String[] array = prepareSetup();
        ArrayList<String> lst = new ArrayList<String>();
        lst.addAll(Arrays.asList(array));

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

                        createDialog(DIALOG_LOAD_TRAITS1);
                        break;

                    case 3:
                        if (MainActivity.dt.getRangeColumns() == null)
                            return;

                        createDialog(DIALOG_LOAD_TRAITS2);
                        break;

                    case 4:
                        if (MainActivity.dt.getRangeColumns() == null)
                            return;

                        createDialog(DIALOG_LOAD_TRAITS3);
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
        LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);

        boolean enabled = service
                .isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!enabled) {
            Intent intent = new Intent(
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

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
                ed.commit();
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

        dialog.setTitle(getString(R.string.currentColumn) + ": " + ep.getString("DROP3", ""));
        dialog.setContentView(R.layout.genericdialog);
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

        dialog.setTitle(getString(R.string.currentColumn) + ": " + ep.getString("DROP2", ""));
        dialog.setContentView(R.layout.genericdialog);
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

        dialog.setTitle(getString(R.string.currentColumn) + ": " + ep.getString("DROP1", ""));
        dialog.setContentView(R.layout.genericdialog);
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
        Editor e = ep.edit();
        e.putString("FieldFile", mChosenFile.substring(mChosenFile.lastIndexOf("/") + 1, mChosenFile.lastIndexOf(".")));
        e.commit();

        createDir(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile",""));
        createDir(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile","")+"/audio");
        createDir(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile","")+"/photo");

        columnFail = false;

        if (mChosenFile.toLowerCase().contains(".xls")) {
            isCSV = false;
            loadXLSFile();
        }

        if (mChosenFile.toLowerCase().contains(".csv")) {
            isCSV = true;
            loadCSVFile();
        }
    }

    private void createDir(String path) {
        File dir = new File(path);
        makeToast(path);
        if (!dir.exists())
            dir.mkdirs();
    }

    private void loadCSVFile() {
        action = DIALOG_LOAD_FIELDFILECSV;

        try {
            importMain.removeAllViews();

            FileReader fr = new FileReader(mChosenFile);
            CSVReader cr = new CSVReader(fr);

            importColumns = cr.readNext();

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

                } else {
                    addRow(importMain, s);
                }
            }
        } catch (Exception n) {

        }

        mUserChoice = "";
        importDirectory = "";

        if (!columnFail)

            importFieldMapDialog.show();
    }

    private void loadXLSFile() {
        action = DIALOG_LOAD_FIELDFILEEXCEL;

        WorkbookSettings wbSettings = new WorkbookSettings();
        wbSettings.setUseTemporaryFileDuringWrite(true);

        try {
            importMain.removeAllViews();

            wb = Workbook.getWorkbook(new File(mChosenFile), wbSettings);

            importColumns = new String[wb.getSheet(0).getColumns()];

            for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                importColumns[s] = wb.getSheet(0).getCell(s, 0).getContents();
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
                } else {
                    addRow(importMain, s);
                }
            }
        } catch (Exception n) {

        }

        if (!columnFail)
            importFieldMapDialog.show();
    }

    private void showFieldFileExcelDialog() {
        final Dialog dialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);

        dialog.setTitle(getString(R.string.choosefieldfile));
        dialog.setContentView(R.layout.genericdialog);
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

                mChosenFile = mFileList[which];

                Editor e = ep.edit();

                e.putString("FieldFile", mChosenFile);

                e.commit();


            }
        });

        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<String>(this, R.layout.listitem, mFileList);
        csvList.setAdapter(itemsAdapter);
        dialog.show();
    }

    // Helper function to add rows to import dialog
    public void addRow(LinearLayout parent, String text) {
        LayoutInflater vi = (LayoutInflater) getApplicationContext().
                getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View v = vi.inflate(R.layout.importrow, null);

        TextView c = (TextView) v.findViewById(R.id.column);
        final Spinner s = (Spinner) v.findViewById(R.id.map);

        c.setText(text);

        String[] likes = new String[4];

        likes[0] = getString(R.string.importunique);
        likes[1] = getString(R.string.importfirst);
        likes[2] = getString(R.string.importsecond);
        likes[3] = getString(R.string.importextra);

        s.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                s.setSelection(position);
                checkImportColumnNames();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        setSpinner(s, likes);
        findMatchingColumn(s, text);
        parent.addView(v);
    }

    // Creates a new thread to do importing
    private Runnable importCSV = new Runnable() {
        public void run() {
            new ImportCSVTask().execute(0);
        }

    };

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

                if (action == DIALOG_LOAD_FIELDFILECSV) {
                    MainActivity.dt.dropRange();
                    MainActivity.dt.createRange(columns);

                    Editor e = ep.edit();

                    e.putString("DROP1", "");
                    e.putString("DROP2", "");
                    e.putString("DROP3", "");

                    e.commit();
                } else {
                    MainActivity.dt.deleteTable(MainActivity.dt.TRAITS);
                }

                data = columns;

                while (data != null) {
                    data = cr.readNext();

                    if (data != null) {
                        switch (action) {
                            case DIALOG_LOAD_FIELDFILECSV:
                                MainActivity.dt.insertRange(columns, data);
                                break;

                            case DIALOG_LOAD_TRAITFILE:
                                MainActivity.dt.insertTraits(data[0], data[1],
                                        data[2], data[3], data[4], data[5],
                                        data[6], "true", "");

                                String[] plots = MainActivity.dt.getPlotID();

                                // For boolean data, always remove existing data
                                // first
                                // Then reinsert the default value
                                if (data[1].equals("boolean")) {
                                    if (plots != null) {
                                        MainActivity.dt.deleteAllBoolean(data[0]);

                                        for (String plot : plots) {
                                            MainActivity.dt.insertUserTraits(plot, data[0],
                                                    "boolean", "false");
                                        }
                                    }
                                }
                                break;
                        }
                    }
                }

                try {
                    cr.close();
                } catch (Exception e) {

                }

                try {
                    fr.close();
                } catch (Exception e) {

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
                ed.putString("ImportUniquePosition", String.valueOf(uPosition));
                ed.putString("ImportFirstPosition", String.valueOf(fPosition));
                ed.putString("ImportSecondPosition", String.valueOf(sPosition));
                ed.putString("ImportExtraPosition", String.valueOf(ePosition));

                ed.putString("ImportUniqueName", importColumns[uPosition]);
                ed.putString("ImportFirstName", importColumns[fPosition]);
                ed.putString("ImportSecondName", importColumns[sPosition]);
                //ed.putString("ImportExtraName", importColumns[ePosition]);

                ed.putBoolean("ImportFieldFinished", true);

                ed.commit();

                MainActivity.reloadData = true;
            }
        }
    }

    private void showNoFieldDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this);

        builder.setTitle(getString(R.string.warning));
        builder.setMessage(getString(R.string.nofieldloaded));

        builder.setPositiveButton(getString(R.string.okay), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        });

        AlertDialog alert = builder.create();
        alert.show();

    }

    private void showNoTraitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this);

        builder.setTitle(getString(R.string.warning));
        builder.setMessage(getString(R.string.notraitloaded));

        builder.setPositiveButton(getString(R.string.okay), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        });

        AlertDialog alert = builder.create();
        alert.show();

    }

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

                if (action == DIALOG_LOAD_FIELDFILEEXCEL) {
                    MainActivity.dt.dropRange();
                    MainActivity.dt.createRange(columns);

                    Editor e = ep.edit();

                    e.putString("DROP1", "");
                    e.putString("DROP2", "");
                    e.putString("DROP3", "");

                    e.commit();
                } else {
                    MainActivity.dt.deleteTable(MainActivity.dt.TRAITS);
                }

                int row = 1;

                while (row < wb.getSheet(0).getRows()) {
                    data = new String[wb.getSheet(0).getColumns()];

                    for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                        data[s] = wb.getSheet(0).getCell(s, row).getContents();
                    }

                    row += 1;

                    if (data != null) {
                        switch (action) {
                            case DIALOG_LOAD_FIELDFILEEXCEL:
                                MainActivity.dt.insertRange(columns, data);
                                break;

                            case DIALOG_LOAD_TRAITFILE:
                                MainActivity.dt.insertTraits(data[0], data[1],
                                        data[2], data[3], data[4], data[5],
                                        data[6], "true", "");

                                String[] plots = MainActivity.dt.getPlotID();

                                // For boolean data, always remove existing data
                                // first
                                // Then reinsert the default value
                                if (data[1].equals("boolean")) {
                                    if (plots != null) {
                                        MainActivity.dt.deleteAllBoolean(data[0]);

                                        for (String plot : plots) {
                                            MainActivity.dt.insertUserTraits(plot, data[0],
                                                    "boolean", "false");
                                        }
                                    }
                                }
                                break;
                        }
                    }
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
                ed.putString("ImportUniquePosition", String.valueOf(uPosition));
                ed.putString("ImportFirstPosition", String.valueOf(fPosition));
                ed.putString("ImportSecondPosition", String.valueOf(sPosition));
                ed.putString("ImportExtraPosition", String.valueOf(ePosition));

                ed.putString("ImportUniqueName", importColumns[uPosition]);
                ed.putString("ImportFirstName", importColumns[fPosition]);
                ed.putString("ImportSecondName", importColumns[sPosition]);
                //ed.putString("ImportExtraName", importColumns[ePosition]);

                ed.putBoolean("ImportFieldFinished", true);

                ed.commit();

                MainActivity.reloadData = true;
            }
        }
    }

    private void showDatabaseDialog() {
        String[] items = new String[1];
        items[0] = getString(R.string.dbreset);

        final Dialog chooseBackupDialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);
        chooseBackupDialog.setTitle(getString(R.string.dbbackup));
        chooseBackupDialog.setContentView(R.layout.config);

        android.view.WindowManager.LayoutParams params = chooseBackupDialog.getWindow().getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        chooseBackupDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);

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
                        showDatabaseResetDialog1();
                        break;
                }
            }
        });

        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<String>(this, R.layout.listitem, items);
        setupList.setAdapter(itemsAdapter);
        chooseBackupDialog.show();
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
                ed.commit();

                dialog.dismiss();

                Toast toast = Toast.makeText(ConfigActivity.this, getString(R.string.resetcomplete), Toast.LENGTH_LONG);
                toast.show();

                try {
                    ConfigActivity.thisActivity.finish();
                } catch (Exception e) {

                }

                try {
                    MainActivity.thisActivity.finish();
                } catch (Exception f) {

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
                    showNoFieldDialog();
                } else if (MainActivity.dt.getTraitColumnsAsString() == null) {
                    showNoTraitDialog();
                } else
                    finish();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

}
