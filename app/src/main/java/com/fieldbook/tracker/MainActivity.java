package com.fieldbook.tracker;

import android.annotation.SuppressLint;
import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuInflater;

import com.fieldbook.tracker.barcodes.*;
import com.fieldbook.tracker.brapi.Observation;
import com.fieldbook.tracker.layoutConfig.SelectorLayoutConfigurator;
import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.search.*;
import com.fieldbook.tracker.traitLayouts.PhotoTraitLayout;
import com.fieldbook.tracker.traitLayouts.TraitLayout;
import com.fieldbook.tracker.traits.*;
import com.fieldbook.tracker.tutorial.*;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.utilities.Utils;

import org.threeten.bp.OffsetDateTime;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static com.fieldbook.tracker.ConfigActivity.dt;

/**
 * All main screen logic resides here
 */

@SuppressLint("ClickableViewAccessibility")
public class MainActivity extends AppCompatActivity {

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
    private SelectorLayoutConfigurator selectorLayoutConfigurator;
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
                        ImageView btn = (ImageView) findViewById(msg.arg1);
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
    private TextWatcher cvNum;
    private TextWatcher cvText;
    private InputMethodManager imm;
    private Boolean dataLocked = false;

    // initRangeAndPlot moved to RangeBox#initAndPlot
    // and moveToSearch came back

    static void disableViews(ViewGroup layout) {
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

    static void enableViews(ViewGroup layout) {
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

        // Validates the text entered for numeric format
        //todo get rid of this- validate/delete in next/last plot
        cvNum = new TextWatcher() {

            // if the trait has a range, updateTrait does not work
            public void afterTextChanged(final Editable en) {
                final String strValue = etCurVal.getText().toString();
                if (strValue.equals(""))
                    return;

                Timer timer = new Timer();
                final long DELAY = 750; // in ms

                timer.cancel();
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    public void run() {
                        runOnUiThread(createDelayThread(en));
                    }
                }, DELAY);
            }

            public void beforeTextChanged(CharSequence arg0,
                                          int arg1, int arg2, int arg3) {
            }

            public void onTextChanged(CharSequence arg0,
                                      int arg1, int arg2, int arg3) {
            }

        };

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

    private Runnable createDelayThread(final Editable en) {
        return new Runnable() {
            @Override
            public void run() {
                final String strValue = etCurVal.getText().toString();
                final TraitObject currentTrait = traitBox.getCurrentTrait();
                final String trait = currentTrait.getTrait();
                if (currentTrait.isValidValue(strValue)) {
                    if (traitBox.existsNewTraits() & currentTrait != null)
                        updateTrait(trait, currentTrait.getFormat(), strValue);
                } else {
                    if (strValue.length() > 0 && currentTrait.isOver(strValue)) {
                        makeToast(getString(R.string.trait_error_maximum_value)
                                + " " + currentTrait.getMaximum());
                    }
                    en.clear();
                    removeTrait(trait);
                }
            }
        };
    }

