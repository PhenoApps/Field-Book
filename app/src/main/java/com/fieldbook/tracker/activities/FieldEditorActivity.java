package com.fieldbook.tracker.activities;

import static com.fieldbook.tracker.activities.TraitEditorActivity.REQUEST_CODE_BRAPI_TRAIT_ACTIVITY;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Rect;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.brapi.BrapiActivity;
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache;
import com.fieldbook.tracker.activities.brapi.io.filter.filterer.BrapiStudyFilterActivity;
import com.fieldbook.tracker.adapters.FieldAdapter;
import com.fieldbook.tracker.async.ImportRunnableTask;
import com.fieldbook.tracker.brapi.BrapiInfoDialogFragment;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.dao.StudyGroupDao;
import com.fieldbook.tracker.database.models.ObservationUnitModel;
import com.fieldbook.tracker.database.models.StudyGroupModel;
import com.fieldbook.tracker.dialogs.FieldCreatorDialogFragment;
import com.fieldbook.tracker.dialogs.FieldSortDialogFragment;
import com.fieldbook.tracker.dialogs.ListAddDialog;
import com.fieldbook.tracker.dialogs.ListSortDialog;
import com.fieldbook.tracker.interfaces.FieldAdapterController;
import com.fieldbook.tracker.interfaces.FieldSortController;
import com.fieldbook.tracker.interfaces.FieldSwitcher;
import com.fieldbook.tracker.location.GPSTracker;
import com.fieldbook.tracker.objects.FieldFileObject;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.preferences.PreferenceKeys;
import com.fieldbook.tracker.utilities.ExportUtil;
import com.fieldbook.tracker.utilities.FieldSwitchImpl;
import com.fieldbook.tracker.utilities.SnackbarUtils;
import com.fieldbook.tracker.utilities.TapTargetUtil;
import com.fieldbook.tracker.utilities.Utils;
import com.fieldbook.tracker.views.SearchBar;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

