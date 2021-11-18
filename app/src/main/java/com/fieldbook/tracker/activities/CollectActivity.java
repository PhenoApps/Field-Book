package com.fieldbook.tracker.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import com.fieldbook.tracker.database.dao.ObservationUnitDao;
import com.fieldbook.tracker.database.dao.ObservationVariableDao;
import com.fieldbook.tracker.database.models.ObservationUnitModel;
import com.fieldbook.tracker.location.GPSTracker;
import com.fieldbook.tracker.location.gnss.ConnectThread;
import com.fieldbook.tracker.location.gnss.GNSSResponseReceiver;
import com.fieldbook.tracker.location.gnss.NmeaParser;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.traits.LayoutCollections;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.adapters.InfoBarAdapter;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.traits.PhotoTraitLayout;
import com.fieldbook.tracker.traits.BaseTraitLayout;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.utilities.DialogUtils;
import com.fieldbook.tracker.utilities.GeodeticUtils;
import com.fieldbook.tracker.utilities.SnackbarUtils;
import com.fieldbook.tracker.utilities.PrefsConstants;
import com.fieldbook.tracker.utilities.Utils;
import com.fieldbook.tracker.database.dao.VisibleObservationVariableDao;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;

import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.jetbrains.annotations.NotNull;
import org.threeten.bp.OffsetDateTime;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import kotlin.Pair;

import static com.fieldbook.tracker.activities.ConfigActivity.dt;
import static com.fieldbook.tracker.location.gnss.GNSSResponseReceiver.ACTION_BROADCAST_GNSS_ROVER;

/**
 * All main screen logic resides here
 */

@SuppressLint("ClickableViewAccessibility")
public class CollectActivity extends AppCompatActivity implements SensorEventListener, GPSTracker.GPSTrackerListener {

    public static boolean searchReload;
    public static String searchRange;
    public static String searchPlot;
    public static boolean reloadData;
    public static boolean partialReload;
    public static Activity thisActivity;
    public static String TAG = "Field Book";
    public static String GEOTAG = "GeoNav";

    private FileWriter mGeoNavLogWriter = null;

    ImageButton deleteValue;
    ImageButton missingValue;
    ImageButton barcodeInput;

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

    /**
     * GeoNav sensors and variables
     */
    private Boolean mGeoNavActivated = false;
    private float[] mGravity;
    private float[] mGeomagneticField;
    private Float mDeclination = null;
    private GPSTracker mGpsTracker;
    private Double mAzimuth = null;
    private Timer mScheduler = null;
    private boolean mNotWarnedInterference = true;
    private LocalBroadcastManager mLocalBroadcastManager = null;
    private ConnectThread mConnectThread = null;
    private Location mExternalLocation = null;
    private Location mInternalLocation = null;
    private double mTeslas = .0;
    private double mLastGeoNavTime = 0L;
    private boolean mFirstLocationFound = false;
    private BluetoothDevice mLastDevice = null;
    public static HandlerThread mAverageHandler = new HandlerThread("averaging");
    private SharedPreferences mPrefs = null;

    private TextWatcher cvText;
    private InputMethodManager imm;
    private Boolean dataLocked = false;

    //variable used to skip the navigate to last used trait in onResume
    private boolean mSkipLastUsedTrait = false;

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

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
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

    /**
     * Is used to ensure the UI entered data is within the bounds of the trait's min/max
     *
     * Added a check to return NA as valid for BrAPI data.
     *
     * @return boolean flag false when data is out of bounds, true otherwise
     */
    private boolean validateData() {
        final String strValue = etCurVal.getText().toString();
        final TraitObject currentTrait = traitBox.getCurrentTrait();

        if (currentTrait == null) return false;

        if (strValue.equals("NA")) return true;

        final String trait = currentTrait.getTrait();

        if (traitBox.existsNewTraits()
                && traitBox.getCurrentTrait() != null
                && strValue.length() > 0
                && !traitBox.getCurrentTrait().isValidValue(etCurVal.getText().toString())) {

            //checks if the trait is numerical and within the bounds (otherwise returns false)
            if (currentTrait.isOver(strValue)) {
                Utils.makeToast(getApplicationContext(),getString(R.string.trait_error_maximum_value)
                        + ": " + currentTrait.getMaximum());
            } else if (currentTrait.isUnder(strValue)) {
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

        barcodeInput = toolbarBottom.findViewById(R.id.barcodeInput);
        barcodeInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new IntentIntegrator(thisActivity)
                        .setPrompt(getString(R.string.main_barcode_text))
                        .setBeepEnabled(false)
                        .setRequestCode(99)
                        .initiateScan();
            }
        });

