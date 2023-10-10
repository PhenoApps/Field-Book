package com.fieldbook.tracker.activities;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.adapters.InfoBarAdapter;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.database.models.ObservationUnitModel;
import com.fieldbook.tracker.dialogs.GeoNavCollectDialog;
import com.fieldbook.tracker.interfaces.FieldSwitcher;
import com.fieldbook.tracker.location.GPSTracker;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.InfoBarModel;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.traits.BaseTraitLayout;
import com.fieldbook.tracker.traits.CategoricalTraitLayout;
import com.fieldbook.tracker.traits.GNSSTraitLayout;
import com.fieldbook.tracker.traits.GoProTraitLayout;
import com.fieldbook.tracker.traits.LayoutCollections;
import com.fieldbook.tracker.traits.PhotoTraitLayout;
import com.fieldbook.tracker.utilities.CategoryJsonUtil;
import com.fieldbook.tracker.utilities.FieldSwitchImpl;
import com.fieldbook.tracker.utilities.GeoJsonUtil;
import com.fieldbook.tracker.utilities.GeoNavHelper;
import com.fieldbook.tracker.utilities.GnssThreadHelper;
import com.fieldbook.tracker.utilities.GoProWrapper;
import com.fieldbook.tracker.utilities.InfoBarHelper;
import com.fieldbook.tracker.utilities.JsonUtil;
import com.fieldbook.tracker.utilities.LocationCollectorUtil;
import com.fieldbook.tracker.utilities.SnackbarUtils;
import com.fieldbook.tracker.utilities.SoundHelperImpl;
import com.fieldbook.tracker.utilities.TapTargetUtil;
import com.fieldbook.tracker.utilities.Utils;
import com.fieldbook.tracker.utilities.VerifyPersonHelper;
import com.fieldbook.tracker.utilities.VibrateUtil;
import com.fieldbook.tracker.views.CollectInputView;
import com.fieldbook.tracker.views.RangeBoxView;
import com.fieldbook.tracker.views.TraitBoxView;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.phenoapps.interfaces.security.SecureBluetooth;
import org.phenoapps.interfaces.usb.camera.UsbCameraInterface;
import org.phenoapps.security.SecureBluetoothActivityImpl;
import org.phenoapps.usb.camera.UsbCameraHelper;
import org.phenoapps.utils.BaseDocumentTreeUtil;
import org.phenoapps.utils.TextToSpeechHelper;
import org.threeten.bp.OffsetDateTime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * All main screen logic resides here
 */

