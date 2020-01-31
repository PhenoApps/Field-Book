package com.fieldbook.tracker.fields;

import android.Manifest;
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
import com.fieldbook.tracker.utilities.ApiKeys;
import com.fieldbook.tracker.brapi.BrapiActivity;
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
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class FieldEditorActivity extends AppCompatActivity {

    private static final int DIALOG_LOAD_FIELDFILECSV = 1000;
    private static final int DIALOG_LOAD_FIELDFILEEXCEL = 1001;
    public static ListView fieldList;
    public static FieldAdapter mAdapter;
    public static Activity thisActivity;
    public static EditText trait;
    private static Handler mHandler = new Handler();
    private static FieldFile.FieldFileBase fieldFile;
    private static SharedPreferences ep;
    private final int PERMISSIONS_REQUEST_STORAGE = 998;
    Spinner unique;
    Spinner primary;
    Spinner secondary;
    int exp_id;
    private Menu systemMenu;
    private Dialog importFieldDialog;
    private int idColPosition;
    // Creates a new thread to do importing
    private Runnable importRunnable = new Runnable() {
        public void run() {
            new ImportRunnableTask().execute(0);
        }
    };

    // Helper function to load data
    public static void loadData() {
        try {
            mAdapter = new FieldAdapter(thisActivity, ConfigActivity.dt.getAllFieldObjects());
            fieldList.setAdapter(mAdapter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void makeToast(String message) {
        Toast.makeText(FieldEditorActivity.thisActivity, message, Toast.LENGTH_SHORT).show();
    }

    private static void scanFile(File filePath) {
        MediaScannerConnection.scanFile(thisActivity, new String[]{filePath.getAbsolutePath()}, null, null);
    }

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

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.settings_fields));
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        thisActivity = this;
        if (ConfigActivity.dt == null) {    // when resuming
            ConfigActivity.dt = new DataHelper(this);
        }
        ConfigActivity.dt.updateExpTable(false, true, false, 0);
        fieldList = findViewById(R.id.myList);
        mAdapter = new FieldAdapter(thisActivity, ConfigActivity.dt.getAllFieldObjects());
        fieldList.setAdapter(mAdapter);

    }

    private void showFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list, null);

        builder.setTitle(R.string.import_dialog_title_fields)
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
                switch (which) {
                    case 0:
                        loadLocalPermission();
                        break;
                    case 1:
                        loadDropbox();
                        break;
                    case 2:
                        loadBrAPI();
                        break;

                }
                importDialog.dismiss();
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, importArray);
        myList.setAdapter(adapter);
        Button importCloseBtn = layout.findViewById(R.id.closeBtn);
        importCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                importDialog.dismiss();
            }
        });
        importDialog.show();
    }

    public void loadLocal() {
        Intent intent = new Intent();

        intent.setClassName(FieldEditorActivity.this,
                FileExploreActivity.class.getName());
        intent.putExtra("path", Constants.FIELDIMPORTPATH);
        intent.putExtra("include", new String[]{"csv", "xls"});
        intent.putExtra("title", getString(R.string.import_dialog_title_fields));
        startActivityForResult(intent, 1);
    }

    public void loadDropbox() {
        DbxChooser mChooser = new DbxChooser(ApiKeys.DROPBOX_APP_KEY);
        mChooser.forResultType(DbxChooser.ResultType.FILE_CONTENT).launch(thisActivity, 3);
    }

    public void loadBrAPI() {
        Intent intent = new Intent();

        intent.setClassName(FieldEditorActivity.this,
                BrapiActivity.class.getName());
        startActivityForResult(intent, 1);
    }

    //TODO
    public void loadBox() {

    }

    //TODO
    public void loadGoogleDrive() {

    }

    //TODO
    public void loadOneDrive() {

    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_STORAGE)
    public void loadLocalPermission() {
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            loadLocal();
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_import),
                    PERMISSIONS_REQUEST_STORAGE, perms);
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
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 2) {
            if (resultCode == RESULT_OK) {
                final String chosenFile = data.getStringExtra("result");
                showFieldFileDialog(chosenFile);
            }
        }

        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                final String chosenFile = data.getStringExtra("result");
                showFieldFileDialog(chosenFile);
            }
        }

        if (requestCode == 3) {
            if (resultCode == RESULT_OK) {
                DbxChooser.Result result = new DbxChooser.Result(data);
                saveFileFromUri(result.getLink(), result.getName());
                final String chosenFile = Constants.FIELDIMPORTPATH + "/" + result.getName();
                showFieldFileDialog(chosenFile);
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

    private void showFieldFileDialog(final String chosenFile) {
        fieldFile = FieldFile.create(chosenFile);
        //todo get URI instead of string
        Editor e = ep.edit();
        e.putString("FieldFile", fieldFile.getStem());
        e.apply();

        if (ConfigActivity.dt.checkFieldName(fieldFile.getStem()) >= 0) {
            makeToast(getString(R.string.fields_study_exists_message));
            SharedPreferences.Editor ed = ep.edit();
            ed.putString("FieldFile", null);
            ed.putBoolean("ImportFieldFinished", false);
            ed.putBoolean("FieldSelected", false);
            ed.apply();
            return;
        }

        if (fieldFile.isOther()) {
            makeToast(getString(R.string.import_error_unsupported));
        }

        makeDirs(fieldFile.getStem());
        loadFile(fieldFile);
    }

    private void makeDirs(final String stem) {
        final String dir = Constants.PLOTDATAPATH + "/" + stem;
        createDir(dir);
        createDir(dir + "/audio");
        createDir(dir + "/photos");
        createDir(dir + "/photos/.thumbnails");
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

    private void loadFile(FieldFile.FieldFileBase fieldFile) {
        String[] importColumns = fieldFile.getColumns();

        String[] reservedNames = new String[]{"id"};

        List<String> list = Arrays.asList(reservedNames);

        //TODO causing crash
        for (String s : importColumns) {
            if (DataHelper.hasSpecialChars(s)) {
                makeToast(getString(R.string.import_error_columns) + " (\"" + s + "\")");
                return;
            }

            if (list.contains(s.toLowerCase())) {
                makeToast(getString(R.string.import_error_column_name) + " \"" + s + "\"");
                return;
            }
        }

        importDialog(importColumns);
    }

    private void importDialog(String[] columns) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_import, null);

        builder.setTitle(R.string.import_dialog_title_fields)
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

    private boolean verifyUniqueColumn(FieldFile.FieldFileBase fieldFile) {
        HashMap<String, String> check = fieldFile.getColumnSet(idColPosition);
        if (check.isEmpty()) {
            return false;
        } else {
            return ConfigActivity.dt.checkUnique(check);
        }
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
        final String uniqueS = unique.getSelectedItem().toString();
        final String primaryS = primary.getSelectedItem().toString();
        final String secondaryS = secondary.getSelectedItem().toString();

        idColPosition = unique.getSelectedItemPosition();

        if (uniqueS.equals(primaryS) || uniqueS.equals(secondaryS) || primaryS.equals(secondaryS)) {
            makeToast(getString(R.string.import_error_column_choice));
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

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
                if (!verifyUniqueColumn(fieldFile)) {
                    uniqueFail = true;
                    return 0;
                }

                if (fieldFile.hasSpecialCharasters()) {
                    return 0;
                }

                fieldFile.open();
                String[] data;
                String[] columns = fieldFile.readNext();

                FieldObject f = fieldFile.createFieldObject();
                f.setUnique_id(unique.getSelectedItem().toString());
                f.setPrimary_id(primary.getSelectedItem().toString());
                f.setSecondary_id(secondary.getSelectedItem().toString());

                exp_id = ConfigActivity.dt.createField(f, Arrays.asList(columns));

                DataHelper.db.beginTransaction();

                try {
                    while (true) {
                        data = fieldFile.readNext();
                        if (data == null)
                            break;

                        ConfigActivity.dt.createFieldData(exp_id, Arrays.asList(columns), Arrays.asList(data));
                    }

                    DataHelper.db.setTransactionSuccessful();
                } finally {
                    DataHelper.db.endTransaction();
                }

                fieldFile.close();

                ConfigActivity.dt.close();
                ConfigActivity.dt.open();

                File newDir = new File(fieldFile.getPath());
                newDir.mkdirs();

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

            if (fail | uniqueFail | fieldFile.hasSpecialCharasters()) {
                ConfigActivity.dt.deleteField(exp_id);
                SharedPreferences.Editor ed = ep.edit();
                ed.putString("FieldFile", null);
                ed.putBoolean("ImportFieldFinished", false);
                ed.apply();
            }
            if (fail) {
//                makeToast(getString(R.string.import_error_general));
            } else if (uniqueFail) {
                makeToast(getString(R.string.import_error_unique));
            } else if (fieldFile.hasSpecialCharasters()) {
                makeToast(getString(R.string.import_error_unique_characters_illegal));
            } else {
                Editor ed = ep.edit();
                ed.putString("ImportUniqueName", unique.getSelectedItem().toString());
                ed.putString("ImportFirstName", primary.getSelectedItem().toString());
                ed.putString("ImportSecondName", secondary.getSelectedItem().toString());
                ed.putBoolean("ImportFieldFinished", true);
                ed.putBoolean("FieldSelected", true);
                ed.apply();

                MainActivity.reloadData = true;
                loadData();
                ConfigActivity.dt.switchField(exp_id);
            }
        }
    }
}