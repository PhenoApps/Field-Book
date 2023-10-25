package com.fieldbook.tracker.activities;

import static androidx.recyclerview.widget.ItemTouchHelper.DOWN;
import static androidx.recyclerview.widget.ItemTouchHelper.END;
import static androidx.recyclerview.widget.ItemTouchHelper.START;
import static androidx.recyclerview.widget.ItemTouchHelper.UP;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.brapi.BrapiTraitActivity;
import com.fieldbook.tracker.adapters.TraitAdapter;
import com.fieldbook.tracker.adapters.TraitAdapterController;
import com.fieldbook.tracker.async.ImportCSVTask;
import com.fieldbook.tracker.brapi.BrapiInfoDialog;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.dialogs.NewTraitDialog;
import com.fieldbook.tracker.objects.FieldFileObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.ArrayIndexComparator;
import com.fieldbook.tracker.utilities.CSVWriter;
import com.fieldbook.tracker.utilities.DialogUtils;
import com.fieldbook.tracker.utilities.FileUtil;
import com.fieldbook.tracker.utilities.TapTargetUtil;
import com.fieldbook.tracker.utilities.Utils;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

@AndroidEntryPoint
public class TraitEditorActivity extends ThemedActivity implements TraitAdapterController, TraitAdapter.TraitSorter {

    public static final String TAG = "TraitEditor";
    public static int REQUEST_CLOUD_FILE_CODE = 5;
    public static int REQUEST_FILE_EXPLORER_CODE = 1;

    private RecyclerView traitList;
    public TraitAdapter traitAdapter;
    public static boolean brapiDialogShown = false;
    private static final Handler mHandler = new Handler();
    private static SharedPreferences ep;

    private final int PERMISSIONS_REQUEST_STORAGE_IMPORT = 999;
    private final int PERMISSIONS_REQUEST_STORAGE_EXPORT = 998;
    private NewTraitDialog traitDialog;
    private Menu systemMenu;

    @Inject
    DataHelper database;

