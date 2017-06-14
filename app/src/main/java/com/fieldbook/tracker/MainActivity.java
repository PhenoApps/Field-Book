package com.fieldbook.tracker;

import android.app.Activity;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
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
import com.fieldbook.tracker.fields.FieldEditorActivity;
import com.fieldbook.tracker.search.*;
import com.fieldbook.tracker.traits.*;
import com.fieldbook.tracker.tutorial.*;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.utilities.ExpandableHeightGridView;
import com.fieldbook.tracker.utilities.GPSTracker;
import com.fieldbook.tracker.utilities.GalleryImageAdapter;
import com.fieldbook.tracker.utilities.RangeObject;
import com.fieldbook.tracker.utilities.Utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
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
public class MainActivity extends AppCompatActivity implements OnClickListener {

    /**
     * Other variables
     */
    private String local;
    private String region;

    private SharedPreferences ep;
    private int paging;

    String inputPlotId = "";
    public int[] rangeID;

    AlertDialog goToId;

    int delay = 100;
    int count = 1;

    public static DataHelper dt;

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
    private boolean savePrefix;

    private Spinner drop1prefix;
    private Spinner drop2prefix;
    private Spinner drop3prefix;

    private String[] myList1;
    private String[] myList2;
    private String[] myList3;

    private int drop1Selection;
    private int drop2Selection;
    private int drop3Selection;

    private TextView drop3;
    private TextView drop2;
    private TextView drop1;

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
    SimpleDateFormat dayFormat = new SimpleDateFormat("dd");
    SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
    SimpleDateFormat monthAlphFormat = new SimpleDateFormat("MMM");
    SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
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

    /**
     * Test area
     */

    private DrawerLayout mDrawer;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private String mActivityTitle;
    NavigationView nvDrawer;

    private Boolean dataLocked = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        // Enforce internal language change
        local = ep.getString("language", Locale.getDefault().getCountry());
        region = ep.getString("region",Locale.getDefault().getLanguage());

        if(region == null) {
            region = "";
        }

