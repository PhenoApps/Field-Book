package com.fieldbook.tracker.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.adapters.TraitAdapter;
import com.fieldbook.tracker.adapters.TraitAdapterController;
import com.fieldbook.tracker.brapi.BrapiInfoDialog;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.dialogs.NewTraitDialog;
import com.fieldbook.tracker.dragsort.DragSortController;
import com.fieldbook.tracker.dragsort.DragSortListView;
import com.fieldbook.tracker.objects.FieldFileObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.CSVReader;
import com.fieldbook.tracker.utilities.CSVWriter;
import com.fieldbook.tracker.utilities.DialogUtils;
import com.fieldbook.tracker.utilities.Utils;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

@AndroidEntryPoint
public class TraitEditorActivity extends AppCompatActivity implements TraitAdapterController {

    public static int REQUEST_CLOUD_FILE_CODE = 5;
    public static int REQUEST_FILE_EXPLORER_CODE = 1;

    public static DragSortListView traitList;
    public static TraitAdapter mAdapter;
    public static boolean brapiDialogShown = false;
    public static Activity thisActivity;
    private static final Handler mHandler = new Handler();
    private static Uri mChosenFile;

    private static SharedPreferences ep;

    private static OnItemClickListener traitListener;

    private static DragSortListView.RemoveListener onRemove = new DragSortListView.RemoveListener() {
        @Override
        public void remove(int which) {
            mAdapter.list.remove(which);
        }
    };