@AndroidEntryPoint
@SuppressLint("ClickableViewAccessibility")
public class CollectActivity extends ThemedActivity
        implements UsbCameraInterface, SummaryFragment.SummaryOpenListener,
        com.fieldbook.tracker.interfaces.CollectController,
        com.fieldbook.tracker.interfaces.CollectRangeController,
        com.fieldbook.tracker.interfaces.CollectTraitController,
        InfoBarAdapter.InfoBarController,
        GoProTraitLayout.GoProCollector,
        GPSTracker.GPSTrackerListener {

    public static final int REQUEST_FILE_EXPLORER_CODE = 1;
    public static final int BARCODE_COLLECT_CODE = 99;
    public static final int BARCODE_SEARCH_CODE = 98;

    private GeoNavHelper geoNavHelper;

    @Inject
    VibrateUtil vibrator;

    @Inject
    GnssThreadHelper gnssThreadHelper;

    @Inject
    DataHelper database;

    @Inject
    VerifyPersonHelper verifyPersonHelper;

    //used to query for infobar prefix/value pairs and building InfoBarModels
    @Inject
    InfoBarHelper infoBarHelper;

    @Inject
    FieldSwitchImpl fieldSwitcher;

    @Inject
    SoundHelperImpl soundHelper;

    @Inject
    GoProWrapper goProWrapper;

    private GPSTracker gps;

    public static boolean searchReload;
    public static String searchRange;
    public static String searchPlot;
    public static String searchUnique;
    public static boolean reloadData;
    public static boolean partialReload;
    public static String TAG = "Field Book";
    public static String GEOTAG = "GeoNav";

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
    private final Object lock = new Object();

    /**
     * Main screen elements
     */
    private Menu systemMenu;
    private InfoBarAdapter infoBarAdapter;
    private TraitBoxView traitBox;
    private RangeBoxView rangeBox;
    private RecyclerView infoBarRv;

    /**
     * Trait-related elements
     */
    private final HandlerThread guiThread = new HandlerThread("ui");

    private CollectInputView collectInputView;

    public Handler myGuiHandler;

    private SharedPreferences mPrefs;

    /**
     * Data lock is controlled by the toolbar lock icon
     * 0: Unlocked, any data can be entered
     * 1: Locked, data cannot be entered
     * 2: Frozen, old data cannot be edited, but new data can be entered
     */
    public static final int UNLOCKED = 0;
    public static final int LOCKED = 1;
    public static final int FROZEN = 2;
    private int dataLocked = UNLOCKED;

    //variable used to skip the navigate to last used trait in onResume
    private boolean mSkipLastUsedTrait = false;

    private TextToSpeechHelper ttsHelper = null;
    /**
     * Usb Camera Helper
     */
    private UsbCameraHelper mUsbCameraHelper = null;

    private SecureBluetoothActivityImpl secureBluetooth;

    //summary fragment listener
    private boolean isSummaryOpen = false;

    /**
     * Multi Measure delete dialogs
     */
    private AlertDialog dialogMultiMeasureDelete;
    private AlertDialog dialogMultiMeasureConfirmDelete;

    /**
     * GeoNav dialog
     */
    private androidx.appcompat.app.AlertDialog dialogGeoNav;
    private androidx.appcompat.app.AlertDialog dialogPrecisionLoss;

    public void triggerTts(String text) {
        if (ep.getBoolean(GeneralKeys.TTS_LANGUAGE_ENABLED, false)) {
            ttsHelper.speak(text);
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gps = new GPSTracker(this, this, 0, 10000);

        guiThread.start();
        myGuiHandler = new Handler(guiThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                synchronized (lock) {
                    if (msg.what == 1) {
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
                    }
                }
            }
        };

        secureBluetooth = new SecureBluetoothActivityImpl(this);
        secureBluetooth.initialize();

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        geoNavHelper = new GeoNavHelper(this);
        ep = getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0);

        ttsHelper = new TextToSpeechHelper(this, () -> {
            String lang = mPrefs.getString(GeneralKeys.TTS_LANGUAGE, "-1");
            if (!lang.equals("-1")) {
                Set<Locale> locales = TextToSpeechHelper.Companion.getAvailableLocales();
                for (Locale l : locales) {
                    if (l.getLanguage().equals(lang)) {
                        ttsHelper.setLanguage(l);
                        break;
                    }
                }
            }
            return null;
        });

        mUsbCameraHelper = new UsbCameraHelper(this);

        goProWrapper.attach();

        loadScreen();

        checkForInitialBarcodeSearch();

        verifyPersonHelper.checkLastOpened();
    }

    private void switchField(int studyId, @Nullable String obsUnitId) {

        try {

            fieldSwitcher.switchField(studyId);

            rangeBox.setAllRangeID();
            int[] rangeID = rangeBox.getRangeID();

            //refresh collect activity UI
            rangeBox.reload();
            rangeBox.refresh();
            initWidgets(false);

            if (obsUnitId != null) {

                reloadData = false;

                //navigate to the plot
                moveToSearch("id", rangeID, null, null, obsUnitId, -1);
            }

            soundHelper.playCelebrate();

        } catch (Exception e) {

            Log.d(TAG,"Error during switch field");

            e.printStackTrace();

        }
    }

    /**
     * Checks if the user has clicked the barcode button on ConfigActivity,
     * this will search for obs unit and load neccessary field file
     */
    private void checkForInitialBarcodeSearch() {

        try {

            Intent i = getIntent();
            if (i != null) {

                //get barcode to search for which will be an obs. unit id
                String barcode = i.getStringExtra("barcode");

                if (barcode != null) {

                    Log.d(TAG, "Searching initial barcode: " + barcode);

                    ObservationUnitModel model = database.getObservationUnitById(barcode);

                    if (model != null) {

                        try {

                            //if barcode matches an obs. unit id
                            if (model.getObservation_unit_db_id().equals(barcode)) {

                                inputPlotId = barcode;

                                FieldObject fo = database.getFieldObject(model.getStudy_id());

                                if (fo != null && fo.getExp_name() != null) {

                                    switchField(model.getStudy_id(), barcode);

                                }
                            }

                        } catch (Exception e) {

                            Log.d(TAG, "Failed while searching for: " + barcode);

                            e.printStackTrace();
                        }

                    } else {

                        Toast.makeText(this, getString(R.string.act_collect_plot_with_code_not_found), Toast.LENGTH_LONG).show();

                    }
                }
            }

        } catch (Exception e) {

            Log.d(TAG, "Something failed while searching. ");

            e.printStackTrace();
        }
    }

    @NonNull
    public CollectInputView getCollectInputView() {
        return collectInputView;
    }

    public String getStudyId() {
        return Integer.toString(ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));
    }

    public String getObservationUnit() {
        return getCRange().plot_id;
    }

    public String getPerson() {
        return ep.getString(GeneralKeys.FIRST_NAME, "") + " " + ep.getString(GeneralKeys.LAST_NAME, "");
    }

    public String getTraitName() {
        return getCurrentTrait().getTrait();
    }

    public String getTraitFormat() {
        return getCurrentTrait().getFormat();
    }

    public BaseTraitLayout getTraitLayout() {
        return traitLayouts.getTraitLayout(getTraitFormat());
    }

    private void initCurrentVals() {

        collectInputView = findViewById(R.id.act_collect_input_view);
        collectInputView.setOnEditorActionListener((exampleView, actionId, event) -> {
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN) {
                rangeBox.rightClick();
                return true;
            }
            return false;
        });
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

        //lock = new Object();

        traitLayouts = new LayoutCollections(this);
        rangeBox = findViewById(R.id.act_collect_range_box);
        traitBox = findViewById(R.id.act_collect_trait_box);
        traitBox.connectRangeBox(rangeBox);
        rangeBox.connectTraitBox(traitBox);

        //setup infobar recycler view ui
        infoBarRv = findViewById(R.id.act_collect_infobar_rv);
        infoBarRv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        initCurrentVals();

        Log.d(TAG, "Load screen.");

        refreshInfoBarAdapter();
    }

    /**
     * Updates the infobar adapter with the new plot information.
     */
    public void refreshInfoBarAdapter() {

        Log.d(TAG, "Refreshing info bar adapter.");

        try {

            infoBarAdapter = new InfoBarAdapter(this);

            infoBarRv.setAdapter(infoBarAdapter);

            List<InfoBarModel> models = infoBarHelper.getInfoBarData();

            infoBarAdapter.submitList(models);

        } catch (Exception e) {

            e.printStackTrace();

            Log.d(TAG, "Error: info bar adapter loading.");
        }
    }

    @Override
    public void refreshMain() {
        rangeBox.saveLastPlot();
        rangeBox.refresh();
        traitBox.setNewTraits(rangeBox.getPlotID());

        Log.d(TAG, "Refresh main.");

        initWidgets(true);

        refreshLock();
    }

    /**
     * Is used to ensure the UI entered data is within the bounds of the trait's min/max
     *
     * Added a check to return NA as valid for BrAPI data.
     *
     * @return boolean flag false when data is out of bounds, true otherwise
     */
    @Override
    public boolean validateData() {
        final String strValue = collectInputView.getText();
        final TraitObject currentTrait = traitBox.getCurrentTrait();

        if (currentTrait == null) return false;

        if (strValue.equals("NA")) return true;

        final String trait = currentTrait.getTrait();

        if (traitBox.existsNewTraits()
                && traitBox.getCurrentTrait() != null
                && strValue.length() > 0
                && !traitBox.getCurrentTrait().isValidValue(strValue)) {

            //checks if the trait is numerical and within the bounds (otherwise returns false)
            if (currentTrait.isOver(strValue)) {
                Utils.makeToast(getApplicationContext(),getString(R.string.trait_error_maximum_value)
                        + ": " + currentTrait.getMaximum());
            } else if (currentTrait.isUnder(strValue)) {
                Utils.makeToast(getApplicationContext(),getString(R.string.trait_error_minimum_value)
                        + ": " + currentTrait.getMinimum());
            }

            removeTrait(trait);
            collectInputView.clear();

            soundHelper.playError();

            return false;
        }

        return true;
    }

    private void setNaText() {
        collectInputView.setText("NA");

        traitLayouts.setNaTraitsText(traitBox.getCurrentFormat());
    }

    private void setNaTextBrapiEmptyField() {
        collectInputView.setHint("NA");

        traitLayouts.setNaTraitsText(traitBox.getCurrentFormat());
    }

    private void initToolbars() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Toolbar toolbarBottom = findViewById(R.id.toolbarBottom);
        toolbarBottom.setNavigationIcon(null);

        String naTts = getString(R.string.act_collect_na_btn_tts);
        String barcodeTts = getString(R.string.act_collect_barcode_btn_tts);
        String deleteTts = getString(R.string.act_collect_delete_btn_tts);

        missingValue = toolbarBottom.findViewById(R.id.missingValue);
        missingValue.setOnClickListener(v -> {
            triggerTts(naTts);
            TraitObject currentTrait = traitBox.getCurrentTrait();
            updateObservation(currentTrait.getTrait(), currentTrait.getFormat(), "NA", null);
            setNaText();
        });

        barcodeInput = toolbarBottom.findViewById(R.id.barcodeInput);
        barcodeInput.setOnClickListener(v -> {
            triggerTts(barcodeTts);
            new IntentIntegrator(CollectActivity.this)
                    .setPrompt(getString(R.string.barcode_scanner_text))
                    .setBeepEnabled(false)
                    .setRequestCode(BARCODE_COLLECT_CODE)
                    .initiateScan();
        });

        deleteValue = toolbarBottom.findViewById(R.id.deleteValue);
        deleteValue.setOnClickListener(v -> {
            boolean status = database.isBrapiSynced(getStudyId(), getObservationUnit(), getTraitName(), getRep());
            // if a brapi observation that has been synced, don't allow deleting
            if (status) {
                if (getTraitFormat().equals("photo")) {
                    // I want to use abstract method
                    PhotoTraitLayout traitPhoto = traitLayouts.getPhotoTrait();
                    traitPhoto.brapiDelete();
                } else {
                    brapiDelete(getTraitName(), false);
                }
            } else {
                traitLayouts.deleteTraitListener(getTraitFormat());
            }

            triggerTts(deleteTts);
        });

    }

    /**
     * Returns the repeated value index as a string depending on settings.
     * Default setting: always return the first available index (because this is what is viewed),
     *      the user can delete the first value, this returns the minimum repeated value.
     * Repeated values setting: return the currently selected repeated value.
     * @return the repeated value index
     */
    @NonNull
    public String getRep() {

        if (collectInputView.isRepeatEnabled()) {

            return collectInputView.getRep(); //gets the selected repeated value index from view

        } else return database.getDefaultRep(getStudyId(), getObservationUnit(), getTraitName());
        //gets the minimum default index
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
    @Override
    public void initWidgets(final boolean rangeSuppress) {
        // Reset dropdowns

        if (!database.isRangeTableEmpty()) {

            Log.d(TAG, "init widgets refreshing info bar");

            refreshInfoBarAdapter();
        }

        traitBox.initTraitDetails();

        // trait is unique, format is not
        String[] traits = database.getVisibleTrait();
        if (traits != null) {
            ArrayAdapter<String> directionArrayAdapter = new ArrayAdapter<>(
                    this, R.layout.custom_spinner_layout, traits);
            directionArrayAdapter
                    .setDropDownViewResource(R.layout.custom_spinner_layout);
            traitBox.initTraitType(directionArrayAdapter, rangeSuppress);

        }
    }

    /**
     * Moves to specific plot/range/plot_id
     * @param command the type of search, search, plot, range, id, barcode or quickgoto
     * @param plotIndices the array of range ids
     * @param range the primary id
     * @param plot the secondary id
     * @param data data to search for
     * @param trait the trait to navigate to
     * @return true if the search was successful, false otherwise
     */
    @Override
    public boolean moveToSearch(
            @NonNull String command,
            int[] plotIndices,
            String range,
            String plot,
            String data,
            int trait) {

        if (plotIndices == null) {
            return false;
        }

        // search moveto
        if (command.equals("search")) {
            for (int j = 1; j <= plotIndices.length; j++) {
                rangeBox.setRangeByIndex(j - 1);

                RangeObject ro = rangeBox.getCRange();

                //issue #634 fix for now to check the search query by plot_id which should be the unique id
                if (Objects.equals(ro.plot_id, searchUnique)) {
                    moveToResultCore(j);
                    return true;
                }
            }
        }

        // new type to skip the toast message and keep previous functionality
        if (command.equals("quickgoto")) {
            for (int j = 1; j <= plotIndices.length; j++) {
                rangeBox.setRangeByIndex(j - 1);

                if (rangeBox.getCRange().range.equals(range) & rangeBox.getCRange().plot.equals(plot)) {
                    moveToResultCore(j);
                    return true;
                }
            }
        }

        //move to plot
        if (command.equals("plot")) {
            for (int j = 1; j <= plotIndices.length; j++) {
                rangeBox.setRangeByIndex(j - 1);

                if (rangeBox.getCRange().plot.equals(data)) {
                    moveToResultCore(j);
                    return true;
                }
            }
        }

        //move to range
        if (command.equals("range")) {
            for (int j = 1; j <= plotIndices.length; j++) {
                rangeBox.setRangeByIndex(j - 1);

                if (rangeBox.getCRange().range.equals(data)) {
                    moveToResultCore(j);
                    return true;
                }
            }
        }

        //move to plot id
        if (command.equals("id") || command.equals("barcode")) {
            int rangeSize = plotIndices.length;
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

        if (!command.equals("quickgoto") && !command.equals("barcode"))
            Utils.makeToast(getApplicationContext(), getString(R.string.main_toolbar_moveto_no_match));

        return false;
    }

    private void moveToResultCore(int j) {
        rangeBox.setPaging(j);

        // Reload traits based on selected plot
        rangeBox.display();

        String pid = rangeBox.getPlotID();

        traitBox.setNewTraits(pid);

        Log.d(TAG, "Move to result core: " + j);

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

        Log.d(TAG, "Move to result core: " + j);

        initWidgets(false);
    }

    @Override
    public void onPause() {

        guiThread.quit();

        // Backup database
        try {

            DocumentFile databaseDir = BaseDocumentTreeUtil.Companion.getDirectory(this, R.string.dir_database);
            if (databaseDir != null) {

                Executor executor = Executors.newFixedThreadPool(2);
                executor.execute(() -> {

                    try {

                        database.exportDatabase(this,"backup");

                    } catch (IOException io) {
                        io.printStackTrace();
                    }

                });
            }

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        geoNavHelper.stopGeoNav();

        gnssThreadHelper.stop();

        //save the last used trait
        if (traitBox.getCurrentTrait() != null)
            ep.edit().putString(GeneralKeys.LAST_USED_TRAIT, traitBox.getCurrentTrait().getTrait()).apply();

        geoNavHelper.stopAverageHandler();

        ep.edit().putInt(GeneralKeys.DATA_LOCK_STATE, dataLocked).apply();

        super.onPause();
    }

    @Override
    public void onDestroy() {

        guiThread.quit();

        //save last plot id
        if (ep.getBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false)) {
            rangeBox.saveLastPlot();
        }

        try {
            ttsHelper.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        getTraitLayout().onExit();

        mUsbCameraHelper.destroy();

        goProWrapper.destroy();

        traitLayoutRefresh();

        gnssThreadHelper.stop();

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!guiThread.isAlive()) {
            try {
                //TODO test with just .run() to avoid exception
                guiThread.start();
            } catch (IllegalThreadStateException e) {
                e.printStackTrace();
            }
        }

        // Update menu item visibility
        if (systemMenu != null) {
            systemMenu.findItem(R.id.help).setVisible(ep.getBoolean(GeneralKeys.TIPS, false));
            systemMenu.findItem(R.id.nextEmptyPlot).setVisible(!ep.getString(GeneralKeys.HIDE_ENTRIES_WITH_DATA_TOOLBAR, "1").equals("1"));
            systemMenu.findItem(R.id.jumpToPlot).setVisible(!ep.getString(GeneralKeys.MOVE_TO_UNIQUE_ID, "1").equals("1"));
            systemMenu.findItem(R.id.datagrid).setVisible(ep.getBoolean(GeneralKeys.DATAGRID_SETTING, false));
        }

        // If reload data is true, it means there was an import operation, and
        // the screen should refresh
        if (reloadData) {
            reloadData = false;
            partialReload = false;

            // displayRange moved to RangeBox#display
            // and display in RangeBox#reload is commented out
            rangeBox.reload();
            traitBox.setPrefixTraits();

            Log.d(TAG, "On resume load data.");

            initWidgets(false);
            traitBox.setSelection(0);

            // try to go to last saved plot
            if (ep.getString(GeneralKeys.LAST_PLOT, null) != null) {
                rangeBox.setAllRangeID();
                int[] rangeID = rangeBox.getRangeID();
                moveToSearch("id", rangeID, null, null, ep.getString(GeneralKeys.LAST_PLOT, null), -1);
            }

        } else if (partialReload) {
            partialReload = false;
            rangeBox.display();
            traitBox.setPrefixTraits();

            Log.d(TAG, "On resume partial reload data.");

            initWidgets(false);

        } else if (searchReload) {
            searchReload = false;
            //rangeBox.resetPaging();
            int[] rangeID = rangeBox.getRangeID();

            moveToSearch("search", rangeID, searchRange, searchPlot, null, -1);
        }

        mPrefs.edit().putBoolean(GeneralKeys.GEONAV_AUTO, false).apply(); //turn off auto nav

        if (mPrefs.getBoolean(GeneralKeys.ENABLE_GEONAV, false)) {

            //setup logger whenever activity resumes
            geoNavHelper.setupGeoNavLogger();

            startGeoNav();
        }

        if (!mSkipLastUsedTrait) {

            mSkipLastUsedTrait = false;

            navigateToLastOpenedTrait();

        }

        dataLocked = ep.getInt(GeneralKeys.DATA_LOCK_STATE, UNLOCKED);

        refreshLock();
    }

    private void startGeoNav() {
        try {
            secureBluetooth.withNearby((adapter) -> {
                geoNavHelper.startGeoNav();
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * LAST_USED_TRAIT is a preference saved in CollectActivity.onPause
     *
     * This function is called to use that preference and navigate to the corresponding trait.
     */
    private void navigateToLastOpenedTrait() {

        //navigate to the last used trait using preferences
        String trait = ep.getString(GeneralKeys.LAST_USED_TRAIT, null);

        navigateToTrait(trait);
    }

    public void navigateToTrait(String trait) {

        if (trait != null) {

            //get all traits, filter the preference trait and check it's visibility
            String[] traits = database.getVisibleTrait();

            try {

                traitBox.setSelection(Arrays.asList(traits).indexOf(trait));

            } catch (NullPointerException e) {

                e.printStackTrace();

            }
        }
    }

    /**
     * Helper function update user data in the memory based hashmap as well as
     * the database
     * @param traitName the trait name
     * @param traitFormat the trait format
     * @param value the new string value to be saved in the database
     * @param nullableRep the repeated value to update, could be null to represent the latest rep value
     */
    public void updateObservation(String traitName, String traitFormat, String value, @Nullable String nullableRep) {

        if (rangeBox.isEmpty()) {
            return;
        }

        traitBox.update(traitName, value);

        String studyId = getStudyId();
        String obsUnit = getObservationUnit();
        String person = ep.getString(GeneralKeys.FIRST_NAME, "") + " " + ep.getString(GeneralKeys.LAST_NAME, "");

        String rep = nullableRep;

        //if not updating a repeated value, get the latest repeated value
        if (nullableRep == null) {

            rep = getRep();
        }

        Observation observation = database.getObservation(studyId, obsUnit, traitName, rep);
        String observationDbId = observation.getDbId();
        OffsetDateTime lastSyncedTime = observation.getLastSyncedTime();

        // Always remove existing trait before inserting again
        // Based on plot_id, prevent duplicates
        database.deleteTrait(studyId, obsUnit, traitName, rep);

        if (!value.isEmpty()) {

            //don't update the database if the value is blank or undesirable
            boolean pass = false;

            if (traitFormat.equals("multicat")
                || CategoricalTraitLayout.isTraitCategorical(traitFormat)) {

                if (value.equals("[]")) {

                    pass = true;
                }
            }

            if (!pass) {
                database.insertObservation(obsUnit, traitName, traitFormat, value, person,
                        getLocationByPreferences(), "", studyId, observationDbId,
                        lastSyncedTime, rep);
            }
        }

        //update the info bar in case a variable is used
        refreshInfoBarAdapter();
        refreshRepeatedValuesToolbarIndicator();
    }

    public void insertRep(String parent, String trait, String value, String rep) {

        String expId = getStudyId();
        String obsUnit = getObservationUnit();
        String person = getPerson();

        database.insertObservation(obsUnit, parent, trait, value, person,
                getLocationByPreferences(), "", expId, null, null, rep);
    }

    public void deleteRep(String trait, String rep) {

        String expId = getStudyId();
        String obsUnit = getObservationUnit();

        database.deleteTrait(expId, obsUnit, trait, rep);
    }

    public String getLocationByPreferences() {

        String expId = Integer.toString(ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));
        String obsUnit = rangeBox.getPlotID();

        return LocationCollectorUtil.Companion
                .getLocationByCollectMode(this, ep, expId, obsUnit, geoNavHelper.getMInternalLocation(), geoNavHelper.getMExternalLocation(), database);
    }

    private void brapiDelete(String parent, Boolean hint) {
        Toast.makeText(getApplicationContext(), getString(R.string.brapi_delete_message), Toast.LENGTH_LONG).show();
        TraitObject trait = traitBox.getCurrentTrait();
        updateObservation(parent, trait.getFormat(), getString(R.string.brapi_na), null);
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

        String exp_id = Integer.toString(ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));
        TraitObject trait = traitBox.getCurrentTrait();
        if (database.isBrapiSynced(exp_id, getObservationUnit(), trait.getTrait(), getRep())) {
            brapiDelete(parent, true);
        } else {
            // Always remove existing trait before inserting again
            // Based on plot_id, prevent duplicate
            traitBox.remove(parent, getObservationUnit(), getRep());
        }
    }

    // for format without specific control
    public void removeTrait() {
        traitBox.remove(getCurrentTrait(), getObservationUnit(), getRep());
        collectInputView.setText("");
    }

    private void customizeToolbarIcons() {
        Set<String> entries = ep.getStringSet(GeneralKeys.TOOLBAR_CUSTOMIZE, new HashSet<>());

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

        systemMenu.findItem(R.id.help).setVisible(ep.getBoolean(GeneralKeys.TIPS, false));
        systemMenu.findItem(R.id.nextEmptyPlot).setVisible(!ep.getString(GeneralKeys.HIDE_ENTRIES_WITH_DATA_TOOLBAR, "1").equals("1"));
        systemMenu.findItem(R.id.jumpToPlot).setVisible(!ep.getString(GeneralKeys.MOVE_TO_UNIQUE_ID, "1").equals("1"));
        systemMenu.findItem(R.id.datagrid).setVisible(ep.getBoolean(GeneralKeys.DATAGRID_SETTING, false));

        //toggle repeated values indicator
        systemMenu.findItem(R.id.action_act_collect_repeated_values_indicator).setVisible(collectInputView.isRepeatEnabled());

        //added in geonav 310 only make goenav switch visible if preference is set
        MenuItem geoNavEnable = systemMenu.findItem(R.id.action_act_collect_geonav_sw);
        geoNavEnable.setVisible(mPrefs.getBoolean(GeneralKeys.ENABLE_GEONAV, false));
//        View actionView = MenuItemCompat.getActionView(geoNavEnable);
//        actionView.setOnClickListener((View) -> onOptionsItemSelected(geoNavEnable));

        customizeToolbarIcons();

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

    private TapTarget collectDataTapTargetView(int id, String title, String desc, int targetRadius) {
        return TapTargetUtil.Companion.getTapTargetSettingsView(this, findViewById(id), title, desc, targetRadius);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Intent intent = new Intent(Intent.ACTION_VIEW);

        final int helpId = R.id.help;
        final int searchId = R.id.search;
        final int resourcesId = R.id.resources;
        final int nextEmptyPlotId = R.id.nextEmptyPlot;
        final int jumpToPlotId = R.id.jumpToPlot;
        final int dataGridId = R.id.datagrid;
        final int lockDataId = R.id.lockData;
        final int summaryId = R.id.summary;
        final int geonavId = R.id.action_act_collect_geonav_sw;
        switch (item.getItemId()) {
            case helpId:
                TapTargetSequence sequence = new TapTargetSequence(this)
                        .targets(collectDataTapTargetView(R.id.act_collect_infobar_rv, getString(R.string.tutorial_main_infobars_title), getString(R.string.tutorial_main_infobars_description),200),
                                collectDataTapTargetView(R.id.traitLeft, getString(R.string.tutorial_main_traits_title), getString(R.string.tutorial_main_traits_description),60),
                                collectDataTapTargetView(R.id.traitType, getString(R.string.tutorial_main_traitlist_title), getString(R.string.tutorial_main_traitlist_description),80),
                                collectDataTapTargetView(R.id.rangeLeft, getString(R.string.tutorial_main_entries_title), getString(R.string.tutorial_main_entries_description),60),
                                collectDataTapTargetView(R.id.valuesPlotRangeHolder, getString(R.string.tutorial_main_navinfo_title), getString(R.string.tutorial_main_navinfo_description),60),
                                collectDataTapTargetView(R.id.traitHolder, getString(R.string.tutorial_main_datacollect_title), getString(R.string.tutorial_main_datacollect_description),200),
                                collectDataTapTargetView(R.id.missingValue, getString(R.string.tutorial_main_na_title), getString(R.string.tutorial_main_na_description),60),
                                collectDataTapTargetView(R.id.deleteValue, getString(R.string.tutorial_main_delete_title), getString(R.string.tutorial_main_delete_description),60)
                        );
                if (systemMenu.findItem(R.id.search).isVisible()) {
                    sequence.target(collectDataTapTargetView(R.id.search, getString(R.string.tutorial_main_search_title), getString(R.string.tutorial_main_search_description),60));
                }
                if (systemMenu.findItem(R.id.resources).isVisible()) {
                    sequence.target(collectDataTapTargetView(R.id.resources, getString(R.string.tutorial_main_resources_title), getString(R.string.tutorial_main_resources_description),60));
                }
                if (systemMenu.findItem(R.id.summary).isVisible()) {
                    sequence.target(collectDataTapTargetView(R.id.summary, getString(R.string.tutorial_main_summary_title), getString(R.string.tutorial_main_summary_description),60));
                }
                if (systemMenu.findItem(R.id.lockData).isVisible()) {
                    sequence.target(collectDataTapTargetView(R.id.lockData, getString(R.string.tutorial_main_lockdata_title), getString(R.string.tutorial_main_lockdata_description),60));
                }

                sequence.start();
                break;
            case searchId:
                intent.setClassName(CollectActivity.this,
                        SearchActivity.class.getName());
                startActivity(intent);
                break;

            case resourcesId:
                DocumentFile dir = BaseDocumentTreeUtil.Companion.getDirectory(this, R.string.dir_resources);
                if (dir != null && dir.exists()) {
                    intent.setClassName(CollectActivity.this, FileExploreActivity.class.getName());
                    intent.putExtra("path", dir.getUri().toString());
                    intent.putExtra("exclude", new String[]{"fieldbook"});
                    intent.putExtra("title", getString(R.string.main_toolbar_resources));
                    startActivityForResult(intent, REQUEST_FILE_EXPLORER_CODE);
                }
                break;
            case nextEmptyPlotId:
                rangeBox.setPaging(rangeBox.movePaging(rangeBox.getPaging(), 1, false, true));
                refreshMain();

                break;
            case jumpToPlotId:
                String moveToUniqueIdValue = ep.getString(GeneralKeys.MOVE_TO_UNIQUE_ID, "");
                if (moveToUniqueIdValue.equals("2")) {
                    moveToPlotID();
                } else if (moveToUniqueIdValue.equals("3")) {
                    new IntentIntegrator(this)
                            .setPrompt(getString(R.string.barcode_scanner_text))
                            .setBeepEnabled(false)
                            .setRequestCode(BARCODE_SEARCH_CODE)
                            .initiateScan();
                }
                break;
            case summaryId:
                showSummary();
                break;
            case dataGridId:
                Intent i = new Intent();
                i.setClassName(CollectActivity.this,
                        DataGridActivity.class.getName());
                i.putExtra("plot_id", rangeBox.getPaging());
                i.putExtra("trait", traitBox.getCurrentTrait().getRealPosition());
                startActivityForResult(i, 2);
                break;
            case lockDataId:
                if (dataLocked == UNLOCKED) dataLocked = LOCKED;
                else if (dataLocked == LOCKED) dataLocked = FROZEN;
                else dataLocked = UNLOCKED;
                ep.edit().putInt(GeneralKeys.DATA_LOCK_STATE, dataLocked).apply();
                lockData();
                break;
            case android.R.id.home:
                finish();
                break;
            /*
             * Toggling the geo nav icon turns the automatic plot navigation on/off.
             * If geonav is enabled, collect activity will auto move to the plot in user's vicinity
             */
            case geonavId:

                Log.d(GEOTAG, "Menu item clicked.");

                dialogGeoNav = new GeoNavCollectDialog(this).create();

                if (!dialogGeoNav.isShowing()) {

                    if (getWindow().isActive()) {

                        try {
                            dialogGeoNav.show();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                return true;

            case R.id.action_act_collect_repeated_values_indicator:

                showMultiMeasureDeleteDialog();

                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showMultiMeasureDeleteDialog() {

        String labelValPref = ep.getString(GeneralKeys.LABELVAL_CUSTOMIZE,"value");

        ObservationModel[] values = database.getRepeatedValues(
                getStudyId(), getObservationUnit(), getTraitName());

        ArrayList<String> is = new ArrayList<>();
        for (ObservationModel m: values) {
            if (!m.getValue().isEmpty()) {
                is.add(m.getValue());
            }
        }

        int size = is.size();

        if (size > 0) {

            String[] items = new String[size];
            boolean[] checked = new boolean[size];

            for (int n = 0; n < size; n++) {

                ObservationModel model = values[n];

                String value = model.getValue();

                if (!value.isEmpty()) {

                    try {

                        ArrayList<BrAPIScaleValidValuesCategories> c = CategoryJsonUtil.Companion.decode(value);

                        value = CategoryJsonUtil.Companion.flattenMultiCategoryValue(c, labelValPref.equals("label"));

                    } catch (Exception ignore) {}

                    items[n] = value;
                    checked[n] = false;
                }
            }

            dialogMultiMeasureDelete = new AlertDialog.Builder(this, R.style.AppAlertDialog)
                    .setTitle(R.string.dialog_multi_measure_delete_title)
                    .setMultiChoiceItems(items, checked, (d, which, isChecked) -> {})
                    .setPositiveButton(R.string.dialog_multi_measure_delete, (d, which) -> {

                        List<ObservationModel> deleteItems = new ArrayList<>();
                        int checkSize = checked.length;
                        for (int j = 0; j < checkSize; j++) {
                            if (checked[j]) {
                                deleteItems.add(values[j]);
                            }
                        }

                        if (!deleteItems.isEmpty()) {

                            showConfirmMultiMeasureDeleteDialog(deleteItems);

                        }
                    })
                    .setNegativeButton(android.R.string.cancel, (d, which) -> {
                        d.dismiss();
                    })
                    .setNeutralButton(R.string.dialog_multi_measure_select_all, (d, which) -> {
                        //Arrays.fill(checked, true);
                    })
                    .create();

            dialogMultiMeasureDelete.setOnShowListener((d) -> {
                AlertDialog ad = (AlertDialog) d;
                ad.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener((v) -> {
                    Arrays.fill(checked, true);
                    ListView lv = ad.getListView();
                    for (int i = 0; i < checked.length; i++) {
                        lv.setItemChecked(i, true);
                    }
                });
            });

            if (!dialogMultiMeasureDelete.isShowing()) {

                dialogMultiMeasureDelete.show();
            }
        } else {

            Toast.makeText(this, R.string.dialog_multi_measure_delete_no_observations, Toast.LENGTH_SHORT).show();

        }
    }

    private void showConfirmMultiMeasureDeleteDialog(List<ObservationModel> models) {

        dialogMultiMeasureConfirmDelete = new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_multi_measure_confirm_delete_title)
            .setPositiveButton(android.R.string.ok, (d, which) -> {
                deleteMultiMeasures(models);
            })
            .setNegativeButton(android.R.string.cancel, (d, which) -> {
                d.dismiss();
            })
            .create();

        if (!dialogMultiMeasureConfirmDelete.isShowing()) {

            dialogMultiMeasureConfirmDelete.show();
        }
    }

    /**
     * Called when multi measure delete dialog deletes an item.
     * @param models the observations to be deleted
     */
    public void deleteMultiMeasures(@NonNull List<ObservationModel> models) {

        for (ObservationModel model : models) {

            deleteRep(model.getObservation_variable_name(), model.getRep());

            ObservationModel[] currentModels = database.getRepeatedValues(getStudyId(), getObservationUnit(), getTraitName());

            if (currentModels.length == 0) {

                collectInputView.setText("");

            } else {

                for (ObservationModel m : currentModels) {
                    try {
                        m.setValue(getTraitLayout().decodeValue(m.getValue()));
                    } catch (Exception ignore) {}
                }

                collectInputView.prepareObservationsExistMode(Arrays.asList(currentModels));

            }

            traitLayoutRefresh();
        }
    }

    @Override
    public void refreshLock() {
        //refresh lock state
        collectInputView.postDelayed(this::lockData, 100);
    }

    /**
     * Given the lock state, changes the ui to allow how data is entered.
     * unlocked, locked, or frozen
     */
    void lockData() {

        int state = ep.getInt(GeneralKeys.DATA_LOCK_STATE, UNLOCKED);

        if (state == LOCKED) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_lock);
            disableDataEntry();
        } else if (state == UNLOCKED) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_unlock);
            enableDataEntry();
        } else {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_lock_clock);
            if (collectInputView.getText().isEmpty()) {
                enableDataEntry();
            } else disableDataEntry();
        }

        TraitObject trait = getCurrentTrait();
        if (trait != null && trait.getTrait() != null) {
            traitLayouts.refreshLock(trait.getFormat());
        }
    }

    public void traitLockData() {
        if (dataLocked == LOCKED) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_lock);
            disableDataEntry();
        } else if (dataLocked == UNLOCKED) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_unlock);
            enableDataEntry();
        } else {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_lock_clock);
            if (collectInputView.getText().isEmpty()) {
                enableDataEntry();
            } else disableDataEntry();
        }
    }

    private void enableDataEntry() {
        missingValue.setEnabled(true);
        deleteValue.setEnabled(true);
        barcodeInput.setEnabled(true);
        traitLayouts.enableViews();
    }

    private void disableDataEntry() {
        missingValue.setEnabled(false);
        deleteValue.setEnabled(false);
        barcodeInput.setEnabled(false);
        traitLayouts.disableViews();
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
                new IntentIntegrator(CollectActivity.this)
                        .setPrompt(getString(R.string.barcode_scanner_text))
                        .setBeepEnabled(false)
                        .setRequestCode(BARCODE_SEARCH_CODE)
                        .initiateScan();
            }
        });

        goToId = builder.create();
        goToId.show();

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

        SummaryFragment fragment = new SummaryFragment();
        fragment.setListener(this);

        isSummaryOpen = true;

        getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();

