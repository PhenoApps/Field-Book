package com.fieldbook.tracker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.fieldbook.tracker.barcodes.*;
import com.fieldbook.tracker.layoutConfig.SelectorLayoutConfigurator;
import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.search.*;
import com.fieldbook.tracker.traits.*;
import com.fieldbook.tracker.tutorial.*;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.utilities.ExpandableHeightGridView;
import com.fieldbook.tracker.utilities.GPSTracker;
import com.fieldbook.tracker.utilities.GalleryImageAdapter;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.utilities.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

/**
 * All main screen logic resides here
 */

@SuppressLint("ClickableViewAccessibility")
public class MainActivity extends AppCompatActivity implements OnClickListener {

    /**
     * Other variables
     */

    private SharedPreferences ep;
    private int paging;

    String inputPlotId = "";
    public int[] rangeID;

    AlertDialog goToId;

    int delay = 100;
    int count = 1;

    public static boolean searchReload;

    private static String displayColor = "#d50000";

    private static Object lock;

    private static HashMap newTraits;

    public static String searchRange;
    public static String searchPlot;

    public static RangeObject cRange;
    private String lastRange = "";

    public static boolean reloadData;
    public static boolean partialReload;

    public static Activity thisActivity;

    private TraitObject currentTrait;

    private static String TAG = "Field Book";
    private Handler repeatHandler;

    /**
     * Main screen elements
     */

    private Menu systemMenu;

    private String[] prefixTraits;

    private SelectorLayoutConfigurator selectorLayoutConfigurator;

    private TextView rangeName;
    private TextView plotName;

    private EditText range;
    private EditText plot;
    private TextView tvRange;
    private TextView tvPlot;

    private Spinner traitType;
    private TextView traitDetails;

    private ImageView rangeLeft;
    private ImageView rangeRight;

    private ImageView traitLeft;
    private ImageView traitRight;

    /**
     * Trait-related elements
     */
    private ArrayList<Drawable> drawables;
    private Gallery photo;
    private GalleryImageAdapter photoAdapter;
    String mCurrentPhotoPath;
    private ArrayList<String> photoLocation;

    private ImageView eImg;

    private MediaRecorder mRecorder;
    private MediaPlayer mPlayer;
    private File mRecordingLocation;
    private ImageButton doRecord;
    private boolean mRecording;
    private boolean mListening = false;

    private TextView month;
    private TextView day;
    private String date = "2000-01-01";
    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private SeekBar seekBar;
    private OnSeekBarChangeListener seekListener;

    private TextView counterTv;

    Button rust0, rust5, rust10, rust15, rust20, rust25, rust30, rust35, rust40, rust45, rust50, rust55, rust60, rust65, rust70, rust75, rust80, rust85, rust90, rust95, rust100, rustR, rustM, rustS, rustDelim;

    final Button buttonArray[] = new Button[12];

    ExpandableHeightGridView gridMultiCat;
    Boolean buttonsCreated;

    private EditText etCurVal;
    private TextWatcher cvNum;
    private TextWatcher cvText;

    private Handler mHandler = new Handler();
    private InputMethodManager imm;

    ImageButton deleteValue;
    ImageButton missingValue;

    SensorManager sensorManager ;
    Sensor accelerometer;
    Sensor magnetometer;

    TextView pitchTv;
    TextView rollTv;
    TextView azimutTv;
    SensorEventListener mEventListener;

    /**
     * Trait layouts
     */
    LinearLayout traitNumeric;
    LinearLayout traitCategorical;
    LinearLayout traitPercent;
    LinearLayout traitDate;
    LinearLayout traitBoolean;
    LinearLayout traitText;
    LinearLayout traitPhoto;
    LinearLayout traitCounter;
    LinearLayout traitAudio;
    LinearLayout traitDiseaseRating;
    LinearLayout traitMulticat;
    LinearLayout traitLocation;
    LinearLayout traitAngle;

    private Boolean dataLocked = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        cRange = new RangeObject();
        cRange.plot = "";
        cRange.plot_id = "";
        cRange.range = "";

