package com.fieldbook.tracker.activities;

import static com.fieldbook.tracker.ui.MediaViewerActivity.EXTRA_TRAIT_DB_ID;

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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.adapters.AttributeAdapter;
import com.fieldbook.tracker.adapters.InfoBarAdapter;
import com.fieldbook.tracker.adapters.TraitsStatusAdapter;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.dao.spectral.DeviceDao;
import com.fieldbook.tracker.database.dao.spectral.ProtocolDao;
import com.fieldbook.tracker.database.dao.spectral.SpectralDao;
import com.fieldbook.tracker.database.dao.spectral.UriDao;
import com.fieldbook.tracker.database.factory.SpectralViewModelFactory;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.database.models.ObservationUnitModel;
import com.fieldbook.tracker.database.repository.SpectralRepository;
import com.fieldbook.tracker.database.viewmodels.SpectralViewModel;
import com.fieldbook.tracker.devices.camera.UsbCameraApi;
import com.fieldbook.tracker.devices.camera.GoProApi;
import com.fieldbook.tracker.devices.camera.CanonApi;
import com.fieldbook.tracker.dialogs.GeoNavCollectDialog;
import com.fieldbook.tracker.dialogs.InvalidValueDialog;
import com.fieldbook.tracker.dialogs.ObservationMetadataFragment;
import com.fieldbook.tracker.dialogs.SearchDialog;
import com.fieldbook.tracker.fragments.CropImageFragment;
import com.fieldbook.tracker.interfaces.CollectController;
import com.fieldbook.tracker.interfaces.CollectRangeController;
import com.fieldbook.tracker.interfaces.CollectTraitController;
import com.fieldbook.tracker.interfaces.FieldSwitcher;
import com.fieldbook.tracker.interfaces.GeoNavController;
import com.fieldbook.tracker.location.GPSTracker;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.InfoBarModel;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.PreferenceKeys;
import com.fieldbook.tracker.preferences.enums.BarcodeScanningOptions;
import com.fieldbook.tracker.traits.AbstractCameraTrait;
import com.fieldbook.tracker.traits.SpectralTraitLayout;
import com.fieldbook.tracker.traits.formats.Formats;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.traits.AudioTraitLayout;
import com.fieldbook.tracker.traits.BaseTraitLayout;
import com.fieldbook.tracker.traits.CanonTraitLayout;
import com.fieldbook.tracker.traits.CategoricalTraitLayout;
import com.fieldbook.tracker.traits.GNSSTraitLayout;
import com.fieldbook.tracker.traits.LayoutCollections;
import com.fieldbook.tracker.traits.PhotoTraitLayout;
import com.fieldbook.tracker.traits.formats.Scannable;
import com.fieldbook.tracker.traits.formats.TraitFormat;
import com.fieldbook.tracker.traits.formats.coders.StringCoder;
import com.fieldbook.tracker.traits.formats.presenters.ValuePresenter;
import com.fieldbook.tracker.utilities.CameraXFacade;
import com.fieldbook.tracker.utilities.CategoryJsonUtil;
import com.fieldbook.tracker.utilities.DocumentTreeUtil;
import com.fieldbook.tracker.utilities.FfmpegHelper;
import com.fieldbook.tracker.utilities.FieldAudioHelper;
import com.fieldbook.tracker.utilities.FieldSwitchImpl;
import com.fieldbook.tracker.utilities.FileUtil;
import com.fieldbook.tracker.utilities.FuzzySearch;
import com.fieldbook.tracker.utilities.GeoJsonUtil;
import com.fieldbook.tracker.utilities.GeoNavHelper;
import com.fieldbook.tracker.utilities.GnssThreadHelper;
import com.fieldbook.tracker.utilities.InfoBarHelper;
import com.fieldbook.tracker.utilities.InsetHandler;
import com.fieldbook.tracker.utilities.JsonUtil;
import com.fieldbook.tracker.utilities.KeyboardListenerHelper;
import com.fieldbook.tracker.utilities.LocationCollectorUtil;
import com.fieldbook.tracker.utilities.MediaKeyCodeActionHelper;
import com.fieldbook.tracker.utilities.NixSensorHelper;
import com.fieldbook.tracker.utilities.SensorHelper;
import com.fieldbook.tracker.utilities.SnackbarUtils;
import com.fieldbook.tracker.utilities.SoundHelperImpl;
import com.fieldbook.tracker.utilities.TapTargetUtil;
import com.fieldbook.tracker.utilities.Utils;
import com.fieldbook.tracker.utilities.VerifyPersonHelper;
import com.fieldbook.tracker.utilities.VibrateUtil;
import com.fieldbook.tracker.utilities.WifiHelper;
import com.fieldbook.tracker.utilities.connectivity.BLEScanner;
import com.fieldbook.tracker.utilities.connectivity.GreenSeekerGattManager;
import com.fieldbook.tracker.utilities.connectivity.ScaleGattManager;
import com.fieldbook.tracker.views.CollectInputView;
import com.fieldbook.tracker.views.RangeBoxView;
import com.fieldbook.tracker.views.TraitBoxView;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.firebase.crashlytics.CustomKeysAndValues;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.serenegiant.widget.UVCCameraTextureView;

import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.phenoapps.interfaces.security.SecureBluetooth;
import org.phenoapps.security.SecureBluetoothActivityImpl;
import org.phenoapps.utils.BaseDocumentTreeUtil;
import org.phenoapps.utils.SoftKeyboardUtil;
import org.phenoapps.utils.TextToSpeechHelper;
import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/**
 * All main screen logic resides here
 */

