package com.fieldbook.tracker.activities;

import android.annotation.SuppressLint;
import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuInflater;

import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.traits.LayoutCollections;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.Observation;
import com.fieldbook.tracker.adapters.InfoBarAdapter;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.traits.PhotoTraitLayout;
import com.fieldbook.tracker.traits.BaseTraitLayout;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.utilities.DialogUtils;
import com.fieldbook.tracker.utilities.Utils;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.threeten.bp.OffsetDateTime;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.fieldbook.tracker.activities.ConfigActivity.dt;

/**
 * All main screen logic resides here
 */

@SuppressLint("ClickableViewAccessibility")
public class CollectActivity extends AppCompatActivity {

    public static boolean searchReload;
    public static String searchRange;
    public static String searchPlot;
    public static boolean reloadData;
    public static boolean partialReload;
    public static Activity thisActivity;
    public static String TAG = "Field Book";
    private static String displayColor = "#d50000";
    ImageButton deleteValue;
    ImageButton missingValue;
    /**
     * Trait layouts
     */
    LayoutCollections traitLayouts;
    private SharedPreferences ep;
    private String inputPlotId = "";
    private AlertDialog goToId;
    private Object lock;
    /**
     * Main screen elements
     */
    private Menu systemMenu;
    private InfoBarAdapter infoBarAdapter;
    private TraitBox traitBox;
    private RangeBox rangeBox;
    /**
     * Trait-related elements
     */
    private EditText etCurVal;
    public final Handler myGuiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            synchronized (lock) {
                switch (msg.what) {
                    case 1:
                        ImageView btn = findViewById(msg.arg1);
                        if (btn.getTag() != null) {  // button is still pressed
                            // schedule next btn pressed check
                            Message msg1 = new Message();
                            msg1.copyFrom(msg);
                            if (msg.arg1 == R.id.rangeLeft) {
                                rangeBox.repeatKeyPress("left");
                            } else {
                                rangeBox.repeatKeyPress("right");
                            }
                            myGuiHandler.removeMessages(1);
                            myGuiHandler.sendMessageDelayed(msg1, msg1.arg2);
                        }
                        break;
                }
            }
        }
    };

    private TextWatcher cvText;
    private InputMethodManager imm;
    private Boolean dataLocked = false;

    public static void disableViews(ViewGroup layout) {
        layout.setEnabled(false);
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child instanceof ViewGroup) {
                disableViews((ViewGroup) child);
            } else {
                child.setEnabled(false);
            }
        }
    }

    public static void enableViews(ViewGroup layout) {
        layout.setEnabled(false);
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child instanceof ViewGroup) {
                enableViews((ViewGroup) child);
            } else {
                child.setEnabled(true);
            }
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);
        if (ConfigActivity.dt == null) {    // when resume
            ConfigActivity.dt = new DataHelper(this);
        }

        ConfigActivity.dt.open();

        loadScreen();
    }

    private void initCurrentVals() {
        // Current value display
        etCurVal = findViewById(R.id.etCurVal);

        etCurVal.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView exampleView, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    rangeBox.rightClick();
                    return true;
                }

                return false;
            }
        });

        // Validates the text entered for text format
        cvText = new TextWatcher() {
            public void afterTextChanged(Editable en) {
                final TraitObject trait = traitBox.getCurrentTrait();
                if (en.toString().length() > 0) {
                    if (traitBox.existsNewTraits() & trait != null)
                        updateTrait(trait.getTrait(), trait.getFormat(), en.toString());
                } else {
                    if (traitBox.existsNewTraits() & trait != null)
                        removeTrait(trait.getTrait());
                }
                //tNum.setSelection(tNum.getText().length());
            }

            public void beforeTextChanged(CharSequence arg0, int arg1,
                                          int arg2, int arg3) {
            }

            public void onTextChanged(CharSequence arg0, int arg1, int arg2,
                                      int arg3) {
            }

        };
    }

    private void loadScreen() {
        setContentView(R.layout.activity_collect);

        initToolbars();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(null);
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        // If the app is just starting up, we must always allow refreshing of data onscreen
        reloadData = true;

        lock = new Object();

        thisActivity = this;

        // Keyboard service manager
        setIMM();

        infoBarAdapter = new InfoBarAdapter(this, ep.getInt(GeneralKeys.INFOBAR_NUMBER, 2), (RecyclerView) findViewById(R.id.selectorList));

        traitLayouts = new LayoutCollections(this);
        traitBox = new TraitBox(this);
        rangeBox = new RangeBox(this);
        initCurrentVals();

    }

    private void refreshMain() {
        rangeBox.saveLastPlot();
        rangeBox.refresh();
        traitBox.setNewTraits(rangeBox.getPlotID());

        initWidgets(true);
    }

    private void playSound(String sound) {
        try {
            int resID = getResources().getIdentifier(sound, "raw", getPackageName());
            MediaPlayer chimePlayer = MediaPlayer.create(CollectActivity.this, resID);
            chimePlayer.start();

            chimePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                }
            });
        } catch (Exception ignore) {
        }
    }

    private boolean validateData() {
        final String strValue = etCurVal.getText().toString();
        final TraitObject currentTrait = traitBox.getCurrentTrait();
        final String trait = currentTrait.getTrait();

        if (traitBox.existsNewTraits()
                && traitBox.getCurrentTrait() != null
                && etCurVal.getText().toString().length() > 0
                && !traitBox.getCurrentTrait().isValidValue(etCurVal.getText().toString())) {

            if (strValue.length() > 0 && currentTrait.isOver(strValue)) {
                Utils.makeToast(getApplicationContext(),getString(R.string.trait_error_maximum_value)
                        + ": " + currentTrait.getMaximum());
            } else if (strValue.length() > 0 && currentTrait.isUnder(strValue)) {
                Utils.makeToast(getApplicationContext(),getString(R.string.trait_error_minimum_value)
                        + ": " + currentTrait.getMinimum());
            }

            removeTrait(trait);
            etCurVal.getText().clear();

            playSound("error");

            return false;
        }

        return true;
    }

    private void setNaText() {
        etCurVal.setText("NA");

        traitLayouts.setNaTraitsText(traitBox.getCurrentFormat());
    }

    private void setNaTextBrapiEmptyField() {
        etCurVal.setHint("NA");

        traitLayouts.setNaTraitsText(traitBox.getCurrentFormat());
    }

    private void initToolbars() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Toolbar toolbarBottom = findViewById(R.id.toolbarBottom);

        missingValue = toolbarBottom.findViewById(R.id.missingValue);
        missingValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TraitObject currentTrait = traitBox.getCurrentTrait();
                updateTrait(currentTrait.getTrait(), currentTrait.getFormat(), "NA");
                setNaText();
            }
        });

        deleteValue = toolbarBottom.findViewById(R.id.deleteValue);
        deleteValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // if a brapi observation that has been synced, don't allow deleting
                TraitObject currentTrait = traitBox.getCurrentTrait();
                if (dt.isBrapiSynced(rangeBox.getPlotID(), currentTrait.getTrait())) {
                    if (currentTrait.getFormat().equals("photo")) {
                        // I want to use abstract method
                        Map newTraits = traitBox.getNewTraits();
                        PhotoTraitLayout traitPhoto = traitLayouts.getPhotoTrait();
                        traitPhoto.brapiDelete(newTraits);
                    } else {
                        brapiDelete(currentTrait.getTrait(), false);
                    }
                } else {
                    traitLayouts.deleteTraitListener(currentTrait.getFormat());
                }
            }
        });

    }

    // This update should only be called after repeating keypress ends
    private void repeatUpdate() {
        if (rangeBox.getRangeID() == null)
            return;

        traitBox.setNewTraits(rangeBox.getPlotID());

        initWidgets(true);
    }

    // This is central to the application
    // Calling this function resets all the controls for traits, and picks one
    // to show based on the current trait data
    private void initWidgets(final boolean rangeSuppress) {
        // Reset dropdowns

        if (!dt.isTableEmpty(DataHelper.RANGE)) {
            final String plotID = rangeBox.getPlotID();
            infoBarAdapter.configureDropdownArray(plotID);
        }

        traitBox.initTraitDetails();

        // trait is unique, format is not
        String[] traits = dt.getVisibleTrait();
        if (traits != null) {
            ArrayAdapter<String> directionArrayAdapter = new ArrayAdapter<>(
                    this, R.layout.custom_spinnerlayout, traits);
            directionArrayAdapter
                    .setDropDownViewResource(R.layout.custom_spinnerlayout);
            traitBox.initTraitType(directionArrayAdapter, rangeSuppress);

        }
    }

    // Moves to specific plot/range/plot_id
    private void moveToSearch(String type, int[] rangeID, String range, String plot, String data) {

        if (rangeID == null) {
            return;
        }

        boolean haveData = false;

        // search moveto
        if (type.equals("search")) {
            for (int j = 1; j <= rangeID.length; j++) {
                rangeBox.setRangeByIndex(j - 1);
                RangeObject cRange = rangeBox.getCRange();

                if (cRange.range.equals(range) & cRange.plot.equals(plot)) {
                    moveToResultCore(j);
                    haveData = true;
                }
            }
        }

        //move to plot
        if (type.equals("plot")) {
            for (int j = 1; j <= rangeID.length; j++) {
                rangeBox.setRangeByIndex(j - 1);
                RangeObject cRange = rangeBox.getCRange();

                if (cRange.plot.equals(data)) {
                    moveToResultCore(j);
                    haveData = true;
                }
            }
        }

        //move to range
        if (type.equals("range")) {
            for (int j = 1; j <= rangeID.length; j++) {
                rangeBox.setRangeByIndex(j - 1);
                RangeObject cRange = rangeBox.getCRange();

                if (cRange.range.equals(data)) {
                    moveToResultCore(j);
                    haveData = true;
                }
            }
        }

        //move to plot id
        if (type.equals("id")) {
            for (int j = 1; j <= rangeID.length; j++) {
                rangeBox.setRangeByIndex(j - 1);
                RangeObject cRange = rangeBox.getCRange();

                if (cRange.plot_id.equals(data)) {
                    moveToResultCore(j);
                    return;
                }
            }
        }

        if (!haveData) {
            Utils.makeToast(getApplicationContext(), getString(R.string.main_toolbar_moveto_no_match));
        }
    }

    private void moveToResultCore(int j) {
        rangeBox.setPaging(j);

        // Reload traits based on selected plot
        rangeBox.display();
        traitBox.setNewTraits(rangeBox.getPlotID());

        initWidgets(false);
    }

    @Override
    public void onPause() {
        // Backup database
        try {
            dt.exportDatabase("backup");
            File exportedDb = new File(Constants.BACKUPPATH + "/" + "backup.db");
            File exportedSp = new File(Constants.BACKUPPATH + "/" + "backup.db_sharedpref.xml");
            Utils.scanFile(CollectActivity.this, exportedDb);
            Utils.scanFile(CollectActivity.this, exportedSp);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        super.onPause();
    }

    @Override
    public void onDestroy() {
        //save last plot id
        if (ep.getBoolean("ImportFieldFinished", false)) {
            rangeBox.saveLastPlot();
        }

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Update menu item visibility
        if (systemMenu != null) {
            systemMenu.findItem(R.id.help).setVisible(ep.getBoolean("Tips", false));
            systemMenu.findItem(R.id.jumpToPlot).setVisible(ep.getBoolean(GeneralKeys.UNIQUE_TEXT, false));
            systemMenu.findItem(R.id.nextEmptyPlot).setVisible(ep.getBoolean(GeneralKeys.NEXT_ENTRY_NO_DATA, false));
            systemMenu.findItem(R.id.barcodeScan).setVisible(ep.getBoolean(GeneralKeys.UNIQUE_CAMERA, false));
            systemMenu.findItem(R.id.datagrid).setVisible(ep.getBoolean(GeneralKeys.DATAGRID_SETTING, false));
        }

        // If reload data is true, it means there was an import operation, and
        // the screen should refresh
        if (ConfigActivity.dt == null) {
            ConfigActivity.dt = new DataHelper(this);
        }

        ConfigActivity.dt.open();

        if (reloadData) {
            reloadData = false;
            partialReload = false;

            // displayRange moved to RangeBox#display
            // and display in RangeBox#reload is commented out
            rangeBox.reload();
            traitBox.setPrefixTraits();

            initWidgets(false);
            traitBox.setSelection(0);

            // try to go to last saved plot
            if (ep.getString("lastplot", null) != null) {
                rangeBox.setAllRangeID();
                int[] rangeID = rangeBox.getRangeID();
                moveToSearch("id", rangeID, null, null, ep.getString("lastplot", null));
            }

        } else if (partialReload) {
            partialReload = false;
            rangeBox.display();
            traitBox.setPrefixTraits();
            initWidgets(false);

        } else if (searchReload) {
            searchReload = false;
            rangeBox.resetPaging();
            int[] rangeID = rangeBox.getRangeID();

            if (rangeID != null) {
                moveToSearch("search", rangeID, searchRange, searchPlot, null);
            }
        }
    }

    /**
     * Helper function update user data in the memory based hashmap as well as
     * the database
     */
    public void updateTrait(String parent, String trait, String value) {

        if (rangeBox.isEmpty()) {
            return;
        }

        Log.w(parent, value);
        traitBox.update(parent, value);

        Observation observation = dt.getObservation(rangeBox.getPlotID(), parent);
        String observationDbId = observation.getDbId();
        OffsetDateTime lastSyncedTime = observation.getLastSyncedTime();

        // Always remove existing trait before inserting again
        // Based on plot_id, prevent duplicates
        dt.deleteTrait(rangeBox.getPlotID(), parent);

        String exp_id = Integer.toString(ep.getInt("SelectedFieldExpId", 0));
        dt.insertUserTraits(rangeBox.getPlotID(), parent, trait, value,
                ep.getString("FirstName", "") + " " + ep.getString("LastName", ""),
                ep.getString("Location", ""), "", exp_id, observationDbId,
                lastSyncedTime);
    }

    private void brapiDelete(String parent, Boolean hint) {
        Toast.makeText(getApplicationContext(), getString(R.string.brapi_delete_message), Toast.LENGTH_LONG).show();
        TraitObject trait = traitBox.getCurrentTrait();
        updateTrait(parent, trait.getFormat(), getString(R.string.brapi_na));
        if (hint) {
            setNaTextBrapiEmptyField();
        } else {
            setNaText();
        }
    }

    // Delete trait, including from database
    public void removeTrait(String parent) {
        if (rangeBox.isEmpty()) {
            return;
        }

        TraitObject trait = traitBox.getCurrentTrait();
        if (dt.isBrapiSynced(rangeBox.getPlotID(), trait.getTrait())) {
            brapiDelete(parent, true);
        } else {
            // Always remove existing trait before inserting again
            // Based on plot_id, prevent duplicate
            traitBox.remove(parent, rangeBox.getPlotID());
        }
    }

    // for format without specific control
    public void removeTrait() {
        traitBox.remove(traitBox.getCurrentTrait(), rangeBox.getPlotID());
        etCurVal.setText("");
    }

    private void customizeToolbarIcons() {
        Set<String> entries = ep.getStringSet(GeneralKeys.TOOLBAR_CUSTOMIZE, new HashSet<String>());

        if (systemMenu != null) {
            systemMenu.findItem(R.id.search).setVisible(entries.contains("search"));
            systemMenu.findItem(R.id.resources).setVisible(entries.contains("resources"));
            systemMenu.findItem(R.id.summary).setVisible(entries.contains("summary"));
            systemMenu.findItem(R.id.lockData).setVisible(entries.contains("lockData"));
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(CollectActivity.this).inflate(R.menu.menu_main, menu);

        systemMenu = menu;

        systemMenu.findItem(R.id.help).setVisible(ep.getBoolean("Tips", false));
        systemMenu.findItem(R.id.jumpToPlot).setVisible(ep.getBoolean(GeneralKeys.UNIQUE_TEXT, false));
        systemMenu.findItem(R.id.nextEmptyPlot).setVisible(ep.getBoolean(GeneralKeys.NEXT_ENTRY_NO_DATA, false));
        systemMenu.findItem(R.id.barcodeScan).setVisible(ep.getBoolean(GeneralKeys.UNIQUE_CAMERA, false));
        systemMenu.findItem(R.id.datagrid).setVisible(ep.getBoolean(GeneralKeys.DATAGRID_SETTING, false));

        customizeToolbarIcons();

        lockData(dataLocked);

        return true;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private TapTarget collectDataTapTargetView(int id, String title, String desc, int color, int targetRadius) {
        return TapTarget.forView(findViewById(id), title, desc)
                // All options below are optional
                .outerCircleColor(color)      // Specify a color for the outer circle
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
                .targetRadius(targetRadius);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Intent intent = new Intent(Intent.ACTION_VIEW);

        switch (item.getItemId()) {
            case R.id.help:
                TapTargetSequence sequence = new TapTargetSequence(this)
                        .targets(collectDataTapTargetView(R.id.selectorList, getString(R.string.tutorial_main_infobars_title), getString(R.string.tutorial_main_infobars_description), R.color.main_primaryDark,200),
                                collectDataTapTargetView(R.id.traitLeft, getString(R.string.tutorial_main_traits_title), getString(R.string.tutorial_main_traits_description), R.color.main_primaryDark,60),
                                collectDataTapTargetView(R.id.traitType, getString(R.string.tutorial_main_traitlist_title), getString(R.string.tutorial_main_traitlist_description), R.color.main_primaryDark,80),
                                collectDataTapTargetView(R.id.rangeLeft, getString(R.string.tutorial_main_entries_title), getString(R.string.tutorial_main_entries_description), R.color.main_primaryDark,60),
                                collectDataTapTargetView(R.id.valuesPlotRangeHolder, getString(R.string.tutorial_main_navinfo_title), getString(R.string.tutorial_main_navinfo_description), R.color.main_primaryDark,60),
                                collectDataTapTargetView(R.id.traitHolder, getString(R.string.tutorial_main_datacollect_title), getString(R.string.tutorial_main_datacollect_description), R.color.main_primaryDark,200),
                                collectDataTapTargetView(R.id.missingValue, getString(R.string.tutorial_main_na_title), getString(R.string.tutorial_main_na_description), R.color.main_primary,60),
                                collectDataTapTargetView(R.id.deleteValue, getString(R.string.tutorial_main_delete_title), getString(R.string.tutorial_main_delete_description), R.color.main_primary,60)
                        );
                if (systemMenu.findItem(R.id.search).isVisible()) {
                    sequence.target(collectDataTapTargetView(R.id.search, getString(R.string.tutorial_main_search_title), getString(R.string.tutorial_main_search_description), R.color.main_primaryDark,60));
                }
                if (systemMenu.findItem(R.id.resources).isVisible()) {
                    sequence.target(collectDataTapTargetView(R.id.resources, getString(R.string.tutorial_main_resources_title), getString(R.string.tutorial_main_resources_description), R.color.main_primaryDark,60));
                }
                if (systemMenu.findItem(R.id.summary).isVisible()) {
                    sequence.target(collectDataTapTargetView(R.id.summary, getString(R.string.tutorial_main_summary_title), getString(R.string.tutorial_main_summary_description), R.color.main_primaryDark,60));
                }
                if (systemMenu.findItem(R.id.lockData).isVisible()) {
                    sequence.target(collectDataTapTargetView(R.id.lockData, getString(R.string.tutorial_main_lockdata_title), getString(R.string.tutorial_main_lockdata_description), R.color.main_primaryDark,60));
                }

                sequence.start();
                break;
            case R.id.search:
                intent.setClassName(CollectActivity.this,
                        SearchActivity.class.getName());
                startActivity(intent);
                break;

            case R.id.resources:
                intent.setClassName(CollectActivity.this,
                        FileExploreActivity.class.getName());
                intent.putExtra("path", Constants.RESOURCEPATH);
                intent.putExtra("exclude", new String[]{"fieldbook"});
                intent.putExtra("title", getString(R.string.main_toolbar_resources));
                startActivityForResult(intent, 1);
                break;
            case R.id.nextEmptyPlot:
                nextEmptyPlot();
                break;
            case R.id.jumpToPlot:
                moveToPlotID();
                break;
            case R.id.barcodeScan:
                new IntentIntegrator(this)
                        .setPrompt(getString(R.string.main_barcode_text))
                        .setBeepEnabled(true)
                        .initiateScan();
                break;
            case R.id.summary:
                showSummary();
                break;
            case R.id.datagrid:
                intent.setClassName(CollectActivity.this,
                        DatagridActivity.class.getName());
                startActivityForResult(intent, 2);
                break;
            case R.id.lockData:
                dataLocked = !dataLocked;
                lockData(dataLocked);
                break;
            case android.R.id.home:
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    void lockData(boolean lock) {
        if (lock) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_lock);
            missingValue.setEnabled(false);
            deleteValue.setEnabled(false);
            etCurVal.setEnabled(false);
            traitLayouts.disableViews();
        } else {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_unlock);
            missingValue.setEnabled(true);
            deleteValue.setEnabled(true);
            traitLayouts.enableViews();
        }
    }

    private void moveToPlotID() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_gotobarcode, null);
        final EditText barcodeId = layout.findViewById(R.id.barcodeid);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.main_toolbar_moveto)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_go), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                inputPlotId = barcodeId.getText().toString();
                rangeBox.setAllRangeID();
                int[] rangeID = rangeBox.getRangeID();
                moveToSearch("id", rangeID, null, null, inputPlotId);
                goToId.dismiss();
            }
        });

        builder.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.setNeutralButton(getString(R.string.main_toolbar_moveto_scan), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                IntentIntegrator integrator = new IntentIntegrator(thisActivity);
                integrator.initiateScan();
            }
        });

        goToId = builder.create();
        goToId.show();
        DialogUtils.styleDialogs(goToId);

        android.view.WindowManager.LayoutParams langParams = goToId.getWindow().getAttributes();
        langParams.width = LayoutParams.MATCH_PARENT;
        goToId.getWindow().setAttributes(langParams);
    }

    public void nextEmptyPlot() {
        try {
            final int id = rangeBox.nextEmptyPlot();
            rangeBox.setRange(id);
            rangeBox.display();
            rangeBox.setLastRange();
            traitBox.setNewTraits(rangeBox.getPlotID());
            initWidgets(true);
        } catch (Exception e) {

        }
    }

    private void showSummary() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_summary, null);
        TextView summaryText = layout.findViewById(R.id.field_name);
        summaryText.setText(traitBox.createSummaryText(rangeBox.getPlotID()));

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.preferences_appearance_toolbar_customize_summary)
                .setCancelable(true)
                .setView(layout);

        builder.setNegativeButton(getString(R.string.dialog_close), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                dialog.dismiss();
            }
        });

        final AlertDialog summaryDialog = builder.create();
        summaryDialog.show();
        DialogUtils.styleDialogs(summaryDialog);

        android.view.WindowManager.LayoutParams params2 = summaryDialog.getWindow().getAttributes();
        params2.width = LayoutParams.MATCH_PARENT;
        summaryDialog.getWindow().setAttributes(params2);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (ep.getBoolean(GeneralKeys.VOLUME_NAVIGATION, false)) {
                    if (action == KeyEvent.ACTION_UP) {
                        rangeBox.moveEntryRight();
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (ep.getBoolean(GeneralKeys.VOLUME_NAVIGATION, false)) {
                    if (action == KeyEvent.ACTION_UP) {
                        rangeBox.moveEntryLeft();
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_ENTER:
                String return_action = ep.getString(GeneralKeys.RETURN_CHARACTER, "0");

                if (return_action.equals("0")) {
                    if (action == KeyEvent.ACTION_UP) {
                        rangeBox.moveEntryRight();
                        return false;
                    }
                }

                if (return_action.equals("1")) {
                    if (action == KeyEvent.ACTION_UP) {
                        traitBox.moveTrait("right");
                        return true;
                    }
                }

                if (return_action.equals("2")) {
                    return true;
                }

                return false;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case 1:
                if (resultCode == RESULT_OK) {
                    String mChosenFileString = data.getStringExtra("result");
                    File mChosenFile = new File(mChosenFileString);

                    String suffix = mChosenFileString.substring(mChosenFileString.lastIndexOf('.') + 1).toLowerCase();

                    String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix);
                    Intent open = new Intent(Intent.ACTION_VIEW);
                    open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    open.setDataAndType(FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".fileprovider", mChosenFile), mime);

                    startActivity(open);
                }
                break;
            case 2:
                if (resultCode == RESULT_OK) {
                    inputPlotId = data.getStringExtra("result");
                    rangeBox.setAllRangeID();
                    int[] rangeID = rangeBox.getRangeID();
                    moveToSearch("id", rangeID, null, null, inputPlotId);
                }
                break;
            case 252:
                if (resultCode == RESULT_OK) {
                    PhotoTraitLayout traitPhoto = traitLayouts.getPhotoTrait();
                    traitPhoto.makeImage(traitBox.getCurrentTrait(),
                            traitBox.getNewTraits());
                }
                break;
        }

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            inputPlotId = result.getContents();
            rangeBox.setAllRangeID();
            int[] rangeID = rangeBox.getRangeID();
            moveToSearch("id", rangeID, null, null, inputPlotId);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public InputMethodManager getIMM() {
        return imm;
    }

    public void setIMM() {
        imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

    }

    public TraitBox getTraitBox() {
        return traitBox;
    }

    public boolean existsTrait(final int ID) {
        final TraitObject trait = traitBox.getCurrentTrait();
        return dt.getTraitExists(ID, trait.getTrait(), trait.getFormat());
    }

    public Map getNewTraits() {
        return traitBox.getNewTraits();
    }

    public void setNewTraits(Map newTraits) {
        traitBox.setNewTraits(newTraits);
    }

    public TraitObject getCurrentTrait() {
        return traitBox.getCurrentTrait();
    }

    public RangeBox getRangeBox() {
        return rangeBox;
    }

    public RangeObject getCRange() {
        return rangeBox.getCRange();
    }

    public EditText getEtCurVal() {
        return etCurVal;
    }

    public TextWatcher getCvText() {
        return cvText;
    }

    public String getDisplayColor() {
        return displayColor;
    }

    public ImageButton getDeleteValue() {
        return deleteValue;
    }

    public ImageView getTraitLeft() {
        return traitBox.getTraitLeft();
    }

    public ImageView getTraitRight() {
        return traitBox.getTraitRight();
    }

    public ImageView getRangeLeft() {
        return rangeBox.getRangeLeft();
    }

    public ImageView getRangeRight() {
        return rangeBox.getRangeRight();
    }

    public Boolean isDataLocked() {
        return dataLocked;
    }

    public SharedPreferences getPreference() {
        return ep;
    }

    public boolean is_cycling_traits_advances() {
        return ep.getBoolean(GeneralKeys.CYCLING_TRAITS_ADVANCES, false);
    }

    ///// class TraitBox /////
    // traitLeft, traitType, and traitRight
    private class TraitBox {
        private CollectActivity parent;
        private String[] prefixTraits;
        private TraitObject currentTrait;

        private Spinner traitType;
        private TextView traitDetails;
        private ImageView traitLeft;
        private ImageView traitRight;

        private Map newTraits;  // { trait name: value }

        TraitBox(CollectActivity parent_) {
            parent = parent_;
            prefixTraits = null;
            newTraits = new HashMap();

            traitType = findViewById(R.id.traitType);
            traitLeft = findViewById(R.id.traitLeft);
            traitRight = findViewById(R.id.traitRight);
            traitDetails = findViewById(R.id.traitDetails);

            traitLeft.setOnTouchListener(createTraitOnTouchListener(traitLeft, R.drawable.main_trait_left_arrow_unpressed,
                    R.drawable.main_trait_left_arrow_pressed));

            // Go to previous trait
            traitLeft.setOnClickListener(new OnClickListener() {

                public void onClick(View arg0) {
                    moveTrait("left");
                }
            });

            traitRight.setOnTouchListener(createTraitOnTouchListener(traitRight, R.drawable.main_trait_right_unpressed,
                    R.drawable.main_trait_right_pressed));

            // Go to next trait
            traitRight.setOnClickListener(new OnClickListener() {

                public void onClick(View arg0) {
                    moveTrait("right");
                }
            });
        }

        void initTraitDetails() {
            if (prefixTraits != null) {
                final TextView traitDetails = findViewById(R.id.traitDetails);

                traitDetails.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                traitDetails.setMaxLines(10);
                                break;
                            case MotionEvent.ACTION_UP:
                                traitDetails.setMaxLines(1);
                                break;
                        }
                        return true;
                    }
                });
            }
        }

        void initTraitType(ArrayAdapter<String> adaptor,
                           final boolean rangeSuppress) {
            final int traitPosition = getSelectedItemPosition();
            traitType.setAdapter(adaptor);

            traitType.setOnItemSelectedListener(new OnItemSelectedListener() {

                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int arg2, long arg3) {

                    // This updates the in memory hashmap from database
                    currentTrait = dt.getDetail(traitType.getSelectedItem()
                            .toString());

                    etCurVal = parent.getEtCurVal();
                    parent.setIMM();
                    InputMethodManager imm = parent.getIMM();
                    if (!currentTrait.getFormat().equals("text")) {
                        try {
                            imm.hideSoftInputFromWindow(etCurVal.getWindowToken(), 0);
                        } catch (Exception ignore) {
                        }
                    }

                    traitDetails.setText(currentTrait.getDetails());

                    if (!rangeSuppress | !currentTrait.getFormat().equals("numeric")) {
                        if (etCurVal.getVisibility() == TextView.VISIBLE) {
                            etCurVal.setVisibility(EditText.GONE);
                            etCurVal.setEnabled(false);
                        }
                    }

                    //Clear all layouts
                    traitLayouts.hideLayouts();

                    //Get current layout object and make it visible
                    BaseTraitLayout currentTraitLayout =
                            traitLayouts.getTraitLayout(currentTrait.getFormat());
                    currentTraitLayout.setVisibility(View.VISIBLE);

                    //Call specific load layout code for the current trait layout
                    if (currentTraitLayout != null) {
                        currentTraitLayout.loadLayout();
                    } else {
                        etCurVal.removeTextChangedListener(parent.getCvText());
                        etCurVal.setVisibility(EditText.VISIBLE);
                        etCurVal.setEnabled(true);
                    }
                }

                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });

            traitBox.setSelection(traitPosition);
        }

        public Map getNewTraits() {
            return newTraits;
        }

        void setNewTraits(final String plotID) {
            newTraits = (HashMap) dt.getUserDetail(plotID).clone();
        }

        void setNewTraits(Map newTraits) {
            this.newTraits = newTraits;
        }

        ImageView getTraitLeft() {
            return traitLeft;
        }

        ImageView getTraitRight() {
            return traitRight;
        }

        boolean existsNewTraits() {
            return newTraits != null;
        }

        void setPrefixTraits() {
            prefixTraits = dt.getRangeColumnNames();
        }

        void setSelection(int pos) {
            traitType.setSelection(pos);
        }

        int getSelectedItemPosition() {
            try {
                return traitType.getSelectedItemPosition();
            } catch (Exception f) {
                return 0;
            }
        }

        public final TraitObject getCurrentTrait() {
            return currentTrait;
        }

        final String getCurrentFormat() {
            return currentTrait.getFormat();
        }

        boolean existsTrait() {
            return newTraits.containsKey(currentTrait.getTrait());
        }

        final String createSummaryText(final String plotID) {
            String[] traitList = dt.getAllTraits();
            StringBuilder data = new StringBuilder();

            //TODO this test crashes app
            if (rangeBox.getCRange() != null) {
                for (String s : traitBox.prefixTraits) {
                    data.append(s).append(": ");
                    data.append(dt.getDropDownRange(s, plotID)[0]).append("\n");
                }
            }

            for (String s : traitList) {
                if (newTraits.containsKey(s)) {
                    data.append(s).append(": ");
                    data.append(newTraits.get(s).toString()).append("\n");
                }
            }
            return data.toString();
        }

        public void remove(String traitName, String plotID) {
            if (newTraits.containsKey(traitName))
                newTraits.remove(traitName);
            dt.deleteTrait(plotID, traitName);
        }

        public void remove(TraitObject trait, String plotID) {
            remove(trait.getTrait(), plotID);
        }

        private OnTouchListener createTraitOnTouchListener(final ImageView arrow,
                                                           final int imageIdUp, final int imageIdDown) {
            return new OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {

                        case MotionEvent.ACTION_DOWN:
                            arrow.setImageResource(imageIdDown);
                            break;
                        case MotionEvent.ACTION_MOVE:
                            break;
                        case MotionEvent.ACTION_UP:
                            arrow.setImageResource(imageIdUp);
                        case MotionEvent.ACTION_CANCEL:
                            break;
                    }

                    // return true to prevent calling btn onClick handler
                    return false;
                }
            };
        }

        void moveTrait(String direction) {
            int pos = 0;

            if (!validateData()) {
                return;
            }

            // Force the keyboard to be hidden to handle bug
            try {
                etCurVal = parent.getEtCurVal();
                InputMethodManager imm = parent.getIMM();
                imm.hideSoftInputFromWindow(etCurVal.getWindowToken(), 0);
            } catch (Exception ignore) {
            }

            RangeBox rangeBox = parent.getRangeBox();
            if (direction.equals("left")) {
                pos = traitType.getSelectedItemPosition() - 1;

                if (pos < 0) {
                    pos = traitType.getCount() - 1;

                    if (parent.is_cycling_traits_advances())
                        rangeBox.clickLeft();
                }
            } else if (direction.equals("right")) {
                pos = traitType.getSelectedItemPosition() + 1;

                if (pos > traitType.getCount() - 1) {
                    pos = 0;

                    if (parent.is_cycling_traits_advances())
                        rangeBox.clickRight();
                }
            }

            traitType.setSelection(pos);
        }

        public void update(String parent, String value) {
            if (newTraits.containsKey(parent)) {
                newTraits.remove(parent);
            }

            newTraits.put(parent, value);
        }
    }

    ///// class RangeBox /////

    class RangeBox {
        private CollectActivity parent;
        private int[] rangeID;
        private int paging;

        private RangeObject cRange;
        private String lastRange;

        private TextView rangeName;
        private TextView plotName;

        private EditText range;
        private EditText plot;
        private TextView tvRange;
        private TextView tvPlot;

        private ImageView rangeLeft;
        private ImageView rangeRight;

        private Handler repeatHandler;

        private int delay = 100;
        private int count = 1;

        RangeBox(CollectActivity parent_) {
            parent = parent_;
            rangeID = null;
            cRange = new RangeObject();
            cRange.plot = "";
            cRange.plot_id = "";
            cRange.range = "";
            lastRange = "";

            initAndPlot();
        }

        // getter
        RangeObject getCRange() {
            return cRange;
        }

        int[] getRangeID() {
            return rangeID;
        }

        int getRangeIDByIndex(int j) {
            return rangeID[j];
        }

        ImageView getRangeLeft() {
            return rangeLeft;
        }

        ImageView getRangeRight() {
            return rangeRight;
        }

        final String getPlotID() {
            return cRange.plot_id;
        }

        boolean isEmpty() {
            return cRange == null || cRange.plot_id.length() == 0;
        }

        private void initAndPlot() {
            range = findViewById(R.id.range);
            plot = findViewById(R.id.plot);

            rangeName = findViewById(R.id.rangeName);
            plotName = findViewById(R.id.plotName);

            rangeLeft = findViewById(R.id.rangeLeft);
            rangeRight = findViewById(R.id.rangeRight);

            tvRange = findViewById(R.id.tvRange);
            tvPlot = findViewById(R.id.tvPlot);

            rangeLeft.setOnTouchListener(createOnLeftTouchListener());
            rangeRight.setOnTouchListener(createOnRightTouchListener());

            // Go to previous range
            rangeLeft.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    moveEntryLeft();
                }
            });

            // Go to next range
            rangeRight.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    moveEntryRight();
                }
            });

            range.setOnEditorActionListener(createOnEditorListener(range,"range"));
            plot.setOnEditorActionListener(createOnEditorListener(plot,"plot"));

            range.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    range.setCursorVisible(true);
                    return false;
                }
            });

            plot.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    plot.setCursorVisible(true);
                    return false;
                }
            });

            setName(10);

            rangeName.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    Utils.makeToast(getApplicationContext(),ep.getString("ImportFirstName", getString(R.string.search_results_dialog_range)));
                    return false;
                }
            });

            plotName.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    Utils.makeToast(getApplicationContext(),ep.getString("ImportSecondName", getString(R.string.search_results_dialog_range)));
                    return false;
                }
            });
        }

        private String truncate(String s, int maxLen) {
            if (s.length() > maxLen)
                return s.substring(0, maxLen - 1) + ":";
            return s;
        }

        private OnEditorActionListener createOnEditorListener(final EditText edit, final String searchType) {
            return new OnEditorActionListener() {
                public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                    // do not do bit check on event, crashes keyboard
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        try {
                            moveToSearch(searchType, rangeID, null, null, view.getText().toString());
                            InputMethodManager imm = parent.getIMM();
                            imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
                        } catch (Exception ignore) {
                        }
                        return true;
                    }

                    return false;
                }
            };
        }

        private Runnable createRunnable(final String directionStr) {
            return new Runnable() {
                @Override
                public void run() {
                    repeatKeyPress(directionStr);

                    if ((count % 5) == 0) {
                        if (delay > 20) {
                            delay = delay - 10;
                        }
                    }

                    count++;
                    if (repeatHandler != null) {
                        repeatHandler.postDelayed(this, delay);
                    }
                }
            };
        }

        private OnTouchListener createOnLeftTouchListener() {
            Runnable actionLeft = createRunnable("left");
            return createOnTouchListener(rangeLeft, actionLeft,
                    R.drawable.main_entry_left_pressed,
                    R.drawable.main_entry_left_unpressed);
        }

        private OnTouchListener createOnRightTouchListener() {
            Runnable actionRight = createRunnable("right");
            return createOnTouchListener(rangeRight, actionRight,
                    R.drawable.main_entry_right_pressed,
                    R.drawable.main_entry_right_unpressed);
        }

        private OnTouchListener createOnTouchListener(final ImageView control,
                                                      final Runnable action, final int imageID, final int imageID2) {
            return new OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            control.setImageResource(imageID);
                            control.performClick();

                            if (repeatHandler != null) {
                                return true;
                            }
                            repeatHandler = new Handler();
                            repeatHandler.postDelayed(action, 750);

                            delay = 100;
                            count = 1;

                            break;
                        case MotionEvent.ACTION_MOVE:
                            break;
                        case MotionEvent.ACTION_UP:
                            control.setImageResource(imageID2);

                            if (repeatHandler == null) {
                                return true;
                            }
                            repeatHandler.removeCallbacks(action);
                            repeatHandler = null;

                            repeatUpdate();
                            break;
                        case MotionEvent.ACTION_CANCEL:
                            control.setImageResource(imageID2);

                            repeatHandler.removeCallbacks(action);
                            repeatHandler = null;

                            v.setTag(null); // mark btn as not pressed
                            break;
                    }

                    // return true to prevent calling btn onClick handler
                    return true;
                }
            };
        }

        // Simulate range right key press
        private void repeatKeyPress(final String directionStr) {
            boolean left = directionStr.equalsIgnoreCase("left");

            if (!validateData()) {
                return;
            }

            if (rangeID != null && rangeID.length > 0) {
                final int step = left ? -1 : 1;
                paging = movePaging(paging, step, true);

                // Refresh onscreen controls
                cRange = dt.getRange(rangeID[paging - 1]);
                rangeBox.saveLastPlot();

                if (cRange.plot_id.length() == 0)
                    return;

                final SharedPreferences ep = parent.getPreference();
                if (ep.getBoolean(GeneralKeys.PRIMARY_SOUND, false)) {
                    if (!cRange.range.equals(lastRange) && !lastRange.equals("")) {
                        lastRange = cRange.range;

                        try {
                            int resID = getResources().getIdentifier("plonk", "raw", getPackageName());
                            MediaPlayer chimePlayer = MediaPlayer.create(CollectActivity.this, resID);
                            chimePlayer.start();

                            chimePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                public void onCompletion(MediaPlayer mp) {
                                    mp.release();
                                }
                            });
                        } catch (Exception ignore) {
                        }
                    }
                }

                rangeBox.display();
                traitBox.setNewTraits(rangeBox.getPlotID());

                initWidgets(true);
            }
        }

        void reload() {
            final SharedPreferences ep = parent.getPreference();
            switchVisibility(ep.getBoolean(GeneralKeys.QUICK_GOTO, false));

            setName(8);

            paging = 1;

            setAllRangeID();
            if (rangeID != null) {
                cRange = dt.getRange(rangeID[0]);

                //TODO NullPointerException
                lastRange = cRange.range;
                display();

                traitBox.setNewTraits(cRange.plot_id);
            }
        }

        // Refresh onscreen controls
        void refresh() {
            cRange = dt.getRange(rangeID[paging - 1]);

            display();
            final SharedPreferences ep = parent.getPreference();
            if (ep.getBoolean(GeneralKeys.PRIMARY_SOUND, false)) {
                if (!cRange.range.equals(lastRange) && !lastRange.equals("")) {
                    lastRange = cRange.range;
                    playSound("plonk");
                }
            }
        }

        // Updates the data shown in the dropdown
        private void display() {
            if (cRange == null)
                return;

            range.setText(cRange.range);
            plot.setText(cRange.plot);

            range.setCursorVisible(false);
            plot.setCursorVisible(false);

            tvRange.setText(cRange.range);
            tvPlot.setText(cRange.plot);
        }

        void rightClick() {
            rangeRight.performClick();
        }

        private void saveLastPlot() {
            final SharedPreferences ep = parent.getPreference();
            Editor ed = ep.edit();
            ed.putString("lastplot", cRange.plot_id);
            ed.apply();
        }

        void switchVisibility(boolean textview) {
            if (textview) {
                tvRange.setVisibility(TextView.GONE);
                tvPlot.setVisibility(TextView.GONE);
                range.setVisibility(EditText.VISIBLE);
                plot.setVisibility(EditText.VISIBLE);
            } else {
                tvRange.setVisibility(TextView.VISIBLE);
                tvPlot.setVisibility(TextView.VISIBLE);
                range.setVisibility(EditText.GONE);
                plot.setVisibility(EditText.GONE);
            }
        }

        public void setName(int maxLen) {
            final SharedPreferences ep = parent.getPreference();
            String primaryName = ep.getString("ImportFirstName", getString(R.string.search_results_dialog_range)) + ":";
            String secondaryName = ep.getString("ImportSecondName", getString(R.string.search_results_dialog_plot)) + ":";
            rangeName.setText(truncate(primaryName, maxLen));
            plotName.setText(truncate(secondaryName, maxLen));
        }

        void setAllRangeID() {
            rangeID = dt.getAllRangeID();
        }

        public void setRange(final int id) {
            cRange = dt.getRange(id);
        }

        void setRangeByIndex(final int j) {
            cRange = dt.getRange(rangeID[j]);
        }

        void setLastRange() {
            lastRange = cRange.range;
        }

        ///// paging /////

        private void moveEntryLeft() {
            final SharedPreferences ep = parent.getPreference();

            if (!validateData()) {
                return;
            }

            if (ep.getBoolean(GeneralKeys.DISABLE_ENTRY_ARROW_LEFT, false)
                    && !parent.getTraitBox().existsTrait()) {
                playSound("error");
            } else {
                if (rangeID != null && rangeID.length > 0) {
                    //index.setEnabled(true);
                    paging = decrementPaging(paging);
                    parent.refreshMain();
                }
            }
        }

        private void moveEntryRight() {
            final SharedPreferences ep = parent.getPreference();

            if (!validateData()) {
                return;
            }

            if (ep.getBoolean(GeneralKeys.DISABLE_ENTRY_ARROW_RIGHT, false)
                    && !parent.getTraitBox().existsTrait()) {
                playSound("error");
            } else {
                if (rangeID != null && rangeID.length > 0) {
                    //index.setEnabled(true);
                    paging = incrementPaging(paging);
                    parent.refreshMain();
                }
            }
        }

        private int decrementPaging(int pos) {
            return movePaging(pos, -1, false);
        }

        private int incrementPaging(int pos) {
            return movePaging(pos, 1, false);
        }

        private int movePaging(int pos, int step, boolean cyclic) {
            // If ignore existing data is enabled, then skip accordingly
            final SharedPreferences ep = parent.getPreference();

            if (ep.getBoolean(GeneralKeys.HIDE_ENTRIES_WITH_DATA, false)) {
                if (step == 1 && pos == rangeID.length) {
                    return 1;
                }

                final int prevPos = pos;
                TraitObject trait = parent.getTraitBox().getCurrentTrait();
                while (true) {
                    pos = moveSimply(pos, step);
                    // absorb the differece
                    // between single click and repeated clicks
                    if (cyclic) {
                        if (pos == prevPos) {
                            return pos;
                        } else if (pos == 1) {
                            pos = rangeID.length;
                        } else if (pos == rangeID.length) {
                            pos = 1;
                        }
                    } else {
                        if (pos == 1 || pos == prevPos) {
                            return pos;
                        }
                    }

                    if (!parent.existsTrait(rangeID[pos - 1])) {
                        return pos;
                    }
                }
            } else {
                return moveSimply(pos, step);
            }
        }

        private int moveSimply(int pos, int step) {
            pos += step;
            if (pos > rangeID.length) {
                return 1;
            } else if (pos < 1) {
                return rangeID.length;
            } else {
                return pos;
            }
        }

        void resetPaging() {
            paging = 1;
        }

        void setPaging(int j) {
            paging = j;
        }

        final int nextEmptyPlot() throws Exception {
            int pos = paging;

            if (pos == rangeID.length) {
                throw new Exception();
            }

            while (pos <= rangeID.length) {
                pos += 1;

                if (pos > rangeID.length) {
                    throw new Exception();
                }

                if (!parent.existsTrait(rangeID[pos - 1])) {
                    paging = pos;
                    return rangeID[pos - 1];
                }
            }
            throw new Exception();      // not come here
        }

        void clickLeft() {
            rangeLeft.performClick();
        }

        void clickRight() {
            rangeRight.performClick();
        }
    }
}