package com.fieldbook.tracker.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.brapi.BrapiActivity;
import com.fieldbook.tracker.adapters.FieldAdapter;
import com.fieldbook.tracker.async.ImportRunnableTask;
import com.fieldbook.tracker.brapi.BrapiInfoDialog;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationUnitModel;
import com.fieldbook.tracker.dialogs.FieldCreatorDialog;
import com.fieldbook.tracker.dialogs.FieldSortDialog;
import com.fieldbook.tracker.interfaces.FieldAdapterController;
import com.fieldbook.tracker.interfaces.FieldSortController;
import com.fieldbook.tracker.interfaces.FieldSwitcher;
import com.fieldbook.tracker.location.GPSTracker;
import com.fieldbook.tracker.objects.FieldFileObject;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.ImportFormat;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.ExportUtil;
import com.fieldbook.tracker.utilities.FieldSwitchImpl;
import com.fieldbook.tracker.utilities.SnackbarUtils;
import com.fieldbook.tracker.utilities.TapTargetUtil;
import com.fieldbook.tracker.utilities.Utils;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.StringJoiner;
import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

@AndroidEntryPoint
public class FieldEditorActivity extends ThemedActivity
        implements FieldSortController, FieldAdapterController, FieldAdapter.AdapterCallback {

    private final String TAG = "FieldEditor";
    private static final int REQUEST_FILE_EXPLORER_CODE = 1;
    private static final int REQUEST_CLOUD_FILE_CODE = 5;
    private static final int REQUEST_BRAPI_IMPORT_ACTIVITY = 10;
    private ArrayList<FieldObject> fieldList;
    public FieldAdapter mAdapter;
    public EditText trait;
    private static final Handler mHandler = new Handler();
    private FieldFileObject.FieldFileBase fieldFile;
    private final int PERMISSIONS_REQUEST_STORAGE = 998;
    Spinner unique;
    Spinner primary;
    Spinner secondary;
    private Menu systemMenu;
    private GPSTracker mGpsTracker;
    private ActionMode actionMode;
    private TextView customTitleView;
    public ExportUtil exportUtil;

    @Inject
    DataHelper database;

    @Inject
    FieldSwitchImpl fieldSwitcher;

    @Inject
    SharedPreferences preferences;
    RecyclerView recyclerView;

    // Creates a new thread to do importing
    private final Runnable importRunnable = new Runnable() {
        public void run() {
            new ImportRunnableTask(FieldEditorActivity.this,
                    fieldFile,
                    unique.getSelectedItemPosition(),
                    unique.getSelectedItem().toString(),
                    primary.getSelectedItem().toString(),
                    secondary.getSelectedItem().toString()).execute(0);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fields);
        Toolbar toolbar = findViewById(R.id.field_toolbar);
        setSupportActionBar(toolbar);
        exportUtil = new ExportUtil(this, database);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.settings_fields));
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        recyclerView = findViewById(R.id.fieldRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        // Initialize adapter
        mAdapter = new FieldAdapter(this, fieldSwitcher, this);
        mAdapter.setOnFieldSelectedListener(new FieldAdapter.OnFieldSelectedListener() {
            @Override
            public void onFieldSelected(int fieldId) {
                FieldDetailFragment fragment = new FieldDetailFragment();
                Bundle args = new Bundle();
                args.putInt("fieldId", fieldId);
                fragment.setArguments(args);

                getSupportFragmentManager().beginTransaction()
                        .replace(android.R.id.content, fragment,"FieldDetailFragmentTag")
                        .addToBackStack(null)
                        .commit();
            }
        });
        recyclerView.setAdapter(mAdapter);

        updateFieldsList();

    }

    @Override
    public void onResume() {
        super.onResume();

        if (systemMenu != null) {
            systemMenu.findItem(R.id.help).setVisible(preferences.getBoolean(GeneralKeys.TIPS, false));
        }

        updateFieldsList();
        mGpsTracker = new GPSTracker(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void updateFieldsList() {
        try {
            fieldList = database.getAllFieldObjects(); // Fetch data from the database
            mAdapter.submitList(new ArrayList<>(fieldList)); // Update the adapter's dataset
            mAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            Log.e(TAG, "Error updating fields list", e);
        }
    }

    // Implementations of methods from FieldAdapter.AdapterCallback
    @Override
    public void onItemSelected(int selectedCount) {
        if (selectedCount == 0 && actionMode != null) {
            actionMode.finish();
        } else if (selectedCount > 0 && actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback);
        }
        if (actionMode != null && customTitleView != null) {
            customTitleView.setText(getString(R.string.selected_count, selectedCount));
        }
    }

    @Override
    public void onItemClear() {
        if (actionMode != null) {
            actionMode.finish();
        }
    }

    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.cab_menu, menu);

            // Create and style the custom title view
            customTitleView = new TextView(FieldEditorActivity.this);
            customTitleView.setTextColor(Color.BLACK); // Set text color
            customTitleView.setTextSize(18); // Set text size

            // Set layout parameters
            ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(
                    ActionBar.LayoutParams.WRAP_CONTENT,
                    ActionBar.LayoutParams.WRAP_CONTENT);
            customTitleView.setLayoutParams(layoutParams);

            // Set the custom view
            mode.setCustomView(customTitleView);

            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_select_all) {
                mAdapter.selectAll();
                int selectedCount = mAdapter.getSelectedItemCount();
                if (actionMode != null && customTitleView != null) {
                    customTitleView.setText(getString(R.string.selected_count, selectedCount));
                }
                return true;
            } else if (itemId == R.id.menu_export) {
                exportUtil.exportMultipleFields(mAdapter.getSelectedItems());
                mAdapter.exitSelectionMode();
                mode.finish();
                return true;
//            } else if (itemId == R.id.menu_archive) {
//                Toast.makeText(getApplicationContext(), "Archive not yet implemented", Toast.LENGTH_SHORT).show();
//                mAdapter.exitSelectionMode();
//                mode.finish();
//                return true;
            } else if (itemId == R.id.menu_delete) {
                showDeleteConfirmationDialog(mAdapter.getSelectedItems(), false);
                return true;
            } else {
                return false;
            }
        }


        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mAdapter.exitSelectionMode();
            actionMode = null;
        }
    };

    public void setActiveField(int studyId) {
        FieldObject field = database.getFieldObject(studyId);
        fieldSwitcher.switchField(studyId);
        CollectActivity.reloadData = true;
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged(); // Refresh adapter to update active icon indication
        }
        // Check if this is a BrAPI field and show BrAPI info dialog if so