        deleteValue = toolbarBottom.findViewById(R.id.deleteValue);
        deleteValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // if a brapi observation that has been synced, don't allow deleting
                String exp_id = Integer.toString(ep.getInt(PrefsConstants.SELECTED_FIELD_ID, 0));
                TraitObject currentTrait = traitBox.getCurrentTrait();
                if (dt.isBrapiSynced(exp_id, rangeBox.getPlotID(), currentTrait.getTrait())) {
                    if (currentTrait.getFormat().equals("photo")) {
                        // I want to use abstract method
                        Map<String, String> newTraits = traitBox.getNewTraits();
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

        if (!dt.isRangeTableEmpty()) {
            String plotID = rangeBox.getPlotID();
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

    /**
     * Moves to specific plot/range/plot_id
     * @param type the type of search, search, plot, range or id
     * @param rangeID the array of range ids
     * @param range the primary id
     * @param plot the secondary id
     * @param data data to search for
     * @param trait the trait to navigate to
     * @return true if the search was successful, false otherwise
     */
    private boolean moveToSearch(String type, int[] rangeID, String range, String plot, String data, int trait) {

        if (rangeID == null) {
            return false;
        }

        // search moveto
        if (type.equals("search")) {
            for (int j = 1; j <= rangeID.length; j++) {
                rangeBox.setRangeByIndex(j - 1);

                if (rangeBox.getCRange().range.equals(range) & rangeBox.getCRange().plot.equals(plot)) {
                    moveToResultCore(j);
                    return true;
                }
            }
        }

        // new type to skip the toast message and keep previous functionality
        if (type.equals("quickgoto")) {
            for (int j = 1; j <= rangeID.length; j++) {
                rangeBox.setRangeByIndex(j - 1);

                if (rangeBox.getCRange().range.equals(range) & rangeBox.getCRange().plot.equals(plot)) {
                    moveToResultCore(j);
                    return true;
                }
            }
        }

        //move to plot
        if (type.equals("plot")) {
            for (int j = 1; j <= rangeID.length; j++) {
                rangeBox.setRangeByIndex(j - 1);

                if (rangeBox.getCRange().plot.equals(data)) {
                    moveToResultCore(j);
                    return true;
                }
            }
        }

        //move to range
        if (type.equals("range")) {
            for (int j = 1; j <= rangeID.length; j++) {
                rangeBox.setRangeByIndex(j - 1);

                if (rangeBox.getCRange().range.equals(data)) {
                    moveToResultCore(j);
                    return true;
                }
            }
        }

        //move to plot id
        if (type.equals("id")) {
            int rangeSize = rangeID.length;
            for (int j = 1; j <= rangeSize; j++) {
                rangeBox.setRangeByIndex(j - 1);

                if (rangeBox.getCRange().plot_id.equals(data)) {

                    if (trait == -1) {
                        moveToResultCore(j);
                    } else moveToResultCore(j, trait);

                    return true;
                }
            }
        }

        if (!type.equals("quickgoto"))
            Utils.makeToast(getApplicationContext(), getString(R.string.main_toolbar_moveto_no_match));

        return false;
    }

    private void moveToResultCore(int j) {
        rangeBox.setPaging(j);

        // Reload traits based on selected plot
        rangeBox.display();

        traitBox.setNewTraits(rangeBox.getPlotID());

        initWidgets(false);
    }

    /**
     * Overloaded version of original moveToResultCore.
     * This version is only called after a grid search, which supplies the trait the user clicked on.
     * This search will update the trait box to the clicked trait.
     * @param j the range box page
     * @param traitIndex the trait to move to
     */
    private void moveToResultCore(int j, int traitIndex) {
        rangeBox.setPaging(j);

        // Reload traits based on selected plot
        rangeBox.display();

        traitBox.setNewTraits(rangeBox.getPlotID());

        traitBox.setSelection(traitIndex);

        initWidgets(false);
    }

    @Override
    public void onPause() {
        // Backup database
        try {
            dt.exportDatabase("backup");
            File exportedDb = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.BACKUPPATH + "/" + "backup.db");
            File exportedSp = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH) + Constants.BACKUPPATH + "/" + "backup.db_sharedpref.xml");
            Utils.scanFile(CollectActivity.this, exportedDb);
            Utils.scanFile(CollectActivity.this, exportedSp);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        updateLastOpenedTime();

        stopGeoNav();

        //save the last used trait
        ep.edit().putString(GeneralKeys.LAST_USED_TRAIT, traitBox.currentTrait.getTrait()).apply();

        mAverageHandler.quit();

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

    /**
     * Called in onResume and stopped in onPause
     * Starts a file in storage/geonav/log.txt
     */
    private void setupGeoNavLogger() {

        if (mPrefs.getBoolean(GeneralKeys.GEONAV_LOG, false)) {

            try {

                File geonavFolder = new File(ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, Constants.MPATH)
                        + Constants.GEONAV_LOG_PATH);

                String interval = mPrefs.getString(GeneralKeys.UPDATE_INTERVAL, "1");
                String address = mPrefs.getString(GeneralKeys.PAIRED_DEVICE_ADDRESS, "")
                        .replaceAll(":", "-")
                        .replaceAll("\\s", "_");
                String thetaPref = mPrefs.getString(GeneralKeys.SEARCH_ANGLE, "22.5");

                File file = new File(geonavFolder, "log_" + interval + "_" + address + "_" + thetaPref + "_" + System.nanoTime() + ".csv");

                if (!geonavFolder.exists()) {

                    if (geonavFolder.mkdir()) {

                        Log.d(TAG, "GeoNav Logger started successfully.");

                        mGeoNavLogWriter = new FileWriter(file, true);

                    } else {

                        Log.d(TAG, "GeoNav Logger start failed.");
                    }

                } else {

                    mGeoNavLogWriter = new FileWriter(file, true);

                }

            } catch (IOException io) {

                io.printStackTrace();

            } catch (SecurityException se) {

                se.printStackTrace();

            }
        }
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
                moveToSearch("id", rangeID, null, null, ep.getString("lastplot", null), -1);
            }

        } else if (partialReload) {
            partialReload = false;
            rangeBox.display();
            traitBox.setPrefixTraits();
            initWidgets(false);

        } else if (searchReload) {
            searchReload = false;
            //rangeBox.resetPaging();
            int[] rangeID = rangeBox.getRangeID();

            if (rangeID != null) {
                moveToSearch("search", rangeID, searchRange, searchPlot, null, -1);
            }
        }

        mPrefs.edit().putBoolean(GeneralKeys.GEONAV_AUTO, false).apply(); //turn off auto nav

        if (mPrefs.getBoolean(GeneralKeys.ENABLE_GEONAV, false)) {

            setupLocalBroadcastManager();

            //setup logger whenever activity resumes
            setupGeoNavLogger();

            startGeoNav();
        }

        checkLastOpened();

        if (!mSkipLastUsedTrait) {

            mSkipLastUsedTrait = false;

            navigateToLastOpenedTrait();

        }

