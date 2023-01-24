package com.fieldbook.tracker.activities;


import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
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
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.adapters.InfoBarAdapter;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.dao.ObservationUnitDao;
import com.fieldbook.tracker.database.dao.StudyDao;
import com.fieldbook.tracker.database.models.ObservationUnitModel;
import com.fieldbook.tracker.interfaces.CollectController;
import com.fieldbook.tracker.interfaces.CollectRangeController;
import com.fieldbook.tracker.interfaces.CollectTraitController;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.GeoNavHelper;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.traits.BaseTraitLayout;
import com.fieldbook.tracker.traits.LayoutCollections;
import com.fieldbook.tracker.traits.PhotoTraitLayout;
import com.fieldbook.tracker.utilities.DialogUtils;
import com.fieldbook.tracker.utilities.LocationCollectorUtil;
import com.fieldbook.tracker.utilities.SnackbarUtils;
import com.fieldbook.tracker.utilities.Utils;
import com.fieldbook.tracker.views.RangeBoxView;
import com.fieldbook.tracker.views.TraitBoxView;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

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
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * All main screen logic resides here
 */

@AndroidEntryPoint
@SuppressLint("ClickableViewAccessibility")
public class CollectActivity extends AppCompatActivity
        implements UsbCameraInterface,
        SummaryFragment.SummaryOpenListener,
        CollectController,
        CollectRangeController,
        CollectTraitController {

    public static final int REQUEST_FILE_EXPLORER_CODE = 1;
    public static final int BARCODE_COLLECT_CODE = 99;
    public static final int BARCODE_SEARCH_CODE = 98;

    @Inject
    DataHelper database;

    @Inject
    GeoNavHelper geoNavHelper;

    public static boolean searchReload;
    public static String searchRange;
    public static String searchPlot;
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
    /**
     * Trait-related elements
     */
    private EditText etCurVal;
    private final HandlerThread guiThread = new HandlerThread("ui");

    public Handler myGuiHandler;

    private SharedPreferences mPrefs;
    private TextWatcher cvText;

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

    public void triggerTts(String text) {
        if (ep.getBoolean(GeneralKeys.TTS_LANGUAGE_ENABLED, false)) {
            ttsHelper.speak(text);
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        loadScreen();

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

    private void initCurrentVals() {
        // Current value display
        etCurVal = findViewById(R.id.etCurVal);

        etCurVal.setOnEditorActionListener((exampleView, actionId, event) -> {
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN) {
                rangeBox.rightClick();
                return true;
            }

            return false;
        });

        // Validates the text entered for text format
        cvText = new TextWatcher() {
            public void afterTextChanged(Editable en) {
                final TraitObject trait = traitBox.getCurrentTrait();
                if (en.toString().length() > 0) {
                    if (traitBox.existsNewTraits() & trait != null) {
                        triggerTts(en.toString());
                        updateTrait(trait.getTrait(), trait.getFormat(), en.toString());
                    }
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

        //lock = new Object();

        infoBarAdapter = new InfoBarAdapter(this, ep.getInt(GeneralKeys.INFOBAR_NUMBER, 2), (RecyclerView) findViewById(R.id.selectorList));

        traitLayouts = new LayoutCollections(this);
        traitBox = findViewById(R.id.act_collect_trait_box);
        rangeBox = findViewById(R.id.act_collect_range_box);
        initCurrentVals();
    }

    @Override
    public void refreshMain() {
        rangeBox.saveLastPlot();
        rangeBox.refresh();
        traitBox.setNewTraits(rangeBox.getPlotID());

        initWidgets(true);

        refreshLock();
    }

    @Override
    public void playSound(String sound) {
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
    @Override
    public boolean validateData() {
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

        String naTts = getString(R.string.act_collect_na_btn_tts);
        String barcodeTts = getString(R.string.act_collect_barcode_btn_tts);
        String deleteTts = getString(R.string.act_collect_delete_btn_tts);

        missingValue = toolbarBottom.findViewById(R.id.missingValue);
        missingValue.setOnClickListener(v -> {
            triggerTts(naTts);
            TraitObject currentTrait = traitBox.getCurrentTrait();
            updateTrait(currentTrait.getTrait(), currentTrait.getFormat(), "NA");
            setNaText();
        });

        barcodeInput = toolbarBottom.findViewById(R.id.barcodeInput);
        barcodeInput.setOnClickListener(v -> {
            triggerTts(barcodeTts);
            new IntentIntegrator(CollectActivity.this)
                    .setPrompt(getString(R.string.main_barcode_text))
                    .setBeepEnabled(false)
                    .setRequestCode(BARCODE_COLLECT_CODE)
                    .initiateScan();
        });

        deleteValue = toolbarBottom.findViewById(R.id.deleteValue);
        deleteValue.setOnClickListener(v -> {
            // if a brapi observation that has been synced, don't allow deleting
            String exp_id = Integer.toString(ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));
            TraitObject currentTrait = traitBox.getCurrentTrait();
            if (database.isBrapiSynced(exp_id, rangeBox.getPlotID(), currentTrait.getTrait())) {
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

            triggerTts(deleteTts);
        });

    }

    // This is central to the application
    // Calling this function resets all the controls for traits, and picks one
    // to show based on the current trait data
    @Override
    public void initWidgets(final boolean rangeSuppress) {
        // Reset dropdowns

        if (!database.isRangeTableEmpty()) {
            String plotID = rangeBox.getPlotID();
            infoBarAdapter.configureDropdownArray(plotID);
        }

        traitBox.initTraitDetails();

        // trait is unique, format is not
        String[] traits = database.getVisibleTrait();
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

                if (rangeBox.getCRange().range.equals(range) & rangeBox.getCRange().plot.equals(plot)) {
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

        updateLastOpenedTime();

        geoNavHelper.stopGeoNav();

        //save the last used trait
        if (traitBox.getCurrentTrait() != null)
            ep.edit().putString(GeneralKeys.LAST_USED_TRAIT, traitBox.getCurrentTrait().getTrait()).apply();

        geoNavHelper.stopAverageHandler();

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

        mUsbCameraHelper.destroy();

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!guiThread.isAlive()) {
            try {
                guiThread.start();
            } catch (IllegalThreadStateException e) {
                e.printStackTrace();
            }
        }

        // Update menu item visibility
        if (systemMenu != null) {
            systemMenu.findItem(R.id.help).setVisible(ep.getBoolean(GeneralKeys.TIPS, false));
            systemMenu.findItem(R.id.jumpToPlot).setVisible(ep.getBoolean(GeneralKeys.UNIQUE_TEXT, false));
            systemMenu.findItem(R.id.nextEmptyPlot).setVisible(!ep.getString(GeneralKeys.HIDE_ENTRIES_WITH_DATA_TOOLBAR, "1").equals("1"));
            systemMenu.findItem(R.id.barcodeScan).setVisible(ep.getBoolean(GeneralKeys.UNIQUE_CAMERA, false));
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

            secureBluetooth.withNearby((adapter) -> {
                geoNavHelper.startGeoNav();
                return null;
            });
        }

        checkLastOpened();

        if (!mSkipLastUsedTrait) {

            navigateToLastOpenedTrait();

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
     * Simple function that checks if the collect activity was opened >24hrs ago.
     * If the condition is met, it asks the user to reenter the collector id.
     */
    private void checkLastOpened() {

        long lastOpen = ep.getLong(GeneralKeys.LAST_TIME_OPENED, 0L);
        long systemTime = System.nanoTime();

        long nanosInOneDay = (long) 1e9*3600*24;

        if (lastOpen != 0L && systemTime - lastOpen > nanosInOneDay) {

            boolean verify = ep.getBoolean(GeneralKeys.VERIFY_USER, true);

            if (verify) {

                String firstName = ep.getString(GeneralKeys.FIRST_NAME,"");
                String lastName = ep.getString(GeneralKeys.LAST_NAME,"");
                if(firstName.length() > 0 || lastName.length() > 0) {
                    //person presumably has been set
                    showAskCollectorDialog(getString(R.string.activity_collect_dialog_verify_collector) + " " + firstName + " " + lastName + "?",
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
        ep.edit().putLong(GeneralKeys.LAST_TIME_OPENED, System.nanoTime()).apply();
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
                    ep.edit().putBoolean(GeneralKeys.VERIFY_USER, false).apply();
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
        String expId = Integer.toString(ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));
        String obsUnit = rangeBox.getPlotID();

        Observation observation = database.getObservation(expId, obsUnit, parent);
        String observationDbId = observation.getDbId();
        OffsetDateTime lastSyncedTime = observation.getLastSyncedTime();

        // Always remove existing trait before inserting again
        // Based on plot_id, prevent duplicates
        database.deleteTrait(expId, obsUnit, parent);

        database.insertUserTraits(rangeBox.getPlotID(), parent, trait, value,
                ep.getString(GeneralKeys.FIRST_NAME, "") + " " + ep.getString(GeneralKeys.LAST_NAME, ""),
                getLocationByPreferences(), "", expId, observationDbId,
                lastSyncedTime);

        //update the info bar in case a variable is used
        infoBarAdapter.notifyItemRangeChanged(0, infoBarAdapter.getItemCount());
    }

    public String getLocationByPreferences() {

        String expId = Integer.toString(ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));
        String obsUnit = rangeBox.getPlotID();

        return LocationCollectorUtil.Companion
                .getLocationByCollectMode(this, ep, expId, obsUnit, geoNavHelper.getMInternalLocation(), geoNavHelper.getMExternalLocation());
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

        String exp_id = Integer.toString(ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));
        TraitObject trait = traitBox.getCurrentTrait();
        if (database.isBrapiSynced(exp_id, rangeBox.getPlotID(), trait.getTrait())) {
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
        systemMenu.findItem(R.id.jumpToPlot).setVisible(ep.getBoolean(GeneralKeys.UNIQUE_TEXT, false));
        systemMenu.findItem(R.id.nextEmptyPlot).setVisible(!ep.getString(GeneralKeys.HIDE_ENTRIES_WITH_DATA_TOOLBAR, "1").equals("1"));
        systemMenu.findItem(R.id.barcodeScan).setVisible(ep.getBoolean(GeneralKeys.UNIQUE_CAMERA, false));
        systemMenu.findItem(R.id.datagrid).setVisible(ep.getBoolean(GeneralKeys.DATAGRID_SETTING, false));

        //added in geonav 310 only make goenav switch visible if preference is set
        MenuItem geoNavEnable = systemMenu.findItem(R.id.action_act_collect_geonav_sw);
        geoNavEnable.setVisible(mPrefs.getBoolean(GeneralKeys.ENABLE_GEONAV, false));
//        View actionView = MenuItemCompat.getActionView(geoNavEnable);
//        actionView.setOnClickListener((View) -> onOptionsItemSelected(geoNavEnable));

        customizeToolbarIcons();

        lockData();

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

        final int helpId = R.id.help;
        final int searchId = R.id.search;
        final int resourcesId = R.id.resources;
        final int nextEmptyPlotId = R.id.nextEmptyPlot;
        final int jumpToPlotId = R.id.jumpToPlot;
        final int barcodeScanId = R.id.barcodeScan;
        final int dataGridId = R.id.datagrid;
        final int lockDataId = R.id.lockData;
        final int summaryId = R.id.summary;
        final int geonavId = R.id.action_act_collect_geonav_sw;
        switch (item.getItemId()) {
            case helpId:
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
                moveToPlotID();
                break;
            case barcodeScanId:
                new IntentIntegrator(this)
                        .setPrompt(getString(R.string.main_barcode_text))
                        .setBeepEnabled(false)
                        .setRequestCode(BARCODE_SEARCH_CODE)
                        .initiateScan();
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

                geoNavHelper.setMGeoNavActivated(!geoNavHelper.getMGeoNavActivated());
                MenuItem navItem = systemMenu.findItem(R.id.action_act_collect_geonav_sw);
                if (geoNavHelper.getMGeoNavActivated()) {

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

    @Override
    public void refreshLock() {
        //refresh lock state
        etCurVal.postDelayed(this::lockData, 100);
    }

    /**
     * Given the lock state, changes the ui to allow how data is entered.
     * unlocked, locked, or frozen
     */
    void lockData() {
        if (dataLocked == LOCKED) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_lock);
            disableDataEntry();
        } else if (dataLocked == UNLOCKED) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_unlock);
            enableDataEntry();
        } else {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_lock_clock);
            if (etCurVal.getText().toString().isEmpty()) {
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
            if (etCurVal.getText().toString().isEmpty()) {
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
                        .setPrompt(getString(R.string.main_barcode_text))
                        .setBeepEnabled(false)
                        .setRequestCode(BARCODE_SEARCH_CODE)
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
                        playSound("hero_simple_celebration");
                    } else {
                        boolean found = false;
                        FieldObject studyObj = null;
                        ObservationUnitModel[] models = ObservationUnitDao.Companion.getAll();
                        for (ObservationUnitModel m : models) {
                            if (m.getObservation_unit_db_id().equals(inputPlotId)) {

                                FieldObject study = StudyDao.Companion.getFieldObject(m.getStudy_id());
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

                            SnackbarUtils.showNavigateSnack(getLayoutInflater(), findViewById(R.id.traitHolder), msg, 8000, null,
                                (v) -> {

                                    //updates obs. range view in database
                                    database.switchField(studyId);

                                    //refresh collect activity UI
                                    rangeBox.reload();
                                    rangeBox.refresh();
                                    initWidgets(false);

                                    //navigate to the plot
                                    moveToSearch("barcode", rangeID, null, null, inputPlotId, -1);

                                    //update selected item in field adapter using preference
                                    ep.edit().putString(GeneralKeys.FIELD_FILE, fieldName).apply();
                                    ep.edit().putInt(GeneralKeys.SELECTED_FIELD_ID, studyId).apply();

                                    playSound("hero_simple_celebration");
                                });

                        } else {

                            playSound("alert_error");

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


                    updateTrait(currentTrait.getTrait(), currentTrait.getFormat(), scannedBarcode);
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

    @NonNull
    public EditText getEtCurVal() {
        return etCurVal;
    }

    @NonNull
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

    /**
     * Lock data if the lock feature is on, or if data is frozen then check
     * if the current value is empty.
     */
    public boolean isDataLocked() {
        return (dataLocked == LOCKED)
                || (!etCurVal.getText().toString().isEmpty() && dataLocked == FROZEN);
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

    /**
     * Inserts a user observation whenever a label is printed.
     * See ResultReceiver onReceiveResult in LabelPrintLayout
     * @param size: The size of the label. e.g "2 x 4 detailed"
     */
    public void insertPrintObservation(String size) {

        TraitObject trait = getCurrentTrait();

        String studyId = Integer.toString(ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));

        database.insertUserTraits(rangeBox.getPlotID(), trait.getFormat(), trait.getTrait(), size,
                ep.getString(GeneralKeys.FIRST_NAME, "") + " " + ep.getString(GeneralKeys.LAST_NAME, ""),
                getLocationByPreferences(), "", studyId, "",
                null);

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

    @Override
    public void setEtCurVal(@NonNull EditText editText) {
        etCurVal = editText;
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

    @NonNull
    @Override
    public HandlerThread getAverageHandler() {
        return geoNavHelper.getMAverageHandler();
    }

}