//        if (field.getImport_format() == ImportFormat.BRAPI) {
//            BrapiInfoDialog brapiInfo = new BrapiInfoDialog(this, getResources().getString(R.string.brapi_info_message));
//            brapiInfo.show();
//        }
    }

    public void showDeleteConfirmationDialog(final List<Integer> fieldIds, boolean isFromDetailFragment) {
        String fieldNames = getFieldNames(fieldIds);
        String message = getResources().getQuantityString(R.plurals.fields_delete_confirmation, fieldIds.size(), fieldNames);
        Spanned formattedMessage;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            formattedMessage = Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY);
        } else {
            formattedMessage = Html.fromHtml(message);
        }

        new AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(getString(R.string.fields_delete_study))
            .setMessage(formattedMessage)
            .setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    deleteFields(fieldIds);
                    if (isFromDetailFragment) { getSupportFragmentManager().popBackStack(); }
                }
            })
            .setNegativeButton(getString(R.string.dialog_no), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            })
            .show();
    }

    private String getFieldNames(final List<Integer> fieldIds) {

        List<String> fieldNames = fieldIds.stream()
                .flatMap(id -> fieldList.stream()
                        .filter(field -> field.getExp_id() == id)
                        .map(field -> "<b>" + field.getExp_alias() + "</b>"))
                .collect(Collectors.toList());

        return TextUtils.join(", ", fieldNames);
    }

    private void deleteFields(List<Integer> fieldIds) {

        for (Integer fieldId : fieldIds) {
            database.deleteField(fieldId);
        }

        // Check if the active field is among those deleted in order to reset related shared preferences
        if (fieldIds.contains(preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, -1))) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.remove(GeneralKeys.FIELD_FILE);
            editor.remove(GeneralKeys.FIELD_ALIAS);
            editor.remove(GeneralKeys.FIELD_OBS_LEVEL);
            editor.putInt(GeneralKeys.SELECTED_FIELD_ID, -1);
            editor.remove(GeneralKeys.UNIQUE_NAME);
            editor.remove(GeneralKeys.PRIMARY_NAME);
            editor.remove(GeneralKeys.SECONDARY_NAME);
            editor.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false);
            editor.remove(GeneralKeys.LAST_PLOT);
            editor.apply();
            CollectActivity.reloadData = true;
        }

        updateFieldsList();
        mAdapter.exitSelectionMode();
        if (actionMode != null) {
            actionMode.finish();
            actionMode = null;
        }

    }

    private void showFileDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list_buttonless, null);

        ListView importSourceList = layout.findViewById(R.id.myList);
        String[] importArray = new String[2];
        importArray[0] = getString(R.string.import_source_local);
        importArray[1] = getString(R.string.import_source_cloud);
        if (preferences.getBoolean(GeneralKeys.BRAPI_ENABLED, false)) {
            String displayName = preferences.getString(GeneralKeys.BRAPI_DISPLAY_NAME, getString(R.string.preferences_brapi_server_test));
            importArray = Arrays.copyOf(importArray, importArray.length + 1);
            importArray[2] = displayName;
        }


        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item_dialog_list, importArray);
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

        android.view.WindowManager.LayoutParams params = importDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        importDialog.getWindow().setAttributes(params);

        importSourceList.setOnItemClickListener((av, arg1, which, arg3) -> {
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
        });
    }

    public void loadLocal() {

        try {
            DocumentFile importDir = BaseDocumentTreeUtil.Companion.getDirectory(this, R.string.dir_field_import);
            if (importDir != null && importDir.exists()) {
                Intent intent = new Intent();
                intent.setClassName(FieldEditorActivity.this, FileExploreActivity.class.getName());
                intent.putExtra("path", importDir.getUri().toString());
                intent.putExtra("include", new String[]{"csv", "xls", "xlsx"});
                intent.putExtra("title", getString(R.string.import_dialog_title_fields));
                startActivityForResult(intent, REQUEST_FILE_EXPLORER_CODE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadBrAPI() {
        Intent intent = new Intent(this, BrapiActivity.class);
        startActivityForResult(intent, REQUEST_BRAPI_IMPORT_ACTIVITY);
    }

    public void loadCloud() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, "cloudFile"), REQUEST_CLOUD_FILE_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getApplicationContext(), "No suitable File Manager was found.", Toast.LENGTH_SHORT).show();
        }
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_STORAGE)
    public void loadLocalPermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
            if (EasyPermissions.hasPermissions(this, perms)) {
                loadLocal();
            } else {
                // Do not have permissions, request them now
                EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_import),
                        PERMISSIONS_REQUEST_STORAGE, perms);
            }
        } else loadLocal();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(FieldEditorActivity.this).inflate(R.menu.menu_fields, menu);

        systemMenu = menu;
        systemMenu.findItem(R.id.help).setVisible(preferences.getBoolean(GeneralKeys.TIPS, false));

        return true;
    }

    private Rect fieldsListItemLocation(int item) {
        View v = recyclerView.getChildAt(item);
        final int[] location = new int[2];
        v.getLocationOnScreen(location);
        Rect droidTarget = new Rect(location[0], location[1], location[0] + v.getWidth() / 5, location[1] + v.getHeight());
        return droidTarget;
    }

    private TapTarget fieldsTapTargetRect(Rect item, String title, String desc) {
        return TapTargetUtil.Companion.getTapTargetSettingsRect(this, item, title, desc);
    }

    private TapTarget fieldsTapTargetMenu(int id, String title, String desc, int targetRadius) {
        return TapTargetUtil.Companion.getTapTargetSettingsView(this, findViewById(id), title, desc, targetRadius);
    }

    //TODO
    private Boolean fieldExists() {

        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.help) {
            TapTargetSequence sequence = new TapTargetSequence(this)
                .targets(fieldsTapTargetMenu(R.id.importField, getString(R.string.tutorial_fields_add_title), getString(R.string.tutorial_fields_add_description), 60),
                        fieldsTapTargetMenu(R.id.importField, getString(R.string.tutorial_fields_add_title), getString(R.string.tutorial_fields_file_description), 60)
                );
            if (fieldExists()) {
                sequence.target(fieldsTapTargetRect(fieldsListItemLocation(0), getString(R.string.tutorial_fields_select_title), getString(R.string.tutorial_fields_select_description)));
                sequence.target(fieldsTapTargetRect(fieldsListItemLocation(0), getString(R.string.tutorial_fields_delete_title), getString(R.string.tutorial_fields_delete_description)));
            }

            sequence.start();
        } else if (itemId == R.id.importField) {
            handleImportAction();
        } else if (itemId == R.id.menu_field_editor_item_creator) {
            FieldCreatorDialog dialog = new FieldCreatorDialog(this);
            dialog.setFieldCreationCallback(new FieldCreatorDialog.FieldCreationCallback() {
                @Override
                public void onFieldCreated(int studyDbId) {
                    fieldSwitcher.switchField(studyDbId);
                    updateFieldsList();
                }
            });
            dialog.show();
        } else if (itemId == android.R.id.home) {
            CollectActivity.reloadData = true;
            finish();
        } else if (itemId == R.id.action_select_plot_by_distance) {
            if (mGpsTracker != null && mGpsTracker.canGetLocation()) {
                selectPlotByDistance();
            } else {
                Toast.makeText(this, R.string.activity_field_editor_no_location_yet, Toast.LENGTH_SHORT).show();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshFieldList() {
        fieldList = database.getAllFieldObjects();
        mAdapter.submitList(new ArrayList<>(fieldList));
    }

    private void handleImportAction() {
        String importer = preferences.getString("IMPORT_SOURCE_DEFAULT", "ask");
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
    }

    /**
     * Programmatically selects the closest field to the user's location.
     * Finds all observation units with geocoordinate data, sorts the list and finds the first item.
     */
    private void selectPlotByDistance() {

        if (mGpsTracker != null && mGpsTracker.canGetLocation()) {

            //get current coordinate of the user
            Location thisLocation = mGpsTracker.getLocation();

            ObservationUnitModel[] units = database.getAllObservationUnits();
            List<ObservationUnitModel> coordinates = new ArrayList<>();

            //find all observation units with a coordinate
            for (ObservationUnitModel model : units) {
                String latlng = model.getGeo_coordinates();
                if (latlng != null && !latlng.isEmpty()) {
                    coordinates.add(model);
                }
            }

            //sort the coordinates based on the distance from the user
            Collections.sort(coordinates, (a, b) -> {
                double distanceA = distanceTo(thisLocation, a.getLocation());
                double distanceB = distanceTo(thisLocation, b.getLocation());
                return Double.compare(distanceA, distanceB);
            });

            Optional<ObservationUnitModel> closest = coordinates.stream().findFirst();

            try {

                if (closest.isPresent()) {

                    ObservationUnitModel model = closest.get();

                    int studyId = model.getStudy_id();

                    FieldObject study = database.getFieldObject(studyId);

                    String studyName = study.getExp_alias();

                    if (studyId == preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)) {

                        SnackbarUtils.showNavigateSnack(getLayoutInflater(),
                                findViewById(R.id.main_content),
                                getString(R.string.activity_field_editor_switch_field_same),
                                null,
                                8000, null, null
                                );

                    } else {

                        SnackbarUtils.showNavigateSnack(
                                getLayoutInflater(),
                                findViewById(R.id.main_content),
                                getString(R.string.activity_field_editor_switch_field, studyName),
                                null,
                                8000,
                                null, (v) -> {
                                    fieldSwitcher.switchField(studyId);
                                    updateFieldsList();
                                }
                        );
                    }
                }

            } catch (NoSuchElementException e) {

                Toast.makeText(this, R.string.activity_field_editor_no_field_found, Toast.LENGTH_SHORT).show();
            }
        } else {

            Toast.makeText(this, R.string.activity_field_editor_no_location_yet, Toast.LENGTH_SHORT).show();

        }
    }

    private double distanceTo(Location thisLocation, Location targetLocation) {
        if (targetLocation == null) return Double.MAX_VALUE;
        else return thisLocation.distanceTo(targetLocation);
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index > -1) {
                        result = cursor.getString(index);
                    }
                }
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

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            // Return to Fields screen if pressed in detail fragment
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
            getSupportFragmentManager().popBackStack();
        } else {
            CollectActivity.reloadData = true;
            super.onBackPressed();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 2) {
            if (resultCode == RESULT_OK) {
                final String chosenFile = data.getStringExtra("result");
                showFieldFileDialog(chosenFile, null);
            }
        }

        if (requestCode == REQUEST_FILE_EXPLORER_CODE) {
            if (resultCode == RESULT_OK) {
                final String chosenFile = data.getStringExtra(FileExploreActivity.EXTRA_RESULT_KEY);
                showFieldFileDialog(chosenFile, null);
            }
        }

        if (requestCode == REQUEST_BRAPI_IMPORT_ACTIVITY) {
            if (resultCode == RESULT_OK && data != null) {
                int fieldId = data.getIntExtra("fieldId", -1);
                if (fieldId != -1) {
                    getFieldSwitcher().switchField(fieldId);
                    BrapiInfoDialog brapiInfo = new BrapiInfoDialog(this, getResources().getString(R.string.brapi_info_message));
                    brapiInfo.show();
                }
            }
        }

        if (requestCode == REQUEST_CLOUD_FILE_CODE && resultCode == RESULT_OK && data.getData() != null) {
            Uri content_describer = data.getData();
            final String chosenFile = getFileName(content_describer);
            if (chosenFile != null) {
                InputStream in = null;
                OutputStream out = null;
                try {
                    in = getContentResolver().openInputStream(content_describer);
                    out = BaseDocumentTreeUtil.Companion.getFileOutputStream(this,
                            R.string.dir_field_import, chosenFile);
                    if (out == null) throw new IOException();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
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
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                String extension = FieldFileObject.getExtension(chosenFile);

                if (!extension.equals("csv") && !extension.equals("xls") && !extension.equals("xlsx")) {
                    Toast.makeText(FieldEditorActivity.this, getString(R.string.import_error_format_field), Toast.LENGTH_LONG).show();
                    return;
                }

                showFieldFileDialog(content_describer.toString(), true);
            }
        }
    }

    private void showFieldFileDialog(final String chosenFile, Boolean isCloud) {

        try {

            Uri docUri = Uri.parse(chosenFile);

            DocumentFile importDoc = DocumentFile.fromSingleUri(this, docUri);

            if (importDoc != null && importDoc.exists()) {

                ContentResolver resolver = getContentResolver();
                if (resolver != null) {

                    String cloudName = null;
                    if (isCloud != null && isCloud) {
                        cloudName = getFileName(Uri.parse(chosenFile));
                    }

                    try (InputStream is = resolver.openInputStream(docUri)) {

                        fieldFile = FieldFileObject.create(this, docUri, is, cloudName);

                        String fieldFileName = fieldFile.getStem();

                        Editor e = preferences.edit();
                        e.putString(GeneralKeys.FIELD_FILE, fieldFileName);
                        e.putString(GeneralKeys.FIELD_ALIAS, fieldFileName);
                        e.apply();

                        if (database.checkFieldName(fieldFileName) >= 0) {
                            Utils.makeToast(getApplicationContext(), getString(R.string.fields_study_exists_message));
                            SharedPreferences.Editor ed = preferences.edit();
                            ed.putString(GeneralKeys.FIELD_FILE, null);
                            ed.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false);
                            ed.apply();
                            return;
                        }

                        if (fieldFile.isOther()) {
                            Utils.makeToast(getApplicationContext(), getString(R.string.import_error_unsupported));
                        }

                        loadFile(fieldFile);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        } catch (Exception e) {

            Utils.makeToast(this, getString(R.string.act_field_editor_load_file_failed));

            e.printStackTrace();
        }
    }

    private void loadFile(FieldFileObject.FieldFileBase fieldFile) {

        String[] importColumns = fieldFile.getColumns();

        if (fieldFile.getOpenFailed()) {
            Utils.makeToast(this, getString(R.string.act_field_editor_file_open_failed));
            return;
        }

        //in some cases getColumns is returning null, so print an error message to the user
        if (importColumns != null) {

            //only reserved word for now is id which is used in many queries
            //other sqlite keywords are sanitized with a tick mark to make them an identifier
            String[] reservedNames = new String[]{"id"};

            //replace specials and emptys and add them to the actual columns list to be displayed
            ArrayList<String> actualColumns = new ArrayList<>();

            List<String> list = Arrays.asList(reservedNames);

            //define flag to let the user know characters were replaced at the end of the loop
            boolean hasSpecialCharacters = false;
            for (String s : importColumns) {

                boolean added = false;

                //replace the special characters, only add to the actual list if it is not empty
                if (DataHelper.hasSpecialChars(s)) {

                    hasSpecialCharacters = true;
                    added = true;
                    String replaced = DataHelper.replaceSpecialChars(s);
                    if (!replaced.isEmpty() && !actualColumns.contains(replaced))
                        actualColumns.add(replaced);

                }

                if (list.contains(s.toLowerCase())) {

                    Utils.makeToast(getApplicationContext(), getString(R.string.import_error_column_name) + " \"" + s + "\"");

                    return;
                }

                if (!added) {

                    if (!s.isEmpty()) actualColumns.add(s);

                }

            }

            if (actualColumns.size() > 0) {

                if (hasSpecialCharacters) {

                    Utils.makeToast(getApplicationContext(), getString(R.string.import_error_columns_replaced));

                }

                importDialog(actualColumns.toArray(new String[]{}));

            } else {

                Toast.makeText(this, R.string.act_field_editor_no_suitable_columns_error,
                        Toast.LENGTH_SHORT).show();
            }
        } else {

            Utils.makeToast(getApplicationContext(), getString(R.string.act_field_editor_failed_to_read_columns));
        }
    }

    private void importDialog(String[] columns) {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_import, null);

        unique = layout.findViewById(R.id.uniqueSpin);
        primary = layout.findViewById(R.id.primarySpin);
        secondary = layout.findViewById(R.id.secondarySpin);

        setSpinner(unique, columns, GeneralKeys.UNIQUE_NAME);
        setSpinner(primary, columns, GeneralKeys.PRIMARY_NAME);
        setSpinner(secondary, columns, GeneralKeys.SECONDARY_NAME);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.import_dialog_title_fields)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_import), (dialogInterface, i) -> {
            if (checkImportColumnNames()) {
                mHandler.post(importRunnable);
            }
        });

        builder.show();
    }

    // Helper function to set spinner adapter and listener
    private void setSpinner(Spinner spinner, String[] data, String pref) {
        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_layout, data);
        spinner.setAdapter(itemsAdapter);
        int spinnerPosition = itemsAdapter.getPosition(preferences.getString(pref, itemsAdapter.getItem(0)));
        spinner.setSelection(spinnerPosition);
    }

    // Validate that column choices are different from one another
    private boolean checkImportColumnNames() {
        final String uniqueS = unique.getSelectedItem().toString();
        final String primaryS = primary.getSelectedItem().toString();
        final String secondaryS = secondary.getSelectedItem().toString();

        if (uniqueS.equals(primaryS) || uniqueS.equals(secondaryS) || primaryS.equals(secondaryS)) {
            Utils.makeToast(getApplicationContext(), getString(R.string.import_error_column_choice));
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

    @Override
    public void showSortDialog(FieldObject field) {

        String order = field.getExp_sort();

        ArrayList<String> sortOrderList = new ArrayList<>();

        if (order != null) {

            List<String> orderList = Arrays.asList(order.split(","));

            if (!orderList.isEmpty() && !orderList.get(0).isEmpty()) {

                sortOrderList.addAll(orderList);

            }
        }

        //initialize: initial items are the current sort order, selectable items are the obs. unit attributes.
        FieldSortDialog d = new FieldSortDialog(this, field,
                sortOrderList.toArray(new String[]{}),
                database.getRangeColumnNames());

        d.show();
    }

    @Override
    public void submitSortList(FieldObject field, String[] attributes) {

        StringJoiner joiner = new StringJoiner(",");
        for (String a : attributes) joiner.add(a);

        field.setExp_sort(joiner.toString());

        try {

            database.updateStudySort(joiner.toString(), field.getExp_id());

            if (preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0) == field.getExp_id()) {

                fieldSwitcher.switchField(field);
                CollectActivity.reloadData = true;
            }

            Toast toast = Toast.makeText(this, R.string.sort_dialog_saved, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL, 0, 0);
            toast.show();

            // Refresh the fragment's data
            FieldDetailFragment fragment = (FieldDetailFragment) getSupportFragmentManager().findFragmentByTag("FieldDetailFragmentTag");
            if (fragment != null) {
                fragment.loadFieldDetails();
            }

        } catch (Exception e) {

            Log.e(TAG, "Error updating sorting", e);

            new AlertDialog.Builder(this, R.style.AppAlertDialog)
                    .setTitle(R.string.dialog_save_error_title)
                    .setPositiveButton(org.phenoapps.androidlibrary.R.string.okButtonText, (dInterface, i) -> Log.d("FieldAdapter", "Sort save error dialog dismissed"))
                    .setMessage(R.string.sort_dialog_error_saving)
                    .create()
                    .show();
        }

        updateFieldsList();

    }

    @Override
    public void queryAndLoadFields() {
        updateFieldsList();
    }

    @NonNull
    @Override
    public DataHelper getDatabase() {
        return database;
    }

    @NonNull
    @Override
    public SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    @NonNull
    @Override
    public FieldSwitcher getFieldSwitcher() {
        return fieldSwitcher;
    }

}