        mAverageHandler = new HandlerThread("averaging");
        mAverageHandler.start();
        mAverageHandler.getLooper();
    }

    /**
     * LAST_USED_TRAIT is a preference saved in CollectActivity.onPause
     *
     * This function is called to use that preference and navigate to the corresponding trait.
     */
    private void navigateToLastOpenedTrait() {

        //navigate to the last used trait using preferences
        String trait = ep.getString(GeneralKeys.LAST_USED_TRAIT, null);

        if (trait != null) {

            //get all traits, filter the preference trait and check it's visibility
            String[] traits = dt.getVisibleTrait();

            try {

                traitBox.setSelection(Arrays.asList(traits).indexOf(trait));

            } catch (NullPointerException e) {

                e.printStackTrace();

            }
        }
    }

    /**
     * Simple function that checks if the collect activity was opened >24hrs ago.
     * If the condition is met, it asks the user to reenter the collector id.
     */
    private void checkLastOpened() {

        long lastOpen = ep.getLong("LastTimeAppOpened", 0L);
        long systemTime = System.nanoTime();

        long nanosInOneDay = (long) 1e9*3600*24;

        if (lastOpen != 0L && systemTime - lastOpen > nanosInOneDay) {

            boolean verify = ep.getBoolean("VerifyUserEvery24Hours", true);

            if (verify) {

                if(ep.getString("FirstName","").length() > 0 || ep.getString("LastName","").length() > 0) {
                    //person presumably has been set
                    showAskCollectorDialog(getString(R.string.activity_collect_dialog_verify_collector) + " " + ep.getString("FirstName","") + " " + ep.getString("LastName","") + "?",
                            getString(R.string.activity_collect_dialog_verify_yes_button),
                            getString(R.string.activity_collect_dialog_neutral_button),
                            getString(R.string.activity_collect_dialog_verify_no_button));
                } else {
                    //person presumably hasn't been set
                    showAskCollectorDialog(getString(R.string.activity_collect_dialog_new_collector),
                            getString(R.string.activity_collect_dialog_verify_no_button),
                            getString(R.string.activity_collect_dialog_neutral_button),
                            getString(R.string.activity_collect_dialog_verify_yes_button));
                }
            }
        }

        updateLastOpenedTime();
    }

    private void updateLastOpenedTime() {
        ep.edit().putLong("LastTimeAppOpened", System.nanoTime()).apply();
    }

    private void showAskCollectorDialog(String message, String positive, String neutral, String negative) {
        new AlertDialog.Builder(this, R.style.AppAlertDialog)
                .setTitle(message)
                //yes button
                .setPositiveButton(positive, (DialogInterface dialog, int which) -> {
                    dialog.dismiss();
                })
                //yes, don't ask again button
                .setNeutralButton(neutral, (DialogInterface dialog, int which) -> {
                    dialog.dismiss();
                    ep.edit().putBoolean("VerifyUserEvery24Hours", false).apply();
                })
                //no (navigates to the person preference)
                .setNegativeButton(negative, (DialogInterface dialog, int which) -> {
                    dialog.dismiss();
                    Intent preferenceIntent = new Intent();
                    preferenceIntent.setClassName(CollectActivity.this,
                            PreferencesActivity.class.getName());
                    preferenceIntent.putExtra("PersonUpdate", true);
                    startActivity(preferenceIntent);
                })
                .show();
    }

    /**
     * Helper function update user data in the memory based hashmap as well as
     * the database
     */
    public void updateTrait(String parent, String trait, String value) {

        if (rangeBox.isEmpty()) {
            return;
        }

        traitBox.update(parent, value);
        String exp_id = Integer.toString(ep.getInt(PrefsConstants.SELECTED_FIELD_ID, 0));

        Observation observation = dt.getObservation(exp_id, rangeBox.getPlotID(), parent);
        String observationDbId = observation.getDbId();
        OffsetDateTime lastSyncedTime = observation.getLastSyncedTime();


        // Always remove existing trait before inserting again
        // Based on plot_id, prevent duplicates
        dt.deleteTrait(exp_id, rangeBox.getPlotID(), parent);

        dt.insertUserTraits(rangeBox.getPlotID(), parent, trait, value,
                ep.getString("FirstName", "") + " " + ep.getString("LastName", ""),
                ep.getString("Location", ""), "", exp_id, observationDbId,
                lastSyncedTime);

        //update the info bar in case a variable is used
        infoBarAdapter.notifyItemRangeChanged(0, infoBarAdapter.getItemCount());
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

        String exp_id = Integer.toString(ep.getInt(PrefsConstants.SELECTED_FIELD_ID, 0));
        TraitObject trait = traitBox.getCurrentTrait();
        if (dt.isBrapiSynced(exp_id, rangeBox.getPlotID(), trait.getTrait())) {
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

        //added in geonav 310 only make goenav switch visible if preference is set
        MenuItem geoNavEnable = systemMenu.findItem(R.id.action_act_collect_geonav_sw);
        geoNavEnable.setVisible(mPrefs.getBoolean(GeneralKeys.ENABLE_GEONAV, false));
//        View actionView = MenuItemCompat.getActionView(geoNavEnable);
//        actionView.setOnClickListener((View) -> onOptionsItemSelected(geoNavEnable));

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
                intent.putExtra("path", ep.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY,Constants.MPATH) + Constants.RESOURCEPATH);
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
                        .setBeepEnabled(false)
                        .setRequestCode(98)
                        .initiateScan();
                break;
            case R.id.summary:
                showSummary();
                break;
            case R.id.datagrid:
                Intent i = new Intent();
                i.setClassName(CollectActivity.this,
                        DataGridActivity.class.getName());
                i.putExtra("plot_id", rangeBox.paging);
                i.putExtra("trait", traitBox.currentTrait.getRealPosition());
                startActivityForResult(i, 2);
                break;
            case R.id.lockData:
                dataLocked = !dataLocked;
                lockData(dataLocked);
                break;
            case android.R.id.home:
                finish();
                break;
            /*
             * Toggling the geo nav icon turns the automatic plot navigation on/off.
             * If geonav is enabled, collect activity will auto move to the plot in user's vicinity
             */
            case R.id.action_act_collect_geonav_sw:

                Log.d(GEOTAG, "Menu item clicked.");

                mGeoNavActivated = !mGeoNavActivated;
                MenuItem navItem = systemMenu.findItem(R.id.action_act_collect_geonav_sw);
                if (mGeoNavActivated) {

                    navItem.setIcon(R.drawable.ic_explore_black_24dp);

                    mPrefs.edit().putBoolean(GeneralKeys.GEONAV_AUTO, true).apply();

                }
                else {

                    navItem.setIcon(R.drawable.ic_explore_off_black_24dp);

                    mPrefs.edit().putBoolean(GeneralKeys.GEONAV_AUTO, false).apply();

                }

                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Called when the toolbar enable geonav icon is set to true.
     * Begins listening for sensor events to obtain an azimuth for the user.
     * Starts a timer (with interval defined in the preferences) that runs the IZ algorithm.
     */
    public void startGeoNav() {

        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        if (sensorManager != null) {

            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_UI);

            //set update interval from the preferences can be 1s, 5s or 10s
            String interval = mPrefs.getString(GeneralKeys.UPDATE_INTERVAL, "1");
            long period = 1000L;
            switch (interval) {
                case "1": {
                    period = 1000L;
                    break;
                }
                case "5": {
                    period = 5000L;
                    break;
                }
                case "10": {
                    period = 10000L;
                    break;
                }
            }

            //find the mac address of the device, if not found then start the internal GPS
            String address = mPrefs.getString(GeneralKeys.PAIRED_DEVICE_ADDRESS, "");
            String internalGps = getString(R.string.pref_behavior_geonav_internal_gps_choice);
            boolean internal = true;

            if (address == null || address.isEmpty() || address.equals(internalGps)) {

                //update no matter the distance change and every 10s
                mGpsTracker = new GPSTracker(this, this, 0, 10000);

            } else {

                BluetoothDevice device = getDeviceByAddress(address);

                if (device != null) {

                    setupCommunicationsUi(device);

                }

                internal = false;
            }

            //start the timer and schedule the IZ algorithm
            mScheduler = new Timer();

            final boolean internalFlag = internal;
            mScheduler.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    runImpactZoneAlgorithm(internalFlag);
                }
            }, 2000L, period);

            GeodeticUtils.Companion.writeGeoNavLog(mGeoNavLogWriter, "start latitude, start longitude, UTC, end latitude, end longitude, azimuth, teslas, bearing, distance, closest, unique id, primary id, secondary id\n");

        } else {

            Toast.makeText(this, R.string.activity_collect_sensor_manager_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * This handler is used within the connect thread to broadcast messages.
     */
    private final Handler mHandler = new Handler(msg -> {

        String nmea = (String) msg.obj;

        if (nmea != null) {

            if (msg.what == GNSSResponseReceiver.MESSAGE_OUTPUT_CODE) {

                if (mLocalBroadcastManager != null) {

                    Intent broadcastNmea = new Intent(ACTION_BROADCAST_GNSS_ROVER);
                    broadcastNmea.putExtra(GNSSResponseReceiver.MESSAGE_STRING_EXTRA_KEY, nmea);

                    mLocalBroadcastManager.sendBroadcast(broadcastNmea);
                }

            } else if (msg.what == GNSSResponseReceiver.MESSAGE_OUTPUT_FAIL) {

                if (mLastDevice != null) {
                    setupCommunicationsUi(mLastDevice);
                }
            }

        } else return false;

        return true;
    });

    /**
     * A simple search function to find the bluetooth device correlated to the given address.
     * @param address the mac address that belongs to a given paired device
     * @return the paired device, could be null if the address is not in the paired list
     */
    @Nullable
    private BluetoothDevice getDeviceByAddress(String address) {

        BluetoothDevice device = null;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter.isEnabled()) {

            Set<BluetoothDevice> paired = adapter.getBondedDevices();
            if (!paired.isEmpty()) {

                for (BluetoothDevice d : paired) {

                    if (d.getAddress().equals(address)) {

                        device = d;

                        break;
                    }
                }
            }
        }

        return device;
    }

    /**
     * Starts the connect thread which messages the LBM with nmea messages.
     * @param device the paired device that has been chosen in the preferences.
     */
    private void setupCommunicationsUi(BluetoothDevice device) {

        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();

        mLastDevice = device;

        mConnectThread = new ConnectThread(device, mHandler);

        mConnectThread.start();

    }

    private final GNSSResponseReceiver mGnssResponseReceiver = new GNSSResponseReceiver() {
        @Override
        public void onGNSSParsed(@NotNull NmeaParser parser) {

            double time = Double.parseDouble(parser.getUtc());

            //only update the gps if it is a newly parsed coordinate
            if (time > mLastGeoNavTime) {

                if (!mFirstLocationFound) {
                    mFirstLocationFound = true;
                    playSound("cycle");
                    Utils.makeToast(CollectActivity.this, getString(R.string.act_collect_geonav_first_location_found));
                }

                mLastGeoNavTime = time;
                String lat = GeodeticUtils.Companion.truncateFixQuality(parser.getLatitude(), parser.getFix());
                String lng = GeodeticUtils.Companion.truncateFixQuality(parser.getLongitude(), parser.getFix());
                String alt = parser.getAltitude();
                int altLength = alt.length();
                alt = alt.substring(0, altLength - 1); //drop the "M"

                //always log external gps updates
                GeodeticUtils.Companion.writeGeoNavLog(mGeoNavLogWriter, lat + "," + lng + "," + time + ",null,null,null,null,null,null,null,null,null,null\n");

                mExternalLocation = new Location("GeoNav Rover");

                //initialize the double values, attempt to parse the strings, if impossible then don't update the coordinate.
                double latValue = Double.NaN;
                double lngValue = Double.NaN;
                double altValue = Double.NaN;
                try {

                    latValue = Double.parseDouble(lat);
                    lngValue = Double.parseDouble(lng);
                    altValue = Double.parseDouble(alt);

                } catch (NumberFormatException nfe) {

                    nfe.printStackTrace();
                }

                if (!Double.isNaN(latValue) && !Double.isNaN(lngValue)) {

                    mExternalLocation.setTime((long) time);
                    mExternalLocation.setLatitude(latValue);
                    mExternalLocation.setLongitude(lngValue);
                    mExternalLocation.setAltitude(altValue);
                }
            }
        }
    };

    /**
     * When an external gps unit is used, a local bm must be setup to
     * communicate between the activity and the connect thread.
     */
    private void setupLocalBroadcastManager() {

        //initialize lbm and create a filter to broadcast nmea strings
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_BROADCAST_GNSS_ROVER);

        /*
         * When a BROADCAST_BT_OUTPUT is received and parsed, this interface is called.
         * The parser parameter is a model for the parsed message, and is used to populate the
         * trait layout UI.
         */
        mLocalBroadcastManager.registerReceiver(mGnssResponseReceiver, filter);
    }

    /**
     * This function is called periodically based on preferences, if the geonav enable
     * switch is true in the toolbar. The function takes a parameter to determine whether
     * the internal or an external gps is used. The internal gps uses the GPSTracker class,
     * while the external GPS location is populated with NMEA data parsed from a communications
     * thread with a paired bluetooth device (chosen in the Settings under Behavior/GeoNav).
     * @param internal the flag determining what gps data to use
     */
    private void runImpactZoneAlgorithm(boolean internal) {

        //the angle of the IZ algorithm to use, see Geodetic util class for more details
        String thetaPref = mPrefs.getString(GeneralKeys.SEARCH_ANGLE, "0");
        double theta = 22.5;
        switch (thetaPref) {
            case "22.5": {
                theta = 22.5;
                break;
            }
            case "45": {
                theta = 45.0;
                break;
            }
            case "67.5": {
                theta = 67.5;
                break;
            }
            case "90": {
                theta = 90.0;
                break;
            }
        }

        String geoNavMethod = mPrefs.getString(GeneralKeys.GEONAV_SEARCH_METHOD, "0");
        double d1 = Double.parseDouble(mPrefs.getString(GeneralKeys.GEONAV_PARAMETER_D1, "0.001"));
        double d2 = Double.parseDouble(mPrefs.getString(GeneralKeys.GEONAV_PARAMETER_D2, "0.01"));

        //user must have a valid pointing direction before attempting the IZ
        if (mAzimuth != null) {

            //initialize the start position and fill with external or internal GPS coordinates
            Location start = new Location("start location");
            if (internal && mInternalLocation != null) {

                start = mInternalLocation;

            } else if (!internal) {

                start = mExternalLocation;
            }

            //get current field id
            int studyId = ep.getInt("SelectedFieldExpId", 0);

            dt.open();

            //find all observation units within the field
            ObservationUnitModel[] units = ObservationUnitDao.Companion.getAll(studyId);
            List<ObservationUnitModel> coordinates = new ArrayList<>();

            //add all units that have non null coordinates.
            for (ObservationUnitModel model : units) {
                if (model.getGeo_coordinates() != null && !model.getGeo_coordinates().isEmpty()) {
                    coordinates.add(model);
                }
            }

            //run the algorithm and time how long it takes
            //long toc = System.currentTimeMillis();

            if (start != null) {

                Pair<ObservationUnitModel, Double> target = GeodeticUtils.Companion
                        .impactZoneSearch(mGeoNavLogWriter, start,
                                coordinates.toArray(new ObservationUnitModel[] {}),
                                mAzimuth, theta, mTeslas, geoNavMethod, d1, d2);

                //long tic = System.currentTimeMillis();

                //if we received a result then show it to the user, create a button to navigate to the plot
                if (target.getFirst() != null) {

                    String id = target.getFirst().getObservation_unit_db_id();

                    if (!id.equals(rangeBox.cRange.plot_id)) {

                        thisActivity.runOnUiThread(() -> {

                            if (mPrefs.getBoolean(GeneralKeys.GEONAV_AUTO, false)) {

                                moveToSearch("id", rangeBox.rangeID, null, null, id, -1);

                                Toast.makeText(this, R.string.activity_collect_found_plot, Toast.LENGTH_SHORT).show();

                            } else {

                                Snackbar mySnackbar = Snackbar.make(findViewById(R.id.layout_main),
                                    id, Snackbar.LENGTH_LONG);

                                mySnackbar.setTextColor(Color.BLACK);
                                mySnackbar.setBackgroundTint(Color.WHITE);
                                mySnackbar.setActionTextColor(Color.BLACK);

                                mySnackbar.setAction(R.string.activity_collect_geonav_navigate, (view) -> {

                                    //when navigate button is pressed use rangeBox to go to the plot id
                                    moveToSearch("id", rangeBox.rangeID, null, null, id, -1);

                                });

                                mySnackbar.show();
                            }

                        });
                    }
                }
            }
        }
    }

    /**
     * Called when the toolbar GeoNav Enable icon is switched to off, or the activity is paused.
     * Simply stops listening to the sensor manager and stops the geonav timer.
     */
    public void stopGeoNav() {

        ((SensorManager) getSystemService(SENSOR_SERVICE)).unregisterListener(this);

        mPrefs.edit().putBoolean(GeneralKeys.GEONAV_AUTO, false).apply(); //turn off auto nav

        if (mScheduler != null) {
            mScheduler.purge();
            mScheduler.cancel();
            mScheduler = null;
        }

        //flush and close geo nav log writer
        try {
            if (mGeoNavLogWriter != null) {
                mGeoNavLogWriter.flush();
                mGeoNavLogWriter.close();
            }
        } catch (IOException io) {
            io.printStackTrace();
        }

        if (mLocalBroadcastManager != null) {
            mLocalBroadcastManager.unregisterReceiver(mGnssResponseReceiver);
            mLocalBroadcastManager = null;
        }

        if (mConnectThread != null) mConnectThread.cancel();
    }

    /**
     * Sensor data listener for the collect activity.
     * This is used to find the direction the user is facing.
     * It is possible that the sensors are experiencing nosie and the hardware should be calibrated.
     * TODO: add calibration image on first event or when noise is detected.
     * For Android hardware it is necessary to calibrate before the first event should be accepted.
     * A global flag mNotWarnedInterference is used to defer the interference notification if already seen.
     * @param event the sensor event, could be magnetic or accelerometer
     */
    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event != null && event.sensor != null) {

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mGravity = GeodeticUtils.Companion.lowPassFilter(event.values.clone(), mGravity);
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                mGeomagneticField = GeodeticUtils.Companion.lowPassFilter(event.values.clone(), mGeomagneticField);
            }

            if (mGravity != null && mGeomagneticField != null) {

                mTeslas = calculateNoise(mGeomagneticField);

                if ((mTeslas < 25 || mTeslas > 65) && mNotWarnedInterference) {
                    mNotWarnedInterference = false;
                    Toast.makeText(this, R.string.activity_collect_geomagnetic_noise_detected,
                            Toast.LENGTH_SHORT).show();
                }

                float[] R = new float[9];
                float[] I = new float[9];

                if (SensorManager.getRotationMatrix(R, I, mGravity, mGeomagneticField)) {

                    float[] orientation = new float[3];

                    SensorManager.getOrientation(R, orientation);

                    /*
                     * values[0]: Azimuth, angle of rotation about the -z axis.
                     * This value represents the angle between the device's y axis and the magnetic north pole.
                     * When facing north, this angle is 0, when facing south, this angle is π.
                     * Likewise, when facing east, this angle is π/2, and when facing west, this angle is -π/2.
                     * The range of values is -π to π.
                     */
                    mAzimuth = Math.toDegrees(orientation[0]);
                    mAzimuth = (mAzimuth + 360) % 360;

                    //"${(Math.toDegrees(orientation[0].toDouble()).toInt() + 360) % 360}"

                    if (mExternalLocation != null) {

                        mDeclination = new GeomagneticField(
                                (float) mExternalLocation.getLatitude(),
                                (float) mExternalLocation.getLongitude(),
                                (float) mExternalLocation.getAltitude(),
                                System.currentTimeMillis()).getDeclination();

                    } else if (mInternalLocation != null) {

                        mDeclination = new GeomagneticField(
                                (float) mInternalLocation.getLatitude(),
                                (float) mInternalLocation.getLongitude(),
                                (float) mInternalLocation.getAltitude(),
                                System.currentTimeMillis()).getDeclination();

                    }

                    //if the declination has been found, correct the direction
                    if (mDeclination != null) {

                        mAzimuth += mDeclination.intValue();

                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * Aggregates the vectors of the field array to create a constant.
     * This output is used in a threshold to determine whether the geomagnetic field
     * is experiencing noise.
     * @param field the geomagnetic vectors x, y, z
     * @return the square root of the summation of all squared vectors
     */
    private Double calculateNoise(float[] field) {

        double sum = 0.0;

        for (float xyz : field) {
            sum += Math.pow(xyz, 2);
        }

        return Math.sqrt(sum);
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
                moveToSearch("id", rangeID, null, null, inputPlotId, -1);
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
                new IntentIntegrator(thisActivity)
                        .setPrompt(getString(R.string.main_barcode_text))
                        .setBeepEnabled(false)
                        .setRequestCode(98)
                        .initiateScan();
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
                } else {
                    Toast.makeText(this, R.string.act_file_explorer_no_file_error, Toast.LENGTH_SHORT).show();
                }
                break;
            case 2:
                if (resultCode == RESULT_OK) {
                    inputPlotId = data.getStringExtra("result");
                    int trait = data.getIntExtra("trait", -1);
                    rangeBox.setAllRangeID();
                    int[] rangeID = rangeBox.getRangeID();
                    moveToSearch("id", rangeID, null, null, inputPlotId, trait);
                    mSkipLastUsedTrait = true;
                }
                break;
            case 98:
                if(resultCode == RESULT_OK) {
                    IntentResult plotSearchResult = IntentIntegrator.parseActivityResult(resultCode, data);
                    inputPlotId = plotSearchResult.getContents();
                    rangeBox.setAllRangeID();
                    int[] rangeID = rangeBox.getRangeID();
                    boolean success = moveToSearch("id", rangeID, null, null, inputPlotId, -1);

                    //play success or error sound if the plotId was not found
                    if (success) {
                        playSound("hero_simple_celebration");
                    } else playSound("alert_error");
                }
                break;
            case 99:
                if(resultCode == RESULT_OK) {
                    // store barcode value as data
                    IntentResult plotDataResult = IntentIntegrator.parseActivityResult(resultCode, data);
                    String scannedBarcode = plotDataResult.getContents();
                    TraitObject currentTrait = traitBox.getCurrentTrait();
                    BaseTraitLayout currentTraitLayout = traitLayouts.getTraitLayout(currentTrait.getFormat());
                    currentTraitLayout.loadLayout();


                    updateTrait(currentTrait.getTrait(), currentTrait.getFormat(), scannedBarcode);
                    currentTraitLayout.loadLayout();
                    validateData();
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

    /**
     * Iterates over all traits for the given ID and returns the trait's index which is missing
     * @param traitIndex current trait index
     * @param ID the plot identifier
     * @return index of the trait missing or -1 if all traits exist
     */
    public int existsAllTraits(final int traitIndex, final int ID) {
        final String[] traits = VisibleObservationVariableDao.Companion.getVisibleTrait();
        final String[] formats = VisibleObservationVariableDao.Companion.getFormat();
        for (int i = 0; i < traits.length; i++) {
            if (i != traitIndex
                    && !dt.getTraitExists(ID, traits[i], formats[i])) return i;
        }
        return -1;
    }

    public List<Integer> getNonExistingTraits(final int ID) {
        final String[] traits = VisibleObservationVariableDao.Companion.getVisibleTrait();
        final String[] formats = VisibleObservationVariableDao.Companion.getFormat();
        final ArrayList<Integer> indices = new ArrayList<>();
        for (int i = 0; i < traits.length; i++) {
            if (!dt.getTraitExists(ID, traits[i], formats[i]))
                indices.add(i);
        }
        return indices;
    }

    public Map<String, String> getNewTraits() {
        return traitBox.getNewTraits();
    }

    public void setNewTraits(Map<String, String> newTraits) {
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

    /**
     * Inserts a user observation whenever a label is printed.
     * See ResultReceiver onReceiveResult in LabelPrintLayout
     * @param size: The size of the label. e.g "2 x 4 detailed"
     */
    public void insertPrintObservation(String size) {

        TraitObject trait = getCurrentTrait();

        String studyId = Integer.toString(ep.getInt(PrefsConstants.SELECTED_FIELD_ID, 0));

        dt.insertUserTraits(rangeBox.getPlotID(), trait.getFormat(), trait.getTrait(), size,
                ep.getString("FirstName", "") + " " + ep.getString("LastName", ""),
                ep.getString("Location", ""), "", studyId, "",
                null);

    }

    @Override
    public void onLocationChanged(@NonNull Location location) {

        mInternalLocation = location;

        //always log location updates
        GeodeticUtils.Companion.writeGeoNavLog(mGeoNavLogWriter, location.getLatitude() + "," + location.getLongitude() + "," + location.getTime() + ",null,null,null,null,null,null,null,null,null,null\n");
    }

    ///// class TraitBox /////
    // traitLeft, traitType, and traitRight
    private class TraitBox {
        private final CollectActivity parent;
        private String[] prefixTraits;
        private TraitObject currentTrait;

        private final Spinner traitType;
        private final TextView traitDetails;
        private final ImageView traitLeft;
        private final ImageView traitRight;

        /**
         * New traits is a map of observations where the key is the trait name
         * and the value is the observation value. This is updated whenever
         * a new plot id is navigated to.
         */
        private Map<String, String> newTraits;  // { trait name: value }

        TraitBox(CollectActivity parent_) {
            parent = parent_;
            prefixTraits = null;
            newTraits = new HashMap<>();

            traitType = findViewById(R.id.traitType);

            //determine trait button function based on user-preferences
            //issues217 introduces the ability to swap trait and plot arrows
            boolean flipFlopArrows = ep.getBoolean("FLIP_FLOP_ARROWS", false);
            if (flipFlopArrows) {
                traitLeft = findViewById(R.id.rangeLeft);
                traitRight = findViewById(R.id.rangeRight);
            } else {
                traitLeft = findViewById(R.id.traitLeft);
                traitRight = findViewById(R.id.traitRight);
            }

            traitDetails = findViewById(R.id.traitDetails);

            //change click-arrow based on preferences
            if (flipFlopArrows) {
                traitLeft.setOnTouchListener(createTraitOnTouchListener(traitLeft, R.drawable.main_entry_left_unpressed,
                        R.drawable.main_entry_left_pressed));
            } else {
                traitLeft.setOnTouchListener(createTraitOnTouchListener(traitLeft, R.drawable.main_trait_left_arrow_unpressed,
                        R.drawable.main_trait_left_arrow_pressed));
            }

            // Go to previous trait
            traitLeft.setOnClickListener(new OnClickListener() {

                public void onClick(View arg0) {
                    moveTrait("left");
                }
            });

            //change click-arrow based on preferences
            if (flipFlopArrows) {
                traitRight.setOnTouchListener(createTraitOnTouchListener(traitRight, R.drawable.main_entry_right_unpressed,
                        R.drawable.main_entry_right_pressed));
            } else {
                traitRight.setOnTouchListener(createTraitOnTouchListener(traitRight, R.drawable.main_trait_right_unpressed,
                        R.drawable.main_trait_right_pressed));
            }

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

        public Map<String, String> getNewTraits() {
            return newTraits;
        }

        /**
         * Called when navigating between plots in collect activity.
         * New Traits hashmap of <trait name to observation value> stores data for the currently
         * selected plot id.
         * @param plotID the new plot id we are transitioning to
         */
        void setNewTraits(final String plotID) {
            newTraits = dt.getUserDetail(plotID);
        }

        void setNewTraits(Map<String, String> newTraits) {
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

        /**
         * Deletes all observation variables named traitName from the db.
         * Also removes the trait from "newTraits"
         * @param traitName the observation variable name
         * @param plotID the unique plot identifier to remove the observations from
         */
        public void remove(String traitName, String plotID) {
            if (newTraits.containsKey(traitName))
                newTraits.remove(traitName);

            String exp_id = Integer.toString(ep.getInt(PrefsConstants.SELECTED_FIELD_ID, 0));

            dt.deleteTrait(exp_id, plotID, traitName);
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

                    if (parent.is_cycling_traits_advances()) {
                        rangeBox.clickLeft();
                    }

                    if(ep.getBoolean(GeneralKeys.CYCLE_TRAITS_SOUND,false)) {
                        playSound("cycle");
                    }
                }
            } else if (direction.equals("right")) {
                pos = traitType.getSelectedItemPosition() + 1;

                if (pos > traitType.getCount() - 1) {
                    pos = 0;

                    if (parent.is_cycling_traits_advances()) {
                        rangeBox.clickRight();
                    }

                    if(ep.getBoolean(GeneralKeys.CYCLE_TRAITS_SOUND,false)) {
                        playSound("cycle");
                    }
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

        //edit text used for quick goto feature range = primary id
        private EditText range;
        //edit text used for quick goto feature plot = secondary id
        private EditText plot;

        private TextView tvRange;
        private TextView tvPlot;

        private ImageView rangeLeft;
        private ImageView rangeRight;

        private Handler repeatHandler;

        /**
         * Variables to track Quick Goto searching
         */
        private boolean rangeEdited = false;
        private boolean plotEdited = false;

        /**
         * unique plot names used in range queries
         * query and save them once during initialization
         */
        private String firstName, secondName, uniqueName;

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

            firstName = ep.getString("ImportFirstName", "");
            secondName = ep.getString("ImportSecondName", "");
            uniqueName = ep.getString("ImportUniqueName", "");

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

            //determine range button function based on user-preferences
            //issues217 introduces the ability to swap trait and plot arrows
            boolean flipFlopArrows = ep.getBoolean("FLIP_FLOP_ARROWS", false);
            if (flipFlopArrows) {
                rangeLeft = findViewById(R.id.traitLeft);
                rangeRight = findViewById(R.id.traitRight);
            } else {
                rangeLeft = findViewById(R.id.rangeLeft);
                rangeRight = findViewById(R.id.rangeRight);
            }

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

        /**
         * This listener is used in the QuickGoto feature.
         * This listens to the primary/secondary edit text's in the rangebox.
         * When the soft keyboard enter key action is pressed (IME_ACTION_DONE)
         * this will use the moveToSearch function.
         * First it will search for both primary/secondary ids if they have both been changed.
         * If one has not been changed or a plot is not found for both terms then it defaults to
         * a search with whatever was changed last.
         * @param edit the edit text to assign this listener to
         * @param searchType the type used in moveToSearch, either plot or range
         */
        private OnEditorActionListener createOnEditorListener(final EditText edit, final String searchType) {
            return new OnEditorActionListener() {
                public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                    // do not do bit check on event, crashes keyboard
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        try {

                            //if both quick goto et's have been changed, attempt a search with them
                            if (rangeBox.rangeEdited && rangeBox.plotEdited) {

                                //if the search fails back-down to the original search
                                if (!moveToSearch("quickgoto", rangeID,
                                        rangeBox.range.getText().toString(),
                                        rangeBox.plot.getText().toString(), null, -1)) {

                                    moveToSearch(searchType, rangeID, null, null, view.getText().toString(), -1);

                                }

                            } else { //original search if only one has changed

                                moveToSearch(searchType, rangeID, null, null, view.getText().toString(), -1);

                            }

                            //reset the changed flags
                            rangeBox.rangeEdited = false;
                            rangeBox.plotEdited = false;

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

            //change click-arrow based on preferences
            boolean flipFlopArrows = ep.getBoolean("FLIP_FLOP_ARROWS", false);
            if (flipFlopArrows) {
                return createOnTouchListener(rangeLeft, actionLeft,
                        R.drawable.main_trait_left_arrow_pressed,
                        R.drawable.main_trait_left_arrow_unpressed);
            } else {
                return createOnTouchListener(rangeLeft, actionLeft,
                        R.drawable.main_entry_left_pressed,
                        R.drawable.main_entry_left_unpressed);
            }
        }

        private OnTouchListener createOnRightTouchListener() {
            Runnable actionRight = createRunnable("right");

            //change click-arrow based on preferences
            boolean flipFlopArrows = ep.getBoolean("FLIP_FLOP_ARROWS", false);
            if (flipFlopArrows) {
                return createOnTouchListener(rangeRight, actionRight,
                        R.drawable.main_trait_right_pressed,
                        R.drawable.main_trait_right_unpressed);
            } else {
                return createOnTouchListener(rangeRight, actionRight,
                        R.drawable.main_entry_right_pressed,
                        R.drawable.main_entry_right_unpressed);
            }
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
                updateCurrentRange(rangeID[paging -1]);

                rangeBox.saveLastPlot();

                if (cRange.plot_id.length() == 0)
                    return;

                final SharedPreferences ep = parent.getPreference();
                if (ep.getBoolean(GeneralKeys.PRIMARY_SOUND, false)) {
                    if (!cRange.range.equals(lastRange) && !lastRange.equals("")) {
                        lastRange = cRange.range;
                        playSound("plonk");
                    }
                }

                rangeBox.display();
                traitBox.setNewTraits(rangeBox.getPlotID());

                initWidgets(true);
            }
        }

        /**
         * Checks whether the preference study names are empty.
         * If they are show a message, otherwise update the current range.
         * @param id the range position to update to
         */
        private void updateCurrentRange(int id) {

            if (!firstName.isEmpty() && !secondName.isEmpty() && !uniqueName.isEmpty()) {

                cRange = dt.getRange(firstName, secondName, uniqueName, id);

            } else {

                Toast.makeText(CollectActivity.this,
                        R.string.act_collect_study_names_empty, Toast.LENGTH_SHORT).show();

                finish();
            }

        }

        void reload() {
            final SharedPreferences ep = parent.getPreference();
            switchVisibility(ep.getBoolean(GeneralKeys.QUICK_GOTO, false));

            setName(8);

            paging = 1;

            setAllRangeID();
            if (rangeID != null) {

                //if the study has no plots this would cause an AIOB exception
                if (rangeID.length > 0) {

                    updateCurrentRange(rangeID[0]);

                    //TODO NullPointerException
                    lastRange = cRange.range;
                    display();

                    traitBox.setNewTraits(cRange.plot_id);

                } else { //if no fields, print a message and finish with result canceled

                    Utils.makeToast(thisActivity, getString(R.string.act_collect_no_plots));

                    setResult(RESULT_CANCELED);

                    finish();
                }
            }
        }

        // Refresh onscreen controls
        void refresh() {

            updateCurrentRange(rangeID[paging - 1]);

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

        private TextWatcher createTextWatcher(String type) {
            return new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {

                    if (type.equals("range")) rangeEdited = true;
                    else plotEdited = true;
                }
            };
        }

        void switchVisibility(boolean textview) {
            if (textview) {
                tvRange.setVisibility(TextView.GONE);
                tvPlot.setVisibility(TextView.GONE);
                range.setVisibility(EditText.VISIBLE);
                plot.setVisibility(EditText.VISIBLE);

                //when the et's are visible create text watchers to listen for changes
                range.addTextChangedListener(createTextWatcher("range"));
                plot.addTextChangedListener(createTextWatcher("plot"));

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
            updateCurrentRange(id);
        }

        void setRangeByIndex(final int j) {
            updateCurrentRange(rangeID[j]);
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

            if (ep.getBoolean(GeneralKeys.ENTRY_NAVIGATION_SOUND, false)
                    && !parent.getTraitBox().existsTrait()) {
                playSound("advance");
            }

            String entryArrow = ep.getString(GeneralKeys.DISABLE_ENTRY_ARROW_NO_DATA, "0");

            if ((entryArrow.equals("1")||entryArrow.equals("3")) && !parent.getTraitBox().existsTrait()) {
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

            if (ep.getBoolean(GeneralKeys.ENTRY_NAVIGATION_SOUND, false)
                    && !parent.getTraitBox().existsTrait()) {
                playSound("advance");
            }

            String entryArrow = ep.getString(GeneralKeys.DISABLE_ENTRY_ARROW_NO_DATA, "0");

            if ((entryArrow.equals("2")||entryArrow.equals("3")) && !parent.getTraitBox().existsTrait()) {
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

        private void chooseNextTrait(int pos, int step) {
            List<Integer> nextTrait = parent.getNonExistingTraits(rangeID[pos - 1]);
            if (!nextTrait.isEmpty()) {
                if (step < 0) {
                    traitBox.setSelection(Collections.max(nextTrait));
                } else traitBox.setSelection(Collections.min(nextTrait));
            }
        }

        private int getTraitIndex(String[] traits) {
            String currentTraitName = traitBox.currentTrait.getTrait();
            int traitIndex = 0;
            for (int i = 0; i < traits.length; i++) {
                if (currentTraitName.equals(traits[i])) {
                    traitIndex = i;
                    break;
                }
            }
            return traitIndex;
        }

        private int checkSkipTraits(String[] traits, int step, int pos, boolean cyclic, boolean skipMode) {

            //edge case where we are on the last position
            //check for missing traits dependent on step for last position
            //if all traits are observed or the only unobserved is to the left, move to pos 1
            if (step == 1 && pos == rangeID.length) {
                if (!skipMode) {
                    int currentTrait = getTraitIndex(traits);
                    int nextTrait = parent.existsAllTraits(currentTrait, rangeID[pos - 1]);
                    if (nextTrait != -1) { //check if this trait is "next" if not then move to 1
                        if (nextTrait > currentTrait) {
                            traitBox.setSelection(nextTrait);
                            return rangeID.length;
                        } else { //when moving to one, select the non existing trait
                            List<Integer> nextTraitOnFirst = parent.getNonExistingTraits(rangeID[0]);
                            if (!nextTraitOnFirst.isEmpty()) {
                                traitBox.setSelection(Collections.min(nextTraitOnFirst));
                                return 1;
                            }
                        } //if all traits exist for 1 then just follow the main loop
                    }
                }
            }

            final int prevPos = pos;
            //first loop is used to detect if all observations are completed
            boolean firstLoop = true;
            //this keeps track of the previous loops position
            //while prevPos keeps track of what position this function was called with.
            int localPrev;
            while (true) {

                //get the index of the currently selected trait
                int traitIndex = getTraitIndex(traits);

                localPrev = pos;
                pos = moveSimply(pos, step);

                //if we wrap around the entire range then observations are completed
                //notify the user and just go to the first range id.
                if (!firstLoop && prevPos == localPrev) {
                    Toast.makeText(CollectActivity.this,
                            R.string.activity_collect_all_obs_made, Toast.LENGTH_SHORT).show();
                    return 1;
                }
                firstLoop = false;

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
                        if (!skipMode) {
                            List<Integer> nextTrait = parent.getNonExistingTraits(rangeID[pos - 1]);
                            if (!nextTrait.isEmpty()) {
                                if (step < 0) {
                                    traitBox.setSelection(Collections.max(nextTrait));
                                } else traitBox.setSelection(Collections.min(nextTrait));
                                return pos;
                            }
                        }
                    }
                }

                if (skipMode) {
                    if (!parent.existsTrait(rangeID[pos - 1])) {
                        return pos;
                    }
                } else {

                    //check all traits for the currently selected range id
                    //this returns the missing trait index or -1 if they all are observed
                    int nextTrait = parent.existsAllTraits(traitIndex, rangeID[localPrev - 1]);
                    //if we press right, but a trait to the left is missing, go to next plot
                    //similarly if we press left, but a trait to the right is missing, go to previous
                    //check if pressing left/right will skip an unobserved trait
                    //if it does, force it to the next plot and set the traitBox to the first unobserved
                    //boolean skipped = Math.abs(prevPos - localPrev) > 1;
                    if (nextTrait < traitIndex && step > 0) {

                        //check which trait is missing in the next position
                        List<Integer> nextPlotTrait = parent.getNonExistingTraits(rangeID[pos - 1]);

                        //if no trait is missing, loop
                        if (!nextPlotTrait.isEmpty()) { //otherwise set the selection and return position

                            //we are moving to the right, so set the left most trait
                            traitBox.setSelection(
                                    Collections.min(nextPlotTrait)
                            );

                            return pos;
                        }

                    } else if ((nextTrait == -1 || nextTrait > traitIndex) && step < 0) {

                        //check which trait is missing in the next position
                        List<Integer> nextPlotTrait = parent.getNonExistingTraits(rangeID[pos - 1]);

                        //if no trait is missing, loop
                        if (!nextPlotTrait.isEmpty()) { //otherwise set the selection and return position

                            //moving to the left so set the right most trait
                            traitBox.setSelection(
                                    Collections.max(nextPlotTrait)
                            );

                            return pos;
                        }
                    //otherwise, set the selection to the missing trait and return the current pos
                    } else if (nextTrait > -1) {

                        traitBox.setSelection(nextTrait);

                        return localPrev;
                    }
                }
            }
        }

        private int movePaging(int pos, int step, boolean cyclic) {
            // If ignore existing data is enabled, then skip accordingly
            final SharedPreferences ep = parent.getPreference();

            final String[] traits = VisibleObservationVariableDao.Companion.getVisibleTrait();

            //three options: 1. disabled 2. skip active trait 3. skip but check all traits
            String skipMode = ep.getString(GeneralKeys.HIDE_ENTRIES_WITH_DATA, "1");

            switch (skipMode) {

                case "2" : {

                    return checkSkipTraits(traits, step, pos, cyclic, true);

                }

                case "3" : {

                    return checkSkipTraits(traits, step, pos, cyclic, false);

                }

                default : return moveSimply(pos, step);

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