package com.fieldbook.tracker.activities;

import static androidx.recyclerview.widget.ItemTouchHelper.DOWN;
import static androidx.recyclerview.widget.ItemTouchHelper.END;
import static androidx.recyclerview.widget.ItemTouchHelper.START;
import static androidx.recyclerview.widget.ItemTouchHelper.UP;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.LifecycleOwnerKt;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.brapi.BrapiTraitActivity;
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache;
import com.fieldbook.tracker.activities.brapi.io.filter.filterer.BrapiTraitFilterActivity;
import com.fieldbook.tracker.adapters.TraitAdapter;
import com.fieldbook.tracker.adapters.TraitAdapterController;
import com.fieldbook.tracker.async.ImportCSVTask;
import com.fieldbook.tracker.async.ImportJsonTraitTask;
import com.fieldbook.tracker.brapi.BrapiInfoDialogFragment;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.dialogs.ListAddDialog;
import com.fieldbook.tracker.dialogs.ListSortDialog;
import com.fieldbook.tracker.dialogs.NewTraitDialog;
import com.fieldbook.tracker.enums.FileFormat;
import com.fieldbook.tracker.fragments.TraitDetailFragment;
import com.fieldbook.tracker.objects.FieldFileObject;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.ImportFormat;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.preferences.PreferenceKeys;
import com.fieldbook.tracker.utilities.CSVWriter;
import com.fieldbook.tracker.utilities.FileUtil;
import com.fieldbook.tracker.utilities.InsetHandler;
import com.fieldbook.tracker.utilities.SharedPreferenceUtils;
import com.fieldbook.tracker.utilities.TapTargetUtil;
import com.fieldbook.tracker.utilities.Utils;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

@AndroidEntryPoint
public class TraitEditorActivity extends ThemedActivity implements TraitAdapterController, TraitAdapter.TraitSorter, NewTraitDialog.TraitDialogDismissListener, TraitAdapter.OnTraitSelectedListener {

    private enum ImportOptions {
        CREATE_NEW(R.drawable.ic_ruler, R.string.traits_dialog_create),
        IMPORT_FROM_FILE(R.drawable.ic_file_generic, R.string.traits_dialog_import_from_file),
        IMPORT_FROM_BRAPI(R.drawable.ic_adv_brapi, 0); // 0 as placeholder, uses PreferenceKeys.BRAPI_DISPLAY_NAME

        final int iconResource;
        final int stringResource;

        ImportOptions(int iconResource, int stringResource) {
            this.iconResource = iconResource;
            this.stringResource = stringResource;
        }
    }

    public static final String TAG = "TraitEditor";
    public static int REQUEST_CLOUD_FILE_CODE = 5;
    public static int REQUEST_FILE_EXPLORER_CODE = 1;
    public static int REQUEST_CODE_BRAPI_TRAIT_ACTIVITY = 2;
    private RecyclerView traitList;
    public TraitAdapter traitAdapter;
    public static boolean brapiDialogShown = false;
    private static final Handler mHandler = new Handler();

    private final int PERMISSIONS_REQUEST_STORAGE_IMPORT = 999;
    private final int PERMISSIONS_REQUEST_STORAGE_EXPORT = 998;
    private Menu systemMenu;

    @Inject
    DataHelper database;

    @Inject
    SharedPreferences preferences;

