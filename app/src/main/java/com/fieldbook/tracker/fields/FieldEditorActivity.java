package com.fieldbook.tracker.fields;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.dropbox.chooser.android.DbxChooser;
import com.fieldbook.tracker.ConfigActivity;
//import com.fieldbook.tracker.utilities.ApiKeys;
import com.fieldbook.tracker.brapi.BrapiActivity;
import com.fieldbook.tracker.io.CSVReader;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.FileExploreActivity;
import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.tutorial.TutorialFieldActivity;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import jxl.Workbook;
import jxl.WorkbookSettings;

public class FieldEditorActivity extends AppCompatActivity {

    private static Handler mHandler = new Handler();

    public static ListView fieldList;
    public static FieldAdapter mAdapter;

    public static Activity thisActivity;
    public static EditText trait;

    private static String mChosenFile;

    private static SharedPreferences ep;

    private Menu systemMenu;

    private boolean columnFail;
    private boolean specialCharactersFail = false;
    private boolean isCSV;
    private boolean isXLS;

    private static final int DIALOG_LOAD_FIELDFILECSV = 1000;
    private static final int DIALOG_LOAD_FIELDFILEEXCEL = 1001;

    private int action;

    private Workbook wb;

    private String[] importColumns;
    private Dialog importFieldDialog;

    Spinner unique;
    Spinner primary;
    Spinner secondary;

    String uniqueS;
    String primaryS;
    String secondaryS;

    private int idColPosition;

    int exp_id;