@AndroidEntryPoint
@SuppressLint("ClickableViewAccessibility")
public class CollectActivity extends ThemedActivity
        implements CollectController,
        CollectRangeController,
        CollectTraitController,
        GeoNavController,
        InfoBarAdapter.InfoBarController,
        GPSTracker.GPSTrackerListener,
        SearchDialog.onSearchResultsClickedListener,
        SensorHelper.RelativeRotationListener,
        SensorHelper.GravityRotationListener {

    public static final int REQUEST_FILE_EXPLORER_CODE = 1;
    public static final int REQUEST_CROP_IMAGE_CODE = 101;
    public static final int REQUEST_CROP_FINISHED_CODE = 103;
    public static final int REQUEST_MEDIA_CODE = 102;
    // pending media values used when we start a crop activity and need to show the confirm dialog
    private String pendingMediaType = null;
    private String pendingMediaPath = null;
    public static final int BARCODE_SEARCH_CODE = 98;

    private final HandlerThread gnssRawLogHandlerThread = new HandlerThread("log");

    private GeoNavHelper geoNavHelper;

    @Inject
    SensorHelper sensorHelper;

    @Inject
    UsbCameraApi usbCameraApi;

    @Inject
    SharedPreferences preferences;

    @Inject
    FfmpegHelper ffmpegHelper;

    @Inject
    GoProApi goProApi;

    @Inject
    WifiHelper wifiHelper;

    @Inject
    CanonApi canonApi;

    @Inject
    KeyboardListenerHelper keyboardListenerHelper;

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
    FieldAudioHelper fieldAudioHelper;

    @Inject
    FieldSwitchImpl fieldSwitcher;

    @Inject
    SoundHelperImpl soundHelper;

    @Inject
    CameraXFacade cameraXFacade;

    @Inject
    NixSensorHelper nixSensorHelper;

    @Inject
    BLEScanner bleScanner;

    @Inject
    GreenSeekerGattManager greenSeekerGattManager;

    @Inject
    ScaleGattManager scaleGattManager;

    @Inject
    FuzzySearch fuzzySearch;

    private SpectralViewModel spectralViewModel;

    //used to track rotation relative to device
    private SensorHelper.RotationModel rotationModel = null;
    private SensorHelper.RotationModel gravityRotationModel = null;

    private GPSTracker gps;

    public static boolean searchReload;
    public static String searchRange;
    public static String searchPlot;
    public static String searchUnique;
    public static boolean reloadData;
    public static boolean partialReload;
    public static String TAG = "Field Book";
    public static String GEOTAG = "GeoNav";

    UVCCameraTextureView uvcView;

    ImageButton deleteValue;
    ImageButton missingValue;
    ImageButton barcodeInput;

    /**
     * Trait layouts
     */
    LayoutCollections traitLayouts;
    private String inputPlotId = "";
    private AlertDialog goToId;
    private final Object lock = new Object();

    /**
     * Main screen elements
     */
    private Menu systemMenu;
    private Toolbar toolbar;
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

    public int numNixInternetWarnings = 0;

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

    private SecureBluetoothActivityImpl secureBluetooth;

    /**
     * Multi Measure delete dialogs
     */
    private AlertDialog dialogMultiMeasureDelete;
    private AlertDialog dialogMultiMeasureConfirmDelete;

    /**
     * GeoNav dialog
     */
    private AlertDialog dialogGeoNav;
    private AlertDialog dialogPrecisionLoss;
    private boolean mlkitEnabled;

    private AlertDialog dialogCrashReport;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gps = new GPSTracker(this, this, 0, 10000);
        
        setupBackCallback();

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
        geoNavHelper = new GeoNavHelper(this, this);

        ttsHelper = new TextToSpeechHelper(this, () -> {
            String lang = mPrefs.getString(PreferenceKeys.TTS_LANGUAGE, "-1");
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

        sensorHelper.register();

        mlkitEnabled = mPrefs.getBoolean(PreferenceKeys.MLKIT_PREFERENCE_KEY, false);

        loadScreen();

        checkForInitialBarcodeSearch();

        verifyPersonHelper.checkLastOpened();

        SpectralDao spectralDao = new SpectralDao(database);
        ProtocolDao protocolDao = new ProtocolDao(database);
        DeviceDao deviceDao = new DeviceDao(database);
        UriDao uriDao = new UriDao(database);

        spectralViewModel = new SpectralViewModelFactory(new SpectralRepository(spectralDao, protocolDao, deviceDao, uriDao))
                .create(SpectralViewModel.class);
    }

    @Override
    protected void onStart() {
        super.onStart();
        usbCameraApi.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        usbCameraApi.onStop();
    }

    public void triggerTts(String text) {
        if (preferences.getBoolean(PreferenceKeys.TTS_LANGUAGE_ENABLED, false)) {
            ttsHelper.speak(text);
        }
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
     * this will search for obs. unit and load neccessary field file
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

                                if (fo != null && fo.getName() != null) {

                                    switchField(model.getStudy_id(), barcode);

                                }
                            }

                        } catch (Exception e) {

                            Log.d(TAG, "Failed while searching for: " + barcode);

                            e.printStackTrace();
                        }

                    } else {

                        Utils.makeToast(getApplicationContext(), getString(R.string.act_collect_plot_with_code_not_found));

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
        return Integer.toString(preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));
    }

    public String getObservationUnit() {
        return getCRange().uniqueId;
    }

    public String getPerson() {
        return preferences.getString(GeneralKeys.FIRST_NAME, "") + " " + preferences.getString(GeneralKeys.LAST_NAME, "");
    }

    public String getTraitName() {
        return getCurrentTrait().getName();
    }

    public String getTraitDbId() {
        return getCurrentTrait().getId();
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
        traitBox.connectRangeBox();
        rangeBox.connectTraitBox(traitBox);

        //setup infobar recycler view ui
        infoBarRv = findViewById(R.id.act_collect_infobar_rv);
        infoBarRv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        initCurrentVals();

        Log.d(TAG, "Load screen.");

        //connect keyboard listener to the main collect container
        ConstraintLayout layoutMain = findViewById(R.id.layout_main);
        keyboardListenerHelper.connect(layoutMain, (visible, height) -> {
            onSoftKeyboardChanged(visible, height);
            return null;
        });

        refreshInfoBarAdapter();

        uvcView = findViewById(R.id.collect_activity_uvc_tv);

        handleFlipFlopPreferences();
    }

    /**
     * Handles the flip flop preferences for the collect activity.
     * Change from issue #934:
     * Originally, the arrow functionalities were switched depending on this preference,
     * now the whole UI is swapped.
     */
    private void handleFlipFlopPreferences() {

        ConstraintLayout layout = findViewById(R.id.layout_main);

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(layout);

        if (preferences.getBoolean(PreferenceKeys.FLIP_FLOP_ARROWS, false)) {
            constraintSet.connect(R.id.act_collect_range_box, ConstraintSet.TOP,
                    R.id.act_collect_infobar_rv, ConstraintSet.BOTTOM, 0);
            constraintSet.connect(R.id.act_collect_range_box, ConstraintSet.BOTTOM,
                    R.id.act_collect_trait_box, ConstraintSet.TOP, 0);
            constraintSet.connect(R.id.act_collect_trait_box, ConstraintSet.TOP,
                    R.id.act_collect_range_box, ConstraintSet.BOTTOM, 0);
            constraintSet.connect(R.id.act_collect_input_view, ConstraintSet.TOP,
                    R.id.act_collect_trait_box, ConstraintSet.BOTTOM, 0);
        } else {
            constraintSet.connect(R.id.act_collect_trait_box, ConstraintSet.TOP,
                    R.id.act_collect_infobar_rv, ConstraintSet.BOTTOM, 0);
            constraintSet.connect(R.id.act_collect_trait_box, ConstraintSet.BOTTOM,
                    R.id.act_collect_range_box, ConstraintSet.TOP, 0);
            constraintSet.connect(R.id.act_collect_range_box, ConstraintSet.TOP,
                    R.id.act_collect_trait_box, ConstraintSet.BOTTOM, 0);
            constraintSet.connect(R.id.act_collect_input_view, ConstraintSet.TOP,
                    R.id.act_collect_range_box, ConstraintSet.BOTTOM, 0);
        }

        constraintSet.applyTo(layout);
    }

    //when softkeyboard is displayed, reset the snackbar to redisplay with a calculated bottom margin
    //this is necessary when its needed to display content above the keyboard without using adjustPan,
    //such as the geonav snackbar messages
    private void onSoftKeyboardChanged(Boolean visible, int keypadHeight) {

        geoNavHelper.resetGeoNavMessages();

        if (visible) {

            try {

                TraitObject trait = getCurrentTrait();

                if (trait != null) {

                    geoNavHelper.setSnackBarBottomMargin(keypadHeight);

                }

            } catch (Exception e) {

                e.printStackTrace();

            }

        } else {

            geoNavHelper.setSnackBarBottomMargin(0);

        }
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
        rangeBox.saveLastPlotAndTrait();
        rangeBox.refresh();

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
    public boolean validateData(@Nullable String data) {
        final TraitObject currentTrait = traitBox.getCurrentTrait();

        if (currentTrait == null) return false;

        if (data == null) return true;

        if (data.equals("NA")) return true;

        if (data.isEmpty()) return true;

        BaseTraitLayout layout = traitLayouts.getTraitLayout(currentTrait.getFormat());
        TraitFormat format = Formats.Companion.findTrait(currentTrait.getFormat());

        String value = data;
        if (format instanceof Scannable) {
            value = ((Scannable) format).preprocess(data);
        }

        if (!layout.validate(value)) {

            // do not delete the observation if
            // trait allows invalid valus

            if (!currentTrait.getInvalidValues()) {

                removeTrait(currentTrait);

                collectInputView.clear();

                soundHelper.playError();

            }

            return false;
        }

        return true;
    }


    @Override
    public void navigateIfDataIsValid(@Nullable String data, @NonNull Function0<Unit> onValidNavigation) {

        if (validateData(data)) {

            onValidNavigation.invoke();

            return;

        }

        TraitObject currentTrait = traitBox.getCurrentTrait();

        if (currentTrait == null) return;

        if (currentTrait.getInvalidValues()) {

            new InvalidValueDialog(this).show(
                    onValidNavigation::invoke,
                    () -> {
                        // do not navigate or delete observation
                    }
            );

        }
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
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Toolbar toolbarBottom = findViewById(R.id.toolbarBottom);
        toolbarBottom.setNavigationIcon(null);

        String naTts = getString(R.string.act_collect_na_btn_tts);
        String barcodeTts = getString(R.string.act_collect_barcode_btn_tts);
        String deleteTts = getString(R.string.act_collect_delete_btn_tts);

        // Bind Compose bottom toolbar (pass current audio recording state so label toggles)
        androidx.compose.ui.platform.ComposeView composeView = findViewById(R.id.toolbarBottomCompose);
        com.fieldbook.tracker.ui.BottomToolbarListener toolbarListener = new com.fieldbook.tracker.ui.BottomToolbarListener() {
            @Override
            public void onMissing() {
                triggerTts(naTts);
                TraitObject currentTrait = traitBox.getCurrentTrait();
                if (currentTrait != null) {
                    String format = currentTrait.getFormat();
                    if (format != null && Formats.Companion.isCameraTrait(format)) {
                        ((AbstractCameraTrait) traitLayouts.getTraitLayout(format)).setImageNa();
                    } else if (format != null && Formats.Companion.isSpectralFormat(format)) {
                        ((SpectralTraitLayout) traitLayouts.getTraitLayout(format)).setNa();
                    } else {
                        updateObservation(currentTrait, "NA", null);
                        setNaText();
                    }
                }
            }

            @Override
            public void onBarcode() {
                triggerTts(barcodeTts);

                requestScanSingleBarcode();
            }

            @Override
            public void onDelete() {
                boolean status = database.isBrapiSynced(getStudyId(), getObservationUnit(), getTraitDbId(), getRep());
                String format = getTraitFormat();
                if (status && !Formats.Companion.isCameraTrait(format)) {
                    brapiDelete(getCurrentTrait(), false);
                } else {
                    traitLayouts.deleteTraitListener(getTraitFormat());
                }
                if (getCurrentObservation() == null) updateCurrentTraitStatus(false);
                initToolbars();
                triggerTts(deleteTts);
            }

            @Override
            public void onDeleteLong() {
                ObservationModel[] models = database.getRepeatedValues(getStudyId(), getObservationUnit(), getTraitDbId());
                if (models.length > 0) {
                    showConfirmMultiMeasureDeleteDialog(java.util.List.of(models));
                }
            }

            @Override
            public void onMediaOption(com.fieldbook.tracker.ui.MediaOption option) {
                switch (option) {
                    case CAPTURE_MEDIA:
                        try {
                            Intent intent = new Intent(CollectActivity.this, CameraActivity.class);
                            intent.putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_PHOTO);
                            intent.putExtra(CameraActivity.EXTRA_TRAIT_NAME, getTraitName());
                            intent.putExtra(CameraActivity.EXTRA_STUDY_ID, getStudyId());
                            intent.putExtra(CameraActivity.EXTRA_OBS_UNIT, getObservationUnit());
                            intent.putExtra(CameraActivity.EXTRA_TRAIT_ID, Integer.parseInt(getCurrentTrait().getId()));
                            cameraXFacade.unbind();
                            // start activity for result so CollectActivity receives returned media path directly
                            startActivityForResult(intent, REQUEST_MEDIA_CODE);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case VIEW_MEDIA:
                        try {
                            Intent mediaIntent = new Intent(CollectActivity.this, com.fieldbook.tracker.ui.MediaViewerActivity.class);
                            mediaIntent.putExtra(com.fieldbook.tracker.ui.MediaViewerActivity.EXTRA_STUDY_ID, getStudyId());
                            mediaIntent.putExtra(com.fieldbook.tracker.ui.MediaViewerActivity.EXTRA_OBS_UNIT, getObservationUnit());
                            mediaIntent.putExtra(EXTRA_TRAIT_DB_ID, getTraitDbId());
                            startActivity(mediaIntent);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                 break;
                }
            }
        };

        // bind the compose view with current state
        try {
            boolean isMediaEnabled = false;
            int numMultiMedia = 0;
            try {
                // get current trait and check multimedia flags
                TraitObject current = getCurrentTrait();
                if (current != null) {
                    isMediaEnabled = current.getMultiMediaPhoto() || current.getMultiMediaVideo() || current.getMultiMediaAudio();
                }
                ObservationModel obs = getCurrentObservation();
                if (obs != null) {
                    numMultiMedia = obs.getMultiMediaCount();
                }
            } catch (Exception ignored) {}
            com.fieldbook.tracker.ui.BottomToolbarBindingsKt.bindBottomToolbar(composeView, toolbarListener, fieldAudioHelper.isRecording(), isMediaEnabled, numMultiMedia);
        } catch (Exception e) {
            // fallback: bind default toolbar
            com.fieldbook.tracker.ui.BottomToolbarBindingsKt.bindBottomToolbar(composeView, toolbarListener);
        }
    }

    private void requestScanSingleBarcode() {
        try {
            Intent intent = new Intent(CollectActivity.this, CameraActivity.class);
            intent.putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_BARCODE);
            intent.putExtra(CameraActivity.EXTRA_TRAIT_NAME, getTraitName());
            intent.putExtra(CameraActivity.EXTRA_STUDY_ID, getStudyId());
            intent.putExtra(CameraActivity.EXTRA_OBS_UNIT, getObservationUnit());
            intent.putExtra(CameraActivity.EXTRA_TRAIT_ID, Integer.parseInt(getCurrentTrait().getId()));
            cameraXFacade.unbind();
            startActivityForResult(intent, REQUEST_MEDIA_CODE);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open camera", e);
        }
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

        } else return database.getDefaultRep(getStudyId(), getObservationUnit(), getTraitDbId());
        //gets the minimum default index
    }

    // This is central to the application
    // Calling this function resets all the controls for traits, and picks one
    // to show based on the current trait data
    @Override
    public void initWidgets(final boolean rangeSuppress) {

        // Reset dropdowns
        refreshInfoBarAdapter();

        traitBox.initTraitDetails();

        TraitObject[] visibleTraits = database.getVisibleTraits().toArray(new TraitObject[] {});

        if (visibleTraits.length == 0) {
            Utils.makeToast(this, getString(R.string.act_trait_editor_no_traits_exist));
            setResult(RESULT_CANCELED);
            finish();
        }

        traitBox.initialize(visibleTraits);

        initToolbars();
        setupCollectInsets();
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
                if (Objects.equals(ro.uniqueId, searchUnique)) {
                    moveToResultCore(j);
                    return true;
                }
            }
        }

        // new type to skip the toast message and keep previous functionality
        if (command.equals("quickgoto")) {
            for (int j = 1; j <= plotIndices.length; j++) {
                rangeBox.setRangeByIndex(j - 1);

                if (rangeBox.getCRange().primaryId.equals(range) & rangeBox.getCRange().secondaryId.equals(plot)) {
                    moveToResultCore(j);
                    return true;
                }
            }
        }

        //move to plot
        if (command.equals("plot")) {
            for (int j = 1; j <= plotIndices.length; j++) {
                rangeBox.setRangeByIndex(j - 1);

                if (rangeBox.getCRange().secondaryId.equals(data)) {
                    moveToResultCore(j);
                    return true;
                }
            }
        }

        //move to range
        if (command.equals("range")) {
            for (int j = 1; j <= plotIndices.length; j++) {
                rangeBox.setRangeByIndex(j - 1);

                if (rangeBox.getCRange().primaryId.equals(data)) {
                    moveToResultCore(j);
                    return true;
                }
            }
        }

        //move to plot id
        if (command.equals("id")) {
            int rangeSize = plotIndices.length;
            for (int j = 1; j <= rangeSize; j++) {
                rangeBox.setRangeByIndex(j - 1);

                if (rangeBox.getCRange().uniqueId.equals(data)) {

                    if (trait == -1) {
                        moveToResultCore(j);
                    } else moveToResultCore(j, trait);

                    return true;
                }
            }
        }

        if (command.equals("barcode")) {
            int currentFieldId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0);
            Log.d("Field Book", "Barcode search in current field: " + currentFieldId + ", searching for: " + data);

            ObservationUnitModel[] matchingUnits = database.getObservationUnitsBySearchAttribute(
                    currentFieldId, data);
            Log.d("Field Book", "Search attribute results: " + matchingUnits.length + " units found");

            if (matchingUnits.length > 0) {
                // If found by search attribute, move to that observation unit
                String matchingObsUnitId = matchingUnits[0].getObservation_unit_db_id();
                Log.d("Field Book", "Found match by search attribute. Unit ID: " + matchingObsUnitId);

                // If multiple matches found, show notification
                if (matchingUnits.length > 1) {
                    Utils.makeToast(this, getString(R.string.search_multiple_matches_found, matchingUnits.length));
                }

                for (int j = 1; j <= plotIndices.length; j++) {
                    rangeBox.setRangeByIndex(j - 1);
                    RangeObject ro = rangeBox.getCRange();

                    if (ro.uniqueId.equals(matchingObsUnitId)) {
                        moveToResultCore(j);
                        return true;
                    }
                }
            }

            // Fallback: check if the barcode directly matches a plot_id
            Log.d("Field Book", "Falling back to direct plot_id matching");
            for (int j = 1; j <= plotIndices.length; j++) {
                rangeBox.setRangeByIndex(j - 1);
                RangeObject ro = rangeBox.getCRange();

                if (ro.uniqueId.equals(data)) {
                    Log.d("Field Book", "Direct match found at index: " + j);
                    moveToResultCore(j);
                    return true;
                }
            }

            // Check other fields if we didn't find it in the current field
            Log.d("Field Book", "Not found in current field, trying other fields");
            return searchAcrossAllFields(data, false, false);
        }

        if (!command.equals("quickgoto") && !command.equals("barcode"))
            Utils.makeToast(this, getString(R.string.main_toolbar_moveto_no_match));

        return false;
    }

    /**
     * Searches for a barcode across all fields when not found in the current field.
     * @param searchValue The barcode or search value to find
     * @param autoNavigate whether to show a snackbar or automatically navigate
     * @param suppressReactions whether to ignore toasts and noises
     * @return true if found in another field, false otherwise
     */
    private boolean searchAcrossAllFields(String searchValue, boolean autoNavigate, boolean suppressReactions) {
        Log.d("Field Book", "Searching across all fields for: " + searchValue);

        boolean found = false;
        FieldObject studyObj = null;

        // Store search value in inputPlotId for use in the fallback
        inputPlotId = searchValue;

        int currentFieldId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0);
        Log.d("Field Book", "Current field ID: " + currentFieldId);

        // Check all other fields by search attribute
        ArrayList<FieldObject> allFields = database.getAllFieldObjects();
        Log.d("Field Book", "Searching across " + allFields.size() + " fields");

        for (FieldObject field : allFields) {
            // Skip the current field
            if (field.getStudyId() == currentFieldId) {
                continue;
            }

            Log.d("Field Book", "Checking field: " + field.getStudyId() + " (" + field.getName() + ")");

            ObservationUnitModel[] matchingUnits = database.getObservationUnitsBySearchAttribute(
                    field.getStudyId(), searchValue);

            Log.d("Field Book", "Found " + matchingUnits.length + " matches in field " + field.getStudyId());

            if (matchingUnits.length > 0) {
                studyObj = field;
                String oldPlotId = inputPlotId;
                inputPlotId = matchingUnits[0].getObservation_unit_db_id();
                Log.d("Field Book", "Match found! Field: " + field.getName() +
                        ", unit ID updated from " + oldPlotId + " to " + inputPlotId);
                found = true;
                break;
            }
        }

        // If not found by search attribute in any field, try direct plot_id matching
        if (!found) {
            Log.d("Field Book", "No matches by search attribute, trying direct ID match");

            ObservationUnitModel[] models = database.getAllObservationUnits();

            for (ObservationUnitModel m : models) {
                if (m.getObservation_unit_db_id().equals(searchValue)) {
                    FieldObject study = database.getFieldObject(m.getStudy_id());
                    if (study != null && study.getName() != null) {
                        studyObj = study;
                        found = true;
                        Log.d("Field Book", "Direct match found in study: " + study.getName());
                        break;
                    }
                }
            }
        }
        
        // Handle the result of the search
        if (found && studyObj != null && studyObj.getName() != null && studyObj.getStudyId() != -1) {
            int studyId = studyObj.getStudyId();
            String fieldName = studyObj.getAlias();
            
            // Save the matching observation unit ID from the matched unit, not the search value
            final String matchedObsUnitId = inputPlotId; // This should be the one set earlier from matchingUnits[0]
            
            Log.d("Field Book", "Showing navigation prompt to field: " + fieldName + " and plot ID: " + matchedObsUnitId);
            
            String msg = getString(R.string.act_collect_barcode_search_exists_in_other_field, fieldName);
            
            SnackbarUtils.showNavigateSnack(getLayoutInflater(), findViewById(R.id.traitHolder), 
                msg, R.id.toolbarBottom, 8000, null,
                (v) -> switchField(studyId, matchedObsUnitId));
                
            return true;
        } else {
            Log.d("Field Book", "No match found in any field");
            if (!suppressReactions) {
                soundHelper.playError();
                Utils.makeToast(getApplicationContext(), getString(R.string.main_toolbar_moveto_no_match));
            }
            return false;
        }
    }

    private void moveToResultCore(int j) {
        rangeBox.setPaging(j);

        // Reload traits based on selected plot
        rangeBox.display();

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

        traitBox.setSelection(traitIndex);

        saveLastTrait();

        Log.d(TAG, "Move to result core: " + j + "with trait index "+ traitIndex);

        initWidgets(false);
    }

    private void saveLastTrait() {
        TraitObject lastTrait = traitBox.getCurrentTrait();
        if (lastTrait != null) {
            preferences.edit().putString(GeneralKeys.LAST_USED_TRAIT, lastTrait.getId()).apply();
        }
    }

    @Override
    public boolean isFieldAudioRecording(){
        return fieldAudioHelper.isRecording();
    }

    @Override
    public void onPause() {

        database.updateEditDate(preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));

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

        saveLastTrait();

        preferences.edit().putInt(GeneralKeys.DATA_LOCK_STATE, dataLocked).apply();

        traitLayouts.unregisterAllReceivers();

        super.onPause();
    }

    @Override
    public void onDestroy() {

        guiThread.quit();

        //save last plot id
        if (preferences.getBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false)) {
            rangeBox.saveLastPlotAndTrait();
        }

        try {
            ttsHelper.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        getTraitLayout().onExit();

        traitLayoutRefresh();

        //nixSensorHelper.disconnect();

        bleScanner.stopScanning();

        greenSeekerGattManager.disconnect();

        scaleGattManager.disconnect();

        usbCameraApi.onDestroy();

        gnssThreadHelper.stop();

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                goProApi.onDestroy();
            }
        } catch (Exception ignore) {}

        sensorHelper.unregister();

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
            systemMenu.findItem(R.id.help).setVisible(preferences.getBoolean(PreferenceKeys.TIPS, false));
            systemMenu.findItem(R.id.nextEmptyPlot).setVisible(!preferences.getString(PreferenceKeys.HIDE_ENTRIES_WITH_DATA_TOOLBAR, "0").equals("0"));
            systemMenu.findItem(R.id.jumpToPlot).setVisible(!preferences.getString(PreferenceKeys.MOVE_TO_UNIQUE_ID, "0").equals("0"));
            systemMenu.findItem(R.id.datagrid).setVisible(preferences.getBoolean(PreferenceKeys.DATAGRID_SETTING, false));
        }

        refreshInfoBarAdapter();

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
            if (preferences.getString(GeneralKeys.LAST_PLOT, null) != null) {
                rangeBox.setAllRangeID();
                int[] rangeID = rangeBox.getRangeID();
                moveToSearch("id", rangeID, null, null, preferences.getString(GeneralKeys.LAST_PLOT, null), -1);
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

        if (mPrefs.getBoolean(PreferenceKeys.ENABLE_GEONAV, false)) {

            //setup logger whenever activity resumes
            geoNavHelper.setupGeoNavLogger();

            startGeoNav();
        }

        if (!mSkipLastUsedTrait) {

            mSkipLastUsedTrait = false;

            navigateToLastOpenedTrait();

        }

        dataLocked = preferences.getInt(GeneralKeys.DATA_LOCK_STATE, UNLOCKED);

        traitLayouts.registerAllReceivers();

        refreshLock();

        traitBox.recalculateTraitStatusBarSizes();
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
     * This function is called to use that preference and navigate to the corresponding trait.
     */
    private void navigateToLastOpenedTrait() {

        //navigate to the last used trait using preferences
        String traitId = preferences.getString(GeneralKeys.LAST_USED_TRAIT, "-1");

        navigateToTrait(traitId);
    }

    public void navigateToTrait(String traitId) {

        if (!traitId.equals("-1")) {

            ArrayList<TraitObject> traits = database.getVisibleTraits();

            try {

                //find trait position of the traitId param
                int index = 0;
                for (int i = 0; i < traits.size(); i++) {
                    if (traits.get(i).getId().equals(traitId)) {
                        index = i;
                        break;
                    }
                }

                traitBox.setSelection(index);

            } catch (NullPointerException e) {

                Log.e(TAG, "Error while navigating to trait: " + traitId);

            }
        }
    }

    public void updateCurrentTraitStatus(Boolean hasObservation) {
        RecyclerView traitBoxRecyclerView = traitBox.getRecyclerView();
        if (traitBoxRecyclerView != null) {
            TraitsStatusAdapter traitsStatusAdapter = (TraitsStatusAdapter) traitBoxRecyclerView.getAdapter();
            if (traitsStatusAdapter != null){
                traitsStatusAdapter.updateCurrentTraitStatus(hasObservation);
            }
        }
    }

    /**
     * Helper function update user data in the memory based hashmap as well as
     * the database
     *
     * @param trait       the trait object to update
     * @param value       the new string value to be saved in the database
     * @param nullableRep the repeated value to update, could be null to represent the latest rep value
     */
    public void updateObservation(TraitObject trait, String value, @Nullable String nullableRep) {

        if (rangeBox.isEmpty()) {
            return;
        }

        String studyId = getStudyId();
        String obsUnit = getObservationUnit();
        String person = preferences.getString(GeneralKeys.FIRST_NAME, "") + " " + preferences.getString(GeneralKeys.LAST_NAME, "");

        String rep = nullableRep;

        //if not updating a repeated value, get the latest repeated value
        if (nullableRep == null) {

            rep = getRep();
        }

        Observation observation = database.getObservation(studyId, obsUnit, trait.getId(), rep);
        String observationDbId = observation.getDbId();
        OffsetDateTime lastSyncedTime = observation.getLastSyncedTime();

        // Always remove existing trait before inserting again
        // Based on plot_id, prevent duplicates
        database.deleteTrait(studyId, obsUnit, trait.getId(), rep);

        if (!value.isEmpty()) {

            //don't update the database if the value is blank or undesirable
            boolean pass = false;

            if (CategoricalTraitLayout.isTraitCategorical(trait.getFormat())) {

                if (value.equals("[]")) {

                    pass = true;
                }
            }

            if (!pass) {
                database.insertObservation(obsUnit, trait.getId(), value, person,
                        getLocationByPreferences(), "", studyId, observationDbId,
                        null, lastSyncedTime, rep);

                runOnUiThread(() -> updateCurrentTraitStatus(true));
            }
        }

        //update the info bar in case a variable is used
        refreshInfoBarAdapter();
        refreshRepeatedValuesToolbarIndicator();
    }

    public void insertRep(String value, String rep) {

        String studyId = getStudyId();
        String obsUnit = getObservationUnit();
        String person = getPerson();
        String traitDbId = getTraitDbId();

        database.insertObservation(obsUnit, traitDbId, value, person,
                getLocationByPreferences(), "", studyId, null, null, null, rep);
    }

    public void deleteRep(String rep) {

        String studyId = getStudyId();
        String obsUnit = getObservationUnit();
        String traitDbId = getTraitDbId();

        database.deleteTrait(studyId, obsUnit, traitDbId, rep);
    }

    public String getLocationByPreferences() {

        String studyId = Integer.toString(preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));
        String obsUnit = rangeBox.getPlotID();

        return LocationCollectorUtil.Companion
                .getLocationByCollectMode(this, preferences, studyId, obsUnit, geoNavHelper.getMInternalLocation(), geoNavHelper.getMExternalLocation(), database);
    }

    private void brapiDelete(TraitObject trait, Boolean hint) {
        Utils.makeToast(this, getString(R.string.brapi_delete_message));
        updateObservation(trait, getString(R.string.brapi_na), null);
        if (hint) {
            setNaTextBrapiEmptyField();
        } else {
            setNaText();
        }
    }

    // Delete trait, including from database
    public void removeTrait(TraitObject trait) {

        if (rangeBox.isEmpty()) {
            return;
        }

        String fieldId = Integer.toString(preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));

        if (database.isBrapiSynced(fieldId, getObservationUnit(), trait.getId(), getRep())) {
            brapiDelete(trait, true);
        } else {
            // Always remove existing trait before inserting again
            // Based on plot_id, prevent duplicate
            traitBox.remove(trait, getObservationUnit(), getRep());
        }
    }

    // for format without specific control
    public void removeTrait() {
        traitBox.remove(getCurrentTrait(), getObservationUnit(), getRep());
        collectInputView.setText("");
    }

    private void customizeToolbarIcons() {
        Set<String> entries = preferences.getStringSet(PreferenceKeys.TOOLBAR_CUSTOMIZE, new HashSet<>());

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

        systemMenu.findItem(R.id.help).setVisible(preferences.getBoolean(PreferenceKeys.TIPS, false));
        systemMenu.findItem(R.id.nextEmptyPlot).setVisible(!preferences.getString(PreferenceKeys.HIDE_ENTRIES_WITH_DATA_TOOLBAR, "0").equals("0"));
        systemMenu.findItem(R.id.jumpToPlot).setVisible(!preferences.getString(PreferenceKeys.MOVE_TO_UNIQUE_ID, "0").equals("0"));
        systemMenu.findItem(R.id.datagrid).setVisible(preferences.getBoolean(PreferenceKeys.DATAGRID_SETTING, false));

        //toggle repeated values indicator
        systemMenu.findItem(R.id.action_act_collect_repeated_values_indicator).setVisible(collectInputView.isRepeatEnabled());

        //added in geonav 310 only make goenav switch visible if preference is set
        MenuItem geoNavEnable = systemMenu.findItem(R.id.action_act_collect_geonav_sw);
        geoNavEnable.setVisible(mPrefs.getBoolean(PreferenceKeys.ENABLE_GEONAV, false));

        MenuItem fieldAudioMic = systemMenu.findItem(R.id.field_audio_mic);
        fieldAudioMic.setVisible(mPrefs.getBoolean(PreferenceKeys.ENABLE_FIELD_AUDIO, false));

        customizeToolbarIcons();

        refreshRepeatedValuesToolbarIndicator();

        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) { // Ensure the menu item view is already created before setting longpress listener
            toolbar.post(() -> setupLongPressListener());
        }
    }

    private void setupLongPressListener() {
        final View menuItemView = toolbar.findViewById(R.id.resources);
        if (menuItemView != null) {
            menuItemView.setOnLongClickListener(v -> {
                openSavedResourceFile();
                return true;
            });
        }
    }

    /**
     * Opens a saved resource file or a specific resource file if provided
     * @param resourceFileName Optional resource file name to open
     */
    private void openSavedResourceFile(String resourceFileName) {
        String fileString = resourceFileName != null ? resourceFileName : 
                            preferences.getString(GeneralKeys.LAST_USED_RESOURCE_FILE, "");
        Log.d(TAG, "fileString after selection: " + fileString);
        if (!fileString.isEmpty()) {
            try {
                Uri resultUri = Uri.parse(fileString);
                String suffix = fileString.substring(fileString.lastIndexOf('.') + 1).toLowerCase();
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix);

                Intent open = new Intent(Intent.ACTION_VIEW);
                open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                open.setDataAndType(resultUri, mime);
                startActivity(open);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Utils.makeToast(this, "No file preference saved, select a file with a short press");
        }
    }

    // Overload for backward compatibility
    private void openSavedResourceFile() {
        openSavedResourceFile(null);
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
        final int fieldAudioMicId = R.id.field_audio_mic;
        int itemId = item.getItemId();
        if (itemId == helpId) {
            TapTargetSequence sequence = new TapTargetSequence(this)
                    .targets(collectDataTapTargetView(R.id.act_collect_infobar_rv, getString(R.string.tutorial_main_infobars_title), getString(R.string.tutorial_main_infobars_description), 200),
                            collectDataTapTargetView(R.id.traitLeft, getString(R.string.tutorial_main_traits_title), getString(R.string.tutorial_main_traits_description), 60),
                            collectDataTapTargetView(R.id.traitTypeTv, getString(R.string.tutorial_main_traitlist_title), getString(R.string.tutorial_main_traitlist_description), 80),
                            collectDataTapTargetView(R.id.rangeLeft, getString(R.string.tutorial_main_entries_title), getString(R.string.tutorial_main_entries_description), 60),
                            collectDataTapTargetView(R.id.namesHolderLayout, getString(R.string.tutorial_main_navinfo_title), getString(R.string.tutorial_main_navinfo_description), 60),
                            collectDataTapTargetView(R.id.traitHolder, getString(R.string.tutorial_main_datacollect_title), getString(R.string.tutorial_main_datacollect_description), 200)
                            //collectDataTapTargetView(R.id.missingValue, getString(R.string.tutorial_main_na_title), getString(R.string.tutorial_main_na_description), 60),
                            //collectDataTapTargetView(R.id.deleteValue, getString(R.string.tutorial_main_delete_title), getString(R.string.tutorial_main_delete_description), 60)
                    );
            if (systemMenu.findItem(R.id.search).isVisible()) {
                sequence.target(collectDataTapTargetView(R.id.search, getString(R.string.tutorial_main_search_title), getString(R.string.tutorial_main_search_description), 60));
            }
            if (systemMenu.findItem(R.id.resources).isVisible()) {
                sequence.target(collectDataTapTargetView(R.id.resources, getString(R.string.tutorial_main_resources_title), getString(R.string.tutorial_main_resources_description), 60));
            }
            if (systemMenu.findItem(R.id.summary).isVisible()) {
                sequence.target(collectDataTapTargetView(R.id.summary, getString(R.string.tutorial_main_summary_title), getString(R.string.tutorial_main_summary_description), 60));
            }
            if (systemMenu.findItem(R.id.lockData).isVisible()) {
                sequence.target(collectDataTapTargetView(R.id.lockData, getString(R.string.tutorial_main_lockdata_title), getString(R.string.tutorial_main_lockdata_description), 60));
            }
            if (systemMenu.findItem(R.id.datagrid).isVisible()) {
                sequence.target(collectDataTapTargetView(R.id.datagrid, getString(R.string.tutorial_main_datagrid_title), getString(R.string.tutorial_main_datagrid_description), 60));
            }
            if (systemMenu.findItem(R.id.field_audio_mic).isVisible()) {
                sequence.target(collectDataTapTargetView(R.id.field_audio_mic, getString(R.string.tutorial_main_field_audio_mic_title), getString(R.string.tutorial_main_field_audio_mic_description), 60));
            }
            if (systemMenu.findItem(R.id.action_act_collect_repeated_values_indicator).isVisible()) {
                sequence.target(collectDataTapTargetView(R.id.action_act_collect_repeated_values_indicator, getString(R.string.tutorial_main_repeated_values_title), getString(R.string.tutorial_main_repeated_values_description), 60));
            }
            if (systemMenu.findItem(R.id.action_act_collect_geonav_sw).isVisible()) {
                sequence.target(collectDataTapTargetView(R.id.action_act_collect_geonav_sw, getString(R.string.tutorial_main_geonav_title), getString(R.string.tutorial_main_geonav_description), 60));
            }

            sequence.start();
        } else if (itemId == searchId) {
            SearchDialog searchdialog = new SearchDialog(this, this);
            searchdialog.show(getSupportFragmentManager(), "DialogTag");
        } else if (itemId == resourcesId) {
            TraitObject currentTrait = getCurrentTrait();
            if (currentTrait != null) {
                Log.d(TAG, "Current trait: " + currentTrait.getName() + ", Resource file: " + currentTrait.getResourceFile());
            } else {
                Log.d(TAG, "Current trait is null");
            }
            if (currentTrait != null && currentTrait.getResourceFile() != null && !currentTrait.getResourceFile().isEmpty()) {
                // Trait has a resource file defined, try to open it directly
                openSavedResourceFile(currentTrait.getResourceFile());
            } else {
                DocumentFile dir = BaseDocumentTreeUtil.Companion.getDirectory(this, R.string.dir_resources);
                if (dir != null && dir.exists()) {
                    intent.setClassName(CollectActivity.this, FileExploreActivity.class.getName());
                    intent.putExtra("path", dir.getUri().toString());
                    intent.putExtra("title", getString(R.string.main_toolbar_resources));
                    startActivityForResult(intent, REQUEST_FILE_EXPLORER_CODE);
                }
            }
        } else if (itemId == nextEmptyPlotId) {
            rangeBox.setPaging(rangeBox.movePaging(rangeBox.getPaging(), 1, true));
            refreshMain();
        } else if (itemId == jumpToPlotId) {
            String moveToUniqueIdValue = preferences.getString(PreferenceKeys.MOVE_TO_UNIQUE_ID, "");
            if (moveToUniqueIdValue.equals("1")) {
                moveToPlotID();
            } else if (moveToUniqueIdValue.equals("2")) {
                requestScanSingleBarcode();
            }
        } else if (itemId == summaryId) {
            showSummary();
        } else if (itemId == dataGridId) {
            Intent i = new Intent();
            i.setClassName(CollectActivity.this,
                    DataGridActivity.class.getName());
            i.putExtra("plot_id", rangeBox.getPaging());
            i.putExtra("trait", traitBox.getCurrentTrait().getRealPosition());
            startActivityForResult(i, 2);
        } else if (itemId == lockDataId) {
            if (dataLocked == UNLOCKED) {
                dataLocked = LOCKED;
                Utils.makeToast(this, getString(R.string.activity_collect_locked_state));
            }
            else if (dataLocked == LOCKED) {
                dataLocked = FROZEN;
                Utils.makeToast(this, getString(R.string.activity_collect_frozen_state));
            }
            else {
                dataLocked = UNLOCKED;
                Utils.makeToast(this, getString(R.string.activity_collect_unlocked_state));
            }
            preferences.edit().putInt(GeneralKeys.DATA_LOCK_STATE, dataLocked).apply();
            lockData();
        } else if (itemId == android.R.id.home) {
            finish();
            /*
             * Toggling the geo nav icon turns the automatic plot navigation on/off.
             * If geonav is enabled, collect activity will auto move to the plot in user's vicinity
             */
        } else if (itemId == geonavId) {
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
        } else if (itemId == R.id.field_audio_mic) {
            MenuItem micItem = systemMenu.findItem(R.id.field_audio_mic);

            // get status from AudioTraitLayout
            TraitObject currentTrait = traitBox.getCurrentTrait();
            BaseTraitLayout currentTraitLayout = traitLayouts.getTraitLayout(currentTrait.getFormat());

            boolean isTraitAudioLayout = currentTraitLayout.isTraitType(AudioTraitLayout.type);
            boolean isTraitAudioRecording = false;
            boolean isTraitAudioPlaying = false;
            if (isTraitAudioLayout) {
                AudioTraitLayout audioTraitLayout = (AudioTraitLayout) currentTraitLayout;
                isTraitAudioRecording = audioTraitLayout.isAudioRecording();
                isTraitAudioPlaying = audioTraitLayout.isAudioPlaybackPlaying();
            }

            // if trait audio is recording, give a warning
            if (isTraitAudioRecording) {
                Utils.makeToast(this, getString(R.string.trait_audio_recording_warning));
            }
            // if trait audio is playing, give a warning
            else if (isTraitAudioPlaying) {
                Utils.makeToast(this, getString(R.string.trait_audio_playing_warning));
            }
            // if trait audio isn't recording or playing
            // record or stop the field audio depending on its state
            else if (!fieldAudioHelper.isRecording()) {
                // TODO: add trait audio playback stopping logic
                fieldAudioHelper.startRecording(true);
                Utils.makeToast(this, getString(R.string.field_audio_recording_start));
                micItem.setIcon(R.drawable.ic_tb_field_mic_on);
                micItem.setTitle(R.string.menu_collect_stop_field_audio);
            } else {
                fieldAudioHelper.stopRecording();
                Utils.makeToast(this, getString(R.string.field_audio_recording_stop));
                micItem.setIcon(R.drawable.ic_tb_field_mic_off);
                micItem.setTitle(R.string.menu_collect_start_field_audio);
            }

            return true;
        } else if (itemId == R.id.action_act_collect_repeated_values_indicator) {
            showRepeatedMeasuresDeleteDialog();

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showRepeatedMeasuresDeleteDialog() {

        ObservationModel[] values = database.getRepeatedValues(
                getStudyId(), getObservationUnit(), getTraitDbId());

        ArrayList<String> is = new ArrayList<>();
        ArrayList<ObservationModel> observations = new ArrayList<>();

        for (ObservationModel m: values) {
            if (!m.getValue().isEmpty()) {
                TraitObject trait = database.getTraitById(String.valueOf(m.getObservation_variable_db_id()));
                String format = m.getObservation_variable_field_book_format();
                if (format != null) {

                    TraitFormat traitFormat = Formats.Companion.findTrait(format);

                    Object valueModel = m.getValue();

                    if (traitFormat instanceof StringCoder) {

                        valueModel = ((StringCoder) traitFormat).decode(m.getValue());

                    }

                    if (traitFormat instanceof ValuePresenter) {

                        is.add(((ValuePresenter) traitFormat).represent(this, valueModel, trait));
                        observations.add(m);

                    } else {

                        is.add(valueModel.toString());
                        observations.add(m);
                    }
                }
            }
        }

        int size = is.size();

        if (size > 0) {

            String[] items = new String[size];
            boolean[] checked = new boolean[size];

            for (int n = 0; n < size; n++) {

                items[n] = is.get(n);
                checked[n] = false;
            }

            dialogMultiMeasureDelete = new AlertDialog.Builder(this, R.style.AppAlertDialog)
                    .setTitle(R.string.dialog_multi_measure_delete_title)
                    .setMultiChoiceItems(items, checked, (d, which, isChecked) -> {
                    })
                    .setNegativeButton(R.string.dialog_multi_measure_delete, (d, which) -> {

                        List<ObservationModel> deleteItems = new ArrayList<>();
                        int checkSize = checked.length;
                        for (int j = 0; j < checkSize; j++) {
                            if (checked[j]) {
                                deleteItems.add(observations.get(j));
                            }
                        }

                        if (!deleteItems.isEmpty()) {

                            showConfirmMultiMeasureDeleteDialog(deleteItems);

                        }
                    })
                    .setPositiveButton(android.R.string.cancel, (d, which) -> {
                        d.dismiss();
                    })
                    .setNeutralButton(R.string.dialog_multi_measure_select_all, (d, which) -> {
                        //Arrays.fill(checked, true);
                    })
                    .create();

            dialogMultiMeasureDelete.setOnShowListener((d) -> {
                AlertDialog ad = (AlertDialog) d;
                ad.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener((v) -> {
                    boolean first = checked[0];
                    Arrays.fill(checked, !first);
                    ListView lv = ad.getListView();
                    for (int i = 0; i < checked.length; i++) {
                        lv.setItemChecked(i, checked[i]);
                    }
                });
            });

            if (!dialogMultiMeasureDelete.isShowing()) {

                dialogMultiMeasureDelete.show();
            }
        } else {

            Utils.makeToast(this, getString(R.string.dialog_multi_measure_delete_no_observations));

        }
    }

    private void showConfirmMultiMeasureDeleteDialog(List<ObservationModel> models) {

        dialogMultiMeasureConfirmDelete = new AlertDialog.Builder(this, R.style.AppAlertDialog)
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

            deleteRep(model.getRep());

            if (model.getObservation_variable_field_book_format() != null && Formats.Companion.isSpectralFormat(model.getObservation_variable_field_book_format())) {
                database.deleteSpectralFact(model.getValue());
            }

            ObservationModel[] currentModels = database.getRepeatedValues(getStudyId(), getObservationUnit(), getTraitDbId());

            if (currentModels.length == 0) {

                updateCurrentTraitStatus(false);

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

        int state = preferences.getInt(GeneralKeys.DATA_LOCK_STATE, UNLOCKED);

        if (state == LOCKED) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_lock);
            disableDataEntry(R.string.activity_collect_locked_state);
        } else if (state == UNLOCKED) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_unlock);
            enableDataEntry();
        } else {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_lock_clock);
            if (collectInputView.getText().isEmpty()) {
                enableDataEntry();
            } else disableDataEntry(R.string.activity_collect_frozen_state);
        }

        TraitObject trait = getCurrentTrait();
        if (trait != null) {
            traitLayouts.refreshLock(trait.getFormat());
        }
    }

    public void traitLockData() {
        if (dataLocked == LOCKED) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_lock);
            disableDataEntry(R.string.activity_collect_locked_state);
        } else if (dataLocked == UNLOCKED) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_unlock);
            enableDataEntry();
        } else {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_lock_clock);
            if (collectInputView.getText().isEmpty()) {
                enableDataEntry();
            } else disableDataEntry(R.string.activity_collect_frozen_state);
        }
    }

    private void enableDataEntry() {
//        missingValue.setEnabled(true);
//        deleteValue.setEnabled(true);
//        barcodeInput.setEnabled(true);
//        traitLayouts.enableViews();
        findViewById(R.id.lockOverlay).setVisibility(View.GONE);
    }

    private void disableDataEntry(int toastMessageId) {
//        missingValue.setEnabled(false);
//        deleteValue.setEnabled(false);
//        barcodeInput.setEnabled(false);
//        traitLayouts.disableViews();
        View overlay = findViewById(R.id.lockOverlay);
        overlay.setOnClickListener((v) -> {
            getSoundHelper().playError();
            Utils.makeToast(this, getString(toastMessageId));
        });
        overlay.setVisibility(View.VISIBLE);
    }

    private void moveToPlotID() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_gotobarcode, null);
        final EditText barcodeId = layout.findViewById(R.id.barcodeid);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
        builder.setTitle(R.string.main_toolbar_moveto)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_go), (dialog, which) -> {
            inputPlotId = barcodeId.getText().toString();
            rangeBox.setAllRangeID();
            int[] rangeID = rangeBox.getRangeID();
            moveToSearch("barcode", rangeID, null, null, inputPlotId, -1);
            goToId.dismiss();
        });

        builder.setNegativeButton(getString(R.string.dialog_cancel), (dialog, which) -> dialog.dismiss());

        builder.setNeutralButton(getString(R.string.main_toolbar_moveto_scan), (dialogInterface, i) -> {
            requestScanSingleBarcode();
        });

        goToId = builder.create();

        goToId.setOnShowListener(dialog -> {
            barcodeId.post(() -> {
                barcodeId.requestFocus();
                SoftKeyboardUtil.Companion.showKeyboard(getContext(), barcodeId, 250L);
            });
        });

        goToId.setOnDismissListener(dialog -> {
            barcodeId.post(() -> SoftKeyboardUtil.Companion.closeKeyboard(getContext(), barcodeId, 250L));
        });

        goToId.show();

        android.view.WindowManager.LayoutParams langParams = goToId.getWindow().getAttributes();
        langParams.width = LayoutParams.MATCH_PARENT;
        goToId.getWindow().setAttributes(langParams);
    }

    private void showSummary() {

        SummaryFragment fragment = new SummaryFragment();

        getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (event.getKeyCode()) {

            //delegate media key events to a helper class that interprets preferences
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                return MediaKeyCodeActionHelper.Companion.dispatchKeyEvent(this, event);

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
                        //save most recently used resource file
                        preferences.edit().putString(GeneralKeys.LAST_USED_RESOURCE_FILE, resultString).apply();

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
                    Utils.makeToast(this, getString(R.string.act_file_explorer_no_file_error));
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
                if (resultCode == RESULT_OK) {
                    Log.d("Field Book", "Barcode scan successful");

                    if (geoNavHelper.getSnackbar() != null) {
                        geoNavHelper.getSnackbar().dismiss();
                    }

                    String barcodeValue;
                    if (mlkitEnabled) {
                        barcodeValue = data.getStringExtra("barcode");
                    } else {
                        IntentResult plotSearchResult = IntentIntegrator.parseActivityResult(resultCode, data);
                        barcodeValue = plotSearchResult.getContents();
                    }

                    if (barcodeValue != null && !barcodeValue.isEmpty()) {
                        Log.d("Field Book", "Scanned barcode: " + barcodeValue);

                        // Set inputPlotId globally to ensure it's available everywhere
                        inputPlotId = barcodeValue;

                        rangeBox.setAllRangeID();
                        int[] rangeID = rangeBox.getRangeID();

                        boolean success = moveToSearch("barcode", rangeID, null, null, barcodeValue, -1);

                        // If success is true, moveToSearch found the barcode, either in current field
                        // or via the fallback in another field. Success sound happens in moveToSearch
                        // if needed. No additional action required here.
                    } else {
                        Log.d("Field Book", "Barcode scan returned empty result");
                        soundHelper.playError();
                        Utils.makeToast(getApplicationContext(), getString(R.string.main_toolbar_moveto_no_match));
                    }
                } else {
                    Log.d("Field Book", "Barcode scan cancelled or failed");
                }
                break;
            case PhotoTraitLayout.PICTURE_REQUEST_CODE:
                String success = getString(R.string.trait_photo_tts_success);
                String fail = getString(R.string.trait_photo_tts_fail);
                if (resultCode == RESULT_OK) {

                    TraitObject currentTrait = getCurrentTrait();
                    if (currentTrait != null) {
                        BaseTraitLayout traitPhoto = traitLayouts.getTraitLayout(currentTrait.getFormat());
                        if (traitPhoto instanceof PhotoTraitLayout) {
                            ((PhotoTraitLayout) traitPhoto).makeImage(currentTrait);
                        }

                        triggerTts(success);
                    }

                } else triggerTts(fail);
                break;

            case REQUEST_CROP_IMAGE_CODE:
                if (resultCode == RESULT_OK && data != null) {
                    String mediaPath = data.getStringExtra("media_path");
                    if (mediaPath != null) {
                        File f = new File(getContext().getCacheDir(), AbstractCameraTrait.TEMPORARY_IMAGE_NAME);
                        Uri uri = Uri.fromFile(f);
                        startCropActivity(getCurrentTrait().getId(), uri);
                    }
                }
                break;
            case REQUEST_MEDIA_CODE:
                if (resultCode == RESULT_OK && data != null) {
                    // Camera returned a media_path
                    String mediaPath = data.getStringExtra("media_path");
                    String mediaType = data.getStringExtra("media_type");
                    String barcode = data.getStringExtra(CameraActivity.EXTRA_BARCODE);
                    if (mediaPath != null) {

                        if (Objects.equals(mediaType, "photo")) {
                            TraitObject trait = getCurrentTrait();
                            String roiString = preferences.getString(GeneralKeys.getCropCoordinatesKey(Integer.parseInt(trait.getId())), "");
                            if (roiString.isEmpty()) {

                                File f = new File(getContext().getCacheDir(), AbstractCameraTrait.TEMPORARY_IMAGE_NAME);
                                Uri uri = Uri.fromFile(f);
                                // we will need to show the confirm dialog once cropping finishes. Use the
                                // cache temp file as the pending media so the cropped image is shown.
                                pendingMediaPath = f.getAbsolutePath();
                                // delete the previously saved copy (mediaPath) to avoid duplicates later
                                try { if (mediaPath != null && !mediaPath.equals(pendingMediaPath)) new File(mediaPath).delete(); } catch (Exception ignore) {}
                                pendingMediaType = mediaType;
                                startCropActivity(getCurrentTrait().getId(), uri);
                            } else {
                                // If an ROI is defined, apply the crop to the saved image before showing the confirm dialog.
                                // Do this off the UI thread to avoid blocking.
                                try {
                                    final String finalMediaPath = mediaPath;
                                    final String finalRoi = roiString;
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        try {

                                            Uri srcUri;
                                            if (finalMediaPath.startsWith("content://") || finalMediaPath.startsWith("file://")) {
                                                srcUri = Uri.parse(finalMediaPath);
                                            } else {
                                                File srcFile = new File(finalMediaPath);
                                                srcUri = Uri.fromFile(srcFile);
                                            }

                                            // Use BitmapLoader to crop the bitmap according to ROI
                                            android.graphics.Bitmap cropped = com.fieldbook.tracker.utilities.BitmapLoader.Companion.cropBitmap(CollectActivity.this, srcUri, finalRoi);

                                            java.io.OutputStream out = null;
                                            try {
                                                out = getContentResolver().openOutputStream(srcUri);
                                                if (out != null) {
                                                    cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out);
                                                    out.flush();
                                                }
                                            } catch (Exception ex) {
                                                ex.printStackTrace();
                                            } finally {
                                                try { if (out != null) out.close(); } catch (Exception ignore) {}
                                            }
                                        } catch (Exception ex) {
                                            ex.printStackTrace();
                                        }

                                        // After cropping (or on failure), show the confirm dialog on UI thread
                                        runOnUiThread(() -> showMediaConfirmDialog(mediaType, mediaPath));
                                    });
                                 } catch (Exception e) {
                                     e.printStackTrace();
                                     showMediaConfirmDialog(mediaType, mediaPath);
                                 }
                             }
                         } else if (mediaType.equals("audio") || mediaType.equals("video")) {
                            runOnUiThread(() -> showMediaConfirmDialog(mediaType, mediaPath));
                        }

                     } else if (barcode != null) {

                        Log.d(TAG, "Fuzzy search on barcode: " + barcode);

                        String option = preferences.getString(PreferenceKeys.BARCODE_SCANNING_OPTIONS, BarcodeScanningOptions.EnterValue.INSTANCE.getValue());
                        String optionEnter = BarcodeScanningOptions.EnterValue.INSTANCE.getValue();
                        String optionMove = BarcodeScanningOptions.Move.INSTANCE.getValue();
                        String optionAsk = BarcodeScanningOptions.Ask.INSTANCE.getValue();
                        String optionEnterIfNotId = BarcodeScanningOptions.EnterIfNotId.INSTANCE.getValue();

                        if (option.equals(optionEnter)) {

                            validateAndSaveBarcodeScan(barcode);

                        } else if (option.equals(optionMove)) {

                            searchAcrossAllFields(barcode, true, false);

                        } else if (option.equals(optionAsk)) {

                            showBarcodeAskDialog(barcode);

                        } else if (option.equals(optionEnterIfNotId)) {

                            if (!searchAcrossAllFields(barcode, true, true)) {
                                validateAndSaveBarcodeScan(barcode);
                            }
                        }
                    }
                }
                break;
            case REQUEST_CROP_FINISHED_CODE:
                if (resultCode == RESULT_OK) {
                    // Crop activity finished; if we have a pending media, show confirm dialog
                    if (pendingMediaType != null && pendingMediaPath != null) {
                        showMediaConfirmDialog(pendingMediaType, pendingMediaPath);
                    }
                    // clear pending state
                    pendingMediaType = null;
                    pendingMediaPath = null;
                }
                break;
        }
    }

    private void validateAndSaveBarcodeScan(String barcode) {
        TraitObject currentTrait = traitBox.getCurrentTrait();
        BaseTraitLayout currentTraitLayout = traitLayouts.getTraitLayout(currentTrait.getFormat());
        TraitFormat traitFormat = Formats.Companion.findTrait(currentTrait.getFormat());

        String oldValue = "";
        ObservationModel currentObs = getCurrentObservation();
        if (currentObs != null) {
            oldValue = currentObs.getValue();
        }

        if (barcode != null && traitFormat instanceof Scannable && validateData(barcode)) {
            updateObservation(currentTrait, ((Scannable) traitFormat).preprocess(barcode), null);
        } else {
            updateObservation(currentTrait, oldValue, null);
        }

        currentTraitLayout.loadLayout();
    }

    private void showBarcodeAskDialog(String barcode) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        builder.setTitle(R.string.barcode_ask_title);
        builder.setMessage(getString(R.string.barcode_ask_message, barcode));

        builder.setPositiveButton(R.string.barcode_ask_enter, (dialog, which) -> validateAndSaveBarcodeScan(barcode));
        builder.setNegativeButton(R.string.barcode_ask_move, (dialog, which) -> searchAcrossAllFields(barcode, true, true));

        builder.create().show();
    }

    public boolean isTraitBlocked() {
        return traitLayouts.getTraitLayout(getCurrentTrait().getFormat()).block();
    }

    //triggers when pressing the repeated values navigation arrows
    public void traitLayoutRefresh() {
        refreshRepeatedValuesToolbarIndicator();
        traitLayouts.getTraitLayout(getCurrentTrait().getFormat()).refreshLayout(false);
        initToolbars();
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

                ObservationModel[] values = database.getRepeatedValues(getStudyId(), getObservationUnit(), getTraitDbId());

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
    public void takePicture() {

        TraitObject trait = getCurrentTrait();

        if (trait != null && trait.getFormat() != null) {

            BaseTraitLayout layout = traitLayouts.getTraitLayout(trait.getFormat());

            if (layout instanceof AbstractCameraTrait) {

                ((AbstractCameraTrait) layout).requestPicture();
            }
        }
    }

    private void setupBackCallback() {
        OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                int count = getSupportFragmentManager().getBackStackEntryCount();

                if (count == 0) {
                        String format = traitBox.getCurrentFormat();
                        if (format.equals(CanonTraitLayout.type)) {
                            canonApi.stopSession();
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    wifiHelper.disconnect();
                                }
                            } catch (Exception ignore) {}
                        }
                        finish();
                } else {
                    getSupportFragmentManager().popBackStack();
                }
            }
        };

        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @NonNull
    @Override
    public TraitBoxView getTraitBox() {
        return traitBox;
    }

    @Override
    public boolean existsTrait(final int plotId) {
        final TraitObject trait = traitBox.getCurrentTrait();
        if (trait != null) {
            return database.getTraitExists(plotId, trait.getId());
        } else return false;
    }

    /**
     * Iterates over all traits for the given ID and returns the trait's index which is missing
     * @param traitIndex current trait index
     * @param plotId the plot identifier
     * @return index of the trait missing or -1 if all traits exist
     */
    @Override
    public int existsAllTraits(final int traitIndex, final int plotId) {
        final ArrayList<TraitObject> traits = database.getVisibleTraits();
        for (int i = 0; i < traits.size(); i++) {
            if (i != traitIndex
                    && !database.getTraitExists(plotId, traits.get(i).getId())) return i;
        }
        return -1;
    }

    @NonNull
    @Override
    public List<Integer> getNonExistingTraits(final int plotId) {
        final ArrayList<TraitObject> traits = database.getVisibleTraits();
        final ArrayList<Integer> indices = new ArrayList<>();
        for (int i = 0; i < traits.size(); i++) {
            if (!database.getTraitExists(plotId, traits.get(i).getId()))
                indices.add(i);
        }
        return indices;
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
        return preferences;
    }

    @Override
    public boolean isCyclingTraitsAdvances() {
        return preferences.getBoolean(PreferenceKeys.CYCLING_TRAITS_ADVANCES, false);
    }

    public boolean isReturnFirstTrait() {
        return preferences.getBoolean(PreferenceKeys.RETURN_FIRST_TRAIT, false);
    }

    /**
     * Inserts a user observation whenever a label is printed.
     * @param plotID: The plot ID at the time of printing.
     * @param traitID: The trait ID at the time of printing.
     * @param traitFormat: The format of the trait.
     * @param labelNumber: The number of labels printed.
     */
    public void insertPrintObservation(String plotID, String traitID, String traitFormat, String labelNumber) {
        String studyId = Integer.toString(preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));

        database.insertObservation(plotID, traitID, labelNumber,
                getPerson(),
                getLocationByPreferences(), "", studyId, "",
                null, null, null);
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
        return preferences;
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
        initToolbars();
    }

    @Override
    public void onInfoBarClicked(int position) {

        infoBarHelper.showInfoBarChoiceDialog(getSupportFragmentManager(), position);

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
    public SecureBluetooth advisor() {
        return secureBluetooth;
    }

    @NonNull
    @Override
    public String queryForLabelValue(@NonNull String plotId, @NonNull AttributeAdapter.AttributeModel attribute) {

        String dataMissingString = getString(R.string.main_infobar_data_missing);

        String label = attribute.getLabel();

        String studyId = Integer.toString(preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0));

        if (attribute.getTrait() == null) {

            if (label.equals(getString(R.string.field_name_attribute))) {
                String fieldName = getPreferences().getString(GeneralKeys.FIELD_ALIAS, "");
                return fieldName.isEmpty() ? dataMissingString : fieldName;
            }

            String value = database.getObservationUnitPropertyValues(label, plotId);

            if (value.isEmpty()) {

                return dataMissingString;

            } else {

                if (label.equals("geo_coordinates") && JsonUtil.Companion.isJsonValid(value)) {

                    return GeoJsonUtil.Companion.decode(value).toCoordinateString(";");

                } else {

                    return value;

                }
            }

        } else {

            ObservationModel[] observations = database.getAllObservations(studyId, plotId, attribute.getTrait().getId());

            int size = observations.length;

            if (size == 0) {
                return dataMissingString;
            }

            String value = observations[size-1].getValue();

            try {

                StringJoiner joiner = new StringJoiner(":");
                ArrayList<BrAPIScaleValidValuesCategories> scale = CategoryJsonUtil.Companion.decode(value);
                for (BrAPIScaleValidValuesCategories s : scale) {
                    joiner.add(attribute.getTrait().getCategoryDisplayValue() ? s.getValue() : s.getLabel());
                }

                return joiner.toString();

            } catch (Exception ignore) {
                return value;
            }
        }
    }

    @NonNull
    @Override
    public List<AttributeAdapter.AttributeModel> getGeoNavPopupSpinnerItems () {

        ArrayList<AttributeAdapter.AttributeModel> items = new ArrayList<>();

        //query database for attributes/traits to use
        try {

            //add all obs unit attribute metadata
            String[] attributes = getDatabase().getAllObservationUnitAttributeNames(Integer.parseInt(getStudyId()));
            for (String attribute : attributes) {
                items.add(new AttributeAdapter.AttributeModel(attribute, null, null));
            }

            //add all visible traits
            TraitObject[] traits = getDatabase().getVisibleTraits().toArray(new TraitObject[0]);
            for (TraitObject traitObject : traits) {
                items.add(new AttributeAdapter.AttributeModel(traitObject.getAlias(), null, traitObject));
            }

            return items;

        } catch (Exception e) {

            Log.e(TAG, "Error occurred when querying for attributes in GeoNavCollectDialog.", e);

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

                dialogPrecisionLoss = new AlertDialog.Builder(this, R.style.AppAlertDialog)
                        .setTitle(getString(R.string.dialog_geonav_precision_loss_title))
                        .setMessage(getString(R.string.dialog_geonav_precision_loss_msg))
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            dialog.dismiss();
                        })
                        .create();

                dialogPrecisionLoss.show();

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

    private void showObservationMetadataDialog(@Nullable ObservationModel model) {

        if (model != null){
            DialogFragment dialogFragment = new ObservationMetadataFragment().newInstance(model);
            dialogFragment.show(this.getSupportFragmentManager(), "observationMetadata");
        }
    }

    public void showObservationMetadataDialog(Integer observationId){
        ObservationModel currentObservationObject = getDatabase().getObservationById(String.valueOf(observationId));
        showObservationMetadataDialog(currentObservationObject);
    }

    public void showObservationMetadataDialog() {
        ObservationModel currentObservationObject = getCurrentObservation();
        showObservationMetadataDialog(currentObservationObject);
    }

    public ObservationModel getCurrentObservation() {
        String rep = getCollectInputView().getRep();

        ObservationModel[] models = getDatabase().getRepeatedValues(getStudyId(), getObservationUnit(), getTraitDbId());

        for (ObservationModel m : models) {
            if (rep.equals(m.getRep())) {
                return m;
            }
        }

        return null;
    }

    @Override
    public synchronized void logNmeaMessage(@NonNull String nmea) {

        try {
            gnssRawLogHandlerThread.getLooper();
            gnssRawLogHandlerThread.start();
        } catch (Exception ignore) {
        }

        new Handler(gnssRawLogHandlerThread.getLooper()).post(() -> logNmeaMessageWork(nmea));

    }

    private void logNmeaMessageWork(@NonNull String nmea) {
        try {

            OutputStream output = null;
            OutputStreamWriter writer = null;

            try {

                DocumentFile file = DocumentTreeUtil.Companion.getFieldDataDirectory(this, DocumentTreeUtil.FIELD_GNSS_LOG);

                if (file != null) {

                    DocumentFile log = file.findFile(DocumentTreeUtil.FIELD_GNSS_LOG_FILE_NAME);

                    if (log == null) {

                        log = file.createFile("text/csv", DocumentTreeUtil.FIELD_GNSS_LOG_FILE_NAME);

                    }

                    if (log != null) {

                        output = getContentResolver().openOutputStream(log.getUri(), "wa");
                        writer = new OutputStreamWriter(output);
                        writer.write(nmea);
                        writer.flush();

                    }
                }

            } catch (Exception e) {

                e.printStackTrace();

            } finally {

                try {

                    if (output != null) output.close();

                } catch (Exception e) {

                    e.printStackTrace();

                }

                try {

                    if (writer != null) writer.close();

                } catch (Exception e) {

                    writer.close();
                }
            }

        } catch (Exception e) {

            e.printStackTrace();

        }
    }

    @NonNull
    @Override
    public UsbCameraApi getUsbApi() {
        return usbCameraApi;
    }

    @NonNull
    @Override
    public UVCCameraTextureView getUvcView() { return uvcView; }

    @NonNull
    @Override
    public WifiHelper getWifiHelper() { return wifiHelper; }

    @NonNull
    @Override
    public GoProApi getGoProApi() {
        return goProApi;
    }

    @NonNull
    @Override
    public FfmpegHelper getFfmpegHelper() {
        return ffmpegHelper;
    }
    @NonNull
    @Override
    public CameraXFacade getCameraXFacade() {
        return cameraXFacade;
    }

    @NonNull
    @Override
    public CanonApi getCanonApi() {
        return canonApi;
    }

    @NonNull
    @Override
    public NixSensorHelper getNixSensorHelper() { return nixSensorHelper; }

    @Override
    public void onSearchResultsClicked(String unique, String range, String plot, boolean reload) {
        searchUnique = unique;
        searchRange = range;
        searchPlot = plot;
        searchReload = reload;
    }

    @Override
    public void onRotationEvent(@NonNull SensorHelper.RotationModel rotation) {
        this.rotationModel = rotation;
    }

    @Nullable
    @Override
    public SensorHelper.RotationModel getRotationRelativeToDevice() {
        return rotationModel;
    }

    @Override
    public void onGravityRotationChanged(@NonNull SensorHelper.RotationModel rotationModel) {
        this.gravityRotationModel = rotationModel;
    }

    @Nullable
    public SensorHelper.RotationModel getDeviceTilt() {
        return gravityRotationModel;
    }

    @Override
    public void askUserSendCrashReport(@NonNull Exception e) {
        if (getWindow().isActive()) {
            try {
                if (dialogCrashReport != null) {
                    dialogCrashReport.dismiss();
                }

                dialogCrashReport = new AlertDialog.Builder(this, R.style.AppAlertDialog)
                        .setTitle(getString(R.string.dialog_crash_report_title))
                        .setMessage(getString(R.string.dialog_crash_report_message))
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            sendCrashReport(e);
                            dialog.dismiss();
                        })
                        .setNegativeButton(android.R.string.no, (dialog, which) -> {
                            FirebaseCrashlytics.getInstance().recordException(e);
                            dialog.dismiss();
                        })
                        .create();

                dialogCrashReport.show();

            } catch (Exception ignore) {
            }
        }
    }

    private void sendCrashReport(Exception e) {

        try {

            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();

            crashlytics.setCrashlyticsCollectionEnabled(true);

            int studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0);
            Log.e(TAG, "Current Study ID: " + studyId);

            CustomKeysAndValues.Builder builder = new CustomKeysAndValues.Builder()
                    .putString("Current Study ID", Integer.toString(studyId));

            int count = 0;
            FieldObject[] fieldObjects = database.getAllFieldObjects().toArray(new FieldObject[0]);
            for (FieldObject fo : fieldObjects) {
                Log.e(TAG, "Field ID: " + count + " " + fo.getStudyId());
                Log.e(TAG, "Field Name: " + count + " " + fo.getName());
                Log.e(TAG, "Field Unique ID: " + count + " " + fo.getUniqueId());

                builder.putString("Field ID " + count, Integer.toString(fo.getStudyId()));
                builder.putString("Field Name " + count, fo.getName());
                builder.putString("Field Unique ID " + count, fo.getUniqueId());

                List<String> attributes = Arrays.asList(database.getAllObservationUnitAttributeNames(fo.getStudyId()));
                Log.e(TAG, attributes.toString());
                builder.putString("Observation Unit Attributes " + count, attributes.toString());

                count = count + 1;
            }

            crashlytics.setCustomKeys(builder.build());

            crashlytics.recordException(e);

            crashlytics.sendUnsentReports();

        } catch (Exception ex) {

            Log.e(TAG, "Error logging study entry attributes: " + ex);

        }
    }

    /**
     * a function that starts the crop activity and sends it required intent data
     */
    public void startCropActivity(String traitId, Uri uri) {
         try {
             Intent intent = new Intent(this, CropImageActivity.class);
             intent.putExtra(CropImageFragment.EXTRA_TRAIT_ID, Integer.parseInt(traitId));
             intent.putExtra(CropImageFragment.EXTRA_IMAGE_URI, uri.toString());
             cameraXFacade.unbind();
             startActivityForResult(intent, REQUEST_CROP_FINISHED_CODE);
         } catch (Exception e) {
             e.printStackTrace();
         }
     }

    public void requestAndCropImage() {
        try {
            Intent intent = new Intent(this, CameraActivity.class);
            intent.putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_CROP);
            intent.putExtra(CameraActivity.EXTRA_TRAIT_ID, Integer.parseInt(getCurrentTrait().getId()));
            intent.putExtra(CameraActivity.EXTRA_STUDY_ID, String.valueOf(preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)));
            intent.putExtra(CameraActivity.EXTRA_OBS_UNIT, getObservationUnit());
            cameraXFacade.unbind();
            startActivityForResult(intent, REQUEST_CROP_IMAGE_CODE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * shows an alert dialog that asks user to define a crop region
     */
    public void showCropDialog(String traitId, Uri uri) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);
            builder.setTitle(R.string.dialog_crop_title);
            builder.setMessage(R.string.dialog_crop_message);
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                startCropActivity(traitId, uri);
            });
            builder.setNegativeButton(android.R.string.no, (dialog, which) -> {
                dialog.dismiss();
            });
            builder.create().show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NonNull
    @Override
    public SpectralViewModel getSpectralViewModel() {
        return spectralViewModel;
    }

    @NonNull
    public BLEScanner getBleScanner() {
        return bleScanner;
    }

    @NonNull
    public GreenSeekerGattManager getGreenSeekerGattManager() {
        return greenSeekerGattManager;
    }

    @NonNull
    public ScaleGattManager getScaleGattManager() {
        return scaleGattManager;
    }

    @Override
    public void updateNumberOfObservations() {
        refreshRepeatedValuesToolbarIndicator();
    }

    private void setupCollectInsets() {
        View rootView = findViewById(android.R.id.content);

        View bottomContent = findViewById(R.id.toolbarBottomCompose);

        InsetHandler.INSTANCE.setupInsetsWithBottomBar(rootView, toolbar, bottomContent);
    }

    public void showMediaConfirmDialog(String mediaType, String mediaPath) {
         if (mediaType == null || mediaPath == null) return;
         try {

            try {

                ObservationModel model = getCurrentObservation();

                if (model == null) {
                    Utils.makeToast(this, getString(R.string.no_observation));
                    return;
                }

                com.fieldbook.tracker.ui.MediaPreviewDialogFragment.Companion.newInstance(String.valueOf(model.getInternal_id_observation()), mediaType, mediaPath)
                        .show(getSupportFragmentManager(), "media_preview");

            } catch (Exception e) {
                e.printStackTrace();
            }
         } catch (Exception e) {
             e.printStackTrace();
         }
     }

    public void onMediaConfirmFromDialog(String obsId, String mediaType, String mediaPath) {
        try {

            ObservationModel obs = database.getObservationById(obsId);
            if (obs == null) {
                Utils.makeToast(this, getString(R.string.no_observations_warning));
                return;
            } else {
                obs.getValue();
            }

            File f = new File(mediaPath);
            String uri = Uri.fromFile(f).toString();
            if (f.exists()) {
                TraitObject currentTrait = getCurrentTrait();
                if (currentTrait != null) {
                    try {
                        String traitName = currentTrait.getName();
                        String sanitizedTraitName = com.fieldbook.tracker.utilities.FileUtil.sanitizeFileName(traitName);
                        DocumentFile traitPhotos = DocumentTreeUtil.Companion.getFieldMediaDirectory(this, sanitizedTraitName);
                        if (traitPhotos != null) {
                            String srcName = f.getName();
                            String ext = "";
                            int dot = srcName.lastIndexOf('.');
                            if (dot > 0) ext = srcName.substring(dot);
                            DateTimeFormatter internalTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZZZZZ");
                            String destName = sanitizedTraitName + "_" + getCRange().uniqueId + "_"
                                    + FileUtil.sanitizeFileName(OffsetDateTime.now().format(internalTimeFormatter)) + ext;
                            DocumentFile dest = traitPhotos.createFile("*/*", destName);
                            if (dest != null) {
                                InputStream in = null;
                                OutputStream out = null;
                                try {
                                    in = new FileInputStream(f);
                                    out = getContentResolver().openOutputStream(dest.getUri());
                                    if (out == null) throw new IOException("Unable to open output stream for destination file");
                                    byte[] buf = new byte[8192];
                                    int len;
                                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                                    out.flush();
                                    try { f.delete(); } catch (Exception ignore) {}
                                    uri = dest.getUri().toString();

                                } finally {
                                    try { if (in != null) in.close(); } catch (Exception ignore) {}
                                    try { if (out != null) out.close(); } catch (Exception ignore) {}
                                }
                            }
                        }
                    } catch (Exception ex) { ex.printStackTrace(); }
                }
            }

            if (mediaType.startsWith("photo")) {
                obs.setPhoto_uri(uri);
            } else if ("audio".equals(mediaType)) {
                obs.setAudio_uri(uri);
            } else {
                obs.setVideo_uri(uri);
            }

            database.updateObservationMediaUris(obs);

            initToolbars();

        } catch (Exception e) { e.printStackTrace(); }
    }

    // Called by MediaPreviewDialogFragment when user cancels (Delete temp file)
    public void onMediaCancelFromDialog(String mediaPath) {
        try { new File(mediaPath).delete(); } catch (Exception ignore) {}
    }
}