    private final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(UP | DOWN | START | END, 0) {
        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {

            TraitAdapter adapter = (TraitAdapter) recyclerView.getAdapter();

            if (adapter != null) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();

                try {
                    preferences.edit().putString(GeneralKeys.TRAITS_LIST_SORT_ORDER, "position").apply();
                    queryAndLoadTraits();
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
                    if (!SharedPreferenceUtils.Companion.isHighContrastTheme(prefs)) {
                        viewHolder.itemView.setAlpha(0.5f);
                    }
                }
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setAlpha(1f);
        }
    });

    // Creates a new thread to do importing
    private void startImportCsv(Uri file) {
        mHandler.post(() -> new ImportCSVTask(this, database, file, () -> {

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

            if (traitAdapter != null) {

                traitAdapter.submitList(traits);

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
            int studyId = getPreferences().getInt(GeneralKeys.SELECTED_FIELD_ID, -1);

            FieldObject field = database.getFieldObject(studyId);

            if (!field.getName().equals("") && field.getDataSourceFormat() == ImportFormat.BRAPI) {

                // noCheckTrait is used when the trait should not be checked, but the dialog
                // should be shown.
                if (noCheckTrait) {

                    BrapiInfoDialogFragment dialogFragment = new BrapiInfoDialogFragment().newInstance(getResources().getString(R.string.brapi_info_message));
                    dialogFragment.show(this.getSupportFragmentManager(), "brapiInfoDialogFragment");
                    return true;
                }

                // Check if this is a BrAPI trait
//                if (traitName != null) {
//
//                    // Just returns an empty trait object in the case the trait isn't found
//                    TraitObject trait = database.getDetail(traitName);
//                    if (trait.getName() == null) {
//                        return false;
//                    }
//
//                    if (trait.getExternalDbId() == null || trait.getExternalDbId().equals("local") || trait.getExternalDbId().equals("")) {
//
//                        // Show info dialog if a BrAPI field is selected.
//                        BrapiInfoDialogFragment dialogFragment = new BrapiInfoDialogFragment().newInstance(getResources().getString(R.string.brapi_info_message));
//                        dialogFragment.show(this.getSupportFragmentManager(), "brapiInfoDialogFragment");
//
//                        // Only show the info dialog on the first non-BrAPI trait selected.
//                        return true;
//
//                    } else {
//                        // Dialog was not shown
//                        return false;
//                    }
//                }
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
        return preferences;
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
            systemMenu.findItem(R.id.help).setVisible(preferences.getBoolean(PreferenceKeys.TIPS, false));
        }

        queryAndLoadTraits();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_traits);
        setupTraitEditorInsets();

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
        traitAdapter.setOnTraitSelectedListener(this);
        traitAdapter.submitList(database.getAllTraitObjects());
        traitList.setAdapter(traitAdapter);

        itemTouchHelper.attachToRecyclerView(traitList);

        FloatingActionButton fab = findViewById(R.id.newTrait);
        fab.setOnClickListener(v -> showImportDialog());

        setupBackCallback();
    }

    // Implement the interface
    @Override
    public void onTraitSelected(String traitId) {
        // Disable touch events on the RecyclerView
        traitList.setEnabled(false);

        // Create and show the TraitDetailFragment
        TraitDetailFragment fragment = new TraitDetailFragment();
        Bundle args = new Bundle();
        args.putString("traitId", traitId);
        fragment.setArguments(args);

        getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, fragment, "TraitDetailFragmentTag")
            .addToBackStack(null)
            .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(TraitEditorActivity.this).inflate(R.menu.menu_traits, menu);

        systemMenu = menu;
        systemMenu.findItem(R.id.help).setVisible(preferences.getBoolean(PreferenceKeys.TIPS, false));

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

        int itemId = item.getItemId();
        if (itemId == R.id.help) {
            TapTargetSequence sequence = new TapTargetSequence(this)
                    .targets(traitsTapTargetMenu(R.id.newTrait, getString(R.string.tutorial_traits_add_title), getString(R.string.tutorial_traits_add_description), 60)
                            //Todo add overflow menu action
                    );

            ArrayList<TraitObject> traits = database.getAllTraitObjects();
            if (traits != null && !traits.isEmpty()) {
                sequence.target(traitsTapTargetRect(traitsListItemLocation(0, 4), getString(R.string.tutorial_traits_visibility_title), getString(R.string.tutorial_traits_visibility_description)));
                sequence.target(traitsTapTargetRect(traitsListItemLocation(0, 2), getString(R.string.tutorial_traits_format_title), getString(R.string.tutorial_traits_format_description)));
            }

            sequence.start();
        } else if (itemId == R.id.deleteTrait) {
            checkShowDeleteDialog();
        } else if (itemId == R.id.sortTrait) {
            showTraitSortDialog();
        } else if (itemId == R.id.export) {
            if (BaseDocumentTreeUtil.Companion.getRoot(this) != null
                    && BaseDocumentTreeUtil.Companion.isEnabled(this)
                    && BaseDocumentTreeUtil.Companion.getDirectory(this, R.string.dir_trait) != null) {
                exportTraitFilePermission();
            } else {
                Toast.makeText(this, R.string.error_storage_directory, Toast.LENGTH_LONG).show();
            }
        } else if (itemId == R.id.toggleTrait) {
            changeAllVisibility();
        } else if (itemId == android.R.id.home) {
            CollectActivity.reloadData = true;
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    private void checkShowDeleteDialog() {
        ArrayList<TraitObject> traits = database.getAllTraitObjects();
        if (traits.isEmpty()) {
            Toast.makeText(this, R.string.act_trait_editor_no_traits_exist, Toast.LENGTH_SHORT).show();
        } else {
            showDeleteTraitDialog((dialog, which) -> {
                for (TraitObject t : traits) {
                    preferences.edit().remove(GeneralKeys.getCropCoordinatesKey(Integer.parseInt(t.getId()))).apply();
                }
                database.deleteTraitsTable();
                queryAndLoadTraits();
                dialog.dismiss();
            }, (dialog, which) -> dialog.dismiss(), null);
        }
    }

    private void changeAllVisibility() {
        boolean globalVis = preferences.getBoolean(GeneralKeys.ALL_TRAITS_VISIBLE, false);
        List<TraitObject> allTraits = database.getAllTraitObjects();

        if (allTraits.isEmpty()) {
            Utils.makeToast(getApplicationContext(), getString(R.string.warning_traits_missing_modify));
            return;
        }

        //issue #305 fix toggles visibility even when all are un-toggled
        globalVis = !allTraits.stream().allMatch(TraitObject::getVisible);

        for (TraitObject allTrait : allTraits) {
            database.updateTraitVisibility(allTrait.getId(), globalVis);
            Log.d(TAG, allTrait.getName());
        }

        globalVis = !globalVis;

        Editor ed = preferences.edit();
        ed.putBoolean(GeneralKeys.ALL_TRAITS_VISIBLE, globalVis);
        ed.apply();
        queryAndLoadTraits();
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_STORAGE_IMPORT)
    public void loadTraitFilePermission() {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
            if (EasyPermissions.hasPermissions(this, perms)) {
                if (preferences.getBoolean(GeneralKeys.TRAITS_EXPORTED, false)) {
                    showFileDialog();
                } else {
                    checkTraitExportDialog();
                }
            } else {
                // Do not have permissions, request them now
                EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_import),
                        PERMISSIONS_REQUEST_STORAGE_IMPORT, perms);
            }
        } else if (preferences.getBoolean(GeneralKeys.TRAITS_EXPORTED, false)) {
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
        boolean brapiEnabled = preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false);
        int optionCount = brapiEnabled ? ImportOptions.values().length : ImportOptions.values().length - 1;

        String[] importArray = new String[optionCount];
        int[] icons = new int[optionCount];
        
        importArray[ImportOptions.CREATE_NEW.ordinal()] = getString(ImportOptions.CREATE_NEW.stringResource);
        importArray[ImportOptions.IMPORT_FROM_FILE.ordinal()] = getString(ImportOptions.IMPORT_FROM_FILE.stringResource);
        icons[ImportOptions.CREATE_NEW.ordinal()] = ImportOptions.CREATE_NEW.iconResource;
        icons[ImportOptions.IMPORT_FROM_FILE.ordinal()] = ImportOptions.IMPORT_FROM_FILE.iconResource;
        
        // Add BrAPI option if enabled
        if (brapiEnabled) {
            String displayName = preferences.getString(PreferenceKeys.BRAPI_DISPLAY_NAME,
                    getString(R.string.brapi_edit_display_name_default));
            importArray[ImportOptions.IMPORT_FROM_BRAPI.ordinal()] = displayName;
            icons[ImportOptions.IMPORT_FROM_BRAPI.ordinal()] = ImportOptions.IMPORT_FROM_BRAPI.iconResource;
        }

        AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: // Create new trait
                        showTraitDialog(null);
                        break;
                    case 1: // Import from file
                        showFileImportDialog();
                        break;
                    case 2: // BrAPI
                        startBrapiTraitActivity(false);
                        break;
                }
            }
        };
        
        ListAddDialog dialog = new ListAddDialog(
            this,
            getString(R.string.traits_new_dialog_title),
            importArray, 
            icons, 
            onItemClickListener
        );
        dialog.show(getSupportFragmentManager(), "ListAddDialog");
    }

    private void showFileImportDialog() {
        
        String[] importArray = new String[2];
        int[] icons = new int[2];
        
        importArray[0] = getString(R.string.import_source_local);
        importArray[1] = getString(R.string.import_source_cloud);
        
        icons[0] = R.drawable.ic_file_generic;
        icons[1] = R.drawable.ic_file_cloud;

        AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: // Local
                        DocumentFile traitDir = BaseDocumentTreeUtil.Companion.getDirectory(TraitEditorActivity.this, R.string.dir_trait);
                        if (traitDir != null && traitDir.exists()) {
                            Intent intent = new Intent();
                            intent.setClassName(TraitEditorActivity.this, FileExploreActivity.class.getName());
                            intent.putExtra("path", traitDir.getUri().toString());
                            intent.putExtra("include", new String[]{"trt"});
                            intent.putExtra("title", getString(R.string.traits_dialog_import));
                            startActivityForResult(intent, REQUEST_FILE_EXPLORER_CODE);
                        }
                        break;
                    case 1: // Cloud
                        loadCloud();
                        break;
                }
            }
        };
        
        ListAddDialog dialog = new ListAddDialog(
            this,
            getString(R.string.traits_dialog_import_from_file),
            importArray, 
            icons, 
            onItemClickListener
        );
        dialog.show(getSupportFragmentManager(), "ListAddDialog");
    }

    public void startBrapiTraitActivity(boolean fromTraitCreator) {

        if (Utils.isConnected(this)) {
            if (prefs.getBoolean(PreferenceKeys.EXPERIMENTAL_NEW_BRAPI_UI, true)) {
                Intent intent = new Intent(this, BrapiTraitFilterActivity.class);
                BrapiFilterCache.Companion.checkClearCache(this);
                startActivity(intent);
            } else {
                Intent intent = new Intent();
                intent.setClassName(this, BrapiTraitActivity.class.getName());
                startActivityForResult(intent, REQUEST_CODE_BRAPI_TRAIT_ACTIVITY);
            }
        } else {
            Toast.makeText(this, R.string.opening_brapi_no_network_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void showFileDialog() {

        ArrayList<TraitObject> traits = database.getAllTraitObjects();

        if (!traits.isEmpty()) {

            for (TraitObject t : traits) {
                preferences.edit().remove(GeneralKeys.getCropCoordinatesKey(Integer.parseInt(t.getId()))).apply();
            }

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
            Toast.makeText(getApplicationContext(), R.string.no_suitable_file_manager_was_found, Toast.LENGTH_SHORT).show();
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

    private void showTraitSortDialog() {
        Map<String, String> sortOptions = new LinkedHashMap<>();
        final String defaultSortOrder = "position";
        String currentSortOrder = preferences.getString(GeneralKeys.TRAITS_LIST_SORT_ORDER, defaultSortOrder);

        sortOptions.put(getString(R.string.traits_sort_default), "position");
        sortOptions.put(getString(R.string.traits_sort_name), "observation_variable_name");
        sortOptions.put(getString(R.string.traits_sort_format), "observation_variable_field_book_format");
        sortOptions.put(getString(R.string.traits_sort_import_order), "internal_id_observation_variable");
        sortOptions.put(getString(R.string.traits_sort_visibility), "visible");

        ListSortDialog dialog = new ListSortDialog(this, sortOptions, currentSortOrder, defaultSortOrder, criteria -> {
            Log.d(TAG, "Updating traits list sort order to : " + criteria);
            preferences.edit().putString(GeneralKeys.TRAITS_LIST_SORT_ORDER, criteria).apply();
            queryAndLoadTraits();
        });
        dialog.show();
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
                Editor ed = preferences.edit();
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

        ArrayList<TraitObject> traits = database.getAllTraitObjects();

        if (traits == null || traits.isEmpty()) {
            Utils.makeToast(getApplicationContext(), getString(R.string.warning_traits_missing_modify));
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(TraitEditorActivity.this, R.style.AppAlertDialog);
        builder.setTitle(getString(R.string.traits_toolbar_delete_all));
        builder.setMessage(getString(R.string.dialog_delete_traits_message));

        builder.setPositiveButton(getString(R.string.dialog_delete), onPositive);
        builder.setNegativeButton(getString(R.string.dialog_no), onNegative);
        builder.setOnDismissListener(onDismiss);

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void setupBackCallback() {
        OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    // if pressed in detail fragment
                    if (traitAdapter != null) {
                        traitAdapter.notifyDataSetChanged();
                    }
                    getSupportFragmentManager().popBackStack();
                    traitList.setEnabled(true); // Re-enable touch events
                } else {
                    CollectActivity.reloadData = true;
                    finish();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_FILE_EXPLORER_CODE) {
            if (resultCode == RESULT_OK) {
                startTraitImport(Uri.parse(data.getStringExtra(FileExploreActivity.EXTRA_RESULT_KEY)));
            } else {
                Toast.makeText(this, R.string.act_file_explorer_no_file_error, Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == REQUEST_CODE_BRAPI_TRAIT_ACTIVITY) {

            if (resultCode == Activity.RESULT_OK) {
                brapiDialogShown = traitAdapter.getInfoDialogShown();
                if (!brapiDialogShown) {
                    brapiDialogShown = displayBrapiInfo(TraitEditorActivity.this, null, true);
                }
            } else {
                showTraitDialog(null);
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

                    startTraitImport(traitExportFile.getUri());
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

                        FileUtil.shareFile(this, preferences, exportDoc);
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

    public void showTraitDialog(@Nullable TraitObject traitObject) {
        queryAndLoadTraits();
        NewTraitDialog traitDialog = new NewTraitDialog(this);
        traitDialog.setTraitObject(traitObject);
        traitDialog.show(getSupportFragmentManager(), "NewTraitDialog");
    }

    // Delete trait
    public void deleteTrait(TraitObject trait) {

        preferences.edit().remove(GeneralKeys.getCropCoordinatesKey(Integer.parseInt(trait.getId()))).apply();
        getDatabase().deleteTrait(trait.getId());
        queryAndLoadTraits();

        CollectActivity.reloadData = true;
    }

    private void refreshTraitDetailFragment() {
        TraitDetailFragment fragment = (TraitDetailFragment) getSupportFragmentManager()
                .findFragmentByTag("TraitDetailFragmentTag");
        if (fragment != null) {
            fragment.refresh();
        }
    }

    @Override
    public void onNewTraitDialogDismiss() {
        if (!brapiDialogShown) {
            brapiDialogShown = displayBrapiInfo(TraitEditorActivity.this, null, true);
        }
        queryAndLoadTraits();
        refreshTraitDetailFragment();
    }

    private void setupTraitEditorInsets() {
        View rootView = findViewById(android.R.id.content);
        Toolbar toolbar = findViewById(R.id.toolbar);
        FloatingActionButton fab = findViewById(R.id.newTrait);

        InsetHandler.INSTANCE.setupStandardInsets(rootView, toolbar);
    }

    private FileFormat detectTraitFileFormat(Uri fileUri) {
        try {
            InputStream inputStream = BaseDocumentTreeUtil.Companion.getUriInputStream(this, fileUri);
            if (inputStream == null) {
                return FileFormat.UNKNOWN;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String firstLine = reader.readLine();
            if (firstLine != null) {
                firstLine = firstLine.trim();
            } else {
                firstLine = "";
            }
            reader.close();
            inputStream.close();

            if (firstLine.startsWith("{") || firstLine.startsWith("[")) {
                return FileFormat.JSON;
            } else if (firstLine.contains("\"trait\"")) {
                return FileFormat.CSV;
            } else {
                return FileFormat.UNKNOWN;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error detecting trait file format", e);
            return FileFormat.UNKNOWN;
        }
    }

    private void startTraitImport(Uri fileUri) {
        String fileName = new FileUtil().getFileName(this, fileUri);
        String extension = FieldFileObject.getExtension(fileName);

        if (!extension.equals("trt")) {
            Utils.makeToast(getApplicationContext(), getString(R.string.import_error_format_trait));
            return;
        }

        new Thread(() -> {
            FileFormat format = detectTraitFileFormat(fileUri);

            runOnUiThread(() -> {
                switch (format) {
                    case JSON:
                        startImportJson(fileUri);
                        break;
                    case CSV:
                        startImportCsv(fileUri);
                        break;
                    case UNKNOWN:
                        Utils.makeToast(getApplicationContext(), getString(R.string.import_error_format_trait));
                        break;
                }
            });
        }).start();
    }
    private void startImportJson(Uri file) {
        ImportJsonTraitTask task = new ImportJsonTraitTask(
                this,
                database,
                file,
                LifecycleOwnerKt.getLifecycleScope(this),
                (success, errorMessage) -> {
                    if (success) {
                        queryAndLoadTraits();
                        CollectActivity.reloadData = true;
                        Utils.makeToast(getApplicationContext(), getString(R.string.trait_import_successful));
                    } else {
                        String message = errorMessage != null ? errorMessage : getString(R.string.import_error_general);
                        Utils.makeToast(getApplicationContext(), message);
                    }
                }
        );
        task.start();
    }
}