//        LayoutInflater inflater = this.getLayoutInflater();
//        inflater.inflate(R.layout.fragment_summary, null);
//        View layout = inflater.inflate(R.layout.dialog_summary, null);
//        TextView summaryText = layout.findViewById(R.id.field_name);
//        summaryText.setText(traitBox.createSummaryText(rangeBox.getPlotID()));
//
//        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
//        builder.setTitle(R.string.preferences_appearance_toolbar_customize_summary)
//                .setCancelable(true)
//                .setView(layout);
//
//        builder.setNegativeButton(getString(R.string.dialog_close), new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int i) {
//                dialog.dismiss();
//            }
//        });
//
//        final AlertDialog summaryDialog = builder.create();
//        summaryDialog.show();
//        DialogUtils.styleDialogs(summaryDialog);
//
//        android.view.WindowManager.LayoutParams params2 = summaryDialog.getWindow().getAttributes();
//        params2.width = LayoutParams.MATCH_PARENT;
//        summaryDialog.getWindow().setAttributes(params2);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        String volumeNavigation = ep.getString(GeneralKeys.VOLUME_NAVIGATION, "0");
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (volumeNavigation.equals("1")) {
                    if (action == KeyEvent.ACTION_UP) {
                        traitBox.moveTrait("right");
                    }
                    return true;
                } else if (volumeNavigation.equals("2")) {
                    if (action == KeyEvent.ACTION_UP) {
                        rangeBox.moveEntryRight();
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (volumeNavigation.equals("1")) {
                    if (action == KeyEvent.ACTION_UP) {
                        traitBox.moveTrait("left");
                    }
                    return true;
                } else if (volumeNavigation.equals("2")) {
                    if (action == KeyEvent.ACTION_UP) {
                        rangeBox.moveEntryLeft();
                    }
                    return true;
                }
                return false;
//                else if (event.action == KeyEvent.ACTION_UP
//                    && code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_TAB) {
//
//                inputEditText?.requestFocus()
//            }
            default:

                if (action == KeyEvent.ACTION_UP) {

                    if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_TAB) {

                        collectInputView.requestFocus();

                        return false;
                    }
                }

                return super.dispatchKeyEvent(event);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_FILE_EXPLORER_CODE:
                if (resultCode == RESULT_OK) {
                    try {

                        String resultString = data.getStringExtra(FileExploreActivity.EXTRA_RESULT_KEY);
                        Uri resultUri = Uri.parse(resultString);

                        String suffix = resultString.substring(resultString.lastIndexOf('.') + 1).toLowerCase();

                        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix);
                        Intent open = new Intent(Intent.ACTION_VIEW);
                        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        open.setDataAndType(resultUri, mime);

                        startActivity(open);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

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
                    //select the rep chosen from datagrid
                    if (collectInputView.isRepeatEnabled()) {
                        int rep = data.getIntExtra("rep", -1);
                        collectInputView.navigateToRep(rep);
                    }
                    mSkipLastUsedTrait = true;
                }
                break;
            case BARCODE_SEARCH_CODE:
                if(resultCode == RESULT_OK) {

                    if (geoNavHelper.getSnackbar() != null) geoNavHelper.getSnackbar().dismiss();

                    IntentResult plotSearchResult = IntentIntegrator.parseActivityResult(resultCode, data);
                    inputPlotId = plotSearchResult.getContents();
                    rangeBox.setAllRangeID();
                    int[] rangeID = rangeBox.getRangeID();
                    boolean success = moveToSearch("barcode", rangeID, null, null, inputPlotId, -1);

                    //play success or error sound if the plotId was not found
                    if (success) {
                        soundHelper.playCelebrate();
                    } else {
                        boolean found = false;
                        FieldObject studyObj = null;
                        ObservationUnitModel[] models = database.getAllObservationUnits();
                        for (ObservationUnitModel m : models) {
                            if (m.getObservation_unit_db_id().equals(inputPlotId)) {

                                FieldObject study = database.getFieldObject(m.getStudy_id());
                                if (study != null && study.getExp_name() != null) {
                                    studyObj = study;
                                    found = true;
                                    break;
                                }
                            }
                        }

                        if (found && studyObj.getExp_name() != null && studyObj.getExp_id() != -1) {

                            int studyId = studyObj.getExp_id();
                            String fieldName = studyObj.getExp_name();

                            String msg = getString(R.string.act_collect_barcode_search_exists_in_other_field, fieldName);

                            SnackbarUtils.showNavigateSnack(getLayoutInflater(), findViewById(R.id.traitHolder), msg, R.id.toolbarBottom,8000, null,
                                (v) -> switchField(studyId, null));

                        } else {

                            soundHelper.playError();

                            Utils.makeToast(getApplicationContext(), getString(R.string.main_toolbar_moveto_no_match));

                        }
                    }
                }
                break;
            case BARCODE_COLLECT_CODE:
                if(resultCode == RESULT_OK) {
                    // store barcode value as data
                    IntentResult plotDataResult = IntentIntegrator.parseActivityResult(resultCode, data);
                    String scannedBarcode = plotDataResult.getContents();
                    TraitObject currentTrait = traitBox.getCurrentTrait();
                    BaseTraitLayout currentTraitLayout = traitLayouts.getTraitLayout(currentTrait.getFormat());
                    currentTraitLayout.loadLayout();


                    updateObservation(currentTrait.getTrait(), currentTrait.getFormat(), scannedBarcode, null);
                    currentTraitLayout.loadLayout();
                    validateData();
                }
                break;
            case PhotoTraitLayout.PICTURE_REQUEST_CODE:
                String success = getString(R.string.trait_photo_tts_success);
                String fail = getString(R.string.trait_photo_tts_fail);
                if (resultCode == RESULT_OK) {
                    PhotoTraitLayout traitPhoto = traitLayouts.getPhotoTrait();
                    traitPhoto.makeImage(traitBox.getCurrentTrait(),
                            traitBox.getNewTraits(), resultCode == RESULT_OK);

                    triggerTts(success);
                } else triggerTts(fail);
                break;
        }
    }

    public boolean isTraitBlocked() {
        return traitLayouts.getTraitLayout(getCurrentTrait().getFormat()).block();
    }

    //triggers when pressing the repeated values navigation arrows
    public void traitLayoutRefresh() {
        refreshRepeatedValuesToolbarIndicator();
        traitLayouts.getTraitLayout(getCurrentTrait().getFormat()).refreshLayout(false);
    }

    //triggers when pressing the repeated values add button
    public void traitLayoutRefreshNew() {
        refreshRepeatedValuesToolbarIndicator();
        traitLayouts.getTraitLayout(getCurrentTrait().getFormat()).refreshLayout(true);
    }

    public void refreshRepeatedValuesToolbarIndicator() {

        if (systemMenu != null) {

            MenuItem item = systemMenu.findItem(R.id.action_act_collect_repeated_values_indicator);

            if (collectInputView.isRepeatEnabled()) {

                item.setVisible(true);

                ObservationModel[] values = database.getRepeatedValues(getStudyId(), getObservationUnit(), getTraitName());

                int n = values.length;

                if (n == 1) {
                    if (values[0].getValue().isEmpty()) {
                        n = 0;
                    }
                } else if (n > 1) {
                    if (values[n-1].getValue().isEmpty()) {
                        n--;
                    }
                }

                switch (n) {
                    case 0:
                        item.setIcon(R.drawable.numeric_0_box);
                        break;
                    case 1:
                        item.setIcon(R.drawable.numeric_1_box);
                        break;
                    case 2:
                        item.setIcon(R.drawable.numeric_2_box_multiple);
                        break;
                    case 3:
                        item.setIcon(R.drawable.numeric_3_box_multiple);
                        break;
                    case 4:
                        item.setIcon(R.drawable.numeric_4_box_multiple);
                        break;
                    case 5:
                        item.setIcon(R.drawable.numeric_5_box_multiple);
                        break;
                    case 6:
                        item.setIcon(R.drawable.numeric_6_box_multiple);
                        break;
                    case 7:
                        item.setIcon(R.drawable.numeric_7_box_multiple);
                        break;
                    case 8:
                        item.setIcon(R.drawable.numeric_8_box_multiple);
                        break;
                    case 9:
                        item.setIcon(R.drawable.numeric_9_box_multiple);
                        break;
                    default:
                        item.setIcon(R.drawable.numeric_9_plus_box_multiple);
                        break;
                }

            } else {

                item.setVisible(false);

            }
        }
    }

    @Override
    public void onBackPressed() {

        int count = getSupportFragmentManager().getBackStackEntryCount();

        if (count == 0) {

            finish();

        } else {
            getSupportFragmentManager().popBackStack();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @NonNull
    @Override
    public TraitBoxView getTraitBox() {
        return traitBox;
    }

    @Override
    public boolean existsTrait(final int ID) {
        final TraitObject trait = traitBox.getCurrentTrait();
        if (trait != null) {
            return database.getTraitExists(ID, trait.getTrait(), trait.getFormat());
        } else return false;
    }

    /**
     * Iterates over all traits for the given ID and returns the trait's index which is missing
     * @param traitIndex current trait index
     * @param ID the plot identifier
     * @return index of the trait missing or -1 if all traits exist
     */
    @Override
    public int existsAllTraits(final int traitIndex, final int ID) {
        final String[] traits = database.getVisibleTrait();
        final String[] formats = database.getFormat();
        for (int i = 0; i < traits.length; i++) {
            if (i != traitIndex
                    && !database.getTraitExists(ID, traits[i], formats[i])) return i;
        }
        return -1;
    }

    @NonNull
    @Override
    public List<Integer> getNonExistingTraits(final int ID) {
        final String[] traits = database.getVisibleTrait();
        final String[] formats = database.getFormat();
        final ArrayList<Integer> indices = new ArrayList<>();
        for (int i = 0; i < traits.length; i++) {
            if (!database.getTraitExists(ID, traits[i], formats[i]))
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

    @NonNull
    public RangeBoxView getRangeBox() {
        return rangeBox;
    }

    public RangeObject getCRange() {
        return rangeBox.getCRange();
    }

    @Override
    public CollectInputView getInputView() {
        return collectInputView;
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

    /**
     * Lock data if the lock feature is on, or if data is frozen then check
     * if the current value is empty.
     */
    public boolean isDataLocked() {
        return (dataLocked == LOCKED)
                || (!collectInputView.getText().isEmpty() && dataLocked == FROZEN);
    }

    public boolean isFrozen() {
        return dataLocked == FROZEN;
    }

    public boolean isLocked() {
        return dataLocked == LOCKED;
    }

    public SharedPreferences getPreference() {
        return ep;
    }

    @Override
    public boolean isCyclingTraitsAdvances() {
        return ep.getBoolean(GeneralKeys.CYCLING_TRAITS_ADVANCES, false);
    }

    public boolean isReturnFirstTrait() {
        return ep.getBoolean(GeneralKeys.RETURN_FIRST_TRAIT, false);
    }

    /**
     * Inserts a user observation whenever a label is printed.
     * See ResultReceiver onReceiveResult in LabelPrintLayout
     * @param size: The size of the label. e.g "2 x 4 detailed"
     */
    public void insertPrintObservation(String size) {

        TraitObject trait = getCurrentTrait();

        String studyId = Integer.toString(ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));

        database.insertObservation(rangeBox.getPlotID(), trait.getFormat(), trait.getTrait(), size,
                ep.getString(GeneralKeys.FIRST_NAME, "") + " " + ep.getString(GeneralKeys.LAST_NAME, ""),
                getLocationByPreferences(), "", studyId, "",
                null, null);

    }

    @Nullable
    @Override
    public UsbCameraHelper getCameraHelper() {
        return mUsbCameraHelper;
    }

    @Override
    public void onSummaryDestroy() {
        isSummaryOpen = false;
    }

    public boolean isSummaryFragmentOpen() {
        return isSummaryOpen;
    }

    @NonNull
    @Override
    public DataHelper getDatabase() {
        return database;
    }

    @Override
    public void resetGeoNavMessages() {
        geoNavHelper.resetGeoNavMessages();
    }

    @Override
    public void callFinish() {
        finish();
    }

    @Override
    public void cancelAndFinish() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @NonNull
    @Override
    public SharedPreferences getPreferences() {
        return ep;
    }

    @NonNull
    @Override
    public LayoutCollections getTraitLayouts() {
        return traitLayouts;
    }

    @NonNull
    @Override
    public SecureBluetoothActivityImpl getSecurityChecker() {
        return secureBluetooth;
    }

    @Nullable
    @Override
    public Handler getAverageHandler() {
        return geoNavHelper.getAverageHandler();
    }

    @Override
    public void inflateTrait(@NonNull BaseTraitLayout layout) {
        getTraitLayout().onExit();
        View v = LayoutInflater.from(this).inflate(layout.layoutId(), null);
        LinearLayout holder = findViewById(R.id.traitHolder);
        holder.removeAllViews();
        holder.addView(v);
        layout.init(this);
        v.setVisibility(View.VISIBLE);
    }

    @Override
    public void onInfoBarClicked(int position) {

        infoBarHelper.showInfoBarChoiceDialog(position);

    }

    @NonNull
    @Override
    public FieldSwitcher getFieldSwitcher() {
        return fieldSwitcher;
    }

    @NonNull
    @Override
    public SoundHelperImpl getSoundHelper() {
        return soundHelper;
    }

    @NonNull
    @Override
    public GeoNavHelper getGeoNavHelper() {
        return geoNavHelper;
    }

    @NonNull
    @Override
    public GnssThreadHelper getGnssThreadHelper() {
        return gnssThreadHelper;
    }

    @NonNull
    @Override
    public GoProWrapper wrapper() {
        return goProWrapper;
    }

    @NonNull
    @Override
    public SecureBluetooth advisor() {
        return secureBluetooth;
    }

    @Override
    public String queryForLabelValue(
            String plotId, String label, Boolean isAttribute
    ) {
        Context context = this;

        String dataMissingString = context.getString(R.string.main_infobar_data_missing);

        if (isAttribute) {

            String[] values = database.getDropDownRange(label, plotId);
            if (values == null || values.length == 0) {
                return dataMissingString;
            } else {
                if (label.equals("geo_coordinates") && JsonUtil.Companion.isJsonValid(values[0])) {

                    return GeoJsonUtil.Companion.decode(values[0]).toCoordinateString(";");

                } else {

                    return values[0];

                }
            }

        } else {

            String value = database.getUserDetail(plotId).get(label);
            if (value == null) {
                value = dataMissingString;
            }

            try {

                String labelValPref = ((CollectActivity) context).getPreferences()
                        .getString(GeneralKeys.LABELVAL_CUSTOMIZE, "value");
                if (labelValPref == null) {
                    labelValPref = "value";
                }

                StringJoiner joiner = new StringJoiner(":");
                ArrayList<BrAPIScaleValidValuesCategories> scale = CategoryJsonUtil.Companion.decode(value);
                for (BrAPIScaleValidValuesCategories s : scale) {
                    if ("label".equals(labelValPref)) {
                        joiner.add(s.getLabel());
                    } else {
                        joiner.add(s.getValue());
                    }
                }

                return joiner.toString();

            } catch (Exception ignore) {
                return value;
            }
        }
    }

    @Override
    public ArrayList<String> getGeoNavPopupSpinnerItems () {
        //query database for attributes/traits to use
        try {
            List<String> attributes = Arrays.asList(getDatabase().getAllObservationUnitAttributeNames(Integer.parseInt(getStudyId())));
            TraitObject[] traits = getDatabase().getAllTraitObjects().toArray(new TraitObject[0]);

            ArrayList<TraitObject> visibleTraits = new ArrayList<>();
            for (TraitObject traitObject : traits) {
                if (traitObject.getVisible()) {
                    visibleTraits.add(traitObject);
                }
            }

            // Map traits to their names
            List<String> traitNames = new ArrayList<>();
            for (TraitObject traitObject : visibleTraits) {
                traitNames.add(traitObject.getTrait());
            }

            // Combine attributes and trait names
            ArrayList<String> result = new ArrayList<>(attributes);
            result.addAll(traitNames);

            return result;
        } catch (Exception e) {
            Log.d(TAG, "Error occurred when querying for attributes in GeoNavCollectDialog.");
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
    
    @NonNull
    @Override
    public VibrateUtil getVibrator() {
        return vibrator;
    }

    @NonNull
    @Override
    public Context getContext() {
        return this;
    }

    public void showLocationPrecisionLossDialog() {

        if (getWindow().isActive()) {

            try {

                if (dialogPrecisionLoss != null) {
                    dialogPrecisionLoss.dismiss();
                }

                dialogPrecisionLoss = new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_geonav_precision_loss_title))
                        .setMessage(getString(R.string.dialog_geonav_precision_loss_msg))
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            dialog.dismiss();
                        })
                        .show();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @NonNull
    @Override
    public GPSTracker getGps() {
        return gps;
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {

        TraitObject trait = getCurrentTrait();

        if (trait != null) {

            if (trait.getFormat().equals("gnss")) {

                ((GNSSTraitLayout) traitLayouts.getTraitLayout("gnss"))
                        .onLocationChanged(location);

            }
        }
    }

    @Override
    public Location getLocation() {

        if (gps == null) return null;

        return gps.getLocation(0, 0);
    }
}