    private final DragSortListView.DropListener onDrop = new DragSortListView.DropListener() {

        @Override
        public void drop(int from, int to) {

            // The new logic is that the drop point is now the center of the list. Any items before / after are reordered based on the drop point's position.
            // This means while the realposition may not start from zero, the ordering will always be correct.

            Log.w("ReorderStart", "start");

            if (from != to) {
                if (to > from) {
                    Log.w("Downward", "drag");

                    try {
                        // e.g. 4
                        String prevID = mAdapter.getItem(from).getId();

                        // e.g. 6
                        String currentID = mAdapter.getItem(to).getId();
                        Integer currentPosition = mAdapter.getItem(to).getRealPosition();

                        database.updateTraitPosition(currentID, currentPosition);
                        database.updateTraitPosition(prevID, currentPosition + 1);

                        // Push everything below down by 1
                        int newCount = 2;

                        for (int i = to + 1; i < mAdapter.getCount(); i++) {
                            database.updateTraitPosition(mAdapter.getItem(i).getId(), currentPosition + newCount);
                            newCount++;
                        }

                        CollectActivity.reloadData = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.w("Upward", "drag");

                    try {
                        // upward drag
                        // e.g. 4
                        String prevID = mAdapter.getItem(from).getId();

                        // e.g. 6
                        Integer currentPosition = mAdapter.getItem(to).getRealPosition();

                        if (currentPosition - to >= 0) {
                            Log.w("Reorder", "top1");

                            // Reorder everything above
                            int newCount = currentPosition - to;

                            for (int i = 0; i < to; i++) {
                                database.updateTraitPosition(mAdapter.getItem(i).getId(), newCount);
                                newCount++;
                            }

                            Log.w("Reorder", "current");

                            database.updateTraitPosition(prevID, currentPosition);

                        } else {
                            // We hit a -1, might as well do a full zero based reorder
                            // Reorder everything above

                            Log.w("Reorder", "top2");

                            for (int i = 0; i < to; i++) {
                                database.updateTraitPosition(mAdapter.getItem(i).getId(), i);
                            }

                            Log.w("Reorder", "current");

                            database.updateTraitPosition(prevID, to);

                            // Reset current position as well, otherwise we don't know where it points to
                            currentPosition = to;
                        }

                        Log.w("Reorder", "below");

                        // Push everything below down by 1
                        int newCount = 1;

                        // last pulled position is from field

                        for (int i = to; i < mAdapter.getCount(); i++) {
                            if (i != from) {
                                database.updateTraitPosition(mAdapter.getItem(i).getId(), currentPosition + newCount);
                                newCount++;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }

            CollectActivity.reloadData = true;
            queryAndLoadTraits();
        }
    };
    private final int PERMISSIONS_REQUEST_STORAGE_IMPORT = 999;
    private final int PERMISSIONS_REQUEST_STORAGE_EXPORT = 998;
    private NewTraitDialog traitDialog;
    private Menu systemMenu;
    // Creates a new thread to do importing
    private Runnable importCSV = () -> new ImportCSVTask().execute(0);

    private DragSortController mController;

    @Inject
    DataHelper database;

    // Helper function to load data
    public void loadData(HashMap<String, Boolean> visibility, ArrayList<TraitObject> traits) {
        try {

            if (!traitList.isShown())
                traitList.setVisibility(ListView.VISIBLE);

            // Determine if our BrAPI dialog was shown with our current trait adapter
            Boolean showBrapiDialog;
            if (mAdapter != null) {
                // Check if current trait adapter has shown a dialog
                brapiDialogShown = !brapiDialogShown ? mAdapter.infoDialogShown : brapiDialogShown;
            } else {
                // We should show our brapi dialog if this is our creating of the mAdapter
                brapiDialogShown = false;
            }

            mAdapter = new TraitAdapter(thisActivity, R.layout.listitem_trait, traits, traitListener, visibility, brapiDialogShown);

            traitList.setAdapter(mAdapter);
            traitList.setDropListener(onDrop);
            traitList.setRemoveListener(onRemove);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean displayBrapiInfo(Context context, @Nullable String traitName, boolean noCheckTrait) {

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
        return mAdapter;
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
            thisActivity.finish();
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

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.settings_traits));
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        thisActivity = this;

        HashMap visibility = database.getTraitVisibility();
        traitList = findViewById(R.id.myList);

        if (!traitList.isShown())
            traitList.setVisibility(ListView.VISIBLE);

        // Determines whether brapi dialog should be shown on first click of a non-BrAPI
        // trait when a BrAPI field is selected.
        brapiDialogShown = false;
        mAdapter = new TraitAdapter(thisActivity, R.layout.listitem_trait, database.getAllTraitObjects(), traitListener, visibility, brapiDialogShown);

        traitList.setAdapter(mAdapter);
        traitList.setDropListener(onDrop);
        traitList.setRemoveListener(onRemove);

        mController = new DragSortController(traitList);
        mController.setDragHandleId(R.id.dragSort);
        mController.setRemoveEnabled(false);
        mController.setSortEnabled(true);
        mController.setDragInitMode(1);

        traitList.setFloatViewManager(mController);
        traitList.setOnTouchListener(mController);
        traitList.setDragEnabled(true);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_new_trait, null);
        traitDialog = new NewTraitDialog(layout, this);

        traitListener = (parent, view, position, id) -> {

            // When a trait is selected, alter the layout of the edit dialog accordingly

            TraitObject o = mAdapter.getItem(position);
            traitDialog.setTraitObject(o);

            queryAndLoadTraits();
            traitDialog.show(true);
        };

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

    private TapTarget traitsTapTargetMenu(int id, String title, String desc) {
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (!mController.getDragging()) {

            switch (item.getItemId()) {
                case R.id.help:
                    TapTargetSequence sequence = new TapTargetSequence(this)
                            .targets(traitsTapTargetMenu(R.id.addTrait, getString(R.string.tutorial_traits_add_title), getString(R.string.tutorial_traits_add_description))
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

        } else {

            Utils.makeToast(thisActivity, getString(R.string.act_trait_editor_menu_click_while_dragging));

            return false;
        }
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
            Log.d("Field", allTrait.getTrait());
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, sortOptions);
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
        DialogUtils.styleDialogs(importExport);

        android.view.WindowManager.LayoutParams params = importExport.getWindow().getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        importExport.getWindow().setAttributes(params);

        myList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                switch (which) {
                    case 0:
                        loadTraitFilePermission();
                        break;
                    case 1:
                        exportTraitFilePermission();
                        break;
                }
                importExport.dismiss();
            }
        });

    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_STORAGE_IMPORT)
    public void loadTraitFilePermission() {
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

    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_STORAGE_EXPORT)
    public void exportTraitFilePermission() {
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            showExportDialog();
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_storage_export),
                    PERMISSIONS_REQUEST_STORAGE_EXPORT, perms);
        }
    }