    private void loadScreen() {
        setContentView(R.layout.activity_main);

        initToolbars();

        getSupportActionBar().setTitle(null);
        getSupportActionBar().getThemedContext();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // If the app is just starting up, we must always allow refreshing of data onscreen
        reloadData = true;

        lock = new Object();

        thisActivity = this;

        // Keyboard service manager
        setIMM();

        selectorLayoutConfigurator = new SelectorLayoutConfigurator(this, ep.getInt(PreferencesActivity.INFOBAR_NUMBER, 2), (RecyclerView) findViewById(R.id.selectorList));

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
            MediaPlayer chimePlayer = MediaPlayer.create(MainActivity.this, resID);
            chimePlayer.start();

            chimePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                }
            });
        } catch (Exception ignore) {
        }
    }

    //TODO
    private boolean validateData() {
        //get rules

        //get data

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
            selectorLayoutConfigurator.configureDropdownArray(plotID);
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
    private void moveToSearch(String type, int[] rangeID, String range, String plot, String plotID) {

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
                    moveToResult(j);
                    haveData = true;
                }
            }
        }

        //move to plot
        if (type.equals("plot")) {
            for (int j = 1; j <= rangeID.length; j++) {
                rangeBox.setRangeByIndex(j - 1);
                RangeObject cRange = rangeBox.getCRange();

                if (cRange.plot.equals(plot)) {
                    moveToResult(j);
                    haveData = true;
                }
            }
        }

        //move to range
        if (type.equals("range")) {
            for (int j = 1; j <= rangeID.length; j++) {
                rangeBox.setRangeByIndex(j - 1);
                RangeObject cRange = rangeBox.getCRange();

                if (cRange.range.equals(range)) {
                    moveToResult(j);
                    haveData = true;
                }
            }
        }

        //move to plot id
        if (type.equals("id")) {
            for (int j = 1; j <= rangeID.length; j++) {
                rangeBox.setRangeByIndex(j - 1);
                RangeObject cRange = rangeBox.getCRange();

                if (cRange.plot_id.equals(plotID)) {
                    moveToResult(j);
                    return;
                }
            }
        }

        if (!haveData)
            makeToast(getString(R.string.main_toolbar_moveto_no_match));
    }

    private void moveToResult(int j) {
        if (ep.getBoolean(PreferencesActivity.HIDE_ENTRIES_WITH_DATA, false)) {
            if (!existsTrait(rangeBox.getRangeIDByIndex(j - 1))) {
                moveToResultCore(j);
            }
        } else {
            moveToResultCore(j);
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
            Utils.scanFile(MainActivity.this, exportedDb);
            Utils.scanFile(MainActivity.this, exportedSp);
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

        try {
            TutorialMainActivity.thisActivity.finish();
        } catch (Exception ignore) {
        }

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Update menu item visibility
        if (systemMenu != null) {
            systemMenu.findItem(R.id.help).setVisible(ep.getBoolean("Tips", false));
            systemMenu.findItem(R.id.jumpToPlot).setVisible(ep.getBoolean(PreferencesActivity.UNIQUE_TEXT, false));
            systemMenu.findItem(R.id.nextEmptyPlot).setVisible(ep.getBoolean(PreferencesActivity.NEXT_ENTRY_NO_DATA, false));
            systemMenu.findItem(R.id.barcodeScan).setVisible(ep.getBoolean(PreferencesActivity.UNIQUE_CAMERA, false));
            systemMenu.findItem(R.id.datagrid).setVisible(ep.getBoolean(PreferencesActivity.DATAGRID_SETTING, false));
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

        String exp_id = Integer.toString(ep.getInt("ExpID", 0));
        dt.insertUserTraits(rangeBox.getPlotID(), parent, trait, value,
                ep.getString("FirstName", "") + " " + ep.getString("LastName", ""),
                ep.getString("Location", ""), "", exp_id, observationDbId,
                lastSyncedTime); //TODO add notes and exp_id
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        new MenuInflater(MainActivity.this).inflate(R.menu.menu_main, menu);

        systemMenu = menu;

        systemMenu.findItem(R.id.help).setVisible(ep.getBoolean("Tips", false));
        systemMenu.findItem(R.id.jumpToPlot).setVisible(ep.getBoolean(PreferencesActivity.UNIQUE_TEXT, false));
        systemMenu.findItem(R.id.nextEmptyPlot).setVisible(ep.getBoolean(PreferencesActivity.NEXT_ENTRY_NO_DATA, false));
        systemMenu.findItem(R.id.barcodeScan).setVisible(ep.getBoolean(PreferencesActivity.UNIQUE_CAMERA, false));
        systemMenu.findItem(R.id.datagrid).setVisible(ep.getBoolean(PreferencesActivity.DATAGRID_SETTING, false));

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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Intent intent = new Intent(Intent.ACTION_VIEW);

        switch (item.getItemId()) {
            case R.id.search:
                try {
                    TutorialMainActivity.thisActivity.finish();
                } catch (Exception e) {
                    Log.e(TAG, "" + e.getMessage());
                }

                intent.setClassName(MainActivity.this,
                        SearchActivity.class.getName());
                startActivity(intent);
                break;

            case R.id.resources:
                intent.setClassName(MainActivity.this,
                        FileExploreActivity.class.getName());
                intent.putExtra("path", Constants.RESOURCEPATH);
                intent.putExtra("exclude", new String[]{"fieldbook"});
                intent.putExtra("title", getString(R.string.main_toolbar_resources));
                startActivityForResult(intent, 1);
                break;

            case R.id.help:
                Intent helpIntent = new Intent();
                helpIntent.setClassName(MainActivity.this,
                        TutorialMainActivity.class.getName());
                startActivity(helpIntent);
                break;
            case R.id.nextEmptyPlot:
                nextEmptyPlot();
                break;
            case R.id.jumpToPlot:
                moveToPlotID();
                break;
            case R.id.barcodeScan:
                IntentIntegrator integrator = new IntentIntegrator(thisActivity);
                integrator.initiateScan();
                break;
            case R.id.summary:
                showSummary();
                break;
            case R.id.datagrid:
                try {
                    TutorialMainActivity.thisActivity.finish();
                } catch (Exception e) {
                    Log.e(TAG, "" + e.getMessage());
                }

                intent.setClassName(MainActivity.this,
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_gotobarcode, null);

        builder.setTitle(R.string.main_toolbar_moveto)
                .setCancelable(true)
                .setView(layout);

        goToId = builder.create();

        android.view.WindowManager.LayoutParams langParams = goToId.getWindow().getAttributes();
        langParams.width = LayoutParams.MATCH_PARENT;
        goToId.getWindow().setAttributes(langParams);

        final EditText barcodeId = layout.findViewById(R.id.barcodeid);
        Button exportButton = layout.findViewById(R.id.saveBtn);
        Button closeBtn = layout.findViewById(R.id.closeBtn);
        Button camBtn = layout.findViewById(R.id.camBtn);

        camBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                IntentIntegrator integrator = new IntentIntegrator(thisActivity);
                integrator.initiateScan();
            }
        });

        closeBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                goToId.dismiss();
            }
        });

        exportButton.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                inputPlotId = barcodeId.getText().toString();
                rangeBox.setAllRangeID();
                int[] rangeID = rangeBox.getRangeID();
                moveToSearch("id", rangeID, null, null, inputPlotId);
                goToId.dismiss();
            }
        });

        goToId.show();
    }

    public void makeToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_summary, null);

        builder.setTitle(R.string.preferences_appearance_toolbar_customize_summary)
                .setCancelable(true)
                .setView(layout);

        final AlertDialog dialog = builder.create();

        android.view.WindowManager.LayoutParams params2 = dialog.getWindow().getAttributes();
        params2.width = LayoutParams.MATCH_PARENT;
        dialog.getWindow().setAttributes(params2);

        Button closeBtn = layout.findViewById(R.id.closeBtn);
        TextView summaryText = layout.findViewById(R.id.field_name);

        closeBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        summaryText.setText(traitBox.createSummaryText(rangeBox.getPlotID()));

        dialog.show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (ep.getBoolean(PreferencesActivity.VOLUME_NAVIGATION, false)) {
                    if (action == KeyEvent.ACTION_UP) {
                        rangeBox.moveEntryRight();
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (ep.getBoolean(PreferencesActivity.VOLUME_NAVIGATION, false)) {
                    if (action == KeyEvent.ACTION_UP) {
                        rangeBox.moveEntryLeft();
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_ENTER:
                String return_action = ep.getString(PreferencesActivity.RETURN_CHARACTER, "1");

                if (return_action.equals("1")) {
                    rangeBox.moveEntryRight();
                    return true;
                }

                if (return_action.equals("2")) {
                    traitBox.moveTrait("right");
                    return true;
                }

                if (return_action.equals("3")) {
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

    public TextWatcher getCvNum() {
        return cvNum;
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
        return ep.getBoolean(PreferencesActivity.CYCLING_TRAITS_ADVANCES, false);
    }

    ///// class TraitBox /////
    // traitLeft, traitType, and traitRight
    private class TraitBox {
        private MainActivity parent;
        private String[] prefixTraits;
        private TraitObject currentTrait;

        private Spinner traitType;
        private TextView traitDetails;
        private ImageView traitLeft;
        private ImageView traitRight;

        private Map newTraits;  // { trait name: value }

        public TraitBox(MainActivity parent_) {
            parent = parent_;
            prefixTraits = null;
            newTraits = new HashMap();

            traitType = findViewById(R.id.traitType);
            traitLeft = findViewById(R.id.traitLeft);
            traitRight = findViewById(R.id.traitRight);
            traitDetails = findViewById(R.id.traitDetails);

            traitLeft.setOnTouchListener(createTraitOnTouchListener(traitLeft,R.drawable.main_trait_left_arrow_unpressed,
                    R.drawable.main_trait_left_arrow_pressed));

            // Go to previous trait
            traitLeft.setOnClickListener(new OnClickListener() {

                public void onClick(View arg0) {
                    moveTrait("left");
                }
            });

            traitRight.setOnTouchListener(createTraitOnTouchListener(traitRight,R.drawable.main_trait_right_unpressed,
                    R.drawable.main_trait_right_pressed));

            // Go to next trait
            traitRight.setOnClickListener(new OnClickListener() {

                public void onClick(View arg0) {
                    moveTrait("right");
                }
            });
        }

        public void initTraitDetails() {
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

        public void initTraitType(ArrayAdapter<String> adaptor,
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
                    TraitLayout currentTraitLayout =
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

        public void setNewTraits(final String plotID) {
            newTraits = (HashMap) dt.getUserDetail(plotID).clone();
        }

        public void setNewTraits(Map newTraits) {
            this.newTraits = newTraits;
        }

        public ImageView getTraitLeft() {
            return traitLeft;
        }

        public ImageView getTraitRight() {
            return traitRight;
        }

        public boolean existsNewTraits() {
            return newTraits != null;
        }

        public void setPrefixTraits() {
            prefixTraits = dt.getRangeColumnNames();
        }

        public void setSelection(int pos) {
            traitType.setSelection(pos);
        }

        public int getSelectedItemPosition() {
            try {
                return traitType.getSelectedItemPosition();
            } catch (Exception f) {
                return 0;
            }
        }

        public final TraitObject getCurrentTrait() {
            return currentTrait;
        }

        public final String getCurrentFormat() {
            return currentTrait.getFormat();
        }

        public boolean existsTrait() {
            return newTraits.containsKey(currentTrait.getTrait());
        }

        public final String createSummaryText(final String plotID) {
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
                                                           final int imageIdUp,final int imageIdDown) {
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

        public void moveTrait(String direction) {
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
        private MainActivity parent;
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

        public RangeBox(MainActivity parent_) {
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
        public RangeObject getCRange() {
            return cRange;
        }

        public int[] getRangeID() {
            return rangeID;
        }

        public int getRangeIDByIndex(int j) {
            return rangeID[j];
        }

        public ImageView getRangeLeft() {
            return rangeLeft;
        }

        public ImageView getRangeRight() {
            return rangeRight;
        }

        public final String getPlotID() {
            return cRange.plot_id;
        }

        public boolean isEmpty() {
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

            range.setOnEditorActionListener(createOnEditorListener(range));
            plot.setOnEditorActionListener(createOnEditorListener(plot));

            setName(10);

            rangeName.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    makeToast(ep.getString("ImportFirstName", getString(R.string.search_results_dialog_range)));
                    return false;
                }
            });

            plotName.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    makeToast(ep.getString("ImportSecondName", getString(R.string.search_results_dialog_range)));
                    return false;
                }
            });
        }

        private String truncate(String s, int maxLen) {
            if (s.length() > maxLen)
                return s.substring(0, maxLen - 1) + ":";
            return s;
        }

        private OnEditorActionListener createOnEditorListener(final EditText edit) {
            return new OnEditorActionListener() {
                public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                    // do not do bit check on event, crashes keyboard
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        try {
                            moveToSearch("range", rangeID, range.getText().toString(), null, null);
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
            if (rangeID != null && rangeID.length > 0) {
                final int step = left ? -1 : 1;
                paging = movePaging(paging, step, true);

                // Refresh onscreen controls
                cRange = dt.getRange(rangeID[paging - 1]);
                rangeBox.saveLastPlot();

                if (cRange.plot_id.length() == 0)
                    return;

                final SharedPreferences ep = parent.getPreference();
                if (ep.getBoolean(PreferencesActivity.PRIMARY_SOUND, false)) {
                    if (!cRange.range.equals(lastRange) && !lastRange.equals("")) {
                        lastRange = cRange.range;

                        try {
                            int resID = getResources().getIdentifier("plonk", "raw", getPackageName());
                            MediaPlayer chimePlayer = MediaPlayer.create(MainActivity.this, resID);
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

        public void reload() {
            final SharedPreferences ep = parent.getPreference();
            switchVisibility(ep.getBoolean(PreferencesActivity.QUICK_GOTO, false));

            setName(8);

            paging = 1;

            setAllRangeID();
            if (rangeID != null) {
                cRange = dt.getRange(rangeID[0]);

                //TODO NullPointerException
                lastRange = cRange.range;
                //display();

                traitBox.setNewTraits(cRange.plot_id);
            }
        }

        // Refresh onscreen controls
        public void refresh() {
            cRange = dt.getRange(rangeID[paging - 1]);

            display();
            final SharedPreferences ep = parent.getPreference();
            if (ep.getBoolean(PreferencesActivity.PRIMARY_SOUND, false)) {
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

        public void rightClick() {
            rangeRight.performClick();
        }

        private void saveLastPlot() {
            final SharedPreferences ep = parent.getPreference();
            Editor ed = ep.edit();
            ed.putString("lastplot", cRange.plot_id);
            ed.apply();
        }

        public void switchVisibility(boolean textview) {
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

        public void setAllRangeID() {
            rangeID = dt.getAllRangeID();
        }

        public void setRange(final int id) {
            cRange = dt.getRange(id);
        }

        public void setRangeByIndex(final int j) {
            cRange = dt.getRange(rangeID[j]);
        }

        public void setLastRange() {
            lastRange = cRange.range;
        }

        ///// paging /////

        private void moveEntryLeft() {
            final SharedPreferences ep = parent.getPreference();
            if (ep.getBoolean(PreferencesActivity.DISABLE_ENTRY_ARROW_LEFT, false)
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
            if (ep.getBoolean(PreferencesActivity.DISABLE_ENTRY_ARROW_RIGHT, false)
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
            if (ep.getBoolean(PreferencesActivity.HIDE_ENTRIES_WITH_DATA, false)) {
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

        public void resetPaging() {
            paging = 1;
        }

        public void setPaging(int j) {
            paging = j;
        }

        public final int nextEmptyPlot() throws Exception {
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

        public void clickLeft() {
            rangeLeft.performClick();
        }

        public void clickRight() {
            rangeRight.performClick();
        }
    }
}