    private final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(UP | DOWN | START | END, 0) {
        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {

            TraitAdapter adapter = (TraitAdapter) recyclerView.getAdapter();

            if (adapter != null) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();

                try {
                    adapter.moveItem(from, to);
                } catch (IndexOutOfBoundsException iobe) {
                    iobe.printStackTrace();
                    return false;
                }
            }

            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

        }
        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                if (viewHolder != null) {
                    viewHolder.itemView.setAlpha(0.5f);
                    viewHolder.itemView.setScaleY(1.618f);
                }
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setAlpha(1f);
            viewHolder.itemView.setScaleY(1f);
        }
    });

    // Creates a new thread to do importing
    private void startImportCsv(Uri file) {
        mHandler.post(() -> new ImportCSVTask(this, database, file, () -> {
            Editor ed = ep.edit();
            ed.putBoolean(GeneralKeys.CREATE_TRAIT_FINISHED, true);
            ed.apply();

            queryAndLoadTraits();

            CollectActivity.reloadData = true;
        }).execute(0));
    }

    // Helper function to load data
    public void loadData(ArrayList<TraitObject> traits) {
        try {

            if (!traitList.isShown())
                traitList.setVisibility(ListView.VISIBLE);

            // Determine if our BrAPI dialog was shown with our current trait adapter
            Boolean showBrapiDialog;
            if (traitAdapter != null) {
                // Check if current trait adapter has shown a dialog
                brapiDialogShown = !brapiDialogShown ? traitAdapter.getInfoDialogShown() : brapiDialogShown;
            } else {
                // We should show our brapi dialog if this is our creating of the mAdapter
                brapiDialogShown = false;
            }

            //traitAdapter = new TraitAdapter(this);

            if (traitAdapter != null) {
                traitAdapter.submitList(traits);
                traitAdapter.notifyDataSetChanged();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean displayBrapiInfo(@NonNull Context context, @Nullable String traitName, boolean noCheckTrait) {

        // Returns true if the dialog is shown, false if not.
        // If we run into an error, do not warn the user since this is just a helper dialog
        try {
            // Check if this is a non-BrAPI field
            String fieldName = context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0)
                    .getString(GeneralKeys.FIELD_FILE, "");
            String fieldSource = context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0)
                    .getString(GeneralKeys.FIELD_SOURCE, "");

            if (!fieldName.equals("") && !fieldSource.equals("local") && !fieldSource.equals("")) {

                // noCheckTrait is used when the trait should not be checked, but the dialog
                // should be shown.
                if (noCheckTrait) {

                    BrapiInfoDialog brapiInfo = new BrapiInfoDialog(context, context.getResources().getString(R.string.brapi_info_message));
                    brapiInfo.show();
                    return true;
                }

                // Check if this is a BrAPI trait
                if (traitName != null) {

                    // Just returns an empty trait object in the case the trait isn't found
                    TraitObject trait = database.getDetail(traitName);
                    if (trait.getTrait() == null) {
                        return false;
                    }

                    if (trait.getExternalDbId() == null || trait.getExternalDbId().equals("local") || trait.getExternalDbId().equals("")) {

                        // Show info dialog if a BrAPI field is selected.
                        BrapiInfoDialog brapiInfo = new BrapiInfoDialog(context, context.getResources().getString(R.string.brapi_info_message));
                        brapiInfo.show();

                        // Only show the info dialog on the first non-BrAPI trait selected.
                        return true;

                    } else {
                        // Dialog was not shown
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("error", e.toString());
            return false;
        }

        return false;
    }

    // getter (get them from NewTraitDialog)
    public TraitAdapter getAdapter() {
        return traitAdapter;
    }

    public SharedPreferences getPreferences() {
        return ep;
    }

    public boolean getBrAPIDialogShown() {
        return brapiDialogShown;
    }

    // when this value changes in NewTraitDialog,
    // the value in this class must change
    public void setBrAPIDialogShown(boolean b) {
        brapiDialogShown = b;
    }

    // because AlertDialog can't use getString
    public String getResourceString(int id) {
        return getString(id);
    }

    @Override
    public void onDestroy() {
        try {
            finish();
        } catch (Exception e) {
            String TAG = "Field Book";
            Log.e(TAG, "" + e.getMessage());
        }

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (systemMenu != null) {
            systemMenu.findItem(R.id.help).setVisible(ep.getBoolean(GeneralKeys.TIPS, false));
        }

        queryAndLoadTraits();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0);

        setContentView(R.layout.activity_traits);

        Toolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.settings_traits));
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        traitList = findViewById(R.id.myList);

        if (!traitList.isShown())
            traitList.setVisibility(ListView.VISIBLE);

        // Determines whether brapi dialog should be shown on first click of a non-BrAPI
        // trait when a BrAPI field is selected.
        brapiDialogShown = false;

        traitAdapter = new TraitAdapter(this);
        traitAdapter.submitList(database.getAllTraitObjects());
        traitList.setAdapter(traitAdapter);

        itemTouchHelper.attachToRecyclerView(traitList);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_new_trait, null);
        traitDialog = new NewTraitDialog(layout, this);

        FloatingActionButton fab = findViewById(R.id.newTrait);
        fab.setOnClickListener(v -> showCreateTraitDialog());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(TraitEditorActivity.this).inflate(R.menu.menu_traits, menu);

        systemMenu = menu;
        systemMenu.findItem(R.id.help).setVisible(ep.getBoolean(GeneralKeys.TIPS, false));

        return true;
    }

    private Rect traitsListItemLocation(int item, int adjust) {
        View v = traitList.getChildAt(item);
        final int[] location = new int[2];
        v.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + v.getWidth() / adjust, location[1] + v.getHeight());
    }

    private TapTarget traitsTapTargetRect(Rect item, String title, String desc) {
        return TapTargetUtil.Companion.getTapTargetSettingsRect(this, item, title, desc);
    }

    private TapTarget traitsTapTargetMenu(int id, String title, String desc, int targetRadius) {
        return TapTargetUtil.Companion.getTapTargetSettingsView(this, findViewById(id), title, desc, targetRadius);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.help:
                TapTargetSequence sequence = new TapTargetSequence(this)
                        .targets(traitsTapTargetMenu(R.id.addTrait, getString(R.string.tutorial_traits_add_title), getString(R.string.tutorial_traits_add_description), 60)
                                //Todo add overflow menu action
                        );

                if (database.getTraitColumnData("trait") != null) {
                    sequence.target(traitsTapTargetRect(traitsListItemLocation(0, 4), getString(R.string.tutorial_traits_visibility_title), getString(R.string.tutorial_traits_visibility_description)));
                    sequence.target(traitsTapTargetRect(traitsListItemLocation(0, 2), getString(R.string.tutorial_traits_format_title), getString(R.string.tutorial_traits_format_description)));
                }

                sequence.start();
                break;

            case R.id.deleteTrait:
                checkShowDeleteDialog();
                break;

            case R.id.sortTrait:
                sortDialog();
                break;

            case R.id.importexport:
                importExportDialog();
                break;

            case R.id.addTrait:
                showCreateTraitDialog();
                break;

            case R.id.toggleTrait:
                changeAllVisibility();
                break;

            case android.R.id.home:
                CollectActivity.reloadData = true;
                finish();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void checkShowDeleteDialog() {
        ArrayList<TraitObject> traits = database.getAllTraitObjects();
        if (traits.isEmpty()) {
            Toast.makeText(this, R.string.act_trait_editor_no_traits_exist, Toast.LENGTH_SHORT).show();
        } else {
            showDeleteTraitDialog((dialog, which) -> {
                database.deleteTraitsTable();
                queryAndLoadTraits();
                dialog.dismiss();
            }, (dialog, which) -> dialog.dismiss(), null);
        }
    }

    private void changeAllVisibility() {
        boolean globalVis = ep.getBoolean(GeneralKeys.ALL_TRAITS_VISIBLE, false);
        List<TraitObject> allTraits = database.getAllTraitObjects();

        if (allTraits.isEmpty()) {
            Utils.makeToast(getApplicationContext(), getString(R.string.warning_traits_missing_modify));
            return;
        }

        //issue #305 fix toggles visibility even when all are un-toggled
        globalVis = !allTraits.stream().allMatch(TraitObject::getVisible);

        for (TraitObject allTrait : allTraits) {
            database.updateTraitVisibility(allTrait.getTrait(), globalVis);
            Log.d(TAG, allTrait.getTrait());
        }

        globalVis = !globalVis;

        Editor ed = ep.edit();
        ed.putBoolean(GeneralKeys.ALL_TRAITS_VISIBLE, globalVis);
        ed.apply();
        queryAndLoadTraits();
    }

    private void importExportDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list_buttonless, null);

        ListView myList = layout.findViewById(R.id.myList);
        String[] sortOptions = new String[2];
        sortOptions[0] = getString(R.string.dialog_import);
        sortOptions[1] = getString(R.string.traits_dialog_export);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item_dialog_list, sortOptions);
        myList.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.settings_traits)
                .setCancelable(true)
                .setView(layout);

        builder.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        final AlertDialog importExport = builder.create();
        importExport.show();

        android.view.WindowManager.LayoutParams params = importExport.getWindow().getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        importExport.getWindow().setAttributes(params);

        myList.setOnItemClickListener((av, arg1, which, arg3) -> {
            switch (which) {
                case 0:
                    loadTraitFilePermission();
                    break;
                case 1:
                    exportTraitFilePermission();
                    break;
            }
            importExport.dismiss();
        });

    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_STORAGE_IMPORT)
    public void loadTraitFilePermission() {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
            if (EasyPermissions.hasPermissions(this, perms)) {
                if (ep.getBoolean(GeneralKeys.TRAITS_EXPORTED, false)) {
                    showFileDialog();
                } else {
                    checkTraitExportDialog();
                }
            } else {
                // Do not have permissions, request them now
                EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_import),
                        PERMISSIONS_REQUEST_STORAGE_IMPORT, perms);
            }
        } else if (ep.getBoolean(GeneralKeys.TRAITS_EXPORTED, false)) {
            showFileDialog();
        } else {
            checkTraitExportDialog();
        }
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_STORAGE_EXPORT)
    public void exportTraitFilePermission() {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
            if (EasyPermissions.hasPermissions(this, perms)) {
                showExportDialog();
            } else {
                // Do not have permissions, request them now
                EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_export),
                        PERMISSIONS_REQUEST_STORAGE_EXPORT, perms);
            }
        } else showExportDialog();
    }

    private void showImportDialog() {

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list_buttonless, null);

        ListView myList = layout.findViewById(R.id.myList);
        String[] importArray = new String[2];
        importArray[0] = getString(R.string.import_source_local);
        importArray[1] = getString(R.string.import_source_cloud);
        if (ep.getBoolean(GeneralKeys.BRAPI_ENABLED, false)) {
            String displayName = ep.getString(GeneralKeys.BRAPI_DISPLAY_NAME, getString(R.string.preferences_brapi_server_test));
            importArray = Arrays.copyOf(importArray, importArray.length + 1);
            importArray[2] = displayName;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item_dialog_list, importArray);
        myList.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.import_dialog_title_traits)
                .setCancelable(true)
                .setView(layout);

        builder.setNegativeButton(getString(R.string.dialog_cancel), (dialog, which) -> dialog.dismiss());

        final AlertDialog importDialog = builder.create();

        importDialog.show();

        android.view.WindowManager.LayoutParams params = importDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        importDialog.getWindow().setAttributes(params);

        myList.setOnItemClickListener((av, arg1, which, arg3) -> {
            Intent intent = new Intent();
            switch (which) {
                case 0:
                    DocumentFile traitDir = BaseDocumentTreeUtil.Companion.getDirectory(this, R.string.dir_trait);
                    if (traitDir != null && traitDir.exists()) {
                        intent.setClassName(this, FileExploreActivity.class.getName());
                        intent.putExtra("path", traitDir.getUri().toString());
                        intent.putExtra("include", new String[]{"trt"});
                        intent.putExtra("title", getString(R.string.traits_dialog_import));
                        startActivityForResult(intent, REQUEST_FILE_EXPLORER_CODE);
                    }
                    break;
                case 1:
                    loadCloud();
                    break;
                case 2:
                    intent.setClassName(this, BrapiTraitActivity.class.getName());
                    startActivityForResult(intent, 2);
                    break;
            }
            importDialog.dismiss();
        });
    }

    private void showFileDialog() {

        ArrayList<TraitObject> traits = database.getAllTraitObjects();

        if (!traits.isEmpty()) {

            showDeleteTraitDialog((dialog, which) -> {

                database.deleteTraitsTable();

            }, null, (dialog) -> {

                showImportDialog();

            });

        } else showImportDialog();

    }

    private void loadCloud() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, "cloudFile"), REQUEST_CLOUD_FILE_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getApplicationContext(), "No suitable File Manager was found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkTraitExportDialog() {

        ArrayList<TraitObject> traits = database.getAllTraitObjects();

        if (traits.isEmpty()) {
            showFileDialog();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(TraitEditorActivity.this, R.style.AppAlertDialog);
        builder.setMessage(getString(R.string.traits_export_check));

        builder.setPositiveButton(getString(R.string.dialog_yes), (dialog, which) -> {

            BaseDocumentTreeUtil.Companion.getDirectory(this, R.string.dir_trait);

            showExportDialog();
            dialog.dismiss();
        });

        builder.setNegativeButton(getString(R.string.dialog_no), (dialog, which) -> {
            showFileDialog();
            dialog.dismiss();
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void sortDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list_buttonless, null);

        String[] allTraits = database.getTraitColumnData("trait");
        if (allTraits == null) {
            Utils.makeToast(getApplicationContext(), getString(R.string.warning_traits_missing_modify));
            return;
        }

        ListView myList = layout.findViewById(R.id.myList);

        String[] sortOptions = new String[3];
        sortOptions[0] = getString(R.string.traits_sort_name);
        sortOptions[1] = getString(R.string.traits_sort_format);
        sortOptions[2] = getString(R.string.traits_sort_visibility);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item_dialog_list, sortOptions);
        myList.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.traits_sort_title)
                .setCancelable(true)
                .setView(layout);

        builder.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        final AlertDialog sortDialog = builder.create();
        sortDialog.show();

        android.view.WindowManager.LayoutParams params = sortDialog.getWindow().getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        sortDialog.getWindow().setAttributes(params);

        myList.setOnItemClickListener((av, arg1, which, arg3) -> {
            switch (which) {
                case 0:
                    sortTraitList("trait");
                    break;
                case 1:
                    sortTraitList("format");
                    break;
                case 2:
                    sortTraitList("isVisible");
                    break;
            }
            sortDialog.dismiss();
        });
    }

    private void sortTraitList(String colName) {
        String[] sortList = database.getTraitColumnData(colName);

        ArrayIndexComparator comparator = new ArrayIndexComparator(sortList);
        Integer[] indexes = comparator.createIndexArray();
        Arrays.sort(indexes, comparator);

        if (colName.equals("isVisible")) {
            Arrays.sort(indexes, Collections.reverseOrder());
        }

        for (int j = 0; j < indexes.length; j++) {
            database.writeNewPosition(colName, sortList[j], Integer.toString(indexes[j]));
            Log.e("TRAIT", sortList[j] + " " + indexes[j].toString());
        }

        queryAndLoadTraits();
    }

    private void showExportDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_save_database, null);

        final EditText exportFile = layout.findViewById(R.id.fileName);
        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", Locale.getDefault());
        String exportName = "trait_export_" + timeStamp.format(Calendar.getInstance().getTime()) + ".trt";
        exportFile.setText(exportName);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.traits_dialog_export)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_save), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                exportTable(exportFile.getText().toString());
                Editor ed = ep.edit();
                ed.putBoolean(GeneralKeys.TRAITS_EXPORTED, true);
                ed.apply();
            }
        });

        builder.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        final AlertDialog exportDialog = builder.create();
        exportDialog.show();

        android.view.WindowManager.LayoutParams langParams = exportDialog.getWindow().getAttributes();
        langParams.width = LayoutParams.MATCH_PARENT;
        exportDialog.getWindow().setAttributes(langParams);
    }

    private void showDeleteTraitDialog(@Nullable DialogInterface.OnClickListener onPositive,
                                       @Nullable DialogInterface.OnClickListener onNegative,
                                       @Nullable DialogInterface.OnDismissListener onDismiss) {

        String[] allTraits = database.getTraitColumnData("trait");

        if (allTraits == null) {
            Utils.makeToast(getApplicationContext(), getString(R.string.warning_traits_missing_modify));
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(TraitEditorActivity.this, R.style.AppAlertDialog);
        builder.setTitle(getString(R.string.traits_toolbar_delete_all));
        builder.setMessage(getString(R.string.dialog_delete_traits_message));

        builder.setPositiveButton(getString(android.R.string.yes), onPositive);
        builder.setNegativeButton(getString(R.string.dialog_no), onNegative);
        builder.setOnDismissListener(onDismiss);

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void showCreateTraitDialog() {
        traitDialog.initTrait();
        traitDialog.show(false);
        traitDialog.prepareFields(0);
    }

    public void onBackPressed() {
        CollectActivity.reloadData = true;
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_FILE_EXPLORER_CODE) {
            if (resultCode == RESULT_OK) {
                startImportCsv(Uri.parse(data.getStringExtra(FileExploreActivity.EXTRA_RESULT_KEY)));
            } else {
                Toast.makeText(this, R.string.act_file_explorer_no_file_error, Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == 2) {
            brapiDialogShown = traitAdapter.getInfoDialogShown();
            if (!brapiDialogShown) {
                brapiDialogShown = displayBrapiInfo(TraitEditorActivity.this, null, true);
            }
        }

        if (requestCode == REQUEST_CLOUD_FILE_CODE && resultCode == RESULT_OK && data.getData() != null) {
            Uri content_describer = data.getData();
            String fileName = new FileUtil().getFileName(this, content_describer);

            //append a unique id to trait file, otherwise os might append (0) after extension
            fileName = fileName.replace(".trt", "_" + UUID.randomUUID().toString() + ".trt");

            DocumentFile traitDir = BaseDocumentTreeUtil.Companion.getDirectory(this, R.string.dir_trait);
            if (traitDir != null && traitDir.exists()) {

                DocumentFile traitExportFile = traitDir.createFile("*/*", fileName);

                if (traitExportFile != null && traitExportFile.exists()) {

                    InputStream in = null;
                    OutputStream out = null;
                    try {
                        out = BaseDocumentTreeUtil.Companion.getFileOutputStream(this, R.string.dir_trait, fileName);
                        in = getContentResolver().openInputStream(content_describer);
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

                    String extension = FieldFileObject.getExtension(fileName);

                    if (!extension.equals("trt")) {
                        Utils.makeToast(getApplicationContext(), getString(R.string.import_error_format_trait));
                        return;
                    }

                    startImportCsv(traitExportFile.getUri());
                }
            }
        }
    }

    // Helper function export data as CSV
    private void exportTable(String exportName) {

        try {

            DocumentFile traitDir = BaseDocumentTreeUtil.Companion.getDirectory(this, R.string.dir_trait);

            if (traitDir != null && traitDir.exists()) {

                DocumentFile exportDoc = traitDir.createFile("*/*", exportName);

                if (exportDoc != null && exportDoc.exists()) {

                    OutputStream output = BaseDocumentTreeUtil.Companion.getFileOutputStream(this, R.string.dir_trait, exportName);

                    if (output != null) {

                        OutputStreamWriter osw = new OutputStreamWriter(output);
                        CSVWriter csvWriter = new CSVWriter(osw, database.getAllTraitsForExport());
                        csvWriter.writeTraitFile(database.getAllTraitsForExport().getColumnNames());

                        csvWriter.close();
                        osw.close();
                        output.close();

                        new FileUtil().shareFile(this, ep, exportDoc);
                    }
                }
            }

        } catch (Exception e) {

            e.printStackTrace();

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void queryAndLoadTraits() {
        //database holds boolean values as string, this creates a new map that casts those values to Booleans
        HashMap<String, String> vis = database.getTraitVisibility();
        HashMap<String, Boolean> visCast = new HashMap<>();
        for (Map.Entry<String, String> v : vis.entrySet()) {
            visCast.put(v.getKey(), v.getValue().equals("true"));
        }
        loadData(database.getAllTraitObjects());
    }

    @NonNull
    @Override
    public DataHelper getDatabase() {
        return database;
    }

    @Override
    public void onDrag(@NonNull TraitAdapter.ViewHolder item) {
        itemTouchHelper.startDrag(item);
    }

    /**
     * Interface implementation to delegate menu options: copy, delete, edit
     * @param v view to anchor popup menu
     * @param trait trait object to handle
     */
    @Override
    public void onMenuItemClicked(View v, TraitObject trait) {

        PopupMenu popupMenu = new PopupMenu(this, v);

        //Inflating the Popup using xml file
        popupMenu.getMenuInflater().inflate(R.menu.menu_trait_list_item, popupMenu.getMenu());

        //registering popup with OnMenuItemClickListener
        popupMenu.setOnMenuItemClickListener((item) -> {

                if (item.getTitle().equals(getString(R.string.traits_options_copy))) {

                    copyTrait(trait);

                } else if (item.getTitle().equals(getString(R.string.traits_options_delete))) {

                    deleteTrait(trait);

                } else if (item.getTitle().equals(getString(R.string.traits_options_edit))) {

                    showEditTraitDialog(trait);

                }

                return false;
            }
        );

        popupMenu.show(); //showing popup menu
    }

    // When a trait is selected, alter the layout of the edit dialog accordingly
    private void showEditTraitDialog(TraitObject trait) {

        traitDialog.setTraitObject(trait);
        queryAndLoadTraits();
        traitDialog.show(true);
    }

    // Delete trait
    private void deleteTrait(TraitObject trait) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.traits_options_delete_title));
        builder.setMessage(getString(R.string.traits_warning_delete));

        builder.setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                getDatabase().deleteTrait(trait.getId());

                queryAndLoadTraits();

                CollectActivity.reloadData = true;
            }
        });

        builder.setNegativeButton(getString(R.string.dialog_no), (dialog, which) -> dialog.dismiss());

        AlertDialog alert = builder.create();
        alert.show();
        DialogUtils.styleDialogs(alert);
    }

    // Copy trait name
    private String copyTraitName(String traitName) {
        if (traitName.contains("-Copy")) {
            traitName = traitName.substring(0, traitName.indexOf("-Copy"));
        }

        String newTraitName = "";

        String[] allTraits = getDatabase().getAllTraitNames();

        for (int i = 0; i < allTraits.length; i++) {
            newTraitName = traitName + "-Copy-(" + i + ")";
            if (!Arrays.asList(allTraits).contains(newTraitName)) {
                return newTraitName;
            }
        }
        return "";    // not come here
    }

    // Copy trait to new trait
    private void copyTrait(TraitObject trait) {

        int pos = getDatabase().getMaxPositionFromTraits() + 1;

        final String newTraitName = copyTraitName(trait.getTrait());

        trait.setTrait(newTraitName);
        trait.setVisible(true);
        trait.setRealPosition(pos);

        getDatabase().insertTraits(trait);
        queryAndLoadTraits();

        CollectActivity.reloadData = true;
    }
}