@AndroidEntryPoint
public class FieldEditorActivity extends ThemedActivity
        implements FieldSortController, FieldAdapterController, FieldAdapter.AdapterCallback {

    private static final int REQUEST_FILE_EXPLORER_CODE = 1;
    private static final int REQUEST_CLOUD_FILE_CODE = 5;
    private static final int REQUEST_BRAPI_IMPORT_ACTIVITY = 10;
    private static final Handler mHandler = new Handler();
    private final String TAG = "FieldEditor";
    private final int PERMISSIONS_REQUEST_STORAGE = 998;
    public FieldAdapter mAdapter;
    public EditText trait;
    public ExportUtil exportUtil;
    @Inject
    DataHelper database;
    @Inject
    FieldSwitchImpl fieldSwitcher;
    @Inject
    SharedPreferences preferences;
    RecyclerView recyclerView;
    private ArrayList<FieldObject> fieldList;
    private FieldFileObject.FieldFileBase fieldFile;
    private Spinner unique;
    // Creates a new thread to do importing
    private final Runnable importRunnable = new Runnable() {
        public void run() {
            new ImportRunnableTask(FieldEditorActivity.this,
                    fieldFile,
                    unique.getSelectedItemPosition(),
                    unique.getSelectedItem().toString()).execute(0);
        }
    };
    private Menu systemMenu;
    private GPSTracker mGpsTracker;
    private SearchBar searchBar;

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
        mAdapter = new FieldAdapter(this, fieldSwitcher, this, false);
        mAdapter.setOnFieldSelectedListener(new FieldAdapter.OnFieldSelectedListener() {
            @Override
            public void onFieldSelected(int fieldId) {
                FieldDetailFragment fragment = new FieldDetailFragment();
                Bundle args = new Bundle();
                args.putInt("fieldId", fieldId);
                fragment.setArguments(args);

                // Disable touch events on the RecyclerView
                recyclerView.setEnabled(false);

                getSupportFragmentManager().beginTransaction()
                        .replace(android.R.id.content, fragment, "FieldDetailFragmentTag")
                        .addToBackStack(null)
                        .commit();
            }
        });
        recyclerView.setAdapter(mAdapter);

        FloatingActionButton fab = findViewById(R.id.newField);
        fab.setOnClickListener(v -> handleImportAction());
        fab.setOnLongClickListener(v -> {
            showFileDialog();
            return true;
        });

        searchBar = findViewById(R.id.act_fields_sb);

        queryAndLoadFields();

    }

    @Override
    public void onResume() {
        super.onResume();

        if (systemMenu != null) {
            systemMenu.findItem(R.id.help).setVisible(preferences.getBoolean(PreferenceKeys.TIPS, false));
        }

        queryAndLoadFields();
        mGpsTracker = new GPSTracker(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    // Implementations of methods from FieldAdapter.AdapterCallback
    @Override
    public void onItemSelected(int selectedCount) {

        if (mAdapter.getSelectedItemCount() > 0) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.selected_count, selectedCount));
            }
        } else {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.settings_fields));
            }
        }

        invalidateOptionsMenu();
    }

    @Override
    public void onItemClear() {

    }

    public void setActiveField(int studyId) {

        //get current field id and compare the input, only switch if they are different
        int currentFieldId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, -1);
        if (currentFieldId == studyId) return;

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
                        if (isFromDetailFragment) {
                            getSupportFragmentManager().popBackStack();
                        }
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

        queryAndLoadFields();
        mAdapter.exitSelectionMode();
    }

    private void showFileDialog() {
        String[] importArray = new String[3];
        importArray[0] = getString(R.string.import_source_local);
        importArray[1] = getString(R.string.import_source_cloud);
        importArray[2] = getString(R.string.fields_new_create_field);
        if (preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)) {
            String displayName = preferences.getString(PreferenceKeys.BRAPI_DISPLAY_NAME, getString(R.string.brapi_edit_display_name_default));
            importArray = Arrays.copyOf(importArray, importArray.length + 1);
            importArray[3] = displayName;
        }

        int[] icons = new int[importArray.length];
        icons[0] = R.drawable.ic_file_generic;
        icons[1] = R.drawable.ic_file_cloud;
        icons[2] = R.drawable.ic_field;
        if (importArray.length > 3) {
            icons[3] = R.drawable.ic_adv_brapi;
        }

        AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        if (checkDirectory()) {
                            loadLocalPermission();
                        }
                        break;
                    case 1:
                        if (checkDirectory()) {
                            loadCloud();
                        }
                        break;
                    case 2:
                        FieldCreatorDialogFragment dialog = new FieldCreatorDialogFragment((ThemedActivity) FieldEditorActivity.this);
                        dialog.setFieldCreationCallback(new FieldCreatorDialogFragment.FieldCreationCallback() {
                            @Override
                            public void onFieldCreated(int studyDbId) {
                                fieldSwitcher.switchField(studyDbId);
                                queryAndLoadFields();
                            }
                        });
                        dialog.show(getSupportFragmentManager(), "FieldCreatorDialogFragment");
                        break;
                    case 3:
                        loadBrAPI();
                        break;
                }
            }
        };

        ListAddDialog dialog = new ListAddDialog(
                this,
                getString(R.string.fields_new_dialog_title),
                importArray,
                icons,
                onItemClickListener
        );
        dialog.show(getSupportFragmentManager(), "ListAddDialog");
    }

    public void loadLocal() {

        try {
            DocumentFile importDir = BaseDocumentTreeUtil.Companion.getDirectory(this, R.string.dir_field_import);
            if (importDir != null && importDir.exists()) {
                Intent intent = new Intent();
                intent.setClassName(FieldEditorActivity.this, FileExploreActivity.class.getName());
                intent.putExtra("path", importDir.getUri().toString());
                intent.putExtra("include", new String[]{"csv", "xls", "xlsx"});
                intent.putExtra("title", getString(R.string.fields_new_dialog_title));
                startActivityForResult(intent, REQUEST_FILE_EXPLORER_CODE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadBrAPI() {

        if (Utils.isConnected(this)) {
            if (prefs.getBoolean(PreferenceKeys.EXPERIMENTAL_NEW_BRAPI_UI, true)) {
                Intent intent = new Intent(this, BrapiStudyFilterActivity.class);
                BrapiFilterCache.Companion.checkClearCache(this);
                startActivityForResult(intent, REQUEST_BRAPI_IMPORT_ACTIVITY);
            } else {
                Intent intent = new Intent();
                intent.setClassName(this, BrapiActivity.class.getName());
                startActivityForResult(intent, REQUEST_CODE_BRAPI_TRAIT_ACTIVITY);
            }
        } else {
            Toast.makeText(this, R.string.opening_brapi_no_network_error, Toast.LENGTH_SHORT).show();
        }
    }

    private Boolean checkDirectory() {
        if (BaseDocumentTreeUtil.Companion.getRoot(this) != null
                && BaseDocumentTreeUtil.Companion.isEnabled(this)
                && BaseDocumentTreeUtil.Companion.getDirectory(this, R.string.dir_field_import) != null) {
            return true;
        } else {
            Toast.makeText(this, R.string.error_storage_directory, Toast.LENGTH_LONG).show();
            return false;
        }
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
        systemMenu.findItem(R.id.help).setVisible(preferences.getBoolean(PreferenceKeys.TIPS, false));

        //TODO rather not use reflection here...
        //https://stackoverflow.com/questions/18374183/how-to-show-icons-in-overflow-menu-in-actionbar
        if(menu.getClass().getSimpleName().equals("MenuBuilder")){
            try{
                Method m = menu.getClass().getDeclaredMethod(
                        "setOptionalIconsVisible", Boolean.TYPE);
                m.setAccessible(true);
                m.invoke(menu, true);
            }
            catch(NoSuchMethodException e){
                Log.e(TAG, "onMenuOpened", e);
            }
            catch(Exception e){
                throw new RuntimeException(e);
            }
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isGroupingPossible = areFieldsGrouped();
        boolean isGroupingEnabled = preferences.getBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, false);
        boolean userHasToggledGrouping = preferences.getBoolean(GeneralKeys.USER_TOGGLED_FIELD_GROUPING, false);

        MenuItem groupToggleItem = menu.findItem(R.id.toggle_group_visibility);
        MenuItem sortFieldsItem = menu.findItem(R.id.sortFields);
        MenuItem exportItem = menu.findItem(R.id.menu_export);
        MenuItem selectAllItem = menu.findItem(R.id.menu_select_all);
        MenuItem groupFieldsItem = menu.findItem(R.id.menu_group_fields);
        MenuItem archiveFieldsItem = menu.findItem(R.id.menu_archive_fields);
        MenuItem deleteFieldsItem = menu.findItem(R.id.menu_delete);

        groupToggleItem.setVisible(isGroupingPossible); // change icon visibility

        if (!isGroupingPossible) { // if grouping is not possible, force disable grouping state
            preferences.edit().putBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, false).apply();
            preferences.edit().putBoolean(GeneralKeys.USER_TOGGLED_FIELD_GROUPING, false).apply(); // set user toggle to false since forced
        } else if (!isGroupingEnabled && !userHasToggledGrouping) { // grouping was disabled AND user did not toggle it, enable grouping
            preferences.edit().putBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, true).apply();
            // if user had toggled grouping to false using the menu item
            // then not including !userHasToggledGrouping in the condition would set the grouping to true
        }

        // set collapse/expand item visibility
        boolean groupingEnabled = preferences.getBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, false);

        menu.findItem(R.id.collapseGroups).setVisible(groupingEnabled);
        menu.findItem(R.id.expandGroups).setVisible(groupingEnabled);

        mAdapter.resetFieldsList(fieldList);

        MenuItem[] defaultMenuListItems = new MenuItem[]{
                sortFieldsItem,
        };

        MenuItem[] selectedMenuListItems = new MenuItem[]{
                exportItem,
                selectAllItem,
                groupFieldsItem,
                archiveFieldsItem,
                deleteFieldsItem};

        if (mAdapter.getSelectedItemCount() == 0) {

            for (MenuItem item : defaultMenuListItems) {
                toggleMenuItem(item, true);
            }

            for (MenuItem item : selectedMenuListItems) {
                toggleMenuItem(item, false);
            }

        } else {

            groupToggleItem.setVisible(false);
            groupToggleItem.setEnabled(false);

            for (MenuItem item : defaultMenuListItems) {
                toggleMenuItem(item, false);
            }

            for (MenuItem item : selectedMenuListItems) {
                toggleMenuItem(item, true);
            }
        }

        return true;
    }

    private void toggleMenuItem(MenuItem item, boolean enabled) {
        item.setEnabled(enabled);
        item.setVisible(enabled);
    }

    /**
     * Return true if:
     * - at least one study group exists OR
     * - at least one field is archived
     */
    private boolean areFieldsGrouped() {
        List<StudyGroupModel> allStudyGroups = database.getAllStudyGroups();

        boolean hasArchivedFields = fieldList.stream().anyMatch(FieldObject::getIsArchived);
        boolean hasGroups = allStudyGroups != null && !allStudyGroups.isEmpty();
        return hasArchivedFields || hasGroups;
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
                    .targets(fieldsTapTargetMenu(R.id.newField, getString(R.string.tutorial_fields_add_title), getString(R.string.tutorial_fields_add_description), 60),
                            fieldsTapTargetMenu(R.id.newField, getString(R.string.tutorial_fields_add_title), getString(R.string.tutorial_fields_file_description), 60)
                    );
            if (fieldExists()) {
                sequence.target(fieldsTapTargetRect(fieldsListItemLocation(0), getString(R.string.tutorial_fields_select_title), getString(R.string.tutorial_fields_select_description)));
                sequence.target(fieldsTapTargetRect(fieldsListItemLocation(0), getString(R.string.tutorial_fields_delete_title), getString(R.string.tutorial_fields_delete_description)));
            }

            sequence.start();
        } else if (itemId == android.R.id.home) {
            CollectActivity.reloadData = true;
            finish();
        } else if (itemId == R.id.action_select_plot_by_distance) {
            if (mGpsTracker != null && mGpsTracker.canGetLocation()) {
                selectPlotByDistance();
            } else {
                Toast.makeText(this, R.string.activity_field_editor_no_location_yet, Toast.LENGTH_SHORT).show();
            }
        } else if (itemId == R.id.sortFields) {
            showFieldsSortDialog();
        } else if (itemId == R.id.toggle_group_visibility) {
            boolean groupingEnabled = preferences.getBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, false);

            preferences.edit().putBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, !groupingEnabled).apply();
            preferences.edit().putBoolean(GeneralKeys.USER_TOGGLED_FIELD_GROUPING, true).apply();

            // if (systemMenu != null) {
            //     updateGroupingIcon();
            // }

            queryAndLoadFields();

            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> recyclerView.scrollToPosition(0),
                    100
            );

            return true;
        } else if (itemId == R.id.collapseGroups) {
            mAdapter.changeStateOfAllGroups(false);
        } else if (itemId == R.id.expandGroups) {
            mAdapter.changeStateOfAllGroups(true);
        } else if (itemId == R.id.menu_select_all) {
            mAdapter.changeStateOfAllGroups(true); // expand all groups
            mAdapter.selectAll();
            return true;
        } else if (itemId == R.id.menu_export) {
            exportUtil.exportMultipleFields(mAdapter.getSelectedItems());
            mAdapter.exitSelectionMode();
            invalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.menu_group_fields) {
            showGroupAssignmentDialog(mAdapter.getSelectedItems());
            mAdapter.exitSelectionMode();
            invalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.menu_archive_fields) {
            for (Integer fieldId : mAdapter.getSelectedItems()) {
                database.setIsArchived(fieldId, true);
            }
            queryAndLoadFields();
            mAdapter.exitSelectionMode();
            invalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.menu_delete) {
            showDeleteConfirmationDialog(mAdapter.getSelectedItems(), false);
            mAdapter.exitSelectionMode();
            invalidateOptionsMenu();
            return true;
        } else {
            return false;
        }
        return super.onOptionsItemSelected(item);
    }

    // private void updateGroupingIcon() {
    //     boolean groupingEnabled = preferences.getBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, false);
    //     systemMenu.findItem(R.id.toggle_group_visibility).setIcon(groupingEnabled ? R.drawable.ic_existing_group : R.drawable.ic_ungroup);
    // }

    private void showFieldsSortDialog() {
        Map<String, String> sortOptions = new LinkedHashMap<>();
        final String defaultSortOrder = "date_import";
        String currentSortOrder = preferences.getString(GeneralKeys.FIELDS_LIST_SORT_ORDER, defaultSortOrder);

        sortOptions.put(getString(R.string.fields_sort_by_name), "study_alias");
        sortOptions.put(getString(R.string.fields_sort_by_import_format), "import_format");
        sortOptions.put(getString(R.string.fields_sort_by_import_date), "date_import");
        sortOptions.put(getString(R.string.fields_sort_by_edit_date), "date_edit");
        sortOptions.put(getString(R.string.fields_sort_by_sync_date), "date_sync");
        sortOptions.put(getString(R.string.fields_sort_by_export_date), "date_export");

        ListSortDialog dialog = new ListSortDialog(this, sortOptions, currentSortOrder, defaultSortOrder, criteria -> {
            Log.d(TAG, "Updating fields list sort order to : " + criteria);
            preferences.edit().putString(GeneralKeys.FIELDS_LIST_SORT_ORDER, criteria).apply();
            queryAndLoadFields();
        });
        dialog.show();
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
                                    queryAndLoadFields();
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
            recyclerView.setEnabled(true); // Re-enable touch events
        } else {
            CollectActivity.reloadData = true;
            super.onBackPressed();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

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
                    BrapiInfoDialogFragment dialogFragment = new BrapiInfoDialogFragment().newInstance(getResources().getString(R.string.brapi_info_message));
                    dialogFragment.show(this.getSupportFragmentManager(), "brapiInfoDialogFragment");
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

            if (importDoc != null) {

                ContentResolver resolver = getContentResolver();
                if (resolver != null) {

                    String cloudName = null;
                    if (isCloud != null && isCloud) {
                        cloudName = getFileName(Uri.parse(chosenFile));
                    } else {
                        if (!importDoc.exists()) return;
                    }

                    try (InputStream is = resolver.openInputStream(docUri)) {

                        fieldFile = FieldFileObject.create(this, docUri, is, cloudName);

                        String fieldFileName = fieldFile.getStem();
                        if (isCloud != null && isCloud) {
                            int index = cloudName.lastIndexOf(".");
                            if (index > -1) {
                                cloudName = cloudName.substring(0, index);
                            }
                            fieldFile.setName(cloudName);
                        }

                        Editor e = preferences.edit();
                        e.putString(GeneralKeys.FIELD_FILE, fieldFileName);
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
        View layout = inflater.inflate(R.layout.dialog_field_file_import, null);

        unique = layout.findViewById(R.id.uniqueSpin);

        setSpinner(unique, columns, GeneralKeys.UNIQUE_NAME);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.fields_new_dialog_title)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_import), (dialogInterface, i) -> {
            mHandler.post(importRunnable);
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
        FieldSortDialogFragment dialogFragment = new FieldSortDialogFragment().newInstance(
                this,
                field,
                sortOrderList.toArray(new String[]{}),
                database.getRangeColumnNames()
        );

        dialogFragment.show(this.getSupportFragmentManager(), "FieldSortDialogFragment");
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
                    .setPositiveButton(R.string.dialog_ok, (dInterface, i) -> Log.d("FieldAdapter", "Sort save error dialog dismissed"))
                    .setMessage(R.string.sort_dialog_error_saving)
                    .create()
                    .show();
        }

        queryAndLoadFields();

    }

    @Override
    public void queryAndLoadFields() {
        try {
            fieldList = database.getAllFieldObjects(); // Fetch data from the database
            mAdapter.resetFieldsList(new ArrayList<>(fieldList));

            database.deleteUnusedStudyGroups();

            invalidateOptionsMenu(); // invokes onPrepareOptionsMenu

            new Handler(Looper.getMainLooper()).postDelayed(this::setupSearchBar, 100);

        } catch (Exception e) {
            Log.e(TAG, "Error updating fields list", e);
        }
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

    private void setupSearchBar() {

        if (recyclerView.canScrollVertically(1) || recyclerView.canScrollVertically(-1)) {

            searchBar.setVisibility(View.VISIBLE);

            searchBar.editText.addTextChangedListener(new android.text.TextWatcher() {

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    // Do nothing
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    mAdapter.setTextFilter(s.toString());
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    // Do nothing
                }
            });

        } else {

            searchBar.setVisibility(View.GONE);

        }
    }

    private void showGroupAssignmentDialog(List<Integer> fieldIds) {
        List<StudyGroupModel> allStudyGroups = database.getAllStudyGroups();
        boolean hasStudyGroups = allStudyGroups != null && !allStudyGroups.isEmpty();

        // check if any of the selected fields are in a group
        boolean anyFieldsInGroup = false;

        for (Integer fieldId : fieldIds) {
            FieldObject field = fieldList.stream()
                    .filter(f -> f.getExp_id() == fieldId)
                    .findFirst()
                    .orElse(null);

            if (field != null) {
                String groupName = StudyGroupDao.Companion.getStudyGroupNameById(field.getGroupId());
                if (groupName != null && !groupName.isEmpty()) {
                    anyFieldsInGroup = true;
                    break;
                }
            }
        }

        List<String> optionStrings = new ArrayList<>();
        List<Integer> optionIcon = new ArrayList<>();

        // "existing group" option
        if (hasStudyGroups) {
            optionStrings.add(getString(R.string.group_existing));
            optionIcon.add(R.drawable.ic_existing_group);
        }

        // "new group" option
        optionStrings.add(getString(R.string.group_new));
        optionIcon.add(R.drawable.ic_new_group);

        // "remove from group" option
        if (anyFieldsInGroup) {
            optionStrings.add(getString(R.string.group_remove));
            optionIcon.add(R.drawable.ic_ungroup);
        }

        String[] options = optionStrings.toArray(new String[0]);
        int[] icons = new int[optionIcon.size()];
        for (int i = 0; i < optionIcon.size(); i++) {
            icons[i] = optionIcon.get(i);
        }

        AdapterView.OnItemClickListener onItemClickListener = (parent, view, position, id) -> {
            String selectedOption = options[position];

            if (selectedOption.equals(getString(R.string.group_existing))) {
                showExistingGroupsDialog(fieldIds);
            } else if (selectedOption.equals(getString(R.string.group_new))) {
                showNewGroupDialog(fieldIds);
            } else if (selectedOption.equals(getString(R.string.group_remove))) {
                for (Integer fieldId : fieldIds) {
                    database.updateStudyGroup(fieldId, null);
                }
                queryAndLoadFields();
                mAdapter.exitSelectionMode();
            }
        };

        ListAddDialog dialog = new ListAddDialog(
                this,
                getString(R.string.dialog_group_options),
                options,
                icons,
                onItemClickListener
        );
        dialog.show(getSupportFragmentManager(), "ListAddDialog");
    }

    private void showExistingGroupsDialog(List<Integer> fieldIds) {
        List<StudyGroupModel> existingGroups = database.getAllStudyGroups();

        new AlertDialog.Builder(this, R.style.AppAlertDialog)
                .setTitle(R.string.group_existing)
                .setItems(existingGroups.stream()
                        .map(StudyGroupModel::getGroupName)
                        .toArray(String[]::new), (dialog, which) -> {
                    Integer groupId = existingGroups.get(which).component1();

                    for (Integer fieldId : fieldIds) {
                        database.updateStudyGroup(fieldId, groupId);
                    }
                    mAdapter.exitSelectionMode();
                    queryAndLoadFields();
                })
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showNewGroupDialog(List<Integer> fieldIds) {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_group_name, null);
        final EditText groupName = layout.findViewById(R.id.groupName);

        groupName.clearFocus();

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.AppAlertDialog)
                .setTitle(R.string.create_new_group)
                .setView(layout)
                .setPositiveButton(getString(R.string.dialog_ok), null)
                .setNegativeButton(getString(R.string.dialog_cancel), (d, i) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String groupNameStr = groupName.getText().toString().trim();
            if (!groupNameStr.isEmpty()) { // add selected fields to group
                for (Integer fieldId : fieldIds) {
                    Integer groupId = database.createOrGetStudyGroup(groupNameStr);
                    database.updateStudyGroup(fieldId, groupId);
                }
                queryAndLoadFields();
                mAdapter.exitSelectionMode();
                dialog.dismiss();
            } else {
                groupName.setError(getString(R.string.dialog_group_name_warning));
            }
        }));

        dialog.show();
    }
}