        Locale locale2 = new Locale(local,region);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2, getBaseContext().getResources()
                .getDisplayMetrics());

        loadScreen();

        // If the user hasn't configured range and traits, open settings screen
        if (!ep.getBoolean("ImportFieldFinished", false) | !ep.getBoolean("CreateTraitFinished", false)) {
            updateAssets();

            Intent intent = new Intent();
            intent.setClassName(MainActivity.this,
                    ConfigActivity.class.getName());
            startActivity(intent);
        }

        SharedPreferences.Editor ed = ep.edit();

        if (ep.getInt("UpdateVersion", -1) < getVersion()) {
            ed.putInt("UpdateVersion", getVersion());
            ed.apply();
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, ChangelogActivity.class);
            startActivity(intent);
            updateAssets();
        }
    }

    private void updateAssets() {
        MainActivity.dt.copyFileOrDir(Constants.MPATH.getAbsolutePath(), "field_import");
        MainActivity.dt.copyFileOrDir(Constants.MPATH.getAbsolutePath(), "resources");
        MainActivity.dt.copyFileOrDir(Constants.MPATH.getAbsolutePath(), "trait");
        MainActivity.dt.copyFileOrDir(Constants.MPATH.getAbsolutePath(), "database");
    }

    private void loadScreen() {
        setContentView(R.layout.main);

        initToolbars();

        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        getSupportActionBar().setTitle(null);
        getSupportActionBar().getThemedContext();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        mActivityTitle = getTitle().toString();

        nvDrawer = (NavigationView) findViewById(R.id.nvView);

        // Setup drawer view

        setupDrawerContent(nvDrawer);
        setupDrawer();
        //lockData(dataLocked);

        // If the app is just starting up, we must always allow refreshing of
        // data onscreen
        reloadData = true;

        lock = new Object();

        dt = new DataHelper(this);

        thisActivity = this;

        // Keyboard service manager
        imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        createDirs();

        range = (EditText) findViewById(R.id.range);
        plot = (EditText) findViewById(R.id.plot);

        tvRange = (TextView) findViewById(R.id.tvRange);
        tvPlot = (TextView) findViewById(R.id.tvPlot);

        drop3 = (TextView) findViewById(R.id.drop3);
        drop2 = (TextView) findViewById(R.id.drop2);
        drop1 = (TextView) findViewById(R.id.drop1);

        drop1prefix = (Spinner) findViewById(R.id.drop1prefix);
        drop2prefix = (Spinner) findViewById(R.id.drop2prefix);
        drop3prefix = (Spinner) findViewById(R.id.drop3prefix);

        traitBoolean = (LinearLayout) findViewById(R.id.booleanLayout);
        traitAudio = (LinearLayout) findViewById(R.id.audioLayout);
        traitCategorical = (LinearLayout) findViewById(R.id.categoricalLayout);
        traitDate = (LinearLayout) findViewById(R.id.dateLayout);
        traitNumeric = (LinearLayout) findViewById(R.id.numericLayout);
        traitPercent = (LinearLayout) findViewById(R.id.percentLayout);
        traitText = (LinearLayout) findViewById(R.id.textLayout);
        traitPhoto = (LinearLayout) findViewById(R.id.photoLayout);
        traitCounter = (LinearLayout) findViewById(R.id.counterLayout);
        traitDiseaseRating = (LinearLayout) findViewById(R.id.diseaseLayout);
        traitMulticat = (LinearLayout) findViewById(R.id.multicatLayout);
        traitLocation = (LinearLayout) findViewById(R.id.locationLayout);

        traitType = (Spinner) findViewById(R.id.traitType);
        newTraits = new HashMap();
        traitDetails = (TextView) findViewById(R.id.traitDetails);

        range.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                // do not do bit check on event, crashes keyboard
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    try {
                        moveRangeTo(rangeID, range.getText().toString(), false);
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
                        movePlotTo(rangeID, plot.getText().toString(), false);
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
        etCurVal = (EditText) findViewById(R.id.etCurVal);

        doRecord = (ImageButton) traitAudio.findViewById(R.id.record);
        doRecord.setOnClickListener(this);

        ImageButton capture = (ImageButton) traitPhoto.findViewById(R.id.capture);
        capture.setOnClickListener(this);
        photo = (Gallery) traitPhoto.findViewById(R.id.photo);

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
                            makeToast(getString(R.string.valuemore) + " " + currentTrait.maximum);
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
                        updateTrait(currentTrait.trait, "text", en.toString());
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
        seekBar = (SeekBar) traitPercent.findViewById(R.id.seekbar);
        seekBar.setMax(100);

        seekListener = new OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar sb, int progress, boolean arg2) {
                if (sb.getProgress() < Integer.parseInt(currentTrait.minimum))
                    sb.setProgress(Integer.parseInt(currentTrait.minimum));

                etCurVal.setText(String.valueOf(sb.getProgress()) + "%");
            }

            public void onStartTrackingTouch(SeekBar arg0) {
            }

            public void onStopTrackingTouch(SeekBar arg0) {
                updateTrait(currentTrait.trait, "percent", String.valueOf(seekBar.getProgress()));
            }
        };

        // Updates the progressbar value on screen and in memory hashmap
        seekBar.setOnSeekBarChangeListener(seekListener);

        month = (TextView) traitDate.findViewById(R.id.mth);
        day = (TextView) traitDate.findViewById(R.id.day);

        rangeName = (TextView) findViewById(R.id.rangeName);
        plotName = (TextView) findViewById(R.id.plotName);

        ImageButton getLocation = (ImageButton) traitLocation.findViewById(R.id.getLocationBtn);

        Button addDayBtn = (Button) traitDate.findViewById(R.id.addDateBtn);
        Button minusDayBtn = (Button) traitDate.findViewById(R.id.minusDateBtn);
        ImageButton saveDayBtn = (ImageButton) traitDate.findViewById(R.id.enterBtn);

        Button addCounterBtn = (Button) traitCounter.findViewById(R.id.addBtn);
        Button minusCounterBtn = (Button) traitCounter.findViewById(R.id.minusBtn);
        counterTv = (TextView) traitCounter.findViewById(R.id.curCount);

        // Multicat
        gridMultiCat = (ExpandableHeightGridView) traitMulticat.findViewById(R.id.catGrid);
        gridMultiCat.setExpanded(true);
        buttonsCreated = false;

        // Numeric
        Button k1 = (Button) traitNumeric.findViewById(R.id.k1);
        Button k2 = (Button) traitNumeric.findViewById(R.id.k2);
        Button k3 = (Button) traitNumeric.findViewById(R.id.k3);
        Button k4 = (Button) traitNumeric.findViewById(R.id.k4);
        Button k5 = (Button) traitNumeric.findViewById(R.id.k5);
        Button k6 = (Button) traitNumeric.findViewById(R.id.k6);
        Button k7 = (Button) traitNumeric.findViewById(R.id.k7);
        Button k8 = (Button) traitNumeric.findViewById(R.id.k8);
        Button k9 = (Button) traitNumeric.findViewById(R.id.k9);
        Button k10 = (Button) traitNumeric.findViewById(R.id.k10);
        Button k11 = (Button) traitNumeric.findViewById(R.id.k11);
        Button k12 = (Button) traitNumeric.findViewById(R.id.k12);
        Button k13 = (Button) traitNumeric.findViewById(R.id.k13);
        Button k14 = (Button) traitNumeric.findViewById(R.id.k14);
        Button k15 = (Button) traitNumeric.findViewById(R.id.k15);
        Button k16 = (Button) traitNumeric.findViewById(R.id.k16);

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

        rust0=(Button) traitDiseaseRating.findViewById(R.id.rust0);
        rust5=(Button) traitDiseaseRating.findViewById(R.id.rust5);
        rust10=(Button) traitDiseaseRating.findViewById(R.id.rust10);
        rust15=(Button) traitDiseaseRating.findViewById(R.id.rust15);
        rust20=(Button) traitDiseaseRating.findViewById(R.id.rust20);
        rust25=(Button) traitDiseaseRating.findViewById(R.id.rust25);
        rust30=(Button) traitDiseaseRating.findViewById(R.id.rust30);
        rust35=(Button) traitDiseaseRating.findViewById(R.id.rust35);
        rust40=(Button) traitDiseaseRating.findViewById(R.id.rust40);
        rust45=(Button) traitDiseaseRating.findViewById(R.id.rust45);
        rust50=(Button) traitDiseaseRating.findViewById(R.id.rust50);
        rust55=(Button) traitDiseaseRating.findViewById(R.id.rust55);
        rust60=(Button) traitDiseaseRating.findViewById(R.id.rust60);
        rust65=(Button) traitDiseaseRating.findViewById(R.id.rust65);
        rust70=(Button) traitDiseaseRating.findViewById(R.id.rust70);
        rust75=(Button) traitDiseaseRating.findViewById(R.id.rust75);
        rust80=(Button) traitDiseaseRating.findViewById(R.id.rust80);
        rust85=(Button) traitDiseaseRating.findViewById(R.id.rust85);
        rust90=(Button) traitDiseaseRating.findViewById(R.id.rust90);
        rust95=(Button) traitDiseaseRating.findViewById(R.id.rust95);
        rust100=(Button) traitDiseaseRating.findViewById(R.id.rust100);
        rustR=(Button) traitDiseaseRating.findViewById(R.id.rustR);
        rustM=(Button) traitDiseaseRating.findViewById(R.id.rustM);
        rustS=(Button) traitDiseaseRating.findViewById(R.id.rustS);
        rustDelim = (Button) traitDiseaseRating.findViewById(R.id.rustDelim);

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

        String primaryName = ep.getString("ImportFirstName", getString(R.string.range));
        String secondaryName = ep.getString("ImportSecondName", getString(R.string.plot));

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
                makeToast(ep.getString("ImportFirstName", getString(R.string.range)));
                return false;
            }
        });

        plotName.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                makeToast(ep.getString("ImportSecondName", getString(R.string.range)));
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

                if (ep.getBoolean("UseDay", false)) {
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
                    fullLocation = truncateDecimalString(String.valueOf(lat)) + "; " + truncateDecimalString(String.valueOf(lng));
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
                counterTv.setText(Integer.toString(Integer.parseInt(counterTv.getText().toString()) + 1));
                updateTrait(currentTrait.trait, "counter", counterTv.getText().toString());
            }
        });

        // Minus counter
        minusCounterBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                counterTv.setText(Integer.toString(Integer.parseInt(counterTv.getText().toString()) - 1));
                updateTrait(currentTrait.trait, "counter", counterTv.getText().toString());
            }
        });

        buttonArray[0] = (Button) traitCategorical.findViewById(R.id.q1);
        buttonArray[1] = (Button) traitCategorical.findViewById(R.id.q2);
        buttonArray[2] = (Button) traitCategorical.findViewById(R.id.q3);
        buttonArray[3] = (Button) traitCategorical.findViewById(R.id.q4);
        buttonArray[4] = (Button) traitCategorical.findViewById(R.id.q5);
        buttonArray[5] = (Button) traitCategorical.findViewById(R.id.q6);
        buttonArray[6] = (Button) traitCategorical.findViewById(R.id.q7);
        buttonArray[7] = (Button) traitCategorical.findViewById(R.id.q8);
        buttonArray[8] = (Button) traitCategorical.findViewById(R.id.q9);
        buttonArray[9] = (Button) traitCategorical.findViewById(R.id.q10);
        buttonArray[10] = (Button) traitCategorical.findViewById(R.id.q11);
        buttonArray[11] = (Button) traitCategorical.findViewById(R.id.q12);

        // Functions to clear all other color except this button's
        buttonArray[0].setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (checkButton(buttonArray[0])) {
                    return;
                }
                updateTrait(currentTrait.trait, currentTrait.format, buttonArray[0].getText().toString());
                setCategoricalButtons(buttonArray, buttonArray[0]);
            }
        });

        buttonArray[1].setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (checkButton(buttonArray[1])) {
                    return;
                }
                updateTrait(currentTrait.trait, currentTrait.format, buttonArray[1].getText().toString());
                setCategoricalButtons(buttonArray, buttonArray[1]);
            }
        });

        buttonArray[2].setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (checkButton(buttonArray[2])) {
                    return;
                }
                updateTrait(currentTrait.trait, currentTrait.format, buttonArray[2].getText().toString());
                setCategoricalButtons(buttonArray, buttonArray[2]);
            }
        });

        buttonArray[3].setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (checkButton(buttonArray[3])) {
                    return;
                }
                updateTrait(currentTrait.trait, currentTrait.format, buttonArray[3].getText().toString());
                setCategoricalButtons(buttonArray, buttonArray[3]);
            }
        });

        buttonArray[4].setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (checkButton(buttonArray[4])) {
                    return;
                }
                updateTrait(currentTrait.trait, currentTrait.format, buttonArray[4].getText().toString());
                setCategoricalButtons(buttonArray, buttonArray[4]);
            }
        });

        buttonArray[5].setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (checkButton(buttonArray[5])) {
                    return;
                }
                updateTrait(currentTrait.trait, currentTrait.format, buttonArray[5].getText().toString());
                setCategoricalButtons(buttonArray, buttonArray[5]);
            }
        });

        buttonArray[6].setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (checkButton(buttonArray[6])) {
                    return;
                }
                updateTrait(currentTrait.trait, currentTrait.format, buttonArray[6].getText().toString());
                setCategoricalButtons(buttonArray, buttonArray[6]);
            }
        });

        buttonArray[7].setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (checkButton(buttonArray[7])) {
                    return;
                }
                updateTrait(currentTrait.trait, currentTrait.format, buttonArray[7].getText().toString());
                setCategoricalButtons(buttonArray, buttonArray[7]);
            }
        });

        buttonArray[8].setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (checkButton(buttonArray[8])) {
                    return;
                }
                updateTrait(currentTrait.trait, currentTrait.format, buttonArray[8].getText().toString());
                setCategoricalButtons(buttonArray, buttonArray[8]);
            }
        });

        buttonArray[9].setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (checkButton(buttonArray[9])) {
                    return;
                }
                updateTrait(currentTrait.trait, currentTrait.format, buttonArray[9].getText().toString());
                setCategoricalButtons(buttonArray, buttonArray[9]);
            }
        });

        buttonArray[10].setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (checkButton(buttonArray[10])) {
                    return;
                }
                updateTrait(currentTrait.trait, currentTrait.format, buttonArray[10].getText().toString());
                setCategoricalButtons(buttonArray, buttonArray[10]);
            }
        });

        buttonArray[11].setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                if (checkButton(buttonArray[11])) {
                    return;
                }
                updateTrait(currentTrait.trait, currentTrait.format, buttonArray[11].getText().toString());
                setCategoricalButtons(buttonArray, buttonArray[11]);
            }
        });

        eImg = (ImageView) traitBoolean.findViewById(R.id.eImg);

        // Boolean
        eImg.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                String val = newTraits.get(currentTrait.trait).toString();

                if (val.equalsIgnoreCase("false")) {
                    val = "true";
                    eImg.setImageResource(R.drawable.boolean_true);
                } else {
                    val = "false";
                    eImg.setImageResource(R.drawable.boolean_false);
                }

                updateTrait(currentTrait.trait, "boolean", val);
            }
        });

        rangeLeft = (ImageView) findViewById(R.id.rangeLeft);
        rangeRight = (ImageView) findViewById(R.id.rangeRight);

        rangeLeft.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        rangeLeft.setImageResource(R.drawable.ml_arrows);
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
                        rangeLeft.setImageResource(R.drawable.ml_arrow);

                        if (repeatHandler == null) {
                            return true;
                        }
                        repeatHandler.removeCallbacks(mActionLeft);
                        repeatHandler = null;

                        repeatUpdate();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        rangeLeft.setImageResource(R.drawable.ml_arrow);

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
                if(ep.getBoolean("DisableEntryNavLeft",false) && !newTraits.containsKey(currentTrait.trait)) {

                    try {
                        int resID = getResources().getIdentifier("error", "raw", getPackageName());
                        MediaPlayer chimePlayer = MediaPlayer.create(MainActivity.this, resID);
                        chimePlayer.start();

                        chimePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            public void onCompletion(MediaPlayer mp) {
                                mp.release();
                            }
                        });
                    } catch (Exception ignore) {
                    }

                } else {
                    if (rangeID != null && rangeID.length > 0) {
                        //index.setEnabled(true);

                        // If ignore existing data is enabled, then skip accordingly
                        if (ep.getBoolean("IgnoreExisting", false)) {
                            int pos = paging;

                            while (pos >= 0) {
                                pos -= 1;

                                if (pos < 1)
                                    return;

                                if (!dt.getTraitExists(rangeID[pos - 1], currentTrait.trait,
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
                        cRange = dt.getRange(rangeID[paging - 1]);

                        Editor ed = ep.edit();
                        ed.putString("lastplot",cRange.plot_id);
                        ed.apply();

                        displayRange(cRange);

                        if (ep.getBoolean("RangeSound", false)) {
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

                        newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
                                .clone();

                        initWidgets(true);
                    }
                }

            }
        });

        rangeRight.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        rangeRight.setImageResource(R.drawable.mr_arrows);
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
                        rangeRight.setImageResource(R.drawable.mr_arrow);

                        if (repeatHandler == null) return true;
                        repeatHandler.removeCallbacks(mActionRight);
                        repeatHandler = null;

                        repeatUpdate();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        rangeRight.setImageResource(R.drawable.mr_arrow);

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

                if(ep.getBoolean("DisableEntryNavRight", false) && !newTraits.containsKey(currentTrait.trait)) {

                    try {
                        int resID = getResources().getIdentifier("error", "raw", getPackageName());
                        MediaPlayer chimePlayer = MediaPlayer.create(MainActivity.this, resID);
                        chimePlayer.start();

                        chimePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            public void onCompletion(MediaPlayer mp) {
                                mp.release();
                            };
                        });
                    } catch (Exception ignore) {
                    }

                } else {
                    if (rangeID != null && rangeID.length > 0) {
                        //index.setEnabled(true);

                        // If ignore existing data is enabled, then skip accordingly
                        if (ep.getBoolean("IgnoreExisting", false)) {
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

                                if (!dt.getTraitExists(rangeID[pos - 1], currentTrait.trait,
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
                        cRange = dt.getRange(rangeID[paging - 1]);

                        Editor ed = ep.edit();
                        ed.putString("lastplot",cRange.plot_id);
                        ed.apply();

                        displayRange(cRange);
                        if (ep.getBoolean("RangeSound", false)) {
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
                        newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
                                .clone();

                        initWidgets(true);
                    }
                }
            }
        });


        traitLeft = (ImageView) findViewById(R.id.traitLeft);
        traitRight = (ImageView) findViewById(R.id.traitRight);

        traitLeft.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        traitLeft.setImageResource(R.drawable.l_arrows);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        traitLeft.setImageResource(R.drawable.l_arrow);
                    case MotionEvent.ACTION_CANCEL:
                        break;
                }

                return false; // return true to prevent calling btn onClick handler
            }
        });

        // Go to previous trait
        traitLeft.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                // Force the keyboard to be hidden
                // This is meant to handle the keyboard bug
                try {
                    imm.hideSoftInputFromWindow(etCurVal.getWindowToken(), 0);
                } catch (Exception ignore) {
                }

                try {
                    imm.hideSoftInputFromWindow(etCurVal.getWindowToken(), 0);
                } catch (Exception ignore) {
                }

                int pos = traitType.getSelectedItemPosition() - 1;

                if (pos < 0) {
                    pos = traitType.getCount() - 1;

                    if (ep.getBoolean("CycleTraits", false))
                        rangeLeft.performClick();
                }

                traitType.setSelection(pos);
            }
        });

        traitRight.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        traitRight.setImageResource(R.drawable.r_arrows);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        traitRight.setImageResource(R.drawable.r_arrow);
                    case MotionEvent.ACTION_CANCEL:
                        break;
                }

                return false; // return true to prevent calling btn onClick handler
            }
        });

        // Go to next trait
        traitRight.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                // Force the keyboard to be hidden
                // This is meant to handle the keyboard bug
                try {
                    imm.hideSoftInputFromWindow(etCurVal.getWindowToken(), 0);
                } catch (Exception ignore) {
                }

                try {
                    imm.hideSoftInputFromWindow(etCurVal.getWindowToken(), 0);
                } catch (Exception ignore) {
                }

                int pos = traitType.getSelectedItemPosition() + 1;

                if (pos > traitType.getCount() - 1) {
                    pos = 0;

                    if (ep.getBoolean("CycleTraits", false))
                        rangeRight.performClick();
                }

                traitType.setSelection(pos);
            }
        });
    }

    private void initToolbars() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Toolbar toolbarBottom = (Toolbar) findViewById(R.id.toolbarBottom);

        missingValue = (ImageButton) toolbarBottom.findViewById(R.id.missingValue);
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

        deleteValue = (ImageButton) toolbarBottom.findViewById(R.id.deleteValue);
        deleteValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (currentTrait.format) {
                    case "categorical":
                        newTraits.remove(currentTrait.trait);
                        dt.deleteTrait(cRange.plot_id, currentTrait.trait);
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
                            eImg.setImageResource(R.drawable.boolean_true);
                        } else {
                            updateTrait(currentTrait.trait, "boolean", "false");
                            eImg.setImageResource(R.drawable.boolean_false);
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
                        doRecord.setImageResource(R.drawable.ic_audio);
                        mListening = false;
                        mRecording = false;
                        break;
                    default:
                        newTraits.remove(currentTrait.trait);
                        dt.deleteTrait(cRange.plot_id, currentTrait.trait);
                        etCurVal.setText("");
                        break;
                }
            }
        });

    }

    private void setupDrawer() {
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.string.drawer_open, R.string.drawer_close) {

            public void onDrawerOpened(View drawerView) {
                TextView person = (TextView) findViewById(R.id.nameLabel);
                person.setText(ep.getString("FirstName","") + " " + ep.getString("LastName",""));

                TextView template = (TextView) findViewById(R.id.currentField);
                template.setText(ep.getString("FieldFile",""));
            }

            public void onDrawerClosed(View view) {
            }

        };

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        selectDrawerItem(menuItem);
                        return true;
                    }
                });
    }

    public void selectDrawerItem(MenuItem menuItem) {

        switch(menuItem.getItemId()) {

            case R.id.nav_settings:
                Intent a = new Intent(this, ConfigActivity.class);
                startActivity(a);
                break;

            case R.id.nav_fields:
                MainActivity.dt.updateExpTable(false,true,false,0);
                Intent b = new Intent(this, FieldEditorActivity.class);
                startActivity(b);
                break;

            case R.id.nav_traits:
                Intent c = new Intent(this, TraitEditorActivity.class);
                startActivity(c);
                break;

            case R.id.nav_person:
                Intent d = new Intent(this, ConfigActivity.class);
                d.putExtra("dialog", "person");
                startActivity(d);
                break;

            case R.id.nav_location:
                Intent e = new Intent(this, ConfigActivity.class);
                e.putExtra("dialog", "location");
                startActivity(e);
                break;

            case R.id.nav_language:
                Intent f = new Intent(this, ConfigActivity.class);
                f.putExtra("dialog", "language");
                startActivity(f);
                break;
        }

        mDrawer.closeDrawers();
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
            dt.deleteTrait(cRange.plot_id, currentTrait.trait);
            setCategoricalButtons(buttonArray, null);
            return true;
        }
        return false;
    }

    // Create all necessary directories and subdirectories
    private void createDirs() {
        createDir(Constants.MPATH.getAbsolutePath());
        createDir(Constants.RESOURCEPATH);
        createDir(Constants.PLOTDATAPATH);
        createDir(Constants.TRAITPATH);
        createDir(Constants.FIELDIMPORTPATH);
        createDir(Constants.FIELDEXPORTPATH);
        createDir(Constants.BACKUPPATH);
        createDir(Constants.UPDATEPATH);
        createDir(Constants.ARCHIVEPATH);

        scanSampleFiles();
    }

    private void scanSampleFiles() {
        String[] fileList = {Constants.TRAITPATH + "/trait_sample.trt", Constants.FIELDIMPORTPATH + "/field_sample.csv", Constants.FIELDIMPORTPATH + "/field_sample.xls", Constants.TRAITPATH + "/severity.txt"};

        for (String aFileList : fileList) {
            File temp = new File(aFileList);
            if (temp.exists()) {
                Utils.scanFile(MainActivity.this,temp);
            }
        }
    }

    // Helper function to create a single directory
    private void createDir(String path) {
        File dir = new File(path);
        File blankFile = new File(path + "/.fieldbook");

        if (!dir.exists()) {
            dir.mkdirs();

            try {
                blankFile.getParentFile().mkdirs();
                blankFile.createNewFile();
                Utils.scanFile(MainActivity.this,blankFile);
            } catch (IOException ignore) {
            }
        }
    }

    // Simulate range left key press
    private void repeatLeft() {
        if (rangeID != null && rangeID.length > 0) {

            // If ignore existing data is enabled, then skip accordingly
            if (ep.getBoolean("IgnoreExisting", false)) {
                int pos = paging;

                while (pos >= 0) {
                    pos -= 1;

                    if (pos < 1)
                        pos = rangeID.length;

                    if (!dt.getTraitExists(rangeID[pos - 1], currentTrait.trait,
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
            cRange = dt.getRange(rangeID[paging - 1]);

            Editor ed = ep.edit();
            ed.putString("lastplot",cRange.plot_id);
            ed.apply();

            if (cRange.plot_id.length() == 0)
                return;

            if (ep.getBoolean("RangeSound", false)) {
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

            newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
                    .clone();

            initWidgets(true);
        }
    }

    // Simulate range right key press
    private void repeatRight() {
        if (rangeID != null && rangeID.length > 0) {
            //index.setEnabled(true);

            // If ignore existing data is enabled, then skip accordingly
            if (ep.getBoolean("IgnoreExisting", false)) {
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

                    if (!dt.getTraitExists(rangeID[pos - 1], currentTrait.trait,
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
            cRange = dt.getRange(rangeID[paging - 1]);
            Editor ed = ep.edit();
            ed.putString("lastplot",cRange.plot_id);
            ed.apply();

            if (cRange.plot_id.length() == 0)
                return;

            if (ep.getBoolean("RangeSound", false)) {
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
            newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
                    .clone();

            initWidgets(true);

        }
    }

    // This update should only be called after repeating keypress ends
    private void repeatUpdate() {
        if (rangeID == null)
            return;

        newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
                .clone();

        drop1Selection = drop1prefix.getSelectedItemPosition();
        drop2Selection = drop2prefix.getSelectedItemPosition();
        drop3Selection = drop3prefix.getSelectedItemPosition();

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

    // This is central to the application
    // Calling this function resets all the controls for traits, and picks one
    // to show based on the current trait data
    private void initWidgets(final boolean rangeSuppress) {
        // Reset dropdowns

        if (prefixTraits != null) {
            savePrefix = false;

            ArrayAdapter<String> prefixArrayAdapter = new ArrayAdapter<>(
                    this, R.layout.spinnerlayout, prefixTraits);

            drop1prefix.setAdapter(prefixArrayAdapter);
            drop1prefix.setSelection(drop1Selection);

            if (!drop1prefix.equals(null)) {
                int spinnerPosition = prefixArrayAdapter.getPosition(ep.getString("DROP1", prefixTraits[0]));
                drop1prefix.setSelection(spinnerPosition);
            }

            drop2prefix.setAdapter(prefixArrayAdapter);
            drop2prefix.setSelection(drop2Selection);

            if (!drop2prefix.equals(null)) {
                int spinnerPosition = prefixArrayAdapter.getPosition(ep.getString("DROP2", prefixTraits[1]));
                drop2prefix.setSelection(spinnerPosition);
            }

            drop3prefix.setAdapter(prefixArrayAdapter);
            drop3prefix.setSelection(drop3Selection);

            if (!drop3prefix.equals(null)) {
                int spinnerPosition = prefixArrayAdapter.getPosition(ep.getString("DROP3",prefixTraits[2]));
                drop3prefix.setSelection(spinnerPosition);
            }

            drop1prefix.setOnItemSelectedListener(new OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int pos, long arg3) {
                    try {

                        if (savePrefix)
                            drop1Selection = pos;

                        myList1 = dt.getDropDownRange(prefixTraits[pos], cRange.plot_id);

                        if (myList1 == null) {
                            drop1.setText(getString(R.string.nodata));
                        } else
                            drop1.setText(myList1[0]);
                            Editor e = ep.edit();
                            e.putString("DROP1", prefixTraits[pos]);
                            e.apply();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    drop1prefix.requestFocus();
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {

                }
            });

            drop2prefix.setOnItemSelectedListener(new OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int pos, long arg3) {

                    try {
                        if (savePrefix)
                            drop2Selection = pos;

                        myList2 = dt.getDropDownRange(prefixTraits[pos], cRange.plot_id);

                        if (myList2 == null) {
                            drop2.setText(getString(R.string.nodata));
                        } else
                            drop2.setText(myList2[0]);
                            Editor e = ep.edit();
                            e.putString("DROP2", prefixTraits[pos]);
                            e.apply();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {

                }
            });

            drop3prefix.setOnItemSelectedListener(new OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int pos, long arg3) {

                    try {
                        if (savePrefix)
                            drop3Selection = pos;

                        myList3 = dt.getDropDownRange(prefixTraits[pos], cRange.plot_id);

                        if (myList3 == null) {
                            drop3.setText(getString(R.string.nodata));
                        } else
                            drop3.setText(myList3[0]);
                            Editor e = ep.edit();
                            e.putString("DROP3", prefixTraits[pos]);
                            e.apply();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {

                }
            });

            savePrefix = true;

            drop1.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch(event.getAction()){
                        case MotionEvent.ACTION_DOWN:
                            drop1.setMaxLines(5);
                            break;
                        case MotionEvent.ACTION_UP:
                            drop1.setMaxLines(1);
                            break;
                    }
                    return true;
                }
            });

            drop2.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch(event.getAction()){
                        case MotionEvent.ACTION_DOWN:
                            drop2.setMaxLines(5);
                            break;
                        case MotionEvent.ACTION_UP:
                            drop2.setMaxLines(1);
                            break;
                    }
                    return true;
                }
            });

            drop3.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch(event.getAction()){
                        case MotionEvent.ACTION_DOWN:
                            drop3.setMaxLines(5);
                            break;
                        case MotionEvent.ACTION_UP:
                            drop3.setMaxLines(1);
                            break;
                    }
                    return true;
                }
            });
        }

        // trait is unique, format is not

        String[] traits = dt.getVisibleTrait();

        int traitPosition;

        try {
            traitPosition = traitType.getSelectedItemPosition();
        } catch (Exception f) {
            traitPosition = 0;
        }

        if (traits != null) {
            ArrayAdapter<String> directionArrayAdapter = new ArrayAdapter<>(
                    this, R.layout.spinnerlayout, traits);
            directionArrayAdapter
                    .setDropDownViewResource(R.layout.spinnerlayout);
            traitType.setAdapter(directionArrayAdapter);

            traitType.setOnItemSelectedListener(new OnItemSelectedListener() {

                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int arg2, long arg3) {

                    // This updates the in memory hashmap from database
                    currentTrait = dt.getDetail(traitType.getSelectedItem()
                            .toString());

                    imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (!currentTrait.format.equals("text")) {
                        try {
                            imm.hideSoftInputFromWindow(etCurVal.getWindowToken(), 0);
                        } catch (Exception ignore) {
                        }

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
                        traitText.setVisibility(View.VISIBLE);
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
                        traitText.setVisibility(View.GONE);
                        traitNumeric.setVisibility(View.VISIBLE);
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
                        traitText.setVisibility(View.GONE);
                        traitNumeric.setVisibility(View.GONE);
                        traitPercent.setVisibility(View.VISIBLE);
                        traitDate.setVisibility(View.GONE);
                        traitCategorical.setVisibility(View.GONE);
                        traitBoolean.setVisibility(View.GONE);
                        traitAudio.setVisibility(View.GONE);
                        traitPhoto.setVisibility(View.GONE);
                        traitCounter.setVisibility(View.GONE);
                        traitDiseaseRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);
                        traitLocation.setVisibility(View.GONE);

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

                            String curVal = newTraits.get(currentTrait.trait).toString() + "%";
                            etCurVal.setText(curVal);
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
                        traitText.setVisibility(View.GONE);
                        traitNumeric.setVisibility(View.GONE);
                        traitPercent.setVisibility(View.GONE);
                        traitDate.setVisibility(View.VISIBLE);
                        traitCategorical.setVisibility(View.GONE);
                        traitBoolean.setVisibility(View.GONE);
                        traitAudio.setVisibility(View.GONE);
                        traitPhoto.setVisibility(View.GONE);
                        traitCounter.setVisibility(View.GONE);
                        traitDiseaseRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);
                        traitLocation.setVisibility(View.GONE);

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
                        traitText.setVisibility(View.GONE);
                        traitNumeric.setVisibility(View.GONE);
                        traitPercent.setVisibility(View.GONE);
                        traitDate.setVisibility(View.GONE);
                        traitCategorical.setVisibility(View.VISIBLE);
                        traitBoolean.setVisibility(View.GONE);
                        traitAudio.setVisibility(View.GONE);
                        traitPhoto.setVisibility(View.GONE);
                        traitCounter.setVisibility(View.GONE);
                        traitDiseaseRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);
                        traitLocation.setVisibility(View.GONE);

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
                                buttonArray[i].setVisibility(Button.VISIBLE);
                                buttonArray[i].setText(cat[i]);
                                buttonArray[i].setTextColor(Color.BLACK);
                                buttonArray[i].setBackgroundColor(getResources().getColor(R.color.button_normal));
                            }
                        }
                    } else if (currentTrait.format.equals("boolean")) {
                        traitText.setVisibility(View.GONE);
                        traitNumeric.setVisibility(View.GONE);
                        traitPercent.setVisibility(View.GONE);
                        traitDate.setVisibility(View.GONE);
                        traitCategorical.setVisibility(View.GONE);
                        traitBoolean.setVisibility(View.VISIBLE);
                        traitAudio.setVisibility(View.GONE);
                        traitPhoto.setVisibility(View.GONE);
                        traitCounter.setVisibility(View.GONE);
                        traitDiseaseRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);
                        traitLocation.setVisibility(View.GONE);

                        etCurVal.setVisibility(EditText.GONE);
                        etCurVal.setEnabled(false);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            if (currentTrait.defaultValue.trim().equalsIgnoreCase("true")) {
                                updateTrait(currentTrait.trait, "boolean", "true");
                                eImg.setImageResource(R.drawable.boolean_true);
                            } else {
                                updateTrait(currentTrait.trait, "boolean", "false");
                                eImg.setImageResource(R.drawable.boolean_false);
                            }
                        } else {
                            String bval = newTraits.get(currentTrait.trait).toString();

                            if (bval.equalsIgnoreCase("false")) {
                                eImg.setImageResource(R.drawable.boolean_false);
                            } else {
                                eImg.setImageResource(R.drawable.boolean_true);
                            }

                        }
                    } else if (currentTrait.format.equals("audio")) {
                        traitText.setVisibility(View.GONE);
                        traitNumeric.setVisibility(View.GONE);
                        traitPercent.setVisibility(View.GONE);
                        traitDate.setVisibility(View.GONE);
                        traitCategorical.setVisibility(View.GONE);
                        traitBoolean.setVisibility(View.GONE);
                        traitAudio.setVisibility(View.VISIBLE);
                        traitPhoto.setVisibility(View.GONE);
                        traitCounter.setVisibility(View.GONE);
                        traitDiseaseRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);
                        traitLocation.setVisibility(View.GONE);

                        etCurVal.setVisibility(EditText.VISIBLE);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            doRecord.setImageResource(R.drawable.ic_audio);
                            etCurVal.setText("");
                        } else if(newTraits.containsKey(currentTrait.trait) && newTraits.get(currentTrait.trait).toString().equals("NA")) {
                            doRecord.setImageResource(R.drawable.ic_audio);
                            etCurVal.setText("NA");
                        } else {
                            mRecordingLocation = new File(newTraits.get(currentTrait.trait).toString());
                            doRecord.setImageResource(R.drawable.ic_play_arrow);
                            etCurVal.setText(getString(R.string.stored));
                        }

                    } else if (currentTrait.format.equals("photo")) {
                        traitText.setVisibility(View.GONE);
                        traitNumeric.setVisibility(View.GONE);
                        traitPercent.setVisibility(View.GONE);
                        traitDate.setVisibility(View.GONE);
                        traitCategorical.setVisibility(View.GONE);
                        traitBoolean.setVisibility(View.GONE);
                        traitAudio.setVisibility(View.GONE);
                        traitPhoto.setVisibility(View.VISIBLE);
                        traitCounter.setVisibility(View.GONE);
                        traitDiseaseRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);
                        traitLocation.setVisibility(View.GONE);

                        etCurVal.removeTextChangedListener(cvText);
                        etCurVal.removeTextChangedListener(cvNum);
                        etCurVal.setVisibility(EditText.GONE);
                        etCurVal.setEnabled(false);

                        // Always set to null as default, then fill in with trait value
                        photoLocation = new ArrayList<>();
                        drawables = new ArrayList<>();

                        File img = new File(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/" + "/photos/");
                        if (img.listFiles() != null) {
                            photoLocation = dt.getPlotPhotos(cRange.plot_id, currentTrait.trait);

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
                        traitText.setVisibility(View.GONE);
                        traitNumeric.setVisibility(View.GONE);
                        traitPercent.setVisibility(View.GONE);
                        traitDate.setVisibility(View.GONE);
                        traitCategorical.setVisibility(View.GONE);
                        traitBoolean.setVisibility(View.GONE);
                        traitAudio.setVisibility(View.GONE);
                        traitPhoto.setVisibility(View.GONE);
                        traitCounter.setVisibility(View.VISIBLE);
                        traitDiseaseRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);
                        traitLocation.setVisibility(View.GONE);

                        etCurVal.setVisibility(EditText.GONE);
                        etCurVal.setEnabled(false);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            counterTv.setText("0");
                        } else {
                            counterTv.setText(newTraits.get(currentTrait.trait).toString());
                        }

                    } else if(currentTrait.format.equals("rust rating") | currentTrait.format.equals("disease rating")) {
                        traitText.setVisibility(View.GONE);
                        traitNumeric.setVisibility(View.GONE);
                        traitPercent.setVisibility(View.GONE);
                        traitDate.setVisibility(View.GONE);
                        traitCategorical.setVisibility(View.GONE);
                        traitBoolean.setVisibility(View.GONE);
                        traitAudio.setVisibility(View.GONE);
                        traitPhoto.setVisibility(View.GONE);
                        traitCounter.setVisibility(View.GONE);
                        traitDiseaseRating.setVisibility(View.VISIBLE);
                        traitMulticat.setVisibility(View.GONE);
                        traitLocation.setVisibility(View.GONE);

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
                        traitMulticat.setVisibility(View.VISIBLE);
                        traitLocation.setVisibility(View.GONE);

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
                                    final Button newButton = (Button) LayoutInflater.from(MainActivity.this).inflate(R.layout.multicat_button, null);
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
        doRecord.setImageResource(R.drawable.ic_stop);
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
                    doRecord.setImageResource(R.drawable.ic_play_arrow);

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

    // Moving to a plot/range will obey the usual settings, such as ignore existing
    // For search results, this is bypassed e.g. always show result regardless
    private void moveTo(int[] rangeID, String range, String plot, boolean alwaysShow) {
        if (rangeID == null | range == null)
            return;

        boolean haveData = false;

        // Loop through all existing records
        // Find the first one that matches plot
        // However, if ignore existing data is enabled, then
        // move to the appropriate plot
        for (int j = 1; j <= rangeID.length; j++) {
            cRange = dt.getRange(rangeID[j - 1]);

            if (cRange.range.equals(range) & cRange.plot.equals(plot)) {
                if (alwaysShow) {
                    paging = j;

                    // Reload traits based on the selected plot
                    displayRange(cRange);

                    newTraits = (HashMap) dt.getUserDetail(
                            cRange.plot_id).clone();

                    initWidgets(false);

                    haveData = true;

                    break;
                } else if (ep.getBoolean("IgnoreExisting", false)) {
                    if (!dt.getTraitExists(rangeID[j - 1], currentTrait.trait,
                            currentTrait.format)) {
                        paging = j;

                        // Reload traits based on the selected
                        // plot
                        displayRange(cRange);

                        newTraits = (HashMap) dt.getUserDetail(
                                cRange.plot_id).clone();

                        initWidgets(false);
                        haveData = true;

                        break;
                    }
                } else {
                    paging = j;

                    // Reload traits based on the selected plot
                    displayRange(cRange);

                    newTraits = (HashMap) dt.getUserDetail(
                            cRange.plot_id).clone();

                    initWidgets(false);
                    haveData = true;

                    break;
                }
            }
        }

        if (!haveData)
            makeToast(getString(R.string.nomatches));
    }

    // Moving to a range will obey the usual settings, such as ignore existing
    // For search results, this is bypassed e.g. always show result regardless
    private void moveRangeTo(int[] rangeID, String range, boolean alwaysShow) {
        if (rangeID == null | range == null)
            return;

        boolean haveData = false;

        // Loop through all existing records
        // Find the first one that matches plot
        // However, if ignore existing data is enabled, then
        // move to the appropriate plot
        for (int j = 1; j <= rangeID.length; j++) {
            cRange = dt.getRange(rangeID[j - 1]);

            if (cRange.range.equals(range)) {
                if (alwaysShow) {
                    paging = j;

                    // Reload traits based on the selected plot
                    displayRange(cRange);

                    newTraits = (HashMap) dt.getUserDetail(
                            cRange.plot_id).clone();

                    initWidgets(false);

                    haveData = true;

                    break;
                } else if (ep.getBoolean("IgnoreExisting", false)) {
                    if (!dt.getTraitExists(rangeID[j - 1], currentTrait.trait,
                            currentTrait.format)) {
                        paging = j;

                        // Reload traits based on the selected
                        // plot
                        displayRange(cRange);

                        newTraits = (HashMap) dt.getUserDetail(
                                cRange.plot_id).clone();

                        initWidgets(false);

                        haveData = true;

                        break;
                    }
                } else {
                    paging = j;

                    // Reload traits based on the selected plot
                    displayRange(cRange);

                    newTraits = (HashMap) dt.getUserDetail(
                            cRange.plot_id).clone();

                    initWidgets(false);

                    haveData = true;

                    break;
                }
            }
        }

        if (!haveData)
            makeToast(getString(R.string.nomatches));
    }

    // Moving to a plot will obey the usual settings, such as ignore existing
    // For search results, this is bypassed e.g. always show result regardless
    private void movePlotTo(int[] rangeID, String plot, boolean alwaysShow) {
        if (rangeID == null | range == null)
            return;

        boolean haveData = false;

        // Loop through all existing records
        // Find the first one that matches plot
        // However, if ignore existing data is enabled, then
        // move to the appropriate plot
        for (int j = 1; j <= rangeID.length; j++) {
            cRange = dt.getRange(rangeID[j - 1]);

            if (cRange.plot.equals(plot)) {
                if (alwaysShow) {
                    paging = j;

                    // Reload traits based on the selected plot
                    displayRange(cRange);

                    newTraits = (HashMap) dt.getUserDetail(
                            cRange.plot_id).clone();

                    initWidgets(false);

                    haveData = true;

                    break;
                } else if (ep.getBoolean("IgnoreExisting", false)) {
                    if (!dt.getTraitExists(rangeID[j - 1], currentTrait.trait,
                            currentTrait.format)) {
                        paging = j;

                        // Reload traits based on the selected
                        // plot
                        displayRange(cRange);

                        newTraits = (HashMap) dt.getUserDetail(
                                cRange.plot_id).clone();

                        initWidgets(false);

                        haveData = true;

                        break;
                    }
                } else {
                    paging = j;

                    // Reload traits based on the selected plot
                    displayRange(cRange);

                    newTraits = (HashMap) dt.getUserDetail(
                            cRange.plot_id).clone();

                    initWidgets(false);

                    haveData = true;

                    break;
                }
            }
        }

        if (!haveData)
            makeToast(getString(R.string.nomatches));
    }

    @Override
    public void onPause() {

        //save last plot id
        if (ep.getBoolean("ImportFieldFinished", false)) {
            if(cRange!=null) {
                Editor ed = ep.edit();
                ed.putString("lastplot", cRange.plot_id);
                ed.apply();
            }
        }

        // Backup database
        try {
            dt.exportDatabase("backup");
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
            Editor ed = ep.edit();
            ed.putString("lastplot", cRange.plot_id);
            ed.apply();
        }

        try {
            TutorialMainActivity.thisActivity.finish();
        } catch (Exception ignore) {
        }

        // Always close the database connection when the app ends
        dt.close();

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Update menu item visibility
        if (systemMenu != null) {

            if (ep.getBoolean("Tips", false)) {
                systemMenu.findItem(R.id.help).setVisible(true);
            } else {
                systemMenu.findItem(R.id.help).setVisible(false);
            }
            if (ep.getBoolean("JumpToPlot", false)) {
                systemMenu.findItem(R.id.jumpToPlot).setVisible(true);
            } else {
                systemMenu.findItem(R.id.jumpToPlot).setVisible(false);
            }

            if (ep.getBoolean("NextEmptyPlot", false)) {
                systemMenu.findItem(R.id.nextEmptyPlot).setVisible(true);
            } else {
                systemMenu.findItem(R.id.nextEmptyPlot).setVisible(false);
            }

            if (ep.getBoolean("BarcodeScan", false)) {
                systemMenu.findItem(R.id.barcodeScan).setVisible(true);
            } else {
                systemMenu.findItem(R.id.barcodeScan).setVisible(false);
            }
            if (ep.getBoolean("DataGrid", false)) {
                systemMenu.findItem(R.id.datagrid).setVisible(true);
            } else {
                systemMenu.findItem(R.id.datagrid).setVisible(false);
            }

        }

        // This allows dynamic language change without exiting the app
        local = ep.getString("language", Locale.getDefault().getCountry());
        region = ep.getString("region",Locale.getDefault().getLanguage());

        Locale locale2 = new Locale(local,region);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2, getBaseContext().getResources()
                .getDisplayMetrics());
        invalidateOptionsMenu();

        nvDrawer.getMenu().clear();
        nvDrawer.inflateMenu(R.menu.drawer_view);

        // If reload data is true, it means there was an import operation, and
        // the screen should refresh
        if (reloadData) {
            reloadData = false;
            partialReload = false;

            if (ep.getBoolean("QuickGoTo", false)) {
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

            String primaryName = ep.getString("ImportFirstName", getString(R.string.range)) + ":";
            String secondaryName = ep.getString("ImportSecondName", getString(R.string.plot)) + ":";

            if(primaryName.length()>8) {
                primaryName = primaryName.substring(0,7) + ":";
            }

            if(secondaryName.length()>8) {
                secondaryName = secondaryName.substring(0,7) + ":";
            }

            rangeName.setText(primaryName);
            plotName.setText(secondaryName);

            paging = 1;

            rangeID = dt.getAllRangeID();

            if (rangeID != null) {
                cRange = dt.getRange(rangeID[0]);
                lastRange = cRange.range;
                displayRange(cRange);

                newTraits = (HashMap) dt.getUserDetail(cRange.plot_id).clone();
            }

            prefixTraits = MainActivity.dt.getRangeColumnNames();

            initWidgets(false);
            traitType.setSelection(0);

            // try to go to last saved plot
            if(ep.getString("lastplot",null)!=null) {
                String plot = dt.getPlotFromId(ep.getString("lastplot",null));
                String range = dt.getRangeFromId(ep.getString("lastplot",null));
                rangeID = dt.getAllRangeID();
                moveTo(rangeID, range, plot, true);
            }

        } else if (partialReload) {
            partialReload = false;

            displayRange(cRange);
            prefixTraits = MainActivity.dt.getRangeColumnNames();
            initWidgets(false);

        } else if (searchReload) {
            searchReload = false;
            paging = 1;

            if (rangeID != null) {
                moveTo(rangeID, searchRange, searchPlot, true);
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
        dt.deleteTrait(cRange.plot_id, parent);

        String exp_id = Integer.toString(ep.getInt("ExpID", 0));
        dt.insertUserTraits(cRange.plot_id, parent, trait, value, ep.getString("FirstName", "") + " " + ep.getString("LastName", ""), ep.getString("Location", ""), "", exp_id); //TODO add notes and exp_id
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
        dt.deleteTrait(cRange.plot_id, parent);
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

        new MenuInflater(MainActivity.this).inflate(R.menu.mainmenu, menu);

        systemMenu = menu;

        if (ep.getBoolean("Tips", false)) {
            systemMenu.findItem(R.id.help).setVisible(true);
        } else {
            systemMenu.findItem(R.id.help).setVisible(false);
        }

        if (ep.getBoolean("JumpToPlot", false)) {
            systemMenu.findItem(R.id.jumpToPlot).setVisible(true);
        } else {
            systemMenu.findItem(R.id.jumpToPlot).setVisible(false);
        }

        if (ep.getBoolean("NextEmptyPlot", false)) {
            systemMenu.findItem(R.id.nextEmptyPlot).setVisible(true);
        } else {
            systemMenu.findItem(R.id.nextEmptyPlot).setVisible(false);
        }

        if (ep.getBoolean("BarcodeScan", false)) {
            systemMenu.findItem(R.id.barcodeScan).setVisible(true);
        } else {
            systemMenu.findItem(R.id.barcodeScan).setVisible(false);
        }

        if (ep.getBoolean("DataGrid", false)) {
            systemMenu.findItem(R.id.datagrid).setVisible(true);
        } else {
            systemMenu.findItem(R.id.datagrid).setVisible(false);
        }

        lockData(dataLocked);

        return true;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Intent intent = new Intent(Intent.ACTION_VIEW);

        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawer.openDrawer(GravityCompat.START);
                return true;

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
                intent.putExtra("title",getString(R.string.resources));
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
                barcodeScan();
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
        }
        return super.onOptionsItemSelected(item);
    }

    private void lockData(Boolean lock) {
        LinearLayout[] traitViews = {traitText, traitNumeric, traitPercent, traitDate, traitCategorical,
                traitBoolean, traitAudio, traitPhoto, traitCounter, traitDiseaseRating, traitMulticat};

        if(lock) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_lock);
            missingValue.setEnabled(false);
            deleteValue.setEnabled(false);
            etCurVal.setEnabled(false);

            for(LinearLayout traitLayout : traitViews) {
                disableViews(traitLayout);
            }

        } else {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_unlock);
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

    private void barcodeScan() {
        IntentIntegrator integrator = new IntentIntegrator(thisActivity);
        integrator.initiateScan();
    }

    private void moveToPlotID() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.gotobarcode, null);

        builder.setTitle(R.string.jumptoplotidbutton)
                .setCancelable(true)
                .setView(layout);

        goToId = builder.create();

        android.view.WindowManager.LayoutParams langParams = goToId.getWindow().getAttributes();
        langParams.width = LayoutParams.MATCH_PARENT;
        goToId.getWindow().setAttributes(langParams);

        final EditText barcodeId = (EditText) layout.findViewById(R.id.barcodeid);
        Button exportButton = (Button) layout.findViewById(R.id.saveBtn);
        Button closeBtn = (Button) layout.findViewById(R.id.closeBtn);
        Button camBtn = (Button) layout.findViewById(R.id.camBtn);

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
                String plot = dt.getPlotFromId(inputPlotId);
                String range = dt.getRangeFromId(inputPlotId);
                rangeID = dt.getAllRangeID();
                moveTo(rangeID, range, plot, true);
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

            if (!dt.getTraitExists(rangeID[pos - 1], currentTrait.trait,
                    currentTrait.format)) {
                paging = pos;
                break;
            }
        }
        cRange = dt.getRange(rangeID[paging - 1]);
        displayRange(cRange);
        lastRange = cRange.range;
        newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
                .clone();
        initWidgets(true);
    }

    public void nextEmptyPlot2() { //TODO fix this
            //If multiTraitJump is on
            //Loop through previous plots
            int pos = paging;
            if (pos == rangeID.length) {
                pos = 1;
                return;
            }

            while (pos <= rangeID.length) {
                //Move to next plot
                pos += 1;

                //Check traits
                String[] traitNames = dt.getVisibleTrait();
                String[] traitType = dt.getFormat();
                int traitHolder = -1;
                for(int traitCounter = 0; traitCounter<traitNames.length; traitCounter++) {
                    //if a trait already exists, break out
                    if (!dt.getTraitExists(rangeID[pos - 1], traitNames[traitCounter],
                            traitType[traitCounter])) {
                        paging = pos;
                        traitHolder = traitCounter;
                        break;
                    }
                }
                if(traitHolder!=-1) {
                    int currentHolder = -1;
                    // Find index for currentTrait
                    for(int currentTraitCounter = 0; currentTraitCounter < traitNames.length; currentTraitCounter++) {
                        if(traitNames[currentTraitCounter].equals(currentTrait.trait)) {
                            currentHolder = currentTraitCounter;
                            break;
                        }
                    }

                    if(currentHolder == traitHolder) {
                        //do nothing
                        break;
                    }
                    else if(currentHolder > traitHolder) {
                        //Loop left currentHolder - traitHolder times
                        for(int loopCounter = 0; loopCounter < (currentHolder-traitHolder); loopCounter++) {
                            traitLeft.performClick();
                        }
                        break;
                    }
                    else {
                        //Loop traits right traitHolder - currentHolder
                        for(int loopCounter = 0; loopCounter < (traitHolder-currentHolder); loopCounter++) {
                            traitRight.performClick();
                        }
                        break;
                    }
                }
            }

        // If ignore existing data is enabled, then skip accordingly


        // Refresh onscreen controls
        cRange = dt.getRange(rangeID[paging - 1]);

        Editor ed = ep.edit();
        ed.putString("lastplot", cRange.plot_id);
        ed.apply();

        displayRange(cRange);

        newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
                .clone();

        initWidgets(true);
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
                        makeToast(getString(R.string.maxphotos));
                } catch (Exception e) {
                    e.printStackTrace();
                    makeToast(getString(R.string.hardwaremissing));
                }
                break;

            case R.id.record:
                newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
                        .clone();

                if (mListening) {
                    mPlayer.stop();
                    doRecord.setImageResource(R.drawable.ic_play_arrow);

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

                    etCurVal.setText(getString(R.string.stored));

                    mRecording = false;
                    //doRecord.setText(R.string.play);
                    doRecord.setImageResource(R.drawable.ic_play_arrow);

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

                    doRecord.setImageResource(R.drawable.ic_stop);
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
                makeToast(getString(R.string.rustwarning));
            } else {
                etCurVal.setText(etCurVal.getText().toString() + v);
            }
        }
    }

    private void deletePhotoWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        builder.setTitle(getString(R.string.warning));
        builder.setMessage(getString(R.string.deletePhoto));

        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {

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
                    dt.deleteTraitByValue(cRange.plot_id, currentTrait.trait, item);

                    // Only do a purge by trait when there are no more images left
                    if (photoLocation.size() == 0)
                        removeTrait(currentTrait.trait);

                    photoAdapter = new GalleryImageAdapter(MainActivity.this, drawables);

                    photo.setAdapter(photoAdapter);
                } else {
                    ArrayList<Drawable> emptyList = new ArrayList<Drawable>();

                    photoAdapter = new GalleryImageAdapter(MainActivity.this, emptyList);

                    photo.setAdapter(photoAdapter);
                }
            }

        });

        builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {

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
                    Uri.fromFile(file));
            startActivityForResult(takePictureIntent, 252);
        }
    }

    private String getRep() {
        int repInt = MainActivity.dt.getRep(MainActivity.cRange.plot_id,currentTrait.trait);
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
        Uri contentUri = Uri.fromFile(file);
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
        View layout = inflater.inflate(R.layout.summary, null);

        builder.setTitle(R.string.mapsummary)
                .setCancelable(true)
                .setView(layout);

        final AlertDialog dialog = builder.create();

        android.view.WindowManager.LayoutParams params2 = dialog.getWindow().getAttributes();
        params2.width = LayoutParams.MATCH_PARENT;
        dialog.getWindow().setAttributes(params2);

        Button closeBtn = (Button) layout.findViewById(R.id.closeBtn);
        TextView summaryText = (TextView) layout.findViewById(R.id.field_name);

        closeBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        String[] traitList = dt.getAllTraits();
        String data = "";

        if (cRange != null) {
            for (String s : prefixTraits) {
                data += s + ": " + dt.getDropDownRange(s, cRange.plot_id)[0] + "\n";
            }
        }

        for (String s : traitList) {
            if (newTraits.containsKey(s)) {
                data += s + ": " + newTraits.get(s).toString() + "\n";
            }
        }

        summaryText.setText(data);

        dialog.show();
    }

    public int getVersion() {
        int v = 0;
        try {
            v = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG,"" + e.getMessage());
        }
        return v;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                return true;

            case 92:
                if (rangeID != null && rangeID.length > 0) {

                    paging -= 1;

                    if (paging < 1)
                        paging = rangeID.length;

                    cRange = dt.getRange(rangeID[paging - 1]);

                    displayRange(cRange);

                    newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
                            .clone();

                    initWidgets(true);
                }

                break;

            case 93:
                if (rangeID != null && rangeID.length > 0) {
                    paging += 1;

                    if (paging > rangeID.length)
                        paging = 1;

                    cRange = dt.getRange(rangeID[paging - 1]);

                    displayRange(cRange);

                    newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
                            .clone();

                    initWidgets(true);
                }
                break;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void updateTraitAllowDuplicates(String parent, String trait, String value) {

        if (cRange == null || cRange.plot_id.length() == 0) {
            return;
        }

        Log.d("Field Book",trait + " " + value);

        if (newTraits.containsKey(parent))
            newTraits.remove(parent);

        newTraits.put(parent, value);

        dt.deleteTraitByValue(cRange.plot_id, parent, value);

        String exp_id = Integer.toString(ep.getInt("ExpID", 0));
        dt.insertUserTraits(cRange.plot_id, parent, trait, value, ep.getString("FirstName","") + " " + ep.getString("LastName",""), ep.getString("Location",""),"",exp_id); //TODO add notes and exp_id
    }

    private void displayPlotImage(String path) {
        try {
            Log.w("Display path", path);

            File f = new File(path);

            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(f), "image/*");
            startActivity(intent);
        } catch (Exception ignore) {
        }
    }

    private Bitmap displayScaledSavedPhoto(String path) {
        if (path == null) {
            makeToast(getString(R.string.photomissing));
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
                return BitmapFactory.decodeResource(getResources(), R.drawable.photo_missing);
            }

            catch(OutOfMemoryError oom) {
                Log.e(TAG, "-- OOM Error in setting image");
            }

            return correctBmp;

        } catch (Exception e) {
            return BitmapFactory.decodeResource(getResources(), R.drawable.photo_missing);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 1:
                if (resultCode == RESULT_OK) {
                    String mChosenFileString = data.getStringExtra("result");
                    File mChosenFile = new File(mChosenFileString);

                    String suffix = mChosenFileString.substring(mChosenFileString.lastIndexOf('.') + 1).toLowerCase();

                    String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix);
                    Intent open = new Intent(Intent.ACTION_VIEW);
                    open.setDataAndType((Uri.fromFile(mChosenFile)), mime);

                    startActivity(open);
                }
                break;
            case 2:
                if (resultCode == RESULT_OK) {
                    inputPlotId = data.getStringExtra("result");
                    String plot = dt.getPlotFromId(inputPlotId);
                    String range = dt.getRangeFromId(inputPlotId);
                    rangeID = dt.getAllRangeID();
                    moveTo(rangeID, range, plot, true);
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
            String plot = dt.getPlotFromId(inputPlotId);
            String range = dt.getRangeFromId(inputPlotId);
            rangeID = dt.getAllRangeID();
            moveTo(rangeID, range, plot, true);
            if(goToId!=null) {
                goToId.dismiss();
            }
        }
    }

    public String truncateDecimalString(String v) {
        int count = 0;

        boolean found = false;

        String truncated = "";

        for (int i = 0; i < v.length(); i++) {
            if (found) {
                count += 1;

                if (count == 5)
                    break;
            }

            if (v.charAt(i) == '.') {
                found = true;
            }

            truncated += v.charAt(i);
        }

        return truncated;
    }
}