    private void showImportDialog() {

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list_buttonless, null);

        ListView myList = layout.findViewById(R.id.myList);
        String[] importArray = new String[3];
        importArray[0] = getString(R.string.import_source_local);
        importArray[1] = getString(R.string.import_source_cloud);
        importArray[2] = getString(R.string.import_source_brapi);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, importArray);
        myList.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.import_dialog_title_traits)
                .setCancelable(true)
                .setView(layout);

        builder.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        final AlertDialog importDialog = builder.create();

        importDialog.show();
        DialogUtils.styleDialogs(importDialog);

        android.view.WindowManager.LayoutParams params = importDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        importDialog.getWindow().setAttributes(params);

        myList.setOnItemClickListener((av, arg1, which, arg3) -> {
            Intent intent = new Intent();
            switch (which) {
                case 0:
                    DocumentFile traitDir = BaseDocumentTreeUtil.Companion.getDirectory(thisActivity, R.string.dir_trait);
                    if (traitDir != null && traitDir.exists()) {
                        intent.setClassName(thisActivity, FileExploreActivity.class.getName());
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
                    intent.setClassName(thisActivity, BrapiTraitActivity.class.getName());
                    startActivityForResult(intent, 2);
                    break;
            }
            importDialog.dismiss();
        });
    }

    private void showFileDialog() {

        ArrayList<TraitObject> traits = database.getAllTraitObjects();

        if (!traits.isEmpty()) {

            showDeleteTraitDialog(null, null, (dialog) -> {

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

            BaseDocumentTreeUtil.Companion.getDirectory(thisActivity, R.string.dir_trait);

            showExportDialog();
            dialog.dismiss();
        });

        builder.setNegativeButton(getString(R.string.dialog_no), (dialog, which) -> {
            showFileDialog();
            dialog.dismiss();
        });

        AlertDialog alert = builder.create();
        alert.show();
        DialogUtils.styleDialogs(alert);
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

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, sortOptions);
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
        DialogUtils.styleDialogs(sortDialog);

        android.view.WindowManager.LayoutParams params = sortDialog.getWindow().getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        sortDialog.getWindow().setAttributes(params);

        myList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
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
            }
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
        DialogUtils.styleDialogs(exportDialog);

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
        builder.setNegativeButton(getString(android.R.string.no), onNegative);
        builder.setOnDismissListener(onDismiss);

        AlertDialog alert = builder.create();
        alert.show();
        DialogUtils.styleDialogs(alert);
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
                mChosenFile = Uri.parse(data.getStringExtra(FileExploreActivity.EXTRA_RESULT_KEY));
                mHandler.post(importCSV);
            } else {
                Toast.makeText(this, R.string.act_file_explorer_no_file_error, Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == 2) {
            brapiDialogShown = mAdapter.infoDialogShown;
            if (!brapiDialogShown) {
                brapiDialogShown = displayBrapiInfo(TraitEditorActivity.this, null, true);
            }
        }

        if (requestCode == REQUEST_CLOUD_FILE_CODE && resultCode == RESULT_OK && data.getData() != null) {
            Uri content_describer = data.getData();
            String fileName = getFileName(content_describer);

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

                    mChosenFile = traitExportFile.getUri();
                    mHandler.post(importCSV);
                }
            }
        }
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index > 0) {
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

                        shareFile(exportDoc);
                    }
                }
            }

        } catch (Exception e) {

            e.printStackTrace();

        }
    }

    /**
     * Scan file to update file list and share exported file
     */
    private void shareFile(DocumentFile docFile) {
        if (docFile != null && docFile.exists()) {
            if (!ep.getBoolean(GeneralKeys.DISABLE_SHARE, false)) {
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_STREAM, docFile.getUri());
                startActivity(Intent.createChooser(intent, "Sending File..."));
            }
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
        loadData(visCast, database.getAllTraitObjects());
    }

    @NonNull
    @Override
    public DataHelper getDatabase() {
        return database;
    }

    private static class ArrayIndexComparator implements Comparator<Integer> {
        private final String[] array;

        ArrayIndexComparator(String[] array) {
            this.array = array;
        }

        Integer[] createIndexArray() {
            Arrays.sort(array);
            Integer[] indexes = new Integer[array.length];
            for (int i = 0; i < array.length; i++) {
                indexes[i] = i;
            }
            return indexes;
        }

        @Override
        public int compare(Integer index1, Integer index2) {
            return array[index1].compareTo(array[index2]);
        }
    }

    private class ImportCSVTask extends AsyncTask<Integer, Integer, Integer> {
        ProgressDialog dialog;

        boolean fail;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            dialog = new ProgressDialog(thisActivity);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html
                    .fromHtml(thisActivity.getString(R.string.import_dialog_importing)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            try {
                String[] data;
                String[] columns;

                InputStream inputStream = BaseDocumentTreeUtil.Companion.getUriInputStream(thisActivity, mChosenFile);
                InputStreamReader fr = new InputStreamReader(inputStream);

                CSVReader cr = new CSVReader(fr);

                columns = cr.readNext();

                data = columns;

                if (database.isTableExists(DataHelper.TRAITS)) {
                    database.deleteTraitsTable();
                }

                //get variable with largest real position
                Optional<TraitObject> maxPosition = database.getAllTraitObjects().stream()
                        .max(Comparator.comparingInt(TraitObject::getRealPosition));

                //by default start from zero
                int positionOffset = 0;

                //if there are other traits, set offset to the max
                if (maxPosition.isPresent()) {

                    try {

                        positionOffset = maxPosition.get().getRealPosition();

                    } catch (NoSuchElementException e) {

                        e.printStackTrace();

                    }
                }

                while (data != null) {
                    data = cr.readNext();

                    //if trait format or name is null then don't import
                    if (data != null && data.length > 1
                            && data[0] != null && data[1] != null) {
                        TraitObject t = new TraitObject();
                        t.setTrait(data[0]);
                        t.setFormat(data[1]);
                        t.setDefaultValue(data[2]);
                        t.setMinimum(data[3]);
                        t.setMaximum(data[4]);
                        t.setDetails(data[5]);
                        t.setCategories(data[6]);
                        //t.visible = data[7].toLowerCase();
                        t.setRealPosition(positionOffset + Integer.parseInt(data[8]));
                        t.setVisible(data[7].equalsIgnoreCase("true"));
                        database.insertTraits(t);
                    }
                }

                try {
                    cr.close();
                } catch (Exception ignore) {
                }

                try {
                    fr.close();
                } catch (Exception ignore) {
                }

                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                database.close();
                database.open();

            } catch (Exception e) {
                e.printStackTrace();
                fail = true;
            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            Editor ed = ep.edit();
            ed.putBoolean(GeneralKeys.CREATE_TRAIT_FINISHED, true);
            ed.apply();

            queryAndLoadTraits();

            CollectActivity.reloadData = true;

            if (dialog.isShowing()) {
                dialog.dismiss();
            }

            if (fail) {
                Utils.makeToast(getApplicationContext(), thisActivity.getString(R.string.import_error_general));
            }
        }
    }
}