        loadScreen();
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
        imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);



        range = findViewById(R.id.range);
        plot = findViewById(R.id.plot);

        tvRange = findViewById(R.id.tvRange);
        tvPlot = findViewById(R.id.tvPlot);

        selectorLayoutConfigurator = new SelectorLayoutConfigurator(this, ep.getInt(PreferencesActivity.INFOBAR_NUMBER, 3), (RecyclerView) findViewById(R.id.selectorList));

        traitBoolean = findViewById(R.id.booleanLayout);
        traitAudio = findViewById(R.id.audioLayout);
        traitCategorical = findViewById(R.id.categoricalLayout);
        traitDate = findViewById(R.id.dateLayout);
        traitNumeric = findViewById(R.id.numericLayout);
        traitPercent = findViewById(R.id.percentLayout);
        traitText = findViewById(R.id.textLayout);
        traitPhoto = findViewById(R.id.photoLayout);
        traitCounter = findViewById(R.id.counterLayout);
        traitDiseaseRating = findViewById(R.id.diseaseLayout);
        traitMulticat = findViewById(R.id.multicatLayout);
        traitLocation = findViewById(R.id.locationLayout);
        traitAngle = findViewById(R.id.angleLayout);

        traitType = findViewById(R.id.traitType);
        newTraits = new HashMap();
        traitDetails = findViewById(R.id.traitDetails);

        range.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                // do not do bit check on event, crashes keyboard
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    try {
                        moveToSearch("range",rangeID,range.getText().toString(),null,null);
                        imm.hideSoftInputFromWindow(range.getWindowToken(), 0);
                    } catch (Exception ignore) {
                    }
                    return true;
                }

                return false;
            }
        });

        plot.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                // do not do bit check on event, crashes keyboard
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    try {
                        moveToSearch("plot",rangeID,null,plot.getText().toString(),null);
                        imm.hideSoftInputFromWindow(plot.getWindowToken(), 0);
                    } catch (Exception ignore) {
                    }
                    return true;
                }

                return false;
            }
        });

        plot.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                plot.setCursorVisible(true);
            }
        });

        range.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                range.setCursorVisible(true);
            }
        });

        // Current value display
        etCurVal = findViewById(R.id.etCurVal);

        doRecord = traitAudio.findViewById(R.id.record);
        doRecord.setOnClickListener(this);

        ImageButton capture = traitPhoto.findViewById(R.id.capture);
        capture.setOnClickListener(this);
        photo = traitPhoto.findViewById(R.id.photo);

        etCurVal.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView exampleView, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    rangeRight.performClick();
                    return true;
                }

                return false;
            }
        });

        // Validates the text entered for numeric format
        //todo get rid of this- validate/delete in next/last plot
        cvNum = new TextWatcher() {

            public void afterTextChanged(final Editable en) {
                Timer timer = new Timer();
                final long DELAY = 750; // in ms

                try {
                    final double val = Double.parseDouble(etCurVal.getText().toString());
                    timer.cancel();
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        public void run() {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    if (currentTrait.minimum.length() > 0) {
                                        if (val < Double.parseDouble(currentTrait.minimum)) {
                                            en.clear();
                                            removeTrait(currentTrait.trait);
                                        }
                                    }
                                }
                            });
                        }
                    }, DELAY);

                    if (currentTrait.maximum.length() > 0) {
                        if (val > Double.parseDouble(currentTrait.maximum)) {
                            makeToast(getString(R.string.trait_error_maximum_value) + " " + currentTrait.maximum);
                            en.clear();
                            removeTrait(currentTrait.trait);
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG,"" + e.getMessage());
                }

                if (en.toString().length() > 0) {
                    if (newTraits != null & currentTrait != null)
                        updateTrait(currentTrait.trait, currentTrait.format, en.toString());
                } else {
                    if (newTraits != null & currentTrait != null)
                        newTraits.remove(currentTrait.trait);
                }
            }

            public void beforeTextChanged(CharSequence arg0, int arg1,
                                          int arg2, int arg3) {
            }

            public void onTextChanged(CharSequence arg0, int arg1, int arg2,
                                      int arg3) {
            }

        };

        // Validates the text entered for text format
        cvText = new TextWatcher() {
            public void afterTextChanged(Editable en) {

                if (en.toString().length() >= 0) {
                    if (newTraits != null & currentTrait != null)
                        updateTrait(currentTrait.trait, currentTrait.format, en.toString());
                } else {
                    if (newTraits != null & currentTrait != null)
                        newTraits.remove(currentTrait.trait);
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

        // Progress bar
        seekBar = traitPercent.findViewById(R.id.seekbar);
        seekBar.setMax(100);

        seekListener = new OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar sb, int progress, boolean arg2) {
                if (sb.getProgress() < Integer.parseInt(currentTrait.minimum))
                    sb.setProgress(Integer.parseInt(currentTrait.minimum));

                etCurVal.setText(String.valueOf(sb.getProgress()));
            }

            public void onStartTrackingTouch(SeekBar arg0) {
            }

            public void onStopTrackingTouch(SeekBar arg0) {
                updateTrait(currentTrait.trait, "percent", String.valueOf(seekBar.getProgress()));
            }
        };

        // Updates the progressbar value on screen and in memory hashmap
        seekBar.setOnSeekBarChangeListener(seekListener);

        month = traitDate.findViewById(R.id.mth);
        day = traitDate.findViewById(R.id.day);

        rangeName = findViewById(R.id.rangeName);
        plotName = findViewById(R.id.plotName);

        ImageButton getLocation = traitLocation.findViewById(R.id.getLocationBtn);

        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        pitchTv = traitAngle.findViewById(R.id.pitch);
        rollTv = traitAngle.findViewById(R.id.roll);
        azimutTv = traitAngle.findViewById(R.id.azimuth);

        mEventListener = new SensorEventListener() {
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }

            float[] mGravity;
            float[] mGeomagnetic;
            Float azimut;
            Float pitch;
            Float roll;

            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                    mGravity = event.values;
                if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                    mGeomagnetic = event.values;
                if (mGravity != null && mGeomagnetic != null) {
                    float R[] = new float[9];
                    float I[] = new float[9];
                    boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
                    if (success) {
                        float orientation[] = new float[3];
                        SensorManager.getOrientation(R, orientation);
                        azimut = orientation[0]; // orientation contains: azimut, pitch and roll
                        pitch = orientation[1];
                        roll = orientation[2];

                        pitchTv.setText(Double.toString(Math.toDegrees(pitch)));
                        rollTv.setText(Double.toString(Math.toDegrees(roll)));
                        azimutTv.setText(Double.toString(Math.toDegrees(azimut)));
                    }
                }
            }
        };

        Button addDayBtn = traitDate.findViewById(R.id.addDateBtn);
        Button minusDayBtn = traitDate.findViewById(R.id.minusDateBtn);
        ImageButton saveDayBtn = traitDate.findViewById(R.id.enterBtn);

        Button addCounterBtn = traitCounter.findViewById(R.id.addBtn);
        Button minusCounterBtn = traitCounter.findViewById(R.id.minusBtn);
        counterTv = traitCounter.findViewById(R.id.curCount);

        // Multicat
        gridMultiCat = traitMulticat.findViewById(R.id.catGrid);
        gridMultiCat.setExpanded(true);
        buttonsCreated = false;

        // Numeric
        Button k1 = traitNumeric.findViewById(R.id.k1);
        Button k2 = traitNumeric.findViewById(R.id.k2);
        Button k3 = traitNumeric.findViewById(R.id.k3);
        Button k4 = traitNumeric.findViewById(R.id.k4);
        Button k5 = traitNumeric.findViewById(R.id.k5);
        Button k6 = traitNumeric.findViewById(R.id.k6);
        Button k7 = traitNumeric.findViewById(R.id.k7);
        Button k8 = traitNumeric.findViewById(R.id.k8);
        Button k9 = traitNumeric.findViewById(R.id.k9);
        Button k10 = traitNumeric.findViewById(R.id.k10);
        Button k11 = traitNumeric.findViewById(R.id.k11);
        Button k12 = traitNumeric.findViewById(R.id.k12);
        Button k13 = traitNumeric.findViewById(R.id.k13);
        Button k14 = traitNumeric.findViewById(R.id.k14);
        Button k15 = traitNumeric.findViewById(R.id.k15);
        Button k16 = traitNumeric.findViewById(R.id.k16);

        k1.setOnClickListener(this);
        k2.setOnClickListener(this);
        k3.setOnClickListener(this);
        k4.setOnClickListener(this);
        k5.setOnClickListener(this);
        k6.setOnClickListener(this);
        k7.setOnClickListener(this);
        k8.setOnClickListener(this);
        k9.setOnClickListener(this);
        k10.setOnClickListener(this);
        k11.setOnClickListener(this);
        k12.setOnClickListener(this);
        k13.setOnClickListener(this);
        k14.setOnClickListener(this);
        k15.setOnClickListener(this);
        k16.setOnClickListener(this);

        k16.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                etCurVal.removeTextChangedListener(cvNum);
                etCurVal.setText("");
                removeTrait(currentTrait.trait);
                etCurVal.addTextChangedListener(cvNum);
                return false;
            }
        });

        rust0= traitDiseaseRating.findViewById(R.id.rust0);
        rust5= traitDiseaseRating.findViewById(R.id.rust5);
        rust10= traitDiseaseRating.findViewById(R.id.rust10);
        rust15= traitDiseaseRating.findViewById(R.id.rust15);
        rust20= traitDiseaseRating.findViewById(R.id.rust20);
        rust25= traitDiseaseRating.findViewById(R.id.rust25);
        rust30= traitDiseaseRating.findViewById(R.id.rust30);
        rust35= traitDiseaseRating.findViewById(R.id.rust35);
        rust40= traitDiseaseRating.findViewById(R.id.rust40);
        rust45= traitDiseaseRating.findViewById(R.id.rust45);
        rust50= traitDiseaseRating.findViewById(R.id.rust50);
        rust55= traitDiseaseRating.findViewById(R.id.rust55);
        rust60= traitDiseaseRating.findViewById(R.id.rust60);
        rust65= traitDiseaseRating.findViewById(R.id.rust65);
        rust70= traitDiseaseRating.findViewById(R.id.rust70);
        rust75= traitDiseaseRating.findViewById(R.id.rust75);
        rust80= traitDiseaseRating.findViewById(R.id.rust80);
        rust85= traitDiseaseRating.findViewById(R.id.rust85);
        rust90= traitDiseaseRating.findViewById(R.id.rust90);
        rust95= traitDiseaseRating.findViewById(R.id.rust95);
        rust100= traitDiseaseRating.findViewById(R.id.rust100);
        rustR= traitDiseaseRating.findViewById(R.id.rustR);
        rustM= traitDiseaseRating.findViewById(R.id.rustM);
        rustS= traitDiseaseRating.findViewById(R.id.rustS);
        rustDelim =  traitDiseaseRating.findViewById(R.id.rustDelim);

        Button[] rustBtnArray = new Button[]{rust0,rust5,rust10,rust15,rust20,rust25,rust30,rust35,rust40,rust45,rust50,rust55,rust60,rust65,rust70,rust75,rust80,rust85,rust90,rust95,rust100};
        List<String> temps = new ArrayList<>();
        List<String> tempsNoFile = Arrays.asList("0","5","10","15","20","25","30","35","40","45","50","55","60","65","70","75","80","85","90","95","100");
        String token1;
        Scanner inFile1 = null;

        try {
            inFile1 = new Scanner(new File(Constants.TRAITPATH + "/severity.txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if(inFile1!=null) {
            while (inFile1.hasNext()) {
                token1 = inFile1.next();
                temps.add(token1);
            }
            inFile1.close();

            //Trim list to 21 since only 21 buttons
            int k = temps.size();
            if ( k > 21 ) {
                temps.subList(21, k).clear();
            }

            for (int i = 0; i < temps.size(); i++) {
                rustBtnArray[i].setVisibility(View.VISIBLE);
                rustBtnArray[i].setText(temps.get(i));
                rustBtnArray[i].setOnClickListener(this);
            }
        } else {
            for (int i = 0; i < tempsNoFile.size(); i++) {
                rustBtnArray[i].setVisibility(View.VISIBLE);
                rustBtnArray[i].setText(tempsNoFile.get(i));
                rustBtnArray[i].setOnClickListener(this);
            }
        }

        rustR.setOnClickListener(this);
        rustM.setOnClickListener(this);
        rustS.setOnClickListener(this);
        rustDelim.setOnClickListener(this);

        String primaryName = ep.getString("ImportFirstName", getString(R.string.search_results_dialog_range));
        String secondaryName = ep.getString("ImportSecondName", getString(R.string.search_results_dialog_plot));

        if(primaryName.length()>10) {
            primaryName = primaryName.substring(0,9) + ":";
        }

        if(secondaryName.length()>10) {
            secondaryName = secondaryName.substring(0,9) + ":";
        }

        rangeName.setText(primaryName);
        plotName.setText(secondaryName);

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

        // Add day
        addDayBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Calendar calendar = Calendar.getInstance();

                //Parse date
                try {
                    calendar.setTime(dateFormat.parse(date));
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                // Add day
                calendar.add(Calendar.DATE, 1);
                date = dateFormat.format(calendar.getTime());

                // Set text
                day.setText(Integer.toString(calendar.get(Calendar.DAY_OF_MONTH)));
                month.setText(getMonthForInt(calendar.get(Calendar.MONTH)));

                // Change text color
                if (newTraits.containsKey(currentTrait.trait)) {
                    month.setTextColor(Color.BLUE);
                    day.setTextColor(Color.BLUE);
                } else {
                    month.setTextColor(Color.BLACK);
                    day.setTextColor(Color.BLACK);
                }
            }
        });

        // Minus day
        minusDayBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Calendar calendar = Calendar.getInstance();

                //Parse date
                try {
                    calendar.setTime(dateFormat.parse(date));
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                //Subtract day, rewrite date
                calendar.add(Calendar.DATE,-1);
                date = dateFormat.format(calendar.getTime());

                //Set text
                day.setText(Integer.toString(calendar.get(Calendar.DAY_OF_MONTH)));
                month.setText(getMonthForInt(calendar.get(Calendar.MONTH)));

                // Change text color
                if (newTraits.containsKey(currentTrait.trait)) {
                    month.setTextColor(Color.BLUE);
                    day.setTextColor(Color.BLUE);
                } else {
                    month.setTextColor(Color.BLACK);
                    day.setTextColor(Color.BLACK);
                }
            }
        });

        // Saving date data
        saveDayBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Calendar calendar = Calendar.getInstance();

                //Parse date
                try {
                    calendar.setTime(dateFormat.parse(date));
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                if (ep.getBoolean(PreferencesActivity.USE_DAY_OF_YEAR, false)) {
                    updateTrait(currentTrait.trait, "date",String.valueOf(calendar.get(Calendar.DAY_OF_YEAR)));
                } else {
                    updateTrait(currentTrait.trait, "date",dateFormat.format(calendar.getTime()));
                }

                // Change the text color accordingly
                month.setTextColor(Color.parseColor(displayColor));
                day.setTextColor(Color.parseColor(displayColor));
            }
        });

        // Get Location
        getLocation.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                GPSTracker gps = new GPSTracker(thisActivity);
                String fullLocation = "";
                double lat;
                double lng;

                if (gps.canGetLocation()) { //GPS enabled
                    lat = gps.getLatitude(); // returns latitude
                    lng = gps.getLongitude(); // returns longitude
                    fullLocation = Utils.truncateDecimalString(String.valueOf(lat),8) + "; " + Utils.truncateDecimalString(String.valueOf(lng),8);
                } else {
                    Intent intent = new Intent(
                            Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                }
                etCurVal.setText(fullLocation);
                updateTrait(currentTrait.trait, "location", fullLocation);
            }
        });

        // Add counter
        addCounterBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                //TODO NullPointerException
                if(newTraits.containsKey(currentTrait.trait) && newTraits.get(currentTrait.trait).toString().equals("NA")) {
                    counterTv.setText("1");
                } else {
                    counterTv.setText(Integer.toString(Integer.parseInt(counterTv.getText().toString()) + 1));
                }
                updateTrait(currentTrait.trait, "counter", counterTv.getText().toString());
            }
        });

        // Minus counter
        minusCounterBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                //TODO NullPointerException
                if(newTraits.containsKey(currentTrait.trait) && newTraits.get(currentTrait.trait).toString().equals("NA")) {
                    counterTv.setText("-1");
                } else {
                    counterTv.setText(Integer.toString(Integer.parseInt(counterTv.getText().toString()) - 1));
                }
                updateTrait(currentTrait.trait, "counter", counterTv.getText().toString());
            }
        });

        buttonArray[0] = traitCategorical.findViewById(R.id.q1);
        buttonArray[1] = traitCategorical.findViewById(R.id.q2);
        buttonArray[2] = traitCategorical.findViewById(R.id.q3);
        buttonArray[3] = traitCategorical.findViewById(R.id.q4);
        buttonArray[4] = traitCategorical.findViewById(R.id.q5);
        buttonArray[5] = traitCategorical.findViewById(R.id.q6);
        buttonArray[6] = traitCategorical.findViewById(R.id.q7);
        buttonArray[7] = traitCategorical.findViewById(R.id.q8);
        buttonArray[8] = traitCategorical.findViewById(R.id.q9);
        buttonArray[9] = traitCategorical.findViewById(R.id.q10);
        buttonArray[10] = traitCategorical.findViewById(R.id.q11);
        buttonArray[11] = traitCategorical.findViewById(R.id.q12);

        // Clear all other color except this button's
        for (final Button btn : buttonArray) {
            // Functions to clear all other color except this button's
            btn.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    if (checkButton(btn)) {
                        return;
                    }
                    updateTrait(currentTrait.trait, currentTrait.format, btn.getText().toString());
                    setCategoricalButtons(buttonArray, btn);
                }

            });
        }

        eImg = traitBoolean.findViewById(R.id.eImg);

        // Boolean
        eImg.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                String val = newTraits.get(currentTrait.trait).toString();

                if (val.equalsIgnoreCase("false")) {
                    val = "true";
                    eImg.setImageResource(R.drawable.trait_boolean_true);
                } else {
                    val = "false";
                    eImg.setImageResource(R.drawable.trait_boolean_false);
                }

                updateTrait(currentTrait.trait, "boolean", val);
            }
        });

        rangeLeft = findViewById(R.id.rangeLeft);
        rangeRight = findViewById(R.id.rangeRight);

        rangeLeft.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        rangeLeft.setImageResource(R.drawable.main_entry_left_pressed);
                        rangeLeft.performClick();

                        if (repeatHandler != null) {
                            return true;
                        }
                        repeatHandler = new Handler();
                        repeatHandler.postDelayed(mActionLeft, 750);

                        delay = 100;
                        count = 1;

                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        rangeLeft.setImageResource(R.drawable.main_entry_left_unpressed);

                        if (repeatHandler == null) {
                            return true;
                        }
                        repeatHandler.removeCallbacks(mActionLeft);
                        repeatHandler = null;

                        repeatUpdate();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        rangeLeft.setImageResource(R.drawable.main_entry_left_unpressed);

                        repeatHandler.removeCallbacks(mActionLeft);
                        repeatHandler = null;

                        v.setTag(null); // mark btn as not pressed
                        break;
                }

                return true; // return true to prevent calling btn onClick handler
            }
        });

        // Go to previous range
        rangeLeft.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                moveEntryLeft();
            }
        });

        rangeRight.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        rangeRight.setImageResource(R.drawable.main_entry_right_pressed);
                        rangeRight.performClick();

                        if (repeatHandler != null) return true;
                        repeatHandler = new Handler();
                        repeatHandler.postDelayed(mActionRight, 750);

                        delay = 100;
                        count = 1;

                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        rangeRight.setImageResource(R.drawable.main_entry_right_unpressed);

                        if (repeatHandler == null) return true;
                        repeatHandler.removeCallbacks(mActionRight);
                        repeatHandler = null;

                        repeatUpdate();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        rangeRight.setImageResource(R.drawable.main_entry_right_unpressed);

                        repeatHandler.removeCallbacks(mActionRight);
                        repeatHandler = null;

                        v.setTag(null); // mark btn as not pressed
                        break;
                }
                return true; // return true to prevent calling btn onClick handler
            }
        });

        // Go to next range
        rangeRight.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                moveEntryRight();
            }
        });

        traitLeft = findViewById(R.id.traitLeft);
        traitRight = findViewById(R.id.traitRight);

        traitLeft.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        traitLeft.setImageResource(R.drawable.main_trait_left_arrow_pressed);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        traitLeft.setImageResource(R.drawable.main_trait_left_arrow_unpressed);
                    case MotionEvent.ACTION_CANCEL:
                        break;
                }

                return false; // return true to prevent calling btn onClick handler
            }
        });

        // Go to previous trait
        traitLeft.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                moveTrait("left");
            }
        });

        traitRight.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        traitRight.setImageResource(R.drawable.main_trait_right_pressed);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        traitRight.setImageResource(R.drawable.main_trait_right_unpressed);
                    case MotionEvent.ACTION_CANCEL:
                        break;
                }

                return false; // return true to prevent calling btn onClick handler
            }
        });

        // Go to next trait
        traitRight.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                moveTrait("right");
            }
        });
    }

    private void moveEntryLeft() {
        if (ep.getBoolean(PreferencesActivity.DISABLE_ENTRY_ARROW_LEFT, false) && !newTraits.containsKey(currentTrait.trait)) {
            playSound("error");
        } else {
            if (rangeID != null && rangeID.length > 0) {
                //index.setEnabled(true);

                // If ignore existing data is enabled, then skip accordingly
                if (ep.getBoolean(PreferencesActivity.HIDE_ENTRIES_WITH_DATA, false)) {
                    int pos = paging;

                    while (pos >= 0) {
                        pos -= 1;

                        if (pos < 1)
                            return;

                        if (!ConfigActivity.dt.getTraitExists(rangeID[pos - 1], currentTrait.trait,
                                currentTrait.format)) {
                            paging = pos;
                            break;
                        }
                    }
                } else {
                    paging -= 1;

                    if (paging < 1)
                        paging = rangeID.length;
                }

                // Refresh onscreen controls
                cRange = ConfigActivity.dt.getRange(rangeID[paging - 1]);

                saveLastPlot();

                displayRange(cRange);

                if (ep.getBoolean(PreferencesActivity.PRIMARY_SOUND, false)) {
                    if (!cRange.range.equals(lastRange) && !lastRange.equals("")) {
                        lastRange = cRange.range;
                        playSound("plonk");
                    }
                }

                newTraits = (HashMap) ConfigActivity.dt.getUserDetail(cRange.plot_id)
                        .clone();

                initWidgets(true);
            }
        }
    }

    private void moveEntryRight() {
        if(ep.getBoolean(PreferencesActivity.DISABLE_ENTRY_ARROW_RIGHT, false) && !newTraits.containsKey(currentTrait.trait)) {
            playSound("error");
        } else {
            if (rangeID != null && rangeID.length > 0) {
                //index.setEnabled(true);

                // If ignore existing data is enabled, then skip accordingly
                if (ep.getBoolean(PreferencesActivity.HIDE_ENTRIES_WITH_DATA, false)) {
                    int pos = paging;

                    if (pos == rangeID.length) {
                        pos = 1;
                        return;
                    }

                    while (pos <= rangeID.length) {
                        pos += 1;

                        if (pos > rangeID.length) {
                            pos = 1;
                            return;
                        }

                        if (!ConfigActivity.dt.getTraitExists(rangeID[pos - 1], currentTrait.trait,
                                currentTrait.format)) {
                            paging = pos;
                            break;
                        }
                    }
                } else {
                    paging += 1;

                    if (paging > rangeID.length)
                        paging = 1;
                }

                // Refresh onscreen controls
                cRange = ConfigActivity.dt.getRange(rangeID[paging - 1]);

                saveLastPlot();

                displayRange(cRange);
                if (ep.getBoolean(PreferencesActivity.PRIMARY_SOUND, false)) {
                    if (!cRange.range.equals(lastRange) && !lastRange.equals("")) {
                        lastRange = cRange.range;
                        playSound("plonk");
                    }
                }
                newTraits = (HashMap) ConfigActivity.dt.getUserDetail(cRange.plot_id)
                        .clone();

                initWidgets(true);
            }
        }
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

    private void moveTrait(String direction) {
        int pos = 0;

        if(!validateData()) {
            return;
        }

        // Force the keyboard to be hidden to handle bug
        try {
            imm.hideSoftInputFromWindow(etCurVal.getWindowToken(), 0);
        } catch (Exception ignore) {
        }

        if(direction.equals("left")) {
            pos = traitType.getSelectedItemPosition() - 1;

            if (pos < 0) {
                pos = traitType.getCount() - 1;

                if (ep.getBoolean(PreferencesActivity.CYCLING_TRAITS_ADVANCES, false))
                    rangeLeft.performClick();
            }
        }

        if(direction.equals("right")) {
            pos = traitType.getSelectedItemPosition() + 1;

            if (pos > traitType.getCount() - 1) {
                pos = 0;

                if (ep.getBoolean(PreferencesActivity.CYCLING_TRAITS_ADVANCES, false))
                    rangeRight.performClick();
            }
        }

        traitType.setSelection(pos);
    }

    //TODO
    private boolean validateData() {
        //get rules

        //get data

        return true;
    }

    private void initToolbars() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Toolbar toolbarBottom = findViewById(R.id.toolbarBottom);

        missingValue = toolbarBottom.findViewById(R.id.missingValue);
        missingValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateTrait(currentTrait.trait, currentTrait.format, "NA");
                etCurVal.setText("NA");

                if (currentTrait.format.equals("date")) {
                    month.setText("");
                    day.setText("NA");
                }

                if (currentTrait.format.equals("counter")) {
                    counterTv.setText("NA");
                }

                if (currentTrait.format.equals("photo")) {

                }
            }
        });

        deleteValue = toolbarBottom.findViewById(R.id.deleteValue);
        deleteValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (currentTrait.format) {
                    case "categorical":
                        newTraits.remove(currentTrait.trait);
                        ConfigActivity.dt.deleteTrait(cRange.plot_id, currentTrait.trait);
                        setCategoricalButtons(buttonArray, null);
                        break;
                    case "percent":
                        seekBar.setOnSeekBarChangeListener(null);
                        etCurVal.setText("");
                        seekBar.setProgress(0);
                        etCurVal.setTextColor(Color.BLACK);

                        if (currentTrait.defaultValue != null
                                && currentTrait.defaultValue.length() > 0) {
                            etCurVal.setText(currentTrait.defaultValue);
                            seekBar.setProgress(Integer
                                    .valueOf(currentTrait.defaultValue));
                        }

                        updateTrait(currentTrait.trait, "percent", String.valueOf(seekBar.getProgress()));
                        seekBar.setOnSeekBarChangeListener(seekListener);
                        break;
                    case "date":
                        removeTrait(currentTrait.trait);

                        final Calendar c = Calendar.getInstance();
                        date = dateFormat.format(c.getTime());

                        month.setTextColor(Color.BLACK);
                        day.setTextColor(Color.BLACK);

                        //This is used to persist moving between months
                        month.setText(getMonthForInt(c.get(Calendar.MONTH)));
                        day.setText(String.format("%02d", c.get(Calendar.DAY_OF_MONTH)));
                        break;
                    case "boolean":
                        if (currentTrait.defaultValue.trim().toLowerCase().equals("true")) {
                            updateTrait(currentTrait.trait, "boolean", "true");
                            eImg.setImageResource(R.drawable.trait_boolean_true);
                        } else {
                            updateTrait(currentTrait.trait, "boolean", "false");
                            eImg.setImageResource(R.drawable.trait_boolean_false);
                        }
                        break;
                    case "photo":
                        deletePhotoWarning();
                        break;
                    case "counter":
                        removeTrait(currentTrait.trait);
                        counterTv.setText("0");
                        break;
                    case "disease rating":
                        etCurVal.removeTextChangedListener(cvNum);
                        etCurVal.setText("");
                        removeTrait(currentTrait.trait);
                        etCurVal.addTextChangedListener(cvNum);
                        break;
                    case "rust rating":
                        etCurVal.removeTextChangedListener(cvNum);
                        etCurVal.setText("");
                        removeTrait(currentTrait.trait);
                        etCurVal.addTextChangedListener(cvNum);
                        break;
                    case "audio":
                        deleteRecording();
                        removeTrait(currentTrait.trait);
                        etCurVal.setText("");
                        mRecording = false;
                        doRecord.setImageResource(R.drawable.trait_audio);
                        mListening = false;
                        mRecording = false;
                        break;
                    default:
                        newTraits.remove(currentTrait.trait);
                        ConfigActivity.dt.deleteTrait(cRange.plot_id, currentTrait.trait);
                        etCurVal.setText("");
                        break;
                }
            }
        });

    }

    Runnable mActionRight = new Runnable() {
        @Override public void run() {
            repeatRight();

            if((count % 5) ==0 ) {
                if(delay>20) {
                    delay = delay - 10;
                }
            }

            count++;
            if(repeatHandler!=null) {
                repeatHandler.postDelayed(this, delay);
            }
        }
    };

    Runnable mActionLeft = new Runnable() {
        @Override public void run() {
            repeatLeft();

            if((count % 5) ==0 ) {
                if(delay>20) {
                    delay = delay - 10;
                }
            }

            count++;

            if(repeatHandler!=null) {
                repeatHandler.postDelayed(this, delay);
            }
        }
    };

    private void setCategoricalButtons(Button[] buttonList, Button choice) {
        for (Button aButtonList : buttonList) {
            if (aButtonList == choice) {
                aButtonList.setTextColor(Color.parseColor(displayColor));
                aButtonList.setBackgroundColor(getResources().getColor(R.color.button_pressed));
            } else {
                aButtonList.setTextColor(Color.BLACK);
                aButtonList.setBackgroundColor(getResources().getColor(R.color.button_normal));
            }
        }
    }

    private Boolean checkButton(Button button) {
        String curCat = "";
        if (newTraits.containsKey(currentTrait.trait)) {
            curCat = newTraits.get(currentTrait.trait)
                    .toString();
        }
        if (button.getText().toString().equals(curCat)) {
            newTraits.remove(currentTrait.trait);
            ConfigActivity.dt.deleteTrait(cRange.plot_id, currentTrait.trait);
            setCategoricalButtons(buttonArray, null);
            return true;
        }
        return false;
    }

    // Simulate range left key press
    private void repeatLeft() {
        if (rangeID != null && rangeID.length > 0) {

            // If ignore existing data is enabled, then skip accordingly
            if (ep.getBoolean(PreferencesActivity.HIDE_ENTRIES_WITH_DATA, false)) {
                int pos = paging;

                while (pos >= 0) {
                    pos -= 1;

                    if (pos < 1)
                        pos = rangeID.length;

                    if (!ConfigActivity.dt.getTraitExists(rangeID[pos - 1], currentTrait.trait,
                            currentTrait.format)) {
                        paging = pos;
                        break;
                    }
                }
            } else {
                paging -= 1;

                if (paging < 1)
                    paging = rangeID.length;
            }

            // Refresh onscreen controls
            cRange = ConfigActivity.dt.getRange(rangeID[paging - 1]);

            saveLastPlot();

            if (cRange.plot_id.length() == 0)
                return;

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

                            ;
                        });
                    } catch (Exception ignore) {
                    }
                }
            }

            displayRange(cRange);

            newTraits = (HashMap) ConfigActivity.dt.getUserDetail(cRange.plot_id)
                    .clone();

            initWidgets(true);
        }
    }

    // Simulate range right key press
    private void repeatRight() {
        if (rangeID != null && rangeID.length > 0) {
            //index.setEnabled(true);

            // If ignore existing data is enabled, then skip accordingly
            if (ep.getBoolean(PreferencesActivity.HIDE_ENTRIES_WITH_DATA, false)) {
                int pos = paging;

                if (pos == rangeID.length) {
                    pos = 1;
                    return;
                }

                while (pos <= rangeID.length) {
                    pos += 1;

                    if (pos > rangeID.length) {
                        pos = 1;
                        return;
                    }

                    if (!ConfigActivity.dt.getTraitExists(rangeID[pos - 1], currentTrait.trait,
                            currentTrait.format)) {
                        paging = pos;
                        break;
                    }
                }
            } else {
                paging += 1;

                if (paging > rangeID.length)
                    paging = 1;
            }

            // Refresh onscreen controls
            cRange = ConfigActivity.dt.getRange(rangeID[paging - 1]);
            saveLastPlot();

            if (cRange.plot_id.length() == 0)
                return;

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

            displayRange(cRange);
            newTraits = (HashMap) ConfigActivity.dt.getUserDetail(cRange.plot_id)
                    .clone();

            initWidgets(true);
        }
    }

    // This update should only be called after repeating keypress ends
    private void repeatUpdate() {
        if (rangeID == null)
            return;

        newTraits = (HashMap) ConfigActivity.dt.getUserDetail(cRange.plot_id)
                .clone();

        initWidgets(true);
    }

    // Updates the data shown in the dropdown
    private void displayRange(RangeObject cRange) {

        if (cRange == null)
            return;

        range.setText(cRange.range);
        plot.setText(cRange.plot);

        range.setCursorVisible(false);
        plot.setCursorVisible(false);

        tvRange.setText(cRange.range);
        tvPlot.setText(cRange.plot);
    }

    private void hideLayouts() {
        traitText.setVisibility(View.GONE);
        traitNumeric.setVisibility(View.GONE);
        traitPercent.setVisibility(View.GONE);
        traitDate.setVisibility(View.GONE);
        traitCategorical.setVisibility(View.GONE);
        traitBoolean.setVisibility(View.GONE);
        traitAudio.setVisibility(View.GONE);
        traitPhoto.setVisibility(View.GONE);
        traitCounter.setVisibility(View.GONE);
        traitDiseaseRating.setVisibility(View.GONE);
        traitMulticat.setVisibility(View.GONE);
        traitLocation.setVisibility(View.GONE);
        traitAngle.setVisibility(View.GONE);
    }

    // This is central to the application
    // Calling this function resets all the controls for traits, and picks one
    // to show based on the current trait data
    private void initWidgets(final boolean rangeSuppress) {
        // Reset dropdowns

        if (!ConfigActivity.dt.isTableEmpty(DataHelper.RANGE)) {
            selectorLayoutConfigurator.configureDropdownArray(cRange.plot_id);
        }

        if (prefixTraits != null) {
            final TextView traitDetails =  findViewById(R.id.traitDetails);

            traitDetails.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch(event.getAction()){
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

        // trait is unique, format is not

        String[] traits = ConfigActivity.dt.getVisibleTrait();

        int traitPosition;

        try {
            traitPosition = traitType.getSelectedItemPosition();
        } catch (Exception f) {
            traitPosition = 0;
        }

        if (traits != null) {
            ArrayAdapter<String> directionArrayAdapter = new ArrayAdapter<>(
                    this, R.layout.custom_spinnerlayout, traits);
            directionArrayAdapter
                    .setDropDownViewResource(R.layout.custom_spinnerlayout);
            traitType.setAdapter(directionArrayAdapter);

            traitType.setOnItemSelectedListener(new OnItemSelectedListener() {

                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int arg2, long arg3) {

                    // This updates the in memory hashmap from database
                    currentTrait = ConfigActivity.dt.getDetail(traitType.getSelectedItem()
                            .toString());

                    imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (!currentTrait.format.equals("text")) {
                        try {
                            imm.hideSoftInputFromWindow(etCurVal.getWindowToken(), 0);
                        } catch (Exception ignore) {
                        }
                    }

                    traitDetails.setText(currentTrait.details);

                    if (!rangeSuppress | !currentTrait.format.equals("numeric")) {
                        if (etCurVal.getVisibility() == TextView.VISIBLE) {
                            etCurVal.setVisibility(EditText.GONE);
                            etCurVal.setEnabled(false);
                        }
                    }

                    // All the logic is here to hide controls except for the current trait
                    // Checks in-memory hashmap
                    // Populate screen with in saved data
                    if (currentTrait.format.equals("text")) {
                        hideLayouts();
                        traitText.setVisibility(View.VISIBLE);

                        etCurVal.setVisibility(EditText.VISIBLE);
                        etCurVal.setSelection(etCurVal.getText().length());
                        etCurVal.setEnabled(true);

                        if (newTraits.containsKey(currentTrait.trait)) {
                            etCurVal.removeTextChangedListener(cvText);
                            etCurVal.setText(newTraits.get(currentTrait.trait).toString());
                            etCurVal.setTextColor(Color.parseColor(displayColor));
                            etCurVal.addTextChangedListener(cvText);
                            etCurVal.setSelection(etCurVal.getText().length());
                        } else {
                            etCurVal.removeTextChangedListener(cvText);
                            etCurVal.setText("");
                            etCurVal.setTextColor(Color.BLACK);

                            if (currentTrait.defaultValue != null && currentTrait.defaultValue.length() > 0) {
                                etCurVal.setText(currentTrait.defaultValue);
                                updateTrait(currentTrait.trait, currentTrait.format, etCurVal.getText().toString());
                            }

                            etCurVal.addTextChangedListener(cvText);
                            etCurVal.setSelection(etCurVal.getText().length());
                        }

                        // This is needed to fix a keyboard bug
                        mHandler.postDelayed(new Runnable() {
                            public void run() {
                                etCurVal.dispatchTouchEvent(MotionEvent.obtain(
                                        SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(),
                                        MotionEvent.ACTION_DOWN, 0, 0, 0));
                                etCurVal.dispatchTouchEvent(MotionEvent.obtain(
                                        SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(),
                                        MotionEvent.ACTION_UP, 0, 0, 0));
                                etCurVal.setSelection(etCurVal.getText().length());
                            }
                        }, 300);
                    } else if (currentTrait.format.equals("numeric")) {
                        hideLayouts();
                        traitNumeric.setVisibility(View.VISIBLE);

                        etCurVal.setVisibility(EditText.VISIBLE);

                        if (newTraits.containsKey(currentTrait.trait)) {
                            etCurVal.removeTextChangedListener(cvNum);
                            etCurVal.setText(newTraits.get(currentTrait.trait).toString());
                            etCurVal.setTextColor(Color.parseColor(displayColor));
                            etCurVal.addTextChangedListener(cvNum);
                        } else {
                            etCurVal.removeTextChangedListener(cvNum);
                            etCurVal.setText("");
                            etCurVal.setTextColor(Color.BLACK);

                            if (currentTrait.defaultValue != null && currentTrait.defaultValue.length() > 0) {
                                etCurVal.setText(currentTrait.defaultValue);
                                updateTrait(currentTrait.trait, currentTrait.format, etCurVal.getText().toString());
                            }

                            etCurVal.addTextChangedListener(cvNum);
                        }

                    } else if (currentTrait.format.equals("percent")) {
                        hideLayouts();
                        traitPercent.setVisibility(View.VISIBLE);

                        etCurVal.setVisibility(EditText.VISIBLE);
                        etCurVal.removeTextChangedListener(cvNum);
                        etCurVal.removeTextChangedListener(cvText);

                        if (newTraits.containsKey(currentTrait.trait) && !newTraits.get(currentTrait.trait).toString().equals("NA")) {

                            etCurVal.setTextColor(Color.BLACK);
                            seekBar.setMax(Integer.parseInt(currentTrait.maximum));
                            seekBar.setOnSeekBarChangeListener(null);

                            if (currentTrait.defaultValue != null) {

                                if (currentTrait.defaultValue.length() > 0) {
                                    if (newTraits.get(currentTrait.trait).toString()
                                            .equals(currentTrait.defaultValue))
                                        etCurVal.setTextColor(Color.BLACK);
                                    else
                                        etCurVal.setTextColor(Color.parseColor(displayColor));
                                } else {
                                    if (newTraits.get(currentTrait.trait).toString().equals("0"))
                                        etCurVal.setTextColor(Color.BLACK);
                                    else
                                        etCurVal.setTextColor(Color.parseColor(displayColor));
                                }
                            } else {
                                if (newTraits.get(currentTrait.trait).toString().equals("0"))
                                    etCurVal.setTextColor(Color.BLACK);
                                else
                                    etCurVal.setTextColor(Color.parseColor(displayColor));
                            }

                            if(newTraits.get(currentTrait.trait).toString().contains("%")) {
                                updateTrait(currentTrait.trait, "percent", newTraits.get(currentTrait.trait).toString().replace("%",""));
                            }

                            etCurVal.setText(newTraits.get(currentTrait.trait).toString());
                            seekBar.setProgress(Integer.parseInt(newTraits.get(currentTrait.trait).toString()));
                            seekBar.setOnSeekBarChangeListener(seekListener);

                        } else if (newTraits.containsKey(currentTrait.trait) && newTraits.get(currentTrait.trait).toString().equals("NA")) {
                            etCurVal.setText("NA");
                            etCurVal.setTextColor(Color.parseColor(displayColor));
                            seekBar.setProgress(0);
                        } else {
                            seekBar.setOnSeekBarChangeListener(null);

                            etCurVal.setText("");
                            seekBar.setProgress(0);
                            etCurVal.setTextColor(Color.BLACK);

                            seekBar.setMax(Integer
                                    .parseInt(currentTrait.maximum));

                            if (currentTrait.defaultValue != null
                                    && currentTrait.defaultValue.length() > 0) {
                                etCurVal.setText(currentTrait.defaultValue);
                                seekBar.setProgress(Integer
                                        .valueOf(currentTrait.defaultValue));
                            }

                            updateTrait(currentTrait.trait, "percent", String.valueOf(seekBar.getProgress()));
                            seekBar.setOnSeekBarChangeListener(seekListener);
                        }

                    } else if (currentTrait.format.equals("date")) {
                        hideLayouts();
                        traitDate.setVisibility(View.VISIBLE);

                        etCurVal.setEnabled(false);
                        etCurVal.setVisibility(View.GONE);

                        final Calendar c = Calendar.getInstance();
                        date = dateFormat.format(c.getTime());

                        if (newTraits.containsKey(currentTrait.trait) && !newTraits.get(currentTrait.trait).toString().equals("NA")) {
                            if(newTraits.get(currentTrait.trait).toString().length() < 4 && newTraits.get(currentTrait.trait).toString().length() > 0) {
                                Calendar calendar = Calendar.getInstance();

                                //convert day of year to yyyy-mm-dd string
                                date = newTraits.get(currentTrait.trait).toString();
                                calendar.set(Calendar.DAY_OF_YEAR, Integer.parseInt(date));
                                date = dateFormat.format(calendar.getTime());

                                //set month/day text and color
                                month.setTextColor(Color.parseColor(displayColor));
                                day.setTextColor(Color.parseColor(displayColor));

                                month.setText(getMonthForInt(calendar.get(Calendar.MONTH)));
                                day.setText(String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)));

                            } else if (newTraits.get(currentTrait.trait).toString().contains(".")) {
                                //convert from yyyy.mm.dd to yyyy-mm-dd
                                String[] oldDate = newTraits.get(currentTrait.trait).toString().split("\\.");
                                date = oldDate[0] + "-" + String.format("%02d", Integer.parseInt(oldDate[1])) + "-" + String.format("%02d", Integer.parseInt(oldDate[2]));

                                //set month/day text and color
                                month.setText(getMonthForInt(Integer.parseInt(oldDate[1])-1));
                                day.setText(oldDate[2]);
                                month.setTextColor(Color.parseColor(displayColor));
                                day.setTextColor(Color.parseColor(displayColor));

                            } else {
                                Calendar calendar = Calendar.getInstance();

                                //new format
                                date = newTraits.get(currentTrait.trait).toString();

                                //Parse date
                                try {
                                    calendar.setTime(dateFormat.parse(date));
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }

                                //set month/day text and color
                                month.setText(getMonthForInt(calendar.get(Calendar.MONTH)));
                                day.setText(String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)));

                                month.setTextColor(Color.parseColor(displayColor));
                                day.setTextColor(Color.parseColor(displayColor));
                            }
                        } else if(newTraits.containsKey(currentTrait.trait) && newTraits.get(currentTrait.trait).toString().equals("NA")) {
                            month.setText("");
                            day.setText("NA");
                        } else {
                            month.setTextColor(Color.BLACK);
                            day.setTextColor(Color.BLACK);
                            month.setText(getMonthForInt(c.get(Calendar.MONTH)));
                            day.setText(String.format("%02d", c.get(Calendar.DAY_OF_MONTH)));
                        }
                    } else if (currentTrait.format.equals("qualitative") | currentTrait.format.equals("categorical")) {
                        hideLayouts();
                        traitCategorical.setVisibility(View.VISIBLE);

                        etCurVal.setVisibility(EditText.GONE);
                        etCurVal.setEnabled(false);

                        String lastQualitative = "";

                        if (newTraits.containsKey(currentTrait.trait)) {
                            lastQualitative = newTraits.get(currentTrait.trait)
                                    .toString();
                        }

                        String[] cat = currentTrait.categories.split("/");

                        // Hide unused buttons
                        for (int i = cat.length; i < 12; i++) {
                            buttonArray[i].setVisibility(Button.GONE);
                        }

                        // Reset button visibility for items in the last row
                        if (12 - cat.length > 0) {
                            for (int i = 11; i >= cat.length; i--) {
                                buttonArray[i].setVisibility(Button.INVISIBLE);
                            }
                        }

                        // Set the color and visibility for the right buttons
                        for (int i = 0; i < cat.length; i++) {
                            if (cat[i].equals(lastQualitative)) {
                                buttonArray[i].setVisibility(Button.VISIBLE);
                                buttonArray[i].setText(cat[i]);
                                buttonArray[i].setTextColor(Color.parseColor(displayColor));
                                buttonArray[i].setBackgroundColor(getResources().getColor(R.color.button_pressed));
                            } else {
                                //TODO debug number of buttons, maybe add validation when creating categorical trait
                                buttonArray[i].setVisibility(Button.VISIBLE);
                                buttonArray[i].setText(cat[i]);
                                buttonArray[i].setTextColor(Color.BLACK);
                                buttonArray[i].setBackgroundColor(getResources().getColor(R.color.button_normal));
                            }
                        }
                    } else if (currentTrait.format.equals("boolean")) {
                        hideLayouts();
                        traitBoolean.setVisibility(View.VISIBLE);

                        etCurVal.setVisibility(EditText.GONE);
                        etCurVal.setEnabled(false);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            if (currentTrait.defaultValue.trim().equalsIgnoreCase("true")) {
                                updateTrait(currentTrait.trait, "boolean", "true");
                                eImg.setImageResource(R.drawable.trait_boolean_true);
                            } else {
                                updateTrait(currentTrait.trait, "boolean", "false");
                                eImg.setImageResource(R.drawable.trait_boolean_false);
                            }
                        } else {
                            String bval = newTraits.get(currentTrait.trait).toString();

                            if (bval.equalsIgnoreCase("false")) {
                                eImg.setImageResource(R.drawable.trait_boolean_false);
                            } else {
                                eImg.setImageResource(R.drawable.trait_boolean_true);
                            }

                        }
                    } else if (currentTrait.format.equals("audio")) {
                        hideLayouts();
                        traitAudio.setVisibility(View.VISIBLE);

                        etCurVal.setVisibility(EditText.VISIBLE);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            doRecord.setImageResource(R.drawable.trait_audio);
                            etCurVal.setText("");
                        } else if(newTraits.containsKey(currentTrait.trait) && newTraits.get(currentTrait.trait).toString().equals("NA")) {
                            doRecord.setImageResource(R.drawable.trait_audio);
                            etCurVal.setText("NA");
                        } else {
                            mRecordingLocation = new File(newTraits.get(currentTrait.trait).toString());
                            doRecord.setImageResource(R.drawable.trait_audio_play);
                            etCurVal.setText(getString(R.string.trait_layout_data_stored));
                        }

                    } else if (currentTrait.format.equals("photo")) {
                        hideLayouts();
                        traitPhoto.setVisibility(View.VISIBLE);

                        etCurVal.removeTextChangedListener(cvText);
                        etCurVal.removeTextChangedListener(cvNum);
                        etCurVal.setVisibility(EditText.GONE);
                        etCurVal.setEnabled(false);

                        // Always set to null as default, then fill in with trait value
                        photoLocation = new ArrayList<>();
                        drawables = new ArrayList<>();

                        File img = new File(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/" + "/photos/");
                        if (img.listFiles() != null) {

                            //TODO causes crash
                            photoLocation = ConfigActivity.dt.getPlotPhotos(cRange.plot_id, currentTrait.trait);

                            for (int i = 0; i < photoLocation.size(); i++) {
                                drawables.add(new BitmapDrawable(displayScaledSavedPhoto(photoLocation.get(i))));
                            }

                            photoAdapter = new GalleryImageAdapter(MainActivity.this, drawables);
                            photo.setAdapter(photoAdapter);
                            photo.setSelection(photo.getCount() - 1);
                            photo.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                                @Override
                                public void onItemClick(AdapterView<?> arg0,
                                                        View arg1, int pos, long arg3) {
                                    displayPlotImage(photoLocation.get(photo.getSelectedItemPosition()));
                                }
                            });

                        } else {
                            photoAdapter = new GalleryImageAdapter(MainActivity.this, drawables);
                            photo.setAdapter(photoAdapter);
                        }

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            if (!img.exists()) {
                                img.mkdirs();
                            }
                        }
                    } else if(currentTrait.format.equals("counter")) {
                        hideLayouts();
                        traitCounter.setVisibility(View.VISIBLE);

                        etCurVal.setVisibility(EditText.GONE);
                        etCurVal.setEnabled(false);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            counterTv.setText("0");
                        } else {
                            counterTv.setText(newTraits.get(currentTrait.trait).toString());
                        }

                    } else if(currentTrait.format.equals("rust rating") | currentTrait.format.equals("disease rating")) {
                        hideLayouts();
                        traitDiseaseRating.setVisibility(View.VISIBLE);

                        etCurVal.removeTextChangedListener(cvText);
                        etCurVal.setVisibility(EditText.VISIBLE);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            etCurVal.removeTextChangedListener(cvNum);
                            etCurVal.setText("");
                            etCurVal.setTextColor(Color.BLACK);

                            if (currentTrait.defaultValue != null
                                    && currentTrait.defaultValue.length() > 0)
                                etCurVal.setText(currentTrait.defaultValue);

                            etCurVal.addTextChangedListener(cvNum);
                        } else {
                            etCurVal.removeTextChangedListener(cvNum);
                            etCurVal.setText(newTraits.get(currentTrait.trait).toString());
                            etCurVal.setTextColor(Color.parseColor(displayColor));
                            etCurVal.addTextChangedListener(cvNum);
                        }

                    } else if(currentTrait.format.equals("multicat")) {
                        hideLayouts();
                        traitMulticat.setVisibility(View.VISIBLE);

                        etCurVal.setVisibility(EditText.VISIBLE);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            etCurVal.removeTextChangedListener(cvNum);
                            etCurVal.setText("");
                            etCurVal.setTextColor(Color.BLACK);
                            etCurVal.addTextChangedListener(cvNum);
                        } else {
                            etCurVal.removeTextChangedListener(cvNum);
                            etCurVal.setText(newTraits.get(currentTrait.trait).toString());
                            etCurVal.setTextColor(Color.parseColor(displayColor));
                            etCurVal.addTextChangedListener(cvNum);
                        }

                        final String[] cat = currentTrait.categories.split("/");

                        if(!dataLocked) {
                            gridMultiCat.setAdapter(new BaseAdapter() {
                                @Override
                                public int getCount() {
                                    return cat.length;
                                }

                                @Override
                                public Object getItem(int position) {
                                    return null;
                                }

                                @Override
                                public long getItemId(int position) {
                                    return 0;
                                }

                                @Override
                                public View getView(int position, View convertView, ViewGroup parent) {
                                    final Button newButton = (Button) LayoutInflater.from(MainActivity.this).inflate(R.layout.custom_button_multicat, null);
                                    newButton.setText(cat[position]);
                                    newButton.setOnClickListener(new OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            if (etCurVal.length() > 0) {
                                                etCurVal.setText(etCurVal.getText().toString() + ":" + newButton.getText().toString());
                                            } else {
                                                etCurVal.setText(newButton.getText().toString());
                                            }
                                        }
                                    });

                                    return newButton;
                                }
                            });
                        }

                        gridMultiCat.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                gridMultiCat.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                                View lastChild = gridMultiCat.getChildAt(gridMultiCat.getChildCount() - 1);
                                gridMultiCat.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, lastChild.getBottom()));
                            }
                        });

                    } else if(currentTrait.format.equals("location")) {
                        hideLayouts();
                        traitLocation.setVisibility(View.VISIBLE);

                        etCurVal.setVisibility(EditText.VISIBLE);

                        if (newTraits.containsKey(currentTrait.trait)) {
                            etCurVal.removeTextChangedListener(cvNum);
                            etCurVal.setText(newTraits.get(currentTrait.trait).toString());
                            etCurVal.setTextColor(Color.parseColor(displayColor));
                            etCurVal.addTextChangedListener(cvNum);
                        } else {
                            etCurVal.removeTextChangedListener(cvNum);

                            etCurVal.setText("");
                            etCurVal.setTextColor(Color.BLACK);

                            if (currentTrait.defaultValue != null
                                    && currentTrait.defaultValue.length() > 0)
                                etCurVal.setText(currentTrait.defaultValue);

                            etCurVal.addTextChangedListener(cvNum);
                        }

                    } else if(currentTrait.format.equals("angle")) {
                        hideLayouts();
                        traitAngle.setVisibility(View.VISIBLE);

                        etCurVal.setVisibility(EditText.VISIBLE);

                        if (newTraits.containsKey(currentTrait.trait)) {
                            etCurVal.removeTextChangedListener(cvNum);
                            etCurVal.setText(newTraits.get(currentTrait.trait).toString());
                            etCurVal.setTextColor(Color.parseColor(displayColor));
                            etCurVal.addTextChangedListener(cvNum);
                        } else {
                            etCurVal.removeTextChangedListener(cvNum);
                            etCurVal.setText("");
                            etCurVal.setTextColor(Color.BLACK);
                            etCurVal.addTextChangedListener(cvNum);

                            sensorManager.registerListener(mEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                                    SensorManager.SENSOR_DELAY_NORMAL);
                            sensorManager.registerListener(mEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                                    SensorManager.SENSOR_DELAY_NORMAL);
                        }
                    } else {
                        traitText.setVisibility(View.GONE);
                        traitNumeric.setVisibility(View.GONE);
                        traitPercent.setVisibility(View.GONE);
                        traitDate.setVisibility(View.GONE);
                        traitCategorical.setVisibility(View.GONE);
                        traitBoolean.setVisibility(View.GONE);
                        traitAudio.setVisibility(View.GONE);
                        traitPhoto.setVisibility(View.GONE);
                        traitCounter.setVisibility(View.GONE);
                        traitDiseaseRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);
                        traitLocation.setVisibility(View.GONE);

                        etCurVal.removeTextChangedListener(cvText);
                        etCurVal.setVisibility(EditText.VISIBLE);
                        etCurVal.setEnabled(true);
                    }
                }

                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });

            traitType.setSelection(traitPosition);
        }
    }

    // For audio trait type
    private void setRecordingLocation(String recordingName) {
        mRecordingLocation = new File(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/audio/",
                recordingName + ".mp4");
    }

    // Make sure we're not recording music playing in the background; ask the
    // MediaPlaybackService to pause playback
    private void stopAudioPlayback() {
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        sendBroadcast(i);
    }

    // Reset the recorder to default state so it can begin recording
    private void prepareRecorder() {

        stopAudioPlayback();

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);

        SimpleDateFormat timeStamp = new SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss", Locale.getDefault());

        Calendar c = Calendar.getInstance();

        String mGeneratedName;
        try {
            mGeneratedName = MainActivity.cRange.plot_id + " " + timeStamp.format(c.getTime());
        } catch (Exception e) {
            mGeneratedName = "error " + timeStamp.format(c.getTime());
        }

        setRecordingLocation(mGeneratedName);
        mRecorder.setOutputFile(mRecordingLocation.getAbsolutePath());

        try {
            mRecorder.prepare();
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
        }
    }

    // Remove the recorder resource
    private void releaseRecorder() {
        if (mRecorder != null) {
            mRecorder.release();
        }

        mRecording = false;
    }

    private void beginPlayback()
    {
        mListening = true;
        doRecord.setImageResource(R.drawable.trait_audio_stop);
        mPlayer = new MediaPlayer();
        mPlayer = MediaPlayer.create(MainActivity.this, Uri.parse(mRecordingLocation.getAbsolutePath()));

        try {
            mPlayer.prepare();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        try {
            mPlayer.start();
            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    mListening = false;
                    doRecord.setImageResource(R.drawable.trait_audio_play);

                    deleteValue.setEnabled(true);
                }
            });
        } catch (NullPointerException e) {
            Log.w(TAG, e.getMessage());
        }
    }

    // Delete recording
    private void deleteRecording() {
        if (mRecordingLocation != null && mRecordingLocation.exists()) {
            mRecordingLocation.delete();
        }
    }

    // Moves to specific plot/range/plot_id
    private void moveToSearch(String type, int[] rangeID, String range, String plot, String plotID) {
        /*

        if (rangeID == null) {
            return;
        }

        boolean haveData = false;

        // search moveto
        if(type.equals("search")) {
            for (int j = 1; j <= rangeID.length; j++) {
                cRange = ConfigActivity.dt.getRange(rangeID[j - 1]);

                if (cRange.range.equals(range) & cRange.plot.equals(plot)) {
                    moveToResult(j);
                    haveData=true;
                }
            }
        }

        //move to plot
        if (type.equals("plot")) {
            for (int j = 1; j <= rangeID.length; j++) {
                cRange = ConfigActivity.dt.getRange(rangeID[j - 1]);

                if (cRange.plot.equals(plot)) {
                    moveToResult(j);
                    haveData=true;
                }
            }
        }

        //move to range
        if(type.equals("range")) {
            for (int j = 1; j <= rangeID.length; j++) {
                cRange = ConfigActivity.dt.getRange(rangeID[j - 1]);

                if (cRange.range.equals(range)) {
                    moveToResult(j);
                    haveData=true;
                }
            }
        }

        //move to plot id
        if(type.equals("id")) {
            for (int j = 1; j <= rangeID.length; j++) {
                cRange = ConfigActivity.dt.getRange(rangeID[j - 1]);

                if (cRange.plot_id.equals(plotID)) {
                    moveToResult(j);
                    haveData=true;
                }
            }
        }

        if (!haveData)
            makeToast(getString(R.string.main_toolbar_moveto_no_match));

            */
    }

    private void moveToResult(int j) {
        if (ep.getBoolean(PreferencesActivity.HIDE_ENTRIES_WITH_DATA, false)) {
            if (!ConfigActivity.dt.getTraitExists(rangeID[j - 1], currentTrait.trait,
                    currentTrait.format)) {
                paging = j;

                // Reload traits based on the selected
                // plot
                displayRange(cRange);

                newTraits = (HashMap) ConfigActivity.dt.getUserDetail(
                        cRange.plot_id).clone();

                initWidgets(false);
            }
        } else {
            paging = j;

            // Reload traits based on the selected plot
            displayRange(cRange);

            newTraits = (HashMap) ConfigActivity.dt.getUserDetail(
                    cRange.plot_id).clone();

            initWidgets(false);
        }
    }

    @Override
    public void onPause() {
        // Backup database
        try {
            ConfigActivity.dt.exportDatabase("backup");
            File exportedDb = new File(Constants.BACKUPPATH + "/" + "backup.db");
            File exportedSp = new File(Constants.BACKUPPATH + "/" + "backup.db_sharedpref.xml");
            Utils.scanFile(MainActivity.this,exportedDb);
            Utils.scanFile(MainActivity.this,exportedSp);
        } catch (Exception e) {
            Log.e(TAG,e.getMessage());
        }

        super.onPause();
    }

    @Override
    public void onDestroy() {

        //save last plot id
        if (ep.getBoolean("ImportFieldFinished", false)) {
            saveLastPlot();
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

        loadScreen();

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
        if (reloadData) {
            reloadData = false;
            partialReload = false;

            if (ep.getBoolean(PreferencesActivity.QUICK_GOTO, false)) {
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

            String primaryName = ep.getString("ImportFirstName", getString(R.string.search_results_dialog_range)) + ":";
            String secondaryName = ep.getString("ImportSecondName", getString(R.string.search_results_dialog_plot)) + ":";

            if(primaryName.length()>8) {
                primaryName = primaryName.substring(0,7) + ":";
            }

            if(secondaryName.length()>8) {
                secondaryName = secondaryName.substring(0,7) + ":";
            }

            rangeName.setText(primaryName);
            plotName.setText(secondaryName);

            paging = 1;

            rangeID = ConfigActivity.dt.getAllRangeID();

            if (rangeID != null) {
                cRange = ConfigActivity.dt.getRange(rangeID[0]);

                //TODO NullPointerException
                lastRange = cRange.range;
                displayRange(cRange);

                newTraits = (HashMap) ConfigActivity.dt.getUserDetail(cRange.plot_id).clone();
            }

            prefixTraits = ConfigActivity.dt.getRangeColumnNames();

            initWidgets(false);
            traitType.setSelection(0);

            // try to go to last saved plot
            if(ep.getString("lastplot",null)!=null) {
                rangeID = ConfigActivity.dt.getAllRangeID();
                moveToSearch("id",rangeID,null,null,ep.getString("lastplot",null));
            }

        } else if (partialReload) {
            partialReload = false;
            displayRange(cRange);
            prefixTraits = ConfigActivity.dt.getRangeColumnNames();
            initWidgets(false);

        } else if (searchReload) {
            searchReload = false;
            paging = 1;

            if (rangeID != null) {
                moveToSearch("search",rangeID, searchRange, searchPlot, null);
            }
        }
    }

    /**
     * Helper function update user data in the memory based hashmap as well as
     * the database
     */
    private void updateTrait(String parent, String trait, String value) {

        if (cRange == null || cRange.plot_id.length() == 0) {
            return;
        }

        Log.w(trait, value);

        if (newTraits.containsKey(parent)) {
            newTraits.remove(parent);
        }

        newTraits.put(parent, value);

        // Always remove existing trait before inserting again
        // Based on plot_id, prevent duplicates
        ConfigActivity.dt.deleteTrait(cRange.plot_id, parent);

        String exp_id = Integer.toString(ep.getInt("ExpID", 0));
        ConfigActivity.dt.insertUserTraits(cRange.plot_id, parent, trait, value, ep.getString("FirstName", "") + " " + ep.getString("LastName", ""), ep.getString("Location", ""), "", exp_id); //TODO add notes and exp_id
    }

    // Delete trait, including from database
    private void removeTrait(String parent) {

        if (cRange == null || cRange.plot_id.length() == 0) {
            return;
        }

        if (newTraits.containsKey(parent))
            newTraits.remove(parent);

        // Always remove existing trait before inserting again
        // Based on plot_id, prevent duplicates
        ConfigActivity.dt.deleteTrait(cRange.plot_id, parent);
    }

    /**
     * Get month name based on numeric value
     */
    String getMonthForInt(int m) {
        String month = "invalid";
        DateFormatSymbols dfs = new DateFormatSymbols();
        String[] months = dfs.getShortMonths();

        if (m >= 0 && m <= 11) {
            month = months[m];
        }

        return month;
    }

    public final Handler myGuiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            synchronized (lock) {
                switch (msg.what) {
                    case 1:
                        ImageView btn = (ImageView) findViewById(msg.arg1);
                        if (btn.getTag() != null) { // button is still pressed
                            Message msg1 = new Message(); // schedule next btn pressed check
                            msg1.copyFrom(msg);
                            if (msg.arg1 == R.id.rangeLeft) {
                                repeatLeft();
                            } else {
                                repeatRight();
                            }
                            myGuiHandler.removeMessages(1);
                            myGuiHandler.sendMessageDelayed(msg1, msg1.arg2);
                        }
                        break;
                }
            }
        }
    };

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
                    Log.e(TAG,"" + e.getMessage());
                }

                intent.setClassName(MainActivity.this,
                        SearchActivity.class.getName());
                startActivity(intent);
                break;

            case R.id.resources:
                intent.setClassName(MainActivity.this,
                        FileExploreActivity.class.getName());
                intent.putExtra("path", Constants.RESOURCEPATH);
                intent.putExtra("exclude", new String[] {"fieldbook"});
                intent.putExtra("title",getString(R.string.main_toolbar_resources));
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
                    Log.e(TAG,"" + e.getMessage());
                }

                intent.setClassName(MainActivity.this,
                        DatagridActivity.class.getName());
                startActivityForResult(intent,2);
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

    private void lockData(Boolean lock) {
        LinearLayout[] traitViews = {traitText, traitNumeric, traitPercent, traitDate, traitCategorical,
                traitBoolean, traitAudio, traitPhoto, traitCounter, traitDiseaseRating, traitMulticat};

        if(lock) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_lock);
            missingValue.setEnabled(false);
            deleteValue.setEnabled(false);
            etCurVal.setEnabled(false);

            for(LinearLayout traitLayout : traitViews) {
                disableViews(traitLayout);
            }

        } else {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_unlock);
            missingValue.setEnabled(true);
            deleteValue.setEnabled(true);

            for(LinearLayout traitLayout : traitViews) {
                enableViews(traitLayout);
            }
        }
    }

    private static void disableViews(ViewGroup layout) {
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

    private static void enableViews(ViewGroup layout) {
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
        Button exportButton =  layout.findViewById(R.id.saveBtn);
        Button closeBtn =  layout.findViewById(R.id.closeBtn);
        Button camBtn =  layout.findViewById(R.id.camBtn);

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
                rangeID = ConfigActivity.dt.getAllRangeID();
                moveToSearch("id",rangeID,null,null,inputPlotId);
                goToId.dismiss();
            }
        });

        goToId.show();
    }

    public void makeToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    public void nextEmptyPlot() {
        int pos = paging;

        if (pos == rangeID.length) {
            return;
        }

        while (pos <= rangeID.length) {
            pos += 1;

            if (pos > rangeID.length) {
                return;
            }

            if (!ConfigActivity.dt.getTraitExists(rangeID[pos - 1], currentTrait.trait,
                    currentTrait.format)) {
                paging = pos;
                break;
            }
        }
        cRange = ConfigActivity.dt.getRange(rangeID[paging - 1]);
        displayRange(cRange);
        lastRange = cRange.range;
        newTraits = (HashMap) ConfigActivity.dt.getUserDetail(cRange.plot_id)
                .clone();
        initWidgets(true);
    }

    private void saveLastPlot() {
        Editor ed = ep.edit();
        ed.putString("lastplot", cRange.plot_id);
        ed.apply();
    }

    public void onClick(View b) {

        String v = "";

        switch (b.getId()) {
            // Photo capture
            case R.id.capture:
                try {
                    int m;

                    try {
                        m = Integer.parseInt(currentTrait.details);
                    } catch (Exception n) {
                        m = 0;
                    }

                    // Do not take photos if limit is reached
                    if (m == 0 || photoLocation.size() < m) {
                        takePicture();
                    } else
                        makeToast(getString(R.string.traits_create_photo_maximum));
                } catch (Exception e) {
                    e.printStackTrace();
                    makeToast(getString(R.string.trait_error_hardware_missing));
                }
                break;

            case R.id.record:
                newTraits = (HashMap) ConfigActivity.dt.getUserDetail(cRange.plot_id)
                        .clone();

                if (mListening) {
                    mPlayer.stop();
                    doRecord.setImageResource(R.drawable.trait_audio_play);

                    mListening = false;
                    deleteValue.setEnabled(true);
                    break;
                }

                if (mRecording) {
                    // Stop recording
                    try {
                        mRecorder.stop();
                        File storedAudio = new File(mRecordingLocation.getAbsolutePath());
                        Utils.scanFile(MainActivity.this,storedAudio);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    releaseRecorder();

                    updateTrait(currentTrait.trait, "audio", mRecordingLocation.getAbsolutePath());

                    etCurVal.setText(getString(R.string.trait_layout_data_stored));

                    mRecording = false;
                    doRecord.setImageResource(R.drawable.trait_audio_play);

                    rangeLeft.setEnabled(true);
                    rangeRight.setEnabled(true);

                    traitLeft.setEnabled(true);
                    traitRight.setEnabled(true);
                    deleteValue.setEnabled(true);
                } else if (newTraits.containsKey(currentTrait.trait)) {
                    beginPlayback();
                    deleteValue.setEnabled(false);

                } else if (!newTraits.containsKey(currentTrait.trait)) {

                    // start recording
                    deleteRecording();
                    deleteValue.setEnabled(false);
                    removeTrait(currentTrait.trait);
                    etCurVal.setText("");

                    prepareRecorder();

                    rangeLeft.setEnabled(false);
                    rangeRight.setEnabled(false);

                    traitLeft.setEnabled(false);
                    traitRight.setEnabled(false);

                    mRecorder.start();
                    mRecording = true;

                    doRecord.setImageResource(R.drawable.trait_audio_stop);
                }
                break;

            case R.id.k1:
                v = ";";
                break;
            case R.id.k2:
                v = "1";
                break;
            case R.id.k3:
                v = "2";
                break;
            case R.id.k4:
                v = "3";
                break;
            case R.id.k5:
                v = "+";
                break;
            case R.id.k6:
                v = "4";
                break;
            case R.id.k7:
                v = "5";
                break;
            case R.id.k8:
                v = "6";
                break;
            case R.id.k9:
                v = "-";
                break;
            case R.id.k10:
                v = "7";
                break;
            case R.id.k11:
                v = "8";
                break;
            case R.id.k12:
                v = "9";
                break;
            case R.id.k13:
                v = "*";
                break;
            case R.id.k14:
                v = ".";
                break;
            case R.id.k15:
                v = "0";
                break;

            case R.id.rust0:
                v = rust0.getText().toString();
                break;
            case R.id.rust5:
                v = rust5.getText().toString();
                break;
            case R.id.rust10:
                v = rust10.getText().toString();
                break;
            case R.id.rust15:
                v = rust15.getText().toString();
                break;
            case R.id.rust20:
                v = rust20.getText().toString();
                break;
            case R.id.rust25:
                v = rust25.getText().toString();
                break;
            case R.id.rust30:
                v = rust30.getText().toString();
                break;
            case R.id.rust35:
                v = rust35.getText().toString();
                break;
            case R.id.rust40:
                v = rust40.getText().toString();
                break;
            case R.id.rust45:
                v = rust45.getText().toString();
                break;
            case R.id.rust50:
                v = rust50.getText().toString();
                break;
            case R.id.rust55:
                v = rust55.getText().toString();
                break;
            case R.id.rust60:
                v = rust60.getText().toString();
                break;
            case R.id.rust65:
                v = rust65.getText().toString();
                break;
            case R.id.rust70:
                v = rust70.getText().toString();
                break;
            case R.id.rust75:
                v = rust75.getText().toString();
                break;
            case R.id.rust80:
                v = rust80.getText().toString();
                break;
            case R.id.rust85:
                v = rust85.getText().toString();
                break;
            case R.id.rust90:
                v = rust90.getText().toString();
                break;
            case R.id.rust95:
                v = rust95.getText().toString();
                break;
            case R.id.rust100:
                v = rust100.getText().toString();
                break;
            case R.id.rustR:
                v = "R";
                break;
            case R.id.rustM:
                v = "M";
                break;
            case R.id.rustS:
                v = "S";
                break;
            case R.id.rustDelim:
                v = "/";
                break;
        }

        if (traitDiseaseRating.getVisibility() == View.VISIBLE && etCurVal.getText().length() > 0 && !v.equals("/") && !etCurVal.getText().toString().substring(etCurVal.getText().length() - 1).equals("/")) {
            String lastChar = etCurVal.getText().toString().substring(etCurVal.getText().toString().length()-1);

            if(!lastChar.matches("^[a-zA-Z]*$")) {
                v = ":" + v;
            }
        }

        if (b.getId() == R.id.k16) {
            if(etCurVal.getText().toString().length()>0) {
                etCurVal.setText(etCurVal.getText().toString().substring(0, etCurVal.getText().toString().length()-1));
            }

            if(etCurVal.getText().toString().length()==0) {
                etCurVal.removeTextChangedListener(cvNum);
                etCurVal.setText("");
                removeTrait(currentTrait.trait);
                etCurVal.addTextChangedListener(cvNum);
            }
        } else {
            if (etCurVal.getText().toString().matches(".*\\d.*") && v.matches(".*\\d.*") && traitDiseaseRating.getVisibility() == View.VISIBLE && !etCurVal.getText().toString().contains("/")) {
                makeToast(getString(R.string.trait_error_disease_severity));
            } else {
                etCurVal.setText(etCurVal.getText().toString() + v);
            }
        }
    }

    private void deletePhotoWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        builder.setTitle(getString(R.string.dialog_warning));
        builder.setMessage(getString(R.string.trait_delete_warning_photo));

        builder.setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                if (photo.getCount() > 0) {
                    String item = photoLocation.get(photo.getSelectedItemPosition());
                    photoLocation.remove(photo.getSelectedItemPosition());
                    drawables.remove(photo.getSelectedItemPosition());

                    File f = new File(item);
                    f.delete();
                    Utils.scanFile(MainActivity.this,f);

                    // Remove individual images
                    ConfigActivity.dt.deleteTraitByValue(cRange.plot_id, currentTrait.trait, item);

                    // Only do a purge by trait when there are no more images left
                    if (photoLocation.size() == 0)
                        removeTrait(currentTrait.trait);

                    photoAdapter = new GalleryImageAdapter(MainActivity.this, drawables);

                    photo.setAdapter(photoAdapter);
                } else {
                    ArrayList<Drawable> emptyList = new ArrayList<>();

                    photoAdapter = new GalleryImageAdapter(MainActivity.this, emptyList);

                    photo.setAdapter(photoAdapter);
                }
            }

        });

        builder.setNegativeButton(getString(R.string.dialog_no), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void takePicture() {
        SimpleDateFormat timeStamp = new SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss", Locale.getDefault());

        File dir = new File(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/photos/");

        dir.mkdirs();

        String generatedName = MainActivity.cRange.plot_id + "_" + currentTrait.trait + "_" + getRep() + "_" + timeStamp.format(Calendar.getInstance().getTime()) + ".jpg";
        mCurrentPhotoPath = generatedName;

        Log.w("File", Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/photos/" + generatedName);

        // Save photo capture with timestamp as filename
        File file = new File(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/photos/",
                generatedName);

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                    FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".fileprovider", file));
            startActivityForResult(takePictureIntent, 252);
        }
    }

    private String getRep() {
        int repInt = ConfigActivity.dt.getRep(MainActivity.cRange.plot_id,currentTrait.trait);
        return String.valueOf(repInt);
    }

    private void makeImage(String photoName) {
        File file = new File(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/photos/",
                photoName);

        Utils.scanFile(MainActivity.this,file.getAbsoluteFile());

        photoLocation.add(file.getAbsolutePath());

        drawables.add(new BitmapDrawable(displayScaledSavedPhoto(file.getAbsolutePath())));

        // Force Gallery to update
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".fileprovider", file);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);

        updateTraitAllowDuplicates(currentTrait.trait, "photo", file.getAbsolutePath());

        photoAdapter = new GalleryImageAdapter(MainActivity.this, drawables);

        photo.setAdapter(photoAdapter);
        photo.setSelection(photoAdapter.getCount() - 1);
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
        TextView summaryText =  layout.findViewById(R.id.field_name);

        closeBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        String[] traitList = ConfigActivity.dt.getAllTraits();
        StringBuilder data = new StringBuilder();

        //TODO this test crashes app
        if (cRange != null) {
            for (String s : prefixTraits) {
                data.append(s).append(": ").append(ConfigActivity.dt.getDropDownRange(s, cRange.plot_id)[0]).append("\n");
            }
        }

        for (String s : traitList) {
            if (newTraits.containsKey(s)) {
                data.append(s).append(": ").append(newTraits.get(s).toString()).append("\n");
            }
        }

        summaryText.setText(data.toString());

        dialog.show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if(ep.getBoolean(PreferencesActivity.VOLUME_NAVIGATION,false)) {
                    if (action == KeyEvent.ACTION_UP) {
                        moveEntryRight();
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if(ep.getBoolean(PreferencesActivity.VOLUME_NAVIGATION,false)) {
                    if (action == KeyEvent.ACTION_UP) {
                        moveEntryLeft();
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_ENTER:
                String return_action = ep.getString(PreferencesActivity.RETURN_CHARACTER,"1");

                if(return_action.equals("1")) {
                    moveEntryRight();
                    return true;
                }

                if(return_action.equals("2")) {
                    moveTrait("right");
                    return true;
                }

                if(return_action.equals("3")) {
                    return true;
                }

                return false;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void updateTraitAllowDuplicates(String parent, String trait, String value) {

        if (cRange == null || cRange.plot_id.length() == 0) {
            return;
        }

        Log.d("Field Book",trait + " " + value);

        if (newTraits.containsKey(parent))
            newTraits.remove(parent);

        newTraits.put(parent, value);

        ConfigActivity.dt.deleteTraitByValue(cRange.plot_id, parent, value);

        String exp_id = Integer.toString(ep.getInt("ExpID", 0));
        ConfigActivity.dt.insertUserTraits(cRange.plot_id, parent, trait, value, ep.getString("FirstName","") + " " + ep.getString("LastName",""), ep.getString("Location",""),"",exp_id); //TODO add notes and exp_id
    }

    private void displayPlotImage(String path) {
        try {
            Log.w("Display path", path);

            File f = new File(path);
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".fileprovider", f), "image/*");
            startActivity(intent);
        } catch (Exception ignore) {
        }
    }

    private Bitmap displayScaledSavedPhoto(String path) {
        if (path == null) {
            makeToast(getString(R.string.trait_error_photo_missing));
            return null;
        }

        try {
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;

            BitmapFactory.decodeFile(path, bmOptions);
            int photoW = bmOptions.outWidth;
            int photoH = bmOptions.outHeight;

            int targetW;
            int targetH;

            if (photoW > photoH) {
                // landscape
                targetW = 800;
                targetH = 600;
            } else {
                // portrait
                targetW = 600;
                targetH = 800;
            }

            int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

            // Decode the image file into a Bitmap sized to fill the View
            bmOptions.inJustDecodeBounds = false;
            bmOptions.inSampleSize = scaleFactor;
            bmOptions.inPurgeable = true;

            Bitmap bitmap = BitmapFactory.decodeFile(path, bmOptions);
            Bitmap correctBmp = bitmap;

            try {
                File f = new File(path);
                ExifInterface exif = new ExifInterface(f.getPath());
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

                int angle = 0;

                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                    angle = 90;
                }
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                    angle = 180;
                }
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                    angle = 270;
                }

                Matrix mat = new Matrix();
                mat.postRotate(angle);

                correctBmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mat, true);
            }

            catch (IOException e) {
                Log.e(TAG, "-- Error in setting image");
                return BitmapFactory.decodeResource(getResources(), R.drawable.trait_photo_missing);
            }

            catch(OutOfMemoryError oom) {
                Log.e(TAG, "-- OOM Error in setting image");
            }

            return correctBmp;

        } catch (Exception e) {
            return BitmapFactory.decodeResource(getResources(), R.drawable.trait_photo_missing);
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
                    rangeID = ConfigActivity.dt.getAllRangeID();
                    moveToSearch("id",rangeID,null,null,inputPlotId);
                }
                break;
            case 252:
                if (resultCode == RESULT_OK) {
                    makeImage(mCurrentPhotoPath);
                }
                break;
        }

        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scanResult != null) {
            inputPlotId = scanResult.getContents();
            rangeID = ConfigActivity.dt.getAllRangeID();
            moveToSearch("id",rangeID,null,null,inputPlotId);
            if(goToId!=null) {
                goToId.dismiss();
            }
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
}