    @Override
    public void onDestroy() {
        try {
            TutorialFieldActivity.thisActivity.finish();
        } catch (Exception e) {
            Log.e(Constants.TAG, "" + e.getMessage());
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
        loadData();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        setContentView(R.layout.activity_fields);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setTitle(null);
        getSupportActionBar().getThemedContext();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        if (getActionBar() != null) {
            getActionBar().setHomeButtonEnabled(true);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ConfigActivity.dt.updateExpTable(false, true, false, 0);

        thisActivity = this;
        fieldList = findViewById(R.id.myList);
        mAdapter = new FieldAdapter(thisActivity, ConfigActivity.dt.getAllFieldObjects());
        fieldList.setAdapter(mAdapter);

    }

    private void showFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list, null);

        builder.setTitle(R.string.import_dialog_title)
                .setCancelable(true)
                .setView(layout);

        final AlertDialog importDialog = builder.create();

        android.view.WindowManager.LayoutParams params = importDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        importDialog.getWindow().setAttributes(params);

        ListView myList = layout.findViewById(R.id.myList);

        String[] importArray = new String[3];
        importArray[0] = getString(R.string.import_source_local);
        importArray[1] = getString(R.string.import_source_dropbox);
        importArray[2] = getString(R.string.import_source_brapi);

        //TODO add google drive (requires Google Play Services)
        //importArray[2] = getString(R.string.importgoogle);

        myList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                Intent intent = new Intent();
                switch (which) {
                    case 0:
                        intent.setClassName(FieldEditorActivity.this,
                                FileExploreActivity.class.getName());
                        intent.putExtra("path", Constants.FIELDIMPORTPATH);
                        intent.putExtra("include", new String[]{"csv", "xls"});
                        intent.putExtra("title", getString(R.string.import_dialog_title));
                        startActivityForResult(intent, 1);
                        break;
                    case 1:
                        //DbxChooser mChooser = new DbxChooser(ApiKeys.DROPBOX_APP_KEY);
                        //mChooser.forResultType(DbxChooser.ResultType.FILE_CONTENT).launch(thisActivity, 3);
                        break;
                    case 2:
                        intent.setClassName(FieldEditorActivity.this,
                                BrapiActivity.class.getName());
                        startActivityForResult(intent, 1);
                        break;

                }
                importDialog.dismiss();
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.listitem, importArray);
        myList.setAdapter(adapter);
        Button importCloseBtn = layout.findViewById(R.id.closeBtn);
        importCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                importDialog.dismiss();
            }
        });
        importDialog.show();
    }

    // Helper function to load data
    public static void loadData() {
        try {
            mAdapter = new FieldAdapter(thisActivity, ConfigActivity.dt.getAllFieldObjects());
            fieldList.setAdapter(mAdapter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(com.fieldbook.tracker.fields.FieldEditorActivity.this).inflate(R.menu.menu_fields, menu);

        systemMenu = menu;

        // Check to see if visibility should be toggled
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
        switch (item.getItemId()) {
            case R.id.help:
                Intent intent = new Intent();
                intent.setClassName(com.fieldbook.tracker.fields.FieldEditorActivity.this,
                        TutorialFieldActivity.class.getName());
                startActivity(intent);
                break;

            case R.id.importField:
                showFileDialog();
                break;

            case android.R.id.home:
                fieldCheck();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void fieldCheck() {
        if (!ep.getBoolean("FieldSelected", true)) {
            makeToast(getString(R.string.fields_select_study));
        } else {
            MainActivity.reloadData = true;
            finish();
        }
    }

    public void onBackPressed() {
        MainActivity.reloadData = true;
        fieldCheck();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 2) {
            if (resultCode == RESULT_OK) {
                mChosenFile = data.getStringExtra("result");
                showFieldFileDialog();
            }
        }

        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                mChosenFile = data.getStringExtra("result");
                showFieldFileDialog();
            }
        }

        if (requestCode == 3) {
            if (resultCode == RESULT_OK) {
                DbxChooser.Result result = new DbxChooser.Result(data);
                saveFileFromUri(result.getLink(), result.getName());
                mChosenFile = Constants.FIELDIMPORTPATH + "/" + result.getName();
                showFieldFileDialog();
            }
        }
    }

    private void saveFileFromUri(Uri sourceUri, String fileName) {
        String sourceFilename = sourceUri.getPath();
        String destinationFilename = Constants.FIELDIMPORTPATH + File.separatorChar + fileName;

        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;

        try {
            bis = new BufferedInputStream(new FileInputStream(sourceFilename));
            bos = new BufferedOutputStream(new FileOutputStream(destinationFilename, false));
            byte[] buf = new byte[1024];
            int length;

            while ((length = bis.read(buf)) > 0) {
                bos.write(buf, 0, length);
            }

        } catch (IOException ignore) {

        } finally {
            try {
                if (bis != null) bis.close();
                if (bos != null) bos.close();
            } catch (IOException ignore) {

            }
        }

        File tempFile = new File(destinationFilename);
        scanFile(tempFile);
    }

    private void showFieldFileDialog() {
        //todo get URI instead of string
        Editor e = ep.edit();
        e.putString("FieldFile", mChosenFile.substring(mChosenFile.lastIndexOf("/") + 1, mChosenFile.lastIndexOf(".")));
        e.apply();

        if (ConfigActivity.dt.checkFieldName(ep.getString("FieldFile", ""))>= 0) {
            makeToast(getString(R.string.fields_study_exists_message));
            SharedPreferences.Editor ed = ep.edit();
            ed.putString("FieldFile", null);
            ed.putBoolean("ImportFieldFinished", false);
            ed.putBoolean("FieldSelected",false);
            ed.apply();
            return;
        }

        columnFail = false;

        if (mChosenFile.toLowerCase().contains(".xls")) {
            isCSV = false;
            isXLS = true;
            action = DIALOG_LOAD_FIELDFILEEXCEL;
        }

        if (mChosenFile.toLowerCase().contains(".csv")) {
            isCSV = true;
            isXLS = false;
            action = DIALOG_LOAD_FIELDFILECSV;
        }

        if (!mChosenFile.toLowerCase().contains(".csv") && !mChosenFile.toLowerCase().contains(".xls")) {
            makeToast(getString(R.string.import_error_unsupported));
        } else {
            makeDirs();
            loadFile();
        }
    }

    private void makeDirs() {
        createDir(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", ""));
        createDir(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/audio");
        createDir(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/photos");
        createDir(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/photos/.thumbnails");
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
            } catch (IOException ignore) {
            }
        }
    }

    private void loadFile() {
        if (action == DIALOG_LOAD_FIELDFILEEXCEL) {
            WorkbookSettings wbSettings = new WorkbookSettings();
            wbSettings.setUseTemporaryFileDuringWrite(true);

            try {
                wb = Workbook.getWorkbook(new File(mChosenFile), wbSettings);
                importColumns = new String[wb.getSheet(0).getColumns()];

                for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                    importColumns[s] = wb.getSheet(0).getCell(s, 0).getContents();
                }

            } catch (Exception ignore) {
            }

        }
        if (action == DIALOG_LOAD_FIELDFILECSV) {
            try {
                FileReader fr = new FileReader(mChosenFile);
                CSVReader cr = new CSVReader(fr);
                importColumns = cr.readNext();
            } catch (Exception ignore) {
            }
        }

        String[] reservedNames = new String[]{"id"};

        List<String> list = Arrays.asList(reservedNames);

        //TODO causing crash
        for (String s : importColumns) {
            if (DataHelper.hasSpecialChars(s)) {
                columnFail = true;
                makeToast(getString(R.string.import_error_columns) + " (\"" + s + "\")");
                break;
            }

            if (list.contains(s.toLowerCase())) {
                columnFail = true;
                makeToast(getString(R.string.import_error_column_name) + " \"" + s + "\"");
                break;
            }
        }

        if (!columnFail) {
            importDialog(importColumns);
        }
    }

    private void importDialog(String[] columns) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_import, null);

        builder.setTitle(R.string.import_dialog_title)
                .setCancelable(true)
                .setView(layout);

        importFieldDialog = builder.create();

        android.view.WindowManager.LayoutParams params2 = importFieldDialog.getWindow().getAttributes();
        params2.width = LayoutParams.MATCH_PARENT;
        importFieldDialog.getWindow().setAttributes(params2);

        Button startImport = layout.findViewById(R.id.okBtn);

        startImport.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                if (checkImportColumnNames()) {
                    importFieldDialog.dismiss();
                    mHandler.post(importRunnable);
                }
            }
        });

        unique = layout.findViewById(R.id.uniqueSpin);
        primary = layout.findViewById(R.id.primarySpin);
        secondary = layout.findViewById(R.id.secondarySpin);

        setSpinner(unique, columns, "ImportUniqueName");
        setSpinner(primary, columns, "ImportFirstName");
        setSpinner(secondary, columns, "ImportSecondName");

        importFieldDialog.show();
    }

    // Creates a new thread to do importing
    private Runnable importRunnable = new Runnable() {
        public void run() {
            new ImportRunnableTask().execute(0);
        }
    };

    private class ImportRunnableTask extends AsyncTask<Integer, Integer, Integer> {
        ProgressDialog dialog;

        boolean fail;
        boolean uniqueFail;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = new ProgressDialog(FieldEditorActivity.this);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html.fromHtml(getString(R.string.import_dialog_importing)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            try {

                String[] data;
                String[] columns;

                if (isCSV) {
                    if (!verifyUniqueColumn(true, false, mChosenFile, null)) {
                        uniqueFail = true;
                        return 0;
                    }

                    if (specialCharactersFail) {
                        return 0;
                    }

                    FileReader fr = new FileReader(mChosenFile);
                    CSVReader cr = new CSVReader(fr);

                    columns = cr.readNext();

                    FieldObject f = new FieldObject();
                    f.setExp_name(ep.getString("FieldFile", ""));
                    f.setExp_alias(ep.getString("FieldFile", ""));
                    f.setUnique_id(uniqueS);
                    f.setPrimary_id(primaryS);
                    f.setSecondary_id(secondaryS);

                    exp_id = ConfigActivity.dt.createField(f, Arrays.asList(columns));

                    data = columns;

                    DataHelper.db.beginTransaction();

                    try {
                        while (data != null) {
                            data = cr.readNext();

                            if (data != null) {
                                ConfigActivity.dt.createFieldData(exp_id, Arrays.asList(columns), Arrays.asList(data));
                            }
                        }

                        DataHelper.db.setTransactionSuccessful();
                    } finally {
                        DataHelper.db.endTransaction();
                    }

                    try {
                        cr.close();
                        fr.close();
                    } catch (Exception ignore) {
                    }

                    ConfigActivity.dt.close();
                    ConfigActivity.dt.open();

                    File newDir = new File(mChosenFile);
                    newDir.mkdirs();
                }

                if (isXLS) {
                    if (!verifyUniqueColumn(false, true, null, wb)) {
                        uniqueFail = true;
                        return 0;
                    }

                    if (specialCharactersFail) {
                        return 0;
                    }

                    columns = new String[wb.getSheet(0).getColumns()];

                    for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                        columns[s] = wb.getSheet(0).getCell(s, 0).getContents();
                    }

                    FieldObject ftmp = new FieldObject();
                    ftmp.setExp_name(ep.getString("FieldFile", ""));
                    ftmp.setExp_alias(ep.getString("FieldFile", ""));
                    ftmp.setUnique_id(uniqueS);
                    ftmp.setPrimary_id(primaryS);
                    ftmp.setSecondary_id(secondaryS);
                    exp_id = ConfigActivity.dt.createField(ftmp, Arrays.asList(columns));

                    int row = 1;

                    DataHelper.db.beginTransaction();

                    try {
                        while (row < wb.getSheet(0).getRows()) {
                            data = new String[wb.getSheet(0).getColumns()];

                            for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                                data[s] = wb.getSheet(0).getCell(s, row).getContents();
                            }

                            row += 1;

                            ConfigActivity.dt.createFieldData(exp_id, Arrays.asList(columns), Arrays.asList(data));
                        }

                        DataHelper.db.setTransactionSuccessful();
                    } finally {
                        DataHelper.db.endTransaction();
                    }

                    ConfigActivity.dt.close();
                    ConfigActivity.dt.open();

                    File newDir = new File(mChosenFile);
                    newDir.mkdirs();
                }

                ConfigActivity.dt.updateExpTable(true, false, false, exp_id);

                Editor e = ep.edit();
                e.putString("DROP1", null);
                e.putString("DROP2", null);
                e.putString("DROP3", null);
                e.putInt("ExpID", exp_id);
                e.apply();

            } catch (Exception e) {
                e.printStackTrace();
                fail = true;

                ConfigActivity.dt.close();
                ConfigActivity.dt.open();
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (dialog.isShowing())
                dialog.dismiss();

            if (fail | uniqueFail | specialCharactersFail) {
                ConfigActivity.dt.deleteField(exp_id);
                SharedPreferences.Editor ed = ep.edit();
                ed.putString("FieldFile", null);
                ed.putBoolean("ImportFieldFinished", false);
                ed.apply();
            }
            if (fail) {
                makeToast(getString(R.string.import_error_general));
            } else if (uniqueFail) {
                makeToast(getString(R.string.import_error_unique));
            } else if (specialCharactersFail) {
                makeToast(getString(R.string.import_error_unique_characters_illegal));
            } else {
                Editor ed = ep.edit();
                ed.putString("ImportUniqueName", unique.getSelectedItem().toString());
                ed.putString("ImportFirstName", primary.getSelectedItem().toString());
                ed.putString("ImportSecondName", secondary.getSelectedItem().toString());
                ed.putBoolean("ImportFieldFinished", true);
                ed.putBoolean("FieldSelected",true);
                ed.apply();

                MainActivity.reloadData = true;
                loadData();
                ConfigActivity.dt.switchField(exp_id);
            }
        }
    }

    private boolean verifyUniqueColumn(Boolean csv, Boolean excel, String path, Workbook wb) {
        if (excel) {
            HashMap<String, String> check = new HashMap<>();

            for (int s = 0; s < wb.getSheet(0).getRows(); s++) {
                String value = wb.getSheet(0).getCell(idColPosition, s).getContents();

                if (check.containsKey(value)) {
                    return false;
                } else {
                    check.put(value, value);
                }

                if (value.contains("/") || value.contains("\\")) {
                    specialCharactersFail = true;
                }
            }

            return ConfigActivity.dt.checkUnique(check);
        }

        if (csv) {
            try {
                HashMap<String, String> check = new HashMap<>();
                FileReader fr = new FileReader(path);
                CSVReader cr = new CSVReader(fr);
                String[] columns = cr.readNext();

                while (columns != null) {
                    columns = cr.readNext();

                    if (columns != null) {
                        if (check.containsKey(columns[idColPosition])) {
                            cr.close();
                            return false;
                        } else {
                            check.put(columns[idColPosition], columns[idColPosition]);
                        }

                        if (columns[idColPosition].contains("/") || columns[idColPosition].contains("\\")) {
                            specialCharactersFail = true;
                        }
                    }
                }

                return ConfigActivity.dt.checkUnique(check);

            } catch (Exception n) {
                n.printStackTrace();
                return false;
            }
        }

        return false;
    }

    // Helper function to set spinner adapter and listener
    private void setSpinner(Spinner spinner, String[] data, String pref) {
        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(this, R.layout.custom_spinnerlayout, data);
        spinner.setAdapter(itemsAdapter);
        int spinnerPosition = itemsAdapter.getPosition(ep.getString(pref, itemsAdapter.getItem(0)));
        spinner.setSelection(spinnerPosition);
    }

    // Validate that column choices are different from one another
    private boolean checkImportColumnNames() {
        uniqueS = unique.getSelectedItem().toString();
        primaryS = primary.getSelectedItem().toString();
        secondaryS = secondary.getSelectedItem().toString();

        idColPosition = unique.getSelectedItemPosition();

        if (uniqueS.equals(primaryS) || uniqueS.equals(secondaryS) || primaryS.equals(secondaryS)) {
            makeToast(getString(R.string.import_error_column_choice));
        }

        return true;
    }

    public static void makeToast(String message) {
        Toast.makeText(FieldEditorActivity.thisActivity, message, Toast.LENGTH_SHORT).show();
    }

    private static void scanFile(File filePath) {
        MediaScannerConnection.scanFile(thisActivity, new String[]{filePath.getAbsolutePath()}, null, null);
    }
}