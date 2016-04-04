package com.fieldbook.tracker.Fields;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;
import android.support.v7.app.AppCompatActivity;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.fieldbook.tracker.CSV.CSVReader;
import com.fieldbook.tracker.Constants;
import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.FileExploreActivity;
import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.Tutorial.TutorialFieldActivity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import jxl.Workbook;
import jxl.WorkbookSettings;

public class FieldEditorActivity extends AppCompatActivity {

    private static Handler mHandler = new Handler();

    private static String TAG = "Field Book";

    public static ListView fieldList;
    public static FieldAdapter mAdapter;

    public static Activity thisActivity;
    public static EditText trait;

    private static String mChosenFile;

    private static SharedPreferences ep;


    private FieldObject o;

    private Menu systemMenu;

    private Toolbar toolbar;

    private boolean columnFail;
    private boolean isCSV;

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

    @Override
    public void onDestroy() {
        try {
            TutorialFieldActivity.thisActivity.finish();
        } catch (Exception e) {
            Log.e(TAG, "" + e.getMessage());
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

        // Enforce internal language change
        String local = ep.getString("language", "en");
        String region = ep.getString("region", "");
        Locale locale2 = new Locale(local, region);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2, getBaseContext().getResources()
                .getDisplayMetrics());

        setContentView(R.layout.fields_activity);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setTitle(null);
        getSupportActionBar().getThemedContext();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        if (getActionBar() != null) {
            getActionBar().setHomeButtonEnabled(true);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        //MainActivity.dt.updateEditedDate();

        thisActivity = this;
        fieldList = (ListView) findViewById(R.id.myList);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.newTrait);
        fab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                importFieldDialog();
            }
        });
    }


    // Helper function to load data
    public static void loadData() {
        try {
            //mAdapter = new FieldAdapter(thisActivity, MainActivity.dt.getAllFieldObjects());
            fieldList.setAdapter(mAdapter);
        } catch (Exception e) {
            ErrorLog("LoadDataError.txt", "" + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(com.fieldbook.tracker.Fields.FieldEditorActivity.this).inflate(R.menu.fieldmenu, menu);

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
                intent.setClassName(com.fieldbook.tracker.Fields.FieldEditorActivity.this,
                        TutorialFieldActivity.class.getName());
                startActivity(intent);
                break;

            case R.id.importField:
                importFieldDialog();
                break;

            case android.R.id.home:
                fieldCheck();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void fieldCheck(){
        if(ep.getString("FieldFile",null)==null) {
            makeToast(getString(R.string.choose_new_default));
        } else {
            MainActivity.reloadData = true;
            finish();
        }
    }

    private void importFieldDialog() {
        Intent intent = new Intent();

        intent.setClassName(FieldEditorActivity.this,
                FileExploreActivity.class.getName());
        intent.putExtra("path", Constants.FIELDIMPORTPATH);
        intent.putExtra("include", new String[]{"csv", "xls"});
        intent.putExtra("title", getString(R.string.importfields));
        startActivityForResult(intent, 2);
    }

    public void onBackPressed() {
        MainActivity.reloadData = true;
        this.finish();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 2) {
            if (resultCode == RESULT_OK) {
                mChosenFile = data.getStringExtra("result");
                showFieldFileDialog();
            }
        }
    }

    private void showFieldFileDialog() {
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
            } catch (IOException e) {
                ErrorLog("DirectoryError.txt", "" + e.getMessage());
            }
        }
    }

    private void loadFile() {
        final String fieldName = mChosenFile.substring(mChosenFile.lastIndexOf("/") + 1, mChosenFile.lastIndexOf("."));

        if (action == DIALOG_LOAD_FIELDFILEEXCEL) {
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
        if (action == DIALOG_LOAD_FIELDFILECSV) {
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
                "group", "having", "if", "ignore", "immediate", "in", "id", "index", "indexed", "initially",
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
                Toast.makeText(FieldEditorActivity.this, getString(R.string.columnfail) + " (\"" + s + "\")", Toast.LENGTH_LONG).show();
                break;
            }
            if (list.contains(s.toLowerCase())) {
                columnFail = true;
                Toast.makeText(FieldEditorActivity.this, getString(R.string.columnfail) + " (\"" + s + "\")", Toast.LENGTH_LONG).show();
                break;
            }
        }

        /*if (MainActivity.dt.getImportFieldExists(fieldName)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

            builder.setTitle(this.getString(R.string.field_exists));
            builder.setMessage(this.getString(R.string.field_exists_message));

            builder.setPositiveButton(this.getString(R.string.yes), new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    if (!columnFail) {
                        //todo get URI instead of string
                        Editor e = ep.edit();
                        e.putString("FieldFile", fieldName);
                        e.commit();

                        makeDirs();
                        importDialog(importColumns);
                    }
                }
            });

            builder.setNegativeButton(this.getString(R.string.no), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    columnFail = true;
                    dialog.dismiss();
                }

            });

            AlertDialog alert = builder.create();
            alert.show();
        } else {
            Editor e = ep.edit();
            e.putString("FieldFile", fieldName);
            e.commit();

            makeDirs();
            importDialog(importColumns);
        } */
    }

    private void importDialog(String[] columns) {
        importFieldDialog = new Dialog(this, R.style.AppDialog);
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
                    uniqueS = unique.getSelectedItem().toString();
                    primaryS = primary.getSelectedItem().toString();
                    secondaryS = secondary.getSelectedItem().toString();

                    importFieldDialog.dismiss();
                    mHandler.post(importRunnable);
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

    // Helper function to set spinner adapter and listener
    private void setSpinner(Spinner spinner, String[] data, String pref) {
        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(this, R.layout.spinnerlayout, data);
        spinner.setAdapter(itemsAdapter);

        int spinnerPosition = itemsAdapter.getPosition(ep.getString(pref, itemsAdapter.getItem(0)));
        spinner.setSelection(spinnerPosition);
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

    //TODO merge with verfiyuniquecolumncsv
    private boolean verifyUniqueColumn(Workbook wb) {
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

    private boolean verifyUniqueColumn(String path) {
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
            ErrorLog("VerifyUniqueError.txt", "" + n.getMessage());
            n.printStackTrace();
            return false;
        }
    }

    // Creates a new thread to do importing
    private Runnable importRunnable = new Runnable() {
        public void run() {
            new ImportTask().execute(0);
        }
    };

    private class ImportTask extends AsyncTask<Integer, Integer, Integer> {
        ProgressDialog dialog;

        boolean fail;
        boolean uniqueFail;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            dialog = new ProgressDialog(FieldEditorActivity.this);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html
                    .fromHtml(getString(R.string.importmsg)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            if (action == DIALOG_LOAD_FIELDFILECSV) {
                try {
                    String[] data;
                    String[] columns;

                    //verify unique
                    if (!verifyUniqueColumn(mChosenFile)) {
                        uniqueFail = true;
                        return 0;
                    }

                    FileReader fr = new FileReader(mChosenFile);
                    CSVReader cr = new CSVReader(fr);
                    columns = cr.readNext();

                    //MainActivity.dt.dropTable(ep.getString("FieldFile", ""));
                    //MainActivity.dt.createTable(ep.getString("FieldFile", ""), columns);
                    //MainActivity.dt.deleteExpId(ep.getString("FieldFile", ""));

                    //MainActivity.importId = null;
                    //MainActivity.importId = String.valueOf(MainActivity.dt.insertExpId(ep.getString("FieldFile", ""), uniqueS, primaryS, secondaryS, Integer.toString(cr.countLines()), "", "")); //TODO x coord, y coord

                    Editor e = ep.edit();
                    //e.putString("ImportID",MainActivity.importId);
                    e.putString("DROP1", null);
                    e.putString("DROP2", null);
                    e.putString("DROP3", null);
                    e.apply();

                    fr = new FileReader(mChosenFile);
                    cr = new CSVReader(fr);
                    columns = cr.readNext();

                    data = columns;

                    while (data != null) {
                        data = cr.readNext();

                        if (data != null) {
                            //MainActivity.dt.insertRange(MainActivity.importId, "field_" + ep.getString("FieldFile", ""), columns, data);
                        }
                    }

                    try {
                        cr.close();
                    } catch (Exception f) {
                        ErrorLog("ImportError.txt", "" + f.getMessage());
                    }

                    try {
                        fr.close();
                    } catch (Exception f) {
                        ErrorLog("ImportError.txt", "" + f.getMessage());
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
                    ErrorLog("ImportError.txt", "" + e.getMessage());
                    e.printStackTrace();
                    fail = true;

                    MainActivity.dt.close();
                    MainActivity.dt.open();
                }

            }

            if (action == DIALOG_LOAD_FIELDFILEEXCEL) {
                try {
                    String[] data;
                    String[] columns;

                    //verify unique
                    if (!verifyUniqueColumn(wb)) {
                        uniqueFail = true;
                        return 0;
                    }

                    columns = new String[wb.getSheet(0).getColumns()];

                    for (int s = 0; s < wb.getSheet(0).getColumns(); s++) {
                        columns[s] = wb.getSheet(0).getCell(s, 0).getContents();
                    }

                    //MainActivity.dt.dropTable(ep.getString("FieldFile", ""));
                    //MainActivity.dt.createTable(ep.getString("FieldFile", ""), columns);
                    //MainActivity.dt.deleteExpId(ep.getString("FieldFile", ""));

                    //MainActivity.importId = null;
                    //MainActivity.importId = String.valueOf(MainActivity.dt.insertExpId(ep.getString("FieldFile", ""), uniqueS, primaryS, secondaryS, Integer.toString(wb.getSheet(0).getRows()), "", "")); //TODO x coord, y coord

                    Editor e = ep.edit();
                    //e.putString("ImportID",MainActivity.importId);
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

                        //MainActivity.dt.insertRange(MainActivity.importId,"field_" + ep.getString("FieldFile", ""),columns, data);
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
                    ErrorLog("ImportError.txt", "" + e.getMessage());
                    e.printStackTrace();
                    fail = true;

                    MainActivity.dt.close();
                    MainActivity.dt.open();
                }

            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (dialog.isShowing())
                dialog.dismiss();

            if (fail)
                Toast.makeText(FieldEditorActivity.this, getString(R.string.importerror),
                        Toast.LENGTH_LONG).show();
            else if (uniqueFail)
                Toast.makeText(FieldEditorActivity.this, getString(R.string.importuniqueerror),
                        Toast.LENGTH_LONG).show();
            else {
                Editor ed = ep.edit();
                ed.putString("ImportUniqueName", unique.getSelectedItem().toString());
                ed.putString("ImportFirstName", primary.getSelectedItem().toString());
                ed.putString("ImportSecondName", secondary.getSelectedItem().toString());
                ed.putBoolean("ImportFieldFinished", true);
                ed.apply();

                MainActivity.reloadData = true;

                loadData();
            }
        }
    }

    public static void ErrorLog(String sFileName, String sErrMsg) {
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
            Log.e(TAG, "" + e.getMessage());
        }
    }

    public static void makeToast(String message) {

        Toast.makeText(FieldEditorActivity.thisActivity, message, Toast.LENGTH_SHORT).show();
    }

    private static void scanFile(File filePath) {
        MediaScannerConnection.scanFile(thisActivity, new String[]{filePath.getAbsolutePath()}, null, null);
    }
}
