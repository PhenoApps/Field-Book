package com.fieldbook.tracker.activities;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AlertDialog;

import android.provider.OpenableColumns;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.fieldbook.tracker.adapters.FieldAdapter;
import com.fieldbook.tracker.objects.FieldFileObject;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.fieldbook.tracker.utilities.DialogUtils;
import com.fieldbook.tracker.utilities.Utils;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;

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
    private static FieldFileObject.FieldFileBase fieldFile;
    private static SharedPreferences ep;
    private final int PERMISSIONS_REQUEST_STORAGE = 998;
    Spinner unique;
    Spinner primary;
    Spinner secondary;
    int exp_id;
    private Menu systemMenu;
    private AlertDialog importFieldDialog;
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

    private static void scanFile(File filePath) {
        MediaScannerConnection.scanFile(thisActivity, new String[]{filePath.getAbsolutePath()}, null, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (systemMenu != null) {
            systemMenu.findItem(R.id.help).setVisible(ep.getBoolean("Tips", false));
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
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list_buttonless, null);

        ListView importSourceList = layout.findViewById(R.id.myList);
        String[] importArray = new String[3];
        importArray[0] = getString(R.string.import_source_local);
        importArray[1] = getString(R.string.import_source_cloud);
        importArray[2] = getString(R.string.import_source_brapi);


        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, importArray);
        importSourceList.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.import_dialog_title_fields)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });

        final AlertDialog importDialog = builder.create();
        importDialog.show();
        DialogUtils.styleDialogs(importDialog);

        android.view.WindowManager.LayoutParams params = importDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        importDialog.getWindow().setAttributes(params);

        importSourceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                switch (which) {
                    case 0:
                        loadLocalPermission();
                        break;
                    case 1:
                        loadCloud();
                        break;
                    case 2:
                        loadBrAPI();
                        break;

                }
                importDialog.dismiss();
            }
        });
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

    public void loadBrAPI() {
        Intent intent = new Intent();

        intent.setClassName(FieldEditorActivity.this,
                BrapiActivity.class.getName());
        startActivityForResult(intent, 1);
    }

    public void loadCloud() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");;
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, "cloudFile"), 5);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getApplicationContext(), "No suitable File Manager was found.", Toast.LENGTH_SHORT).show();
        }
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
        new MenuInflater(FieldEditorActivity.this).inflate(R.menu.menu_fields, menu);

        systemMenu = menu;
        systemMenu.findItem(R.id.help).setVisible(ep.getBoolean("Tips", false));

        return true;
    }

    private Rect fieldsListItemLocation(int item) {
        View v = fieldList.getChildAt(item);
        final int[] location = new int[2];
        v.getLocationOnScreen(location);
        Rect droidTarget = new Rect(location[0], location[1], location[0] + v.getWidth() / 5, location[1] + v.getHeight());
        return droidTarget;
    }

    private TapTarget fieldsTapTargetRect(Rect item, String title, String desc) {
        return TapTarget.forBounds(item, title, desc)
                // All options below are optional
                .outerCircleColor(R.color.main_primaryDark)      // Specify a color for the outer circle
                .outerCircleAlpha(0.95f)            // Specify the alpha amount for the outer circle
                .targetCircleColor(R.color.black)   // Specify a color for the target circle
                .titleTextSize(30)                  // Specify the size (in sp) of the title text
                .descriptionTextSize(20)            // Specify the size (in sp) of the description text
                .descriptionTextColor(R.color.black)  // Specify the color of the description text
                .descriptionTypeface(Typeface.DEFAULT_BOLD)
                .textColor(R.color.black)            // Specify a color for both the title and description text
                .dimColor(R.color.black)            // If set, will dim behind the view with 30% opacity of the given color
                .drawShadow(true)                   // Whether to draw a drop shadow or not
                .cancelable(false)                  // Whether tapping outside the outer circle dismisses the view
                .tintTarget(true)                   // Whether to tint the target view's color
                .transparentTarget(true)           // Specify whether the target is transparent (displays the content underneath)
                .targetRadius(60);
    }

    private TapTarget fieldsTapTargetMenu(int id, String title, String desc) {
        return TapTarget.forView(findViewById(id), title, desc)
                // All options below are optional
                .outerCircleColor(R.color.main_primaryDark)      // Specify a color for the outer circle
                .outerCircleAlpha(0.95f)            // Specify the alpha amount for the outer circle
                .targetCircleColor(R.color.black)   // Specify a color for the target circle
                .titleTextSize(30)                  // Specify the size (in sp) of the title text
                .descriptionTextSize(20)            // Specify the size (in sp) of the description text
                .descriptionTextColor(R.color.black)  // Specify the color of the description text
                .descriptionTypeface(Typeface.DEFAULT_BOLD)
                .textColor(R.color.black)            // Specify a color for both the title and description text
                .dimColor(R.color.black)            // If set, will dim behind the view with 30% opacity of the given color
                .drawShadow(true)                   // Whether to draw a drop shadow or not
                .cancelable(false)                  // Whether tapping outside the outer circle dismisses the view
                .tintTarget(true)                   // Whether to tint the target view's color
                .transparentTarget(true)           // Specify whether the target is transparent (displays the content underneath)
                .targetRadius(60);
    }

    //TODO
    private Boolean fieldExists() {

        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.help:
                TapTargetSequence sequence = new TapTargetSequence(this)
                        .targets(fieldsTapTargetMenu(R.id.importField, getString(R.string.tutorial_fields_add_title), getString(R.string.tutorial_fields_add_description)),
                                fieldsTapTargetMenu(R.id.importField, getString(R.string.tutorial_fields_add_title), getString(R.string.tutorial_fields_file_description))
                        );

                if (fieldExists()) {
                    sequence.target(fieldsTapTargetRect(fieldsListItemLocation(0), getString(R.string.tutorial_fields_select_title), getString(R.string.tutorial_fields_select_description)));
                    sequence.target(fieldsTapTargetRect(fieldsListItemLocation(0), getString(R.string.tutorial_fields_delete_title), getString(R.string.tutorial_fields_delete_description)));
                }

                sequence.start();

                break;

            case R.id.importField:
                String importer = ep.getString("IMPORT_SOURCE_DEFAULT", "ask");

                switch (importer) {
                    case "ask":
                        showFileDialog();
                        break;
                    case "local":
                        loadLocal();
                        break;
                    case "brapi":
                        loadBrAPI();
                        break;
                    case "cloud":
                        loadCloud();
                        break;
                    default:
                        showFileDialog();
                }
                break;

            case android.R.id.home:
                CollectActivity.reloadData = true;
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public void onBackPressed() {
        CollectActivity.reloadData = true;
        finish();
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

        if (requestCode == 5 && resultCode == RESULT_OK && data.getData() != null) {
            Uri content_describer = data.getData();
            InputStream in = null;
            OutputStream out = null;
            try {
                in = getContentResolver().openInputStream(content_describer);
                out = new FileOutputStream(new File(Constants.FIELDIMPORTPATH + "/" + getFileName(content_describer)));
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (out != null){
                    try {
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            final String chosenFile = Constants.FIELDIMPORTPATH + "/" + getFileName(content_describer);

            String extension = "";
            int i = chosenFile.lastIndexOf('.');
            if (i > 0) {
                extension = chosenFile.substring(i+1);
            }

            if(!extension.equals("csv") && !extension.equals("xls")) {
                Toast.makeText(FieldEditorActivity.thisActivity, getString(R.string.import_error_format_field), Toast.LENGTH_LONG).show();
                return;
            }

            showFieldFileDialog(chosenFile);
        }
    }

    private void showFieldFileDialog(final String chosenFile) {
        fieldFile = FieldFileObject.create(chosenFile);
        //todo get URI instead of string
        Editor e = ep.edit();
        e.putString("FieldFile", fieldFile.getStem());
        e.apply();

        if (ConfigActivity.dt.checkFieldName(fieldFile.getStem()) >= 0) {
            Utils.makeToast(getApplicationContext(),getString(R.string.fields_study_exists_message));
            SharedPreferences.Editor ed = ep.edit();
            ed.putString("FieldFile", null);
            ed.putBoolean("ImportFieldFinished", false);
            ed.apply();
            return;
        }

        if (fieldFile.isOther()) {
            Utils.makeToast(getApplicationContext(),getString(R.string.import_error_unsupported));
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

    private void loadFile(FieldFileObject.FieldFileBase fieldFile) {
        String[] importColumns = fieldFile.getColumns();

        String[] reservedNames = new String[]{"id"};

        List<String> list = Arrays.asList(reservedNames);

        //TODO causing crash
        for (String s : importColumns) {
            if (DataHelper.hasSpecialChars(s)) {
                Utils.makeToast(getApplicationContext(),getString(R.string.import_error_columns) + " (\"" + s + "\")");
                return;
            }

            if (list.contains(s.toLowerCase())) {
                Utils.makeToast(getApplicationContext(),getString(R.string.import_error_column_name) + " \"" + s + "\"");
                return;
            }
        }

        importDialog(importColumns);
    }

    private void importDialog(String[] columns) {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_import, null);

        unique = layout.findViewById(R.id.uniqueSpin);
        primary = layout.findViewById(R.id.primarySpin);
        secondary = layout.findViewById(R.id.secondarySpin);

        setSpinner(unique, columns, "ImportUniqueName");
        setSpinner(primary, columns, "ImportFirstName");
        setSpinner(secondary, columns, "ImportSecondName");

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.import_dialog_title_fields)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_import), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (checkImportColumnNames()) {
                    mHandler.post(importRunnable);
                }
            }
        });

        importFieldDialog = builder.create();
        importFieldDialog.show();
        DialogUtils.styleDialogs(importFieldDialog);

        android.view.WindowManager.LayoutParams params2 = importFieldDialog.getWindow().getAttributes();
        params2.width = LayoutParams.MATCH_PARENT;
        importFieldDialog.getWindow().setAttributes(params2);
    }

    private boolean verifyUniqueColumn(FieldFileObject.FieldFileBase fieldFile) {
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
            Utils.makeToast(getApplicationContext(),getString(R.string.import_error_column_choice));
            return false;
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
                //makeToast(getString(R.string.import_error_general));
            } else if (uniqueFail) {
                Utils.makeToast(getApplicationContext(),getString(R.string.import_error_unique));
            } else if (fieldFile.hasSpecialCharasters()) {
                Utils.makeToast(getApplicationContext(),getString(R.string.import_error_unique_characters_illegal));
            } else {
                Editor ed = ep.edit();
                ed.putString("ImportUniqueName", unique.getSelectedItem().toString());
                ed.putString("ImportFirstName", primary.getSelectedItem().toString());
                ed.putString("ImportSecondName", secondary.getSelectedItem().toString());
                ed.putBoolean("ImportFieldFinished", true);
                ed.apply();

                CollectActivity.reloadData = true;
                loadData();
                ConfigActivity.dt.switchField(exp_id);
            }
        }
    }
}