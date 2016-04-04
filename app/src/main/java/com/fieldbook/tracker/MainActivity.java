package com.fieldbook.tracker;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
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
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
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
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
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

import com.fieldbook.tracker.Barcodes.*;
import com.fieldbook.tracker.Search.*;
import com.fieldbook.tracker.Trait.*;
import com.fieldbook.tracker.Tutorial.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormatSymbols;
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

    private String currentServerVersion = "";
    String versionName;
    int versionNum;

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

    private TextView dpi;
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

    private AutoResizeTextView drop3;
    private AutoResizeTextView drop2;
    private AutoResizeTextView drop1;

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
    private Button doRecord;
    private Button clearRecord;
    private boolean mRecording;
    private boolean mListening = false;

    private int tempMonth;
    private TextView month;
    private TextView day;

    private SeekBar seekBar;
    private OnSeekBarChangeListener seekListener;

    private TextView counterTv;

    Button rust0, rust5, rust10, rust15, rust20, rust25, rust30, rust35, rust40, rust45, rust50, rust55, rust60, rust65, rust70, rust75, rust80, rust85, rust90, rust95, rust100, rustR, rustMR, rustMS, rustS, rustDelim, rustClear;

    final Button buttonArray[] = new Button[12];

    ExpandableHeightGridView gridMultiCat;
    Boolean buttonsCreated;

    private EditText eNum;
    private EditText pNum;
    private EditText tNum;
    private TextWatcher eNumUpdate;
    private TextWatcher tNumUpdate;

    private Handler mHandler = new Handler();

    private InputMethodManager imm;

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
    LinearLayout traitRustRating;
    LinearLayout traitMulticat;

    /**
     * Test area
     */

    private DrawerLayout mDrawer;
    private Toolbar toolbar;
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private String mActivityTitle;
    NavigationView nvDrawer;

    private Boolean dataLocked = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        // Enforce internal language change
        local = ep.getString("language", "en");
        region = ep.getString("region", "");
        Locale locale2 = new Locale(local,region);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2, getBaseContext().getResources()
                .getDisplayMetrics());

        loadScreen();
        //checkNewVersion(); TODO fix this

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

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

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

        drop3 = (AutoResizeTextView) findViewById(R.id.drop3);
        drop2 = (AutoResizeTextView) findViewById(R.id.drop2);
        drop1 = (AutoResizeTextView) findViewById(R.id.drop1);

        drop1prefix = (Spinner) findViewById(R.id.drop1prefix);
        drop2prefix = (Spinner) findViewById(R.id.drop2prefix);
        drop3prefix = (Spinner) findViewById(R.id.drop3prefix);

        dpi = (TextView) findViewById(R.id.dpi);
        int pixelSize = getPixelSize();

        drop1.setTextSize(pixelSize);
        drop2.setTextSize(pixelSize);
        drop3.setTextSize(pixelSize);

        drop1.setAddEllipsis(true);
        drop2.setAddEllipsis(true);
        drop3.setAddEllipsis(true);

        traitBoolean = (LinearLayout) findViewById(R.id.booleanLayout);
        traitAudio = (LinearLayout) findViewById(R.id.audioLayout);
        traitCategorical = (LinearLayout) findViewById(R.id.categoricalLayout);
        traitDate = (LinearLayout) findViewById(R.id.dateLayout);
        traitNumeric = (LinearLayout) findViewById(R.id.numericLayout);
        traitPercent = (LinearLayout) findViewById(R.id.percentLayout);
        traitText = (LinearLayout) findViewById(R.id.textLayout);
        traitPhoto = (LinearLayout) findViewById(R.id.photoLayout);
        traitCounter = (LinearLayout) findViewById(R.id.counterLayout);
        traitRustRating = (LinearLayout) findViewById(R.id.rustLayout);
        traitMulticat = (LinearLayout) findViewById(R.id.multicatLayout);

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
                    } catch (Exception e) {
                        ErrorLog("MainScreenError.txt", "" + e.getMessage());
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
                    } catch (Exception e) {
                        ErrorLog("MainScreenError.txt", "" + e.getMessage());
                    }
                    return true;
                }

                return false;
            }
        });

        // tNum is the text format entry
        tNum = (EditText) findViewById(R.id.tNum);

        // eNum is the numeric format entry
        eNum = (EditText) findViewById(R.id.eNum);

        // pNum is the text area reflecting the progress bar value
        pNum = (EditText) findViewById(R.id.pNum);

        // Clear button for most traits
        doRecord = (Button) traitAudio.findViewById(R.id.record);
        doRecord.setOnClickListener(this);

        clearRecord = (Button) traitAudio.findViewById(R.id.clearRecord);
        clearRecord.setOnClickListener(this);

        Button capture = (Button) traitPhoto.findViewById(R.id.capture);
        capture.setOnClickListener(this);

        Button captureClear = (Button) traitPhoto.findViewById(R.id.clearPhoto);
        captureClear.setOnClickListener(this);

        photo = (Gallery) traitPhoto.findViewById(R.id.photo);

        tNum.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView exampleView, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    rangeRight.performClick();

                    return true;
                }

                return false;
            }
        });

        // Validates the text entered for numeric format
        eNumUpdate = new TextWatcher() {

            public void afterTextChanged(final Editable en) {
                Timer timer = new Timer();
                final long DELAY = 750; // in ms

                try {
                    final double val = Double.parseDouble(eNum.getText().toString());
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
                            Toast.makeText(
                                    MainActivity.this,
                                    getString(R.string.valuemore) + " "
                                            + currentTrait.maximum,
                                    Toast.LENGTH_LONG).show();

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

        eNum.addTextChangedListener(eNumUpdate);

        // Validates the text entered for text format
        tNumUpdate = new TextWatcher() {
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

        //tNum.addTextChangedListener(tNumUpdate);

        // Progress bar
        seekBar = (SeekBar) traitPercent.findViewById(R.id.seekbar);
        Button clearPercent = (Button) traitPercent.findViewById(R.id.clearPercent);
        seekBar.setMax(100);

        seekListener = new OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar sb, int progress,
                                          boolean arg2) {

                if (sb.getProgress() < Integer.parseInt(currentTrait.minimum))
                    sb.setProgress(Integer.parseInt(currentTrait.minimum));

                pNum.setText(String.valueOf(sb.getProgress()) + "%");
            }

            public void onStartTrackingTouch(SeekBar arg0) {
            }

            public void onStopTrackingTouch(SeekBar arg0) {

                updateTrait(currentTrait.trait, "percent", String.valueOf(seekBar.getProgress()));
            }
        };

        eNum.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View arg0, MotionEvent arg1) {
                return true;
            }
        });

        // Updates the progressbar value on screen and in memory hashmap
        seekBar.setOnSeekBarChangeListener(seekListener);

        month = (TextView) traitDate.findViewById(R.id.mth);
        day = (TextView) traitDate.findViewById(R.id.day);

        rangeName = (TextView) findViewById(R.id.rangeName);
        plotName = (TextView) findViewById(R.id.plotName);

        Button addDayBtn = (Button) traitDate.findViewById(R.id.addDateBtn);
        Button minusDayBtn = (Button) traitDate.findViewById(R.id.minusDateBtn);
        Button saveDayBtn = (Button) traitDate.findViewById(R.id.enterBtn);
        Button clearDate = (Button) traitDate.findViewById(R.id.clearDateBtn);
        Button noDateBtn = (Button) traitDate.findViewById(R.id.noDateBtn);

        Button addCounterBtn = (Button) traitCounter.findViewById(R.id.addBtn);
        Button minusCounterBtn = (Button) traitCounter.findViewById(R.id.minusBtn);
        Button clearCounterBtn = (Button) traitCounter.findViewById(R.id.clearCounterBtn);
        counterTv = (TextView) traitCounter.findViewById(R.id.curCount);

        // Multicat
        Button clearMultiCat = (Button) traitMulticat.findViewById(R.id.clearMultiCatBtn);
        gridMultiCat = (ExpandableHeightGridView) traitMulticat.findViewById(R.id.catGrid);
        gridMultiCat.setExpanded(true);
        buttonsCreated = false;

        //Button clearBtn = (Button) findViewById(R.id.clearBtn);
        Button clearCat = (Button) traitCategorical.findViewById(R.id.clearCatBtn);

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

        rust0=(Button)traitRustRating.findViewById(R.id.rust0);
        rust5=(Button)traitRustRating.findViewById(R.id.rust5);
        rust10=(Button)traitRustRating.findViewById(R.id.rust10);
        rust15=(Button)traitRustRating.findViewById(R.id.rust15);
        rust20=(Button)traitRustRating.findViewById(R.id.rust20);
        rust25=(Button)traitRustRating.findViewById(R.id.rust25);
        rust30=(Button)traitRustRating.findViewById(R.id.rust30);
        rust35=(Button)traitRustRating.findViewById(R.id.rust35);
        rust40=(Button)traitRustRating.findViewById(R.id.rust40);
        rust45=(Button)traitRustRating.findViewById(R.id.rust45);
        rust50=(Button)traitRustRating.findViewById(R.id.rust50);
        rust55=(Button)traitRustRating.findViewById(R.id.rust55);
        rust60=(Button)traitRustRating.findViewById(R.id.rust60);
        rust65=(Button)traitRustRating.findViewById(R.id.rust65);
        rust70=(Button)traitRustRating.findViewById(R.id.rust70);
        rust75=(Button)traitRustRating.findViewById(R.id.rust75);
        rust80=(Button)traitRustRating.findViewById(R.id.rust80);
        rust85=(Button)traitRustRating.findViewById(R.id.rust85);
        rust90=(Button)traitRustRating.findViewById(R.id.rust90);
        rust95=(Button)traitRustRating.findViewById(R.id.rust95);
        rust100=(Button)traitRustRating.findViewById(R.id.rust100);
        rustR=(Button)traitRustRating.findViewById(R.id.rustR);
        rustMR=(Button)traitRustRating.findViewById(R.id.rustMR);
        rustMS=(Button)traitRustRating.findViewById(R.id.rustMS);
        rustS=(Button)traitRustRating.findViewById(R.id.rustS);
        rustDelim = (Button) traitRustRating.findViewById(R.id.rustDelim);
        rustClear = (Button) traitRustRating.findViewById(R.id.clearRustBtn);

        Button[] rustBtnArray = new Button[]{rust0,rust5,rust10,rust15,rust20,rust25,rust30,rust35,rust40,rust45,rust50,rust55,rust60,rust65,rust70,rust75,rust80,rust85,rust90,rust95,rust100};
        List<String> temps = new ArrayList<String>();
        List<String> tempsNoFile = Arrays.asList("0","5","10","15","20","25","30","35","40","45","50","55","60","65","70","75","80","85","90","95","100");
        String token1;
        Scanner inFile1 = null;

        try {
            inFile1 = new Scanner(new File(Constants.TRAITPATH + "/severity.txt"));
        } catch (FileNotFoundException e) {
            ErrorLog("RustError.txt", "" + e.getMessage());
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
        rustMR.setOnClickListener(this);
        rustMS.setOnClickListener(this);
        rustS.setOnClickListener(this);
        rustDelim.setOnClickListener(this);
        rustClear.setOnClickListener(this);

        rangeName.setText(ep.getString("ImportFirstName", getString(R.string.range)) + ":");
        plotName.setText(ep.getString("ImportSecondName", getString(R.string.plot)) + ":");

        clearPercent.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                seekBar.setOnSeekBarChangeListener(null);

                pNum.setText("");
                seekBar.setProgress(0);
                pNum.setTextColor(Color.BLACK);

                if (currentTrait.defaultValue != null
                        && currentTrait.defaultValue.length() > 0) {
                    pNum.setText(currentTrait.defaultValue + "%");
                    seekBar.setProgress(Integer
                            .valueOf(currentTrait.defaultValue));
                }

                updateTrait(currentTrait.trait, "percent", String.valueOf(seekBar.getProgress()));

                seekBar.setOnSeekBarChangeListener(seekListener);
            }
        });

        // Clear function for date
        clearDate.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                removeTrait(currentTrait.trait);

                final Calendar c = Calendar.getInstance();

                month.setTextColor(Color.BLACK);
                day.setTextColor(Color.BLACK);

                if (currentTrait.defaultValue.trim().length() > 0) {
                    String[] d = currentTrait.defaultValue.split("\\.");

                    //This is used to persist moving between months
                    tempMonth = Integer.parseInt(d[1]) - 1;

                    month.setText(getMonthForInt(Integer.parseInt(d[1]) - 1));
                    day.setText(d[2]);
                } else {
                    //This is used to persist moving between months
                    tempMonth = c.get(Calendar.MONTH);

                    month.setText(getMonthForInt(c.get(Calendar.MONTH)));
                    day.setText(String.format("%02d", c
                            .get(Calendar.DAY_OF_MONTH)));
                }

            }
        });


        // Add day
        addDayBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.MONTH, tempMonth);
                int max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

                Integer i = Integer.parseInt(day.getText().toString());

                if (i + 1 > max) {
                    tempMonth += 1;

                    if (tempMonth > 11)
                        tempMonth = 0;

                    day.setText(String.format("%02d", 1));
                    month.setText(getMonthForInt(tempMonth));
                } else {
                    day.setText(String.format("%02d", i + 1));
                }

                // Change the text color accordingly
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
                Integer i = Integer.parseInt(day.getText().toString());
                if (i - 1 <= 0) {
                    tempMonth -= 1;

                    if (tempMonth <= 0)
                        tempMonth = 11;

                    Calendar calendar = Calendar.getInstance();
                    calendar.set(Calendar.MONTH, tempMonth);
                    int max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

                    day.setText(String.format("%02d", max));
                    month.setText(getMonthForInt(tempMonth));
                } else {
                    day.setText(String.format("%02d", i - 1));
                }

                // Change the text color accordingly
                if (newTraits.containsKey(currentTrait.trait)) {
                    month.setTextColor(Color.BLUE);
                    day.setTextColor(Color.BLUE);
                } else {
                    month.setTextColor(Color.BLACK);
                    day.setTextColor(Color.BLACK);
                }
            }
        });

        //No Day Button
        noDateBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.MONTH, 0);
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.YEAR, 2000);

                day.setText(String.format("%02d", 1));
                month.setText(getMonthForInt(0));

                tempMonth = 0;

                if (ep.getBoolean("UseDay", false)) {
                    updateTrait(currentTrait.trait, "date",String.valueOf(calendar.get(Calendar.DAY_OF_YEAR)));
                } else {
                    updateTrait(currentTrait.trait, "date",
                            calendar.get(Calendar.YEAR) + "."
                                    + (calendar.get(Calendar.MONTH) + 1) + "."
                                    + calendar.get(Calendar.DAY_OF_MONTH));
                }

                // Change the text color accordingly
                month.setTextColor(Color.parseColor(displayColor));
                day.setTextColor(Color.parseColor(displayColor));
            }
        });

        // Saving date data
        saveDayBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.MONTH, tempMonth);
                calendar.set(Calendar.MONTH, tempMonth);
                Integer i = Integer.parseInt(day.getText().toString());
                calendar.set(Calendar.DAY_OF_MONTH, i);

                if (ep.getBoolean("UseDay", false)) {
                    updateTrait(currentTrait.trait, "date",String.valueOf(calendar.get(Calendar.DAY_OF_YEAR)));
                } else {
                    updateTrait(currentTrait.trait, "date",
                            calendar.get(Calendar.YEAR) + "."
                                    + (calendar.get(Calendar.MONTH) + 1) + "."
                                    + calendar.get(Calendar.DAY_OF_MONTH));
                }

                // Change the text color accordingly
                month.setTextColor(Color.parseColor(displayColor));
                day.setTextColor(Color.parseColor(displayColor));
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

        // Clear counter
        clearCounterBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                removeTrait(currentTrait.trait);
                counterTv.setText("0");
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

        // Clear button for qualitative
        clearCat.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                newTraits.remove(currentTrait.trait);
                dt.deleteTrait(cRange.plot_id, currentTrait.trait);
                setCategoricalButtons(buttonArray, null);
            }
        });

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

        // Multicat clear button
        clearMultiCat.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                newTraits.remove(currentTrait.trait);
                dt.deleteTrait(cRange.plot_id, currentTrait.trait);
                eNum.setText("");
            }
        });

        eImg = (ImageView) traitBoolean.findViewById(R.id.eImg);
        Button clearBoolean = (Button) traitBoolean.findViewById(R.id.clearBoolean);

        // Boolean
        eImg.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                String val = newTraits.get(currentTrait.trait).toString();

                if (val.equals("false")) {
                    val = "true";
                    eImg.setImageResource(R.drawable.boolean_true);
                } else {
                    val = "false";
                    eImg.setImageResource(R.drawable.boolean_false);
                }

                updateTrait(currentTrait.trait, "boolean", val);
            }
        });

        // Clear function for boolean
        clearBoolean.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                if (currentTrait.defaultValue.trim().toLowerCase()
                        .equals("true")) {
                    updateTrait(currentTrait.trait, "boolean", "true");
                    eImg.setImageResource(R.drawable.boolean_true);
                } else {
                    updateTrait(currentTrait.trait, "boolean", "false");
                    eImg.setImageResource(R.drawable.boolean_false);
                }
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

                        if (repeatHandler != null) return true;
                        repeatHandler = new Handler();
                        repeatHandler.postDelayed(mActionLeft, 750);

                        delay = 100;
                        count = 1;

                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        rangeLeft.setImageResource(R.drawable.ml_arrow);

                        if (repeatHandler == null) return true;
                        repeatHandler.removeCallbacks(mActionLeft);
                        repeatHandler = null;

                        repeatUpdate();
                        break;
                    case MotionEvent.ACTION_CANCEL:
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
                    } catch (Exception e) {
                        ErrorLog("SoundError.txt", "" + e.getMessage());
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
                                } catch (Exception e) {
                                    ErrorLog("SoundError.txt", "" + e.getMessage());
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
                    } catch (Exception e) {
                        ErrorLog("SoundError.txt", "" + e.getMessage());
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
                                } catch (Exception e) {
                                    ErrorLog("SoundError.txt", "" + e.getMessage());
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
                    imm.hideSoftInputFromWindow(eNum.getWindowToken(), 0);
                } catch (Exception e) {
                    ErrorLog("MainScreenError.txt", "" + e.getMessage());
                }

                try {
                    imm.hideSoftInputFromWindow(tNum.getWindowToken(), 0);
                } catch (Exception e) {
                    ErrorLog("MainScreenError.txt", "" + e.getMessage());
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
                    imm.hideSoftInputFromWindow(eNum.getWindowToken(), 0);
                } catch (Exception e) {
                    ErrorLog("MainScreenError.txt", "" + e.getMessage());
                }

                try {
                    imm.hideSoftInputFromWindow(tNum.getWindowToken(), 0);
                } catch (Exception e) {
                    ErrorLog("MainScreenError.txt", "" + e.getMessage());
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
                Intent b = new Intent(this, ConfigActivity.class);
                b.putExtra("dialog","fields");
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
            repeatHandler.postDelayed(this, delay);
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
            repeatHandler.postDelayed(this, delay);
        }
    };

    private void setCategoricalButtons(Button[] buttonList, Button choice) {
        for (Button aButtonList : buttonList) {
            if (aButtonList == choice) {
                aButtonList.setTextColor(Color.parseColor(displayColor));
                aButtonList.getBackground().setColorFilter(getResources().getColor(R.color.button_pressed), PorterDuff.Mode.SRC);
            } else {
                aButtonList.setTextColor(Color.BLACK);
                aButtonList.getBackground().setColorFilter(getResources().getColor(R.color.button_normal), PorterDuff.Mode.SRC);
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

    // Auto Sizing TextView defaults change with resolution
    // The reported resolution and the layout the device picks mismatch on some devices
    // So what we do is embed the sizing we want into the layout file itself
    // And follow the layout instead of using screenMetrics.density
    private int getPixelSize() {
        if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_SMALL) {
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 16,
                    getResources().getDisplayMetrics());
        } else if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_NORMAL) {
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 25,
                    getResources().getDisplayMetrics());
        } else if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_LARGE) {
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 30,
                    getResources().getDisplayMetrics());
        } else {
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 30,
                    getResources().getDisplayMetrics());
        }
    }

    public static float dipToPixels(Context context, float dipValue) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
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
        createDir(Constants.ERRORPATH);
        createDir(Constants.UPDATEPATH);
        createDir(Constants.ARCHIVEPATH);

        scanSampleFiles();
    }

    private void scanSampleFiles() {
        String[] fileList = {Constants.TRAITPATH + "/trait_sample.trt", Constants.FIELDIMPORTPATH + "/field_sample.csv", Constants.FIELDIMPORTPATH + "/field_sample.xls", Constants.TRAITPATH + "/severity.txt"};

        for (String aFileList : fileList) {
            File temp = new File(aFileList);
            if (temp.exists()) {
                scanFile(temp);
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
                scanFile(blankFile);
            } catch (IOException e) {
                ErrorLog("DirectoryError.txt", "" + e.getMessage());
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
                    } catch (Exception e) {
                        ErrorLog("SoundError.txt", "" + e.getMessage());
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
                    } catch (Exception e) {
                        ErrorLog("SoundError.txt", "" + e.getMessage());
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

        tvRange.setText(cRange.range);
        tvPlot.setText(cRange.plot);

        //TODO change to dp
        int pixelSize = getPixelSize();

        drop1.setTextSize(pixelSize);
        drop2.setTextSize(pixelSize);
        drop3.setTextSize(pixelSize);
    }

    // This is central to the application
    // Calling this function resets all the controls for traits, and picks one
    // to show based on the current trait data
    private void initWidgets(final boolean rangeSuppress) {
        // Reset dropdowns

        int pixelSize = getPixelSize();

        drop1.setTextSize(pixelSize);
        drop2.setTextSize(pixelSize);
        drop3.setTextSize(pixelSize);

        if (prefixTraits != null) {
            savePrefix = false;

            ArrayAdapter<String> prefixArrayAdapter = new ArrayAdapter<String>(
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
                        ErrorLog("DropdownError.txt", "" + e.getMessage());
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
                        ErrorLog("DropdownError.txt", "" + e.getMessage());
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
                        ErrorLog("DropdownError.txt", "" + e.getMessage());
                        e.printStackTrace();

                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {

                }
            });

            savePrefix = true;

            drop1.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    makeToast(drop1.getText().toString());
                    return false;
                }
            });

            drop2.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    makeToast(drop2.getText().toString());
                    return false;
                }
            });

            drop3.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    makeToast(drop3.getText().toString());
                    return false;
                }
            });

        }

        // trait is unique, format is not

        String[] traits = dt.getVisibleTrait();

        int traitPosition;

        try {
            traitPosition = traitType.getSelectedItemPosition();
        } catch (Exception f) {
            ErrorLog("MainScreenTraitError.txt", "" + f.getMessage());
            traitPosition = 0;
        }

        if (traits != null) {
            ArrayAdapter<String> directionArrayAdapter = new ArrayAdapter<String>(
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
                            imm.hideSoftInputFromWindow(eNum.getWindowToken(), 0);
                        } catch (Exception e) {
                            ErrorLog("KeyboardError.txt", "" + e.getMessage());
                        }

                        try {
                            imm.hideSoftInputFromWindow(tNum.getWindowToken(), 0);
                        } catch (Exception e) {
                            ErrorLog("KeyboardError.txt", "" + e.getMessage());
                        }
                    }

                    traitDetails.setText(currentTrait.details);

                    if (!rangeSuppress | !currentTrait.format.equals("numeric")) {
                        if (eNum.getVisibility() == TextView.VISIBLE) {
                            eNum.setVisibility(EditText.GONE);
                            eNum.setEnabled(false);
                        }
                    }

                    // All the logic is here
                    // What it does is hide all other controls except the
                    // current displayed trait
                    // Checks the in memory hashmap
                    // If there is existing data, then populate the control with
                    // the data
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
                        traitRustRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);

                        tNum.setVisibility(EditText.VISIBLE);
                        tNum.setSelection(tNum.getText().length());
                        tNum.setEnabled(true);

                        eNum.setVisibility(EditText.GONE);
                        eNum.removeTextChangedListener(eNumUpdate);
                        eNum.setEnabled(false);

                        pNum.setVisibility(EditText.GONE);

                        if (newTraits.containsKey(currentTrait.trait)) {
                            tNum.removeTextChangedListener(tNumUpdate);

                            tNum.setText(newTraits.get(currentTrait.trait).toString());

                            tNum.setTextColor(Color.parseColor(displayColor));

                            tNum.addTextChangedListener(tNumUpdate);
                            tNum.setSelection(tNum.getText().length());

                        } else {
                            tNum.removeTextChangedListener(tNumUpdate);

                            tNum.setText("");
                            tNum.setTextColor(Color.BLACK);

                            if (currentTrait.defaultValue != null
                                    && currentTrait.defaultValue.length() > 0)
                                tNum.setText(currentTrait.defaultValue);

                            tNum.addTextChangedListener(tNumUpdate);
                            tNum.setSelection(tNum.getText().length());

                        }

                        // This is needed to fix the keyboard bug
                        mHandler.postDelayed(new Runnable() {
                            public void run() {
                                tNum.dispatchTouchEvent(MotionEvent.obtain(
                                        SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(),
                                        MotionEvent.ACTION_DOWN, 0, 0, 0));
                                tNum.dispatchTouchEvent(MotionEvent.obtain(
                                        SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(),
                                        MotionEvent.ACTION_UP, 0, 0, 0));
                                tNum.setSelection(tNum.getText().length());
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
                        traitRustRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        eNum.setVisibility(EditText.VISIBLE);
                        eNum.setEnabled(true);
                        eNum.setCursorVisible(false);

                        pNum.setVisibility(EditText.GONE);

                        if (newTraits.containsKey(currentTrait.trait)) {
                            eNum.removeTextChangedListener(eNumUpdate);
                            eNum.setText(newTraits.get(currentTrait.trait).toString());
                            eNum.setTextColor(Color.parseColor(displayColor));
                            eNum.addTextChangedListener(eNumUpdate);
                        } else {
                            eNum.removeTextChangedListener(eNumUpdate);

                            eNum.setText("");
                            eNum.setTextColor(Color.BLACK);

                            if (currentTrait.defaultValue != null
                                    && currentTrait.defaultValue.length() > 0)
                                eNum.setText(currentTrait.defaultValue);

                            eNum.addTextChangedListener(eNumUpdate);
                        }

                        // This is needed to fix the keyboard bug
                        mHandler.postDelayed(new Runnable() {

                            public void run() {
                                eNum.dispatchTouchEvent(MotionEvent.obtain(
                                        SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(),
                                        MotionEvent.ACTION_DOWN, 0, 0, 0));
                                eNum.dispatchTouchEvent(MotionEvent.obtain(
                                        SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(),
                                        MotionEvent.ACTION_UP, 0, 0, 0));
                            }

                        }, 300);

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
                        traitRustRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        pNum.setVisibility(EditText.VISIBLE);

                        eNum.setVisibility(EditText.GONE);
                        eNum.removeTextChangedListener(eNumUpdate);
                        eNum.setEnabled(false);

                        if (newTraits.containsKey(currentTrait.trait)) {

                            pNum.setTextColor(Color.BLACK);

                            seekBar.setMax(Integer
                                    .parseInt(currentTrait.maximum));

                            seekBar.setOnSeekBarChangeListener(null);

                            if (currentTrait.defaultValue != null) {

                                if (currentTrait.defaultValue.length() > 0) {
                                    if (newTraits.get(currentTrait.trait).toString()
                                            .equals(currentTrait.defaultValue))
                                        pNum.setTextColor(Color.BLACK);
                                    else
                                        pNum.setTextColor(Color.parseColor(displayColor));
                                } else {
                                    if (newTraits.get(currentTrait.trait).toString().equals("0"))
                                        pNum.setTextColor(Color.BLACK);
                                    else
                                        pNum.setTextColor(Color.parseColor(displayColor));
                                }
                            } else {
                                if (newTraits.get(currentTrait.trait).toString().equals("0"))
                                    pNum.setTextColor(Color.BLACK);
                                else
                                    pNum.setTextColor(Color.parseColor(displayColor));
                            }

                            pNum.setText(newTraits.get(currentTrait.trait).toString()
                                    + "%");

                            seekBar.setProgress(Integer.parseInt(newTraits.get(
                                    currentTrait.trait).toString()));

                            seekBar.setOnSeekBarChangeListener(seekListener);

                        } else {
                            seekBar.setOnSeekBarChangeListener(null);

                            pNum.setText("");
                            seekBar.setProgress(0);
                            pNum.setTextColor(Color.BLACK);

                            seekBar.setMax(Integer
                                    .parseInt(currentTrait.maximum));

                            if (currentTrait.defaultValue != null
                                    && currentTrait.defaultValue.length() > 0) {
                                pNum.setText(currentTrait.defaultValue + "%");
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
                        traitRustRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);

                        tNum.setEnabled(false);
                        tNum.setVisibility(View.GONE);
                        pNum.setVisibility(View.GONE);
                        eNum.setVisibility(View.GONE);

                        final Calendar c = Calendar.getInstance();

                        if (newTraits.containsKey(currentTrait.trait)) {
                            if(!newTraits.get(currentTrait.trait).toString().contains(".")) { // no period means it was stored as a day of the year
                                Calendar b = Calendar.getInstance();
                                b.set(Calendar.DAY_OF_YEAR, Integer.parseInt(newTraits.get(currentTrait.trait).toString()));

                                String[] e = {Integer.toString(b.get(Calendar.MONTH)),Integer.toString(b.get(Calendar.DAY_OF_MONTH))};

                                month.setTextColor(Color.parseColor(displayColor));
                                day.setTextColor(Color.parseColor(displayColor));

                                tempMonth = Integer.parseInt(e[0]);

                                month.setText(getMonthForInt(Integer.parseInt(e[0])));
                                day.setText(e[1]);
                            } else {
                                String[] d = newTraits.get(currentTrait.trait).toString()
                                        .split("\\.");

                                month.setTextColor(Color.parseColor(displayColor));
                                day.setTextColor(Color.parseColor(displayColor));

                                //This is used to persist moving between months
                                tempMonth = Integer.parseInt(d[1]) - 1;

                                month.setText(getMonthForInt(Integer.parseInt(d[1])-1));
                                day.setText(d[2]);
                            }

                        } else {
                            month.setTextColor(Color.BLACK);
                            day.setTextColor(Color.BLACK);

                            if (currentTrait.defaultValue.trim().length() > 0) {
                                String[] d = currentTrait.defaultValue.split("\\.");

                                //This is used to persist moving between months
                                tempMonth = Integer.parseInt(d[1]) - 1;

                                month.setText(getMonthForInt(Integer.parseInt(d[1]) - 1));
                                day.setText(d[2]);
                            } else {
                                //This is used to persist moving between months
                                tempMonth = c.get(Calendar.MONTH);

                                month.setText(getMonthForInt(c.get(Calendar.MONTH)));
                                day.setText(String.format("%02d", c.get(Calendar.DAY_OF_MONTH)));
                            }
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
                        traitRustRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);
                        pNum.setVisibility(EditText.GONE);
                        eNum.setVisibility(EditText.GONE);

                        String lastQualitative = "";

                        if (newTraits.containsKey(currentTrait.trait)) {
                            lastQualitative = newTraits.get(currentTrait.trait)
                                    .toString();
                        }

                        String[] cat = currentTrait.categories.split("/");

                        // Hide all unused buttons
                        // For example, there are only 7 items
                        // items 10, 11, 12 will be removed totally
                        for (int i = cat.length; i < 12; i++) {
                            buttonArray[i].setVisibility(Button.GONE);
                        }

                        // Reset button visibility for items in the last row
                        // For example, there are only 7 items
                        // items 8, 9 will be invisible
                        if (12 - cat.length > 0) {
                            for (int i = 11; i >= cat.length; i--) {
                                buttonArray[i].setVisibility(Button.INVISIBLE);
                            }
                        }

                        // Set the textcolor and visibility for the right
                        // buttons
                        for (int i = 0; i < cat.length; i++) {
                            if (cat[i].equals(lastQualitative)) {
                                buttonArray[i].setVisibility(Button.VISIBLE);
                                buttonArray[i].setText(cat[i]);
                                buttonArray[i].setTextColor(Color.parseColor(displayColor));
                                buttonArray[i].getBackground().setColorFilter(getResources().getColor(R.color.button_pressed), PorterDuff.Mode.SRC);
                            } else {
                                buttonArray[i].setVisibility(Button.VISIBLE);
                                buttonArray[i].setText(cat[i]);
                                buttonArray[i].setTextColor(Color.BLACK);
                                buttonArray[i].getBackground().setColorFilter(getResources().getColor(R.color.button_normal), PorterDuff.Mode.SRC);
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
                        traitRustRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        pNum.setVisibility(EditText.GONE);

                        eNum.setVisibility(EditText.GONE);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            if (currentTrait.defaultValue.trim().toLowerCase()
                                    .equals("true")) {
                                updateTrait(currentTrait.trait, "boolean", "true");
                                eImg.setImageResource(R.drawable.boolean_true);
                            } else {
                                updateTrait(currentTrait.trait, "boolean", "false");
                                eImg.setImageResource(R.drawable.boolean_false);
                            }
                        } else {
                            String bval = newTraits.get(currentTrait.trait).toString();

                            if (bval.equals("false")) {
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
                        traitRustRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);

                        tNum.removeTextChangedListener(tNumUpdate);
                        tNum.setVisibility(EditText.VISIBLE);
                        tNum.setEnabled(false);

                        pNum.setVisibility(EditText.GONE);

                        eNum.setVisibility(EditText.GONE);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            doRecord.setText(getString(R.string.record));
                            tNum.setText("");
                            //tNum.setText(R.string.nodata);
                        } else {
                            mRecordingLocation = new File(newTraits.get(currentTrait.trait).toString());
                            doRecord.setText(getString(R.string.play));
                            tNum.setText(getString(R.string.stored));
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
                        traitRustRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);

                        tNum.removeTextChangedListener(tNumUpdate);
                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        pNum.setVisibility(EditText.GONE);

                        eNum.setVisibility(EditText.GONE);

                        // Always set to null as default, then fill in with trait value
                        photoLocation = new ArrayList<>();
                        drawables = new ArrayList<>();

                        File img = new File(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/" + "/photos/");
                        if (img.listFiles() != null) {
                            photoLocation = dt.getPlotPhotos(cRange.plot_id);

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

                        } else
                        {
                            photoAdapter = new GalleryImageAdapter(MainActivity.this, drawables);
                            photo.setAdapter(photoAdapter);
                        }

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            tNum.setText("");

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
                        traitRustRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.GONE);

                        tNum.removeTextChangedListener(tNumUpdate);
                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        pNum.setVisibility(EditText.GONE);
                        eNum.setVisibility(EditText.GONE);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            counterTv.setText("0");
                        } else {
                            counterTv.setText(newTraits.get(currentTrait.trait).toString());
                        }

                    } else if(currentTrait.format.equals("rust rating")) {
                        traitText.setVisibility(View.GONE);
                        traitNumeric.setVisibility(View.GONE);
                        traitPercent.setVisibility(View.GONE);
                        traitDate.setVisibility(View.GONE);
                        traitCategorical.setVisibility(View.GONE);
                        traitBoolean.setVisibility(View.GONE);
                        traitAudio.setVisibility(View.GONE);
                        traitPhoto.setVisibility(View.GONE);
                        traitCounter.setVisibility(View.GONE);
                        traitRustRating.setVisibility(View.VISIBLE);
                        traitMulticat.setVisibility(View.GONE);

                        tNum.removeTextChangedListener(tNumUpdate);
                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        eNum.setVisibility(EditText.VISIBLE);
                        eNum.setEnabled(true);

                        pNum.setVisibility(EditText.GONE);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            eNum.removeTextChangedListener(eNumUpdate);
                            eNum.setText("");
                            eNum.setTextColor(Color.BLACK);

                            if (currentTrait.defaultValue != null
                                    && currentTrait.defaultValue.length() > 0)
                                eNum.setText(currentTrait.defaultValue);

                            eNum.addTextChangedListener(eNumUpdate);
                        } else {
                            eNum.removeTextChangedListener(eNumUpdate);
                            eNum.setText(newTraits.get(currentTrait.trait).toString());
                            eNum.setTextColor(Color.parseColor(displayColor));
                            eNum.addTextChangedListener(eNumUpdate);
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
                        traitRustRating.setVisibility(View.GONE);
                        traitMulticat.setVisibility(View.VISIBLE);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        eNum.setVisibility(EditText.VISIBLE);
                        eNum.setEnabled(true);
                        eNum.setCursorVisible(false);

                        pNum.setVisibility(EditText.GONE);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            eNum.removeTextChangedListener(eNumUpdate);
                            eNum.setText("");
                            eNum.setTextColor(Color.BLACK);
                            eNum.addTextChangedListener(eNumUpdate);
                        } else {
                            eNum.removeTextChangedListener(eNumUpdate);
                            eNum.setText(newTraits.get(currentTrait.trait).toString());
                            eNum.setTextColor(Color.parseColor(displayColor));
                            eNum.addTextChangedListener(eNumUpdate);
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
                                            if (eNum.length() > 0) {
                                                eNum.setText(eNum.getText().toString() + ":" + newButton.getText().toString());
                                            } else {
                                                eNum.setText(newButton.getText().toString());
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
                        traitRustRating.setVisibility(View.GONE);

                        tNum.removeTextChangedListener(tNumUpdate);
                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        eNum.setVisibility(EditText.VISIBLE);
                        eNum.setEnabled(true);

                        pNum.setVisibility(EditText.GONE);
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
            ErrorLog("AudioError.txt", "" + e.getMessage());
            mGeneratedName = "error " + timeStamp.format(c.getTime());
        }

        setRecordingLocation(mGeneratedName);
        mRecorder.setOutputFile(mRecordingLocation.getAbsolutePath());

        try {
            mRecorder.prepare();
        } catch (IllegalStateException e) {
            ErrorLog("AudioError.txt", "" + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            ErrorLog("AudioError.txt", "" + e.getMessage());
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
        doRecord.setText(R.string.stop);
        mPlayer = new MediaPlayer();
        mPlayer = MediaPlayer.create(MainActivity.this, Uri.parse(mRecordingLocation.getAbsolutePath()));

        try {
            mPlayer.prepare();
        }
        catch (Exception e) {
            ErrorLog("AudioError.txt", "" + e.getMessage());
            e.printStackTrace();
        }

        try {
            mPlayer.start();
            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    mListening = false;
                    doRecord.setText(R.string.play);
                    clearRecord.setEnabled(true);
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
            Toast.makeText(MainActivity.this,
                    getString(R.string.nomatches), Toast.LENGTH_LONG)
                    .show();
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
            Toast.makeText(MainActivity.this,
                    getString(R.string.nomatches), Toast.LENGTH_LONG)
                    .show();
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
            Toast.makeText(MainActivity.this,
                    getString(R.string.nomatches), Toast.LENGTH_LONG)
                    .show();
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
            File exportedDb = new File(Constants.BACKUPPATH + "/" + "backup" + ".db");
            File exportedSp = new File(Constants.BACKUPPATH + "/" + "backup" + "_sharedpref.xml");
            scanFile(exportedDb);
            scanFile(exportedSp);
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
        } catch (Exception e) {
            ErrorLog("TutorialError.txt", "" + e.getMessage());
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
        local = ep.getString("language", "en");
        region = ep.getString("region", "");
        Locale locale2 = new Locale(local,region);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2, getBaseContext().getResources()
                .getDisplayMetrics());
        invalidateOptionsMenu();

        if(ConfigActivity.languageChange) {
            ConfigActivity.languageChange = false;
            loadScreen();
        }

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

            rangeName.setText(ep.getString("ImportFirstName", getString(R.string.range)) + ":");
            plotName.setText(ep.getString("ImportSecondName", getString(R.string.plot)) + ":");

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
        dt.insertUserTraits(cRange.plot_id, parent, trait, value, ep.getString("FirstName","") + " " + ep.getString("LastName",""), ep.getString("Location",""),"",""); //TODO add notes and exp_id
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
                traitBoolean, traitAudio, traitPhoto, traitCounter, traitRustRating, traitMulticat};

        if(lock) {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_lock);

            for(LinearLayout traitLayout : traitViews) {
                disableViews(traitLayout);
            }

        } else {
            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_unlock);

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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(MainActivity.this, getString(R.string.maxphotos), Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    e.printStackTrace();

                    ErrorLog("CameraError.txt", "" + e.getMessage());

                    Toast toast = Toast.makeText(MainActivity.this, getString(R.string.hardwaremissing), Toast.LENGTH_LONG);
                    toast.show();

                }
                break;

            // Clear Photo
            case R.id.clearPhoto:
                deletePhotoWarning();
                break;

            case R.id.record:
                newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
                        .clone();

                if (mListening) {
                    mPlayer.stop();
                    doRecord.setText(R.string.play);
                    mListening = false;
                    clearRecord.setEnabled(true);
                    break;
                }

                if (mRecording) {
                    // Stop recording
                    try {
                        mRecorder.stop();
                        File storedAudio = new File(mRecordingLocation.getAbsolutePath());
                        scanFile(storedAudio);
                    } catch (Exception e) {
                        ErrorLog("AudioError.txt", "" + e.getMessage());
                        e.printStackTrace();
                    }

                    releaseRecorder();

                    updateTrait(currentTrait.trait, "audio", mRecordingLocation.getAbsolutePath());

                    tNum.setText(getString(R.string.stored));

                    mRecording = false;
                    doRecord.setText(R.string.play);

                    rangeLeft.setEnabled(true);
                    rangeRight.setEnabled(true);

                    traitLeft.setEnabled(true);
                    traitRight.setEnabled(true);
                    clearRecord.setEnabled(true);
                } else if (newTraits.containsKey(currentTrait.trait)) {
                    beginPlayback();
                    clearRecord.setEnabled(false);

                } else if (!newTraits.containsKey(currentTrait.trait)) {

                    // start recording
                    deleteRecording();
                    clearRecord.setEnabled(false);
                    removeTrait(currentTrait.trait);
                    tNum.setText("");

                    prepareRecorder();

                    rangeLeft.setEnabled(false);
                    rangeRight.setEnabled(false);

                    traitLeft.setEnabled(false);
                    traitRight.setEnabled(false);

                    mRecorder.start();
                    mRecording = true;

                    doRecord.setText(R.string.stop);
                }
                break;

            case R.id.clearRecord:
                deleteRecording();
                removeTrait(currentTrait.trait);
                tNum.setText("");
                mRecording = false;
                doRecord.setText(R.string.record);
                mListening = false;
                mRecording = false;
                break;

            // Below this point are for the custom keypad

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
            case R.id.rustMR:
                v = "MR";
                break;
            case R.id.rustMS:
                v = "MS";
                break;
            case R.id.rustS:
                v = "S";
                break;
            case R.id.rustDelim:
                v = "/";
                break;
            }

        if (traitRustRating.getVisibility() == View.VISIBLE && eNum.getText().length() > 0 && !v.equals("/") && !eNum.getText().toString().substring(eNum.getText().length() - 1).equals("/")) {
            v = ":" + v;
        }

        if (b.getId() == R.id.k16 || b.getId() == R.id.clearRustBtn) {
            //eNum.setText(eNum.getText().toString().substring(0, eNum.getText().toString().length()-1));
            eNum.removeTextChangedListener(eNumUpdate);
            eNum.setText("");
            removeTrait(currentTrait.trait);
            eNum.addTextChangedListener(eNumUpdate);
        } else {
            if (eNum.getText().toString().matches(".*\\d.*") && v.matches(".*\\d.*") && traitRustRating.getVisibility() == View.VISIBLE && !eNum.getText().toString().contains("/")) {
                makeToast(getString(R.string.rustwarning));
            } else {
                eNum.setText(eNum.getText().toString() + v);
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
                    scanFile(f);

                    // Remove individual images
                    dt.deleteTraitByValue(cRange.plot_id, currentTrait.trait, item);

                    // Only do a purge by trait when there are no more images left
                    if (photoLocation.size() == 0)
                        removeTrait(currentTrait.trait);

                    tNum.setText("");

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

        String generatedName = MainActivity.cRange.plot_id + "_" + timeStamp.format(Calendar.getInstance().getTime()) + ".jpg";
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

    private void makeImage(String photoName) {
        File file = new File(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/photos/",
                photoName);

        scanFile(file.getAbsoluteFile());

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
        TextView summaryText = (TextView) layout.findViewById(R.id.text1);

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

        if (cRange == null || cRange.plot_id.length() == 0)
        {
            return;
        }

        Log.w(trait, value);

        if (newTraits.containsKey(parent))
            newTraits.remove(parent);

        newTraits.put(parent, value);

        dt.deleteTraitByValue(cRange.plot_id, parent, value);

        dt.insertUserTraits(cRange.plot_id, parent, trait, value, ep.getString("FirstName","") + " " + ep.getString("LastName",""), ep.getString("Location",""),"",""); //TODO add notes and exp_id

        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss",
                Locale.getDefault());

        Editor ed = ep.edit();
        ed.putString("Dataset_" + ep.getString("FieldFile", "") + "_" + cRange.range + "_" + cRange.plot, timeStamp.format(Calendar.getInstance().getTime()));
        ed.apply();
    }

    private void displayPlotImage(String path) {
        try {
            Log.w("Display path", path);

            File f = new File(path);

            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(f), "image/*");
            startActivity(intent);
        } catch (Exception e) {
            ErrorLog("PhotoError.txt", "" + e.getMessage());
        }
    }

    private Bitmap displayScaledSavedPhoto(String path) {
        if (path == null) {
            Toast toast = Toast.makeText(MainActivity.this, getString(R.string.photomissing), Toast.LENGTH_LONG);

            toast.setGravity(Gravity.TOP, 0, 0);
            toast.show();

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

            // landscape photo
            if (photoW > photoH) {
                // Get the dimensions of the View
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

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);

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
            }
            catch(OutOfMemoryError oom) {
                Log.e(TAG, "-- OOM Error in setting image");
            }

            return correctBmp;

        } catch (Exception e) {
            ErrorLog("CameraError.txt", "" + e.getMessage());
            e.printStackTrace();
            makeToast(getString(R.string.photodecodefail));
            return null;
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

    public void ErrorLog(String sFileName, String sErrMsg)
    {
        try
        {
            SimpleDateFormat lv_parser = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");

            File file = new File(Constants.ERRORPATH, sFileName);

            FileWriter filewriter = new FileWriter(file, true);
            BufferedWriter out = new BufferedWriter(filewriter);

            out.write(lv_parser.format(Calendar.getInstance().getTime()) + " " + sErrMsg + "\n");
            out.flush();
            out.close();

            scanFile(file);
        }
        catch (Exception e)
        {
            Log.e(TAG,"" + e.getMessage());
        }
    }

    private void scanFile(File filePath) {
        MediaScannerConnection.scanFile(this, new String[]{filePath.getAbsolutePath()}, null, null);
    }

    private void checkNewVersion() {
        final PackageManager packageManager = this.getPackageManager();

        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(this.getPackageName(), 0);
            versionName = packageInfo.versionName;
            versionNum = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = null;
        }

        new checkVersion().execute();
    }

    private class checkVersion extends AsyncTask<Void, Void, Void> {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                try {
                    Document doc = Jsoup
                            .connect("http://wheatgenetics.org/appupdates/fieldbook/currentversion.html"
                            )
                            .get();
                    Elements spans = doc.select("div[itemprop=softwareVersion]");
                    currentServerVersion = spans.first().ownText();
                } catch (IOException e) {
                    ErrorLog("VersionCheckError.txt", "" + e.getMessage());
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            int currentServerVersionInt = 0;

            if(!currentServerVersion.equals("")) {
                currentServerVersionInt = Integer.parseInt(currentServerVersion.replace(".",""));
            }

            System.out.println("Field.Book." + currentServerVersion + ".apk" + "\t" + versionName);
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected() && currentServerVersionInt>versionNum && currentServerVersion.length()>0) {
                downloadUpdate();
            }
        }
    }

    private void downloadUpdate() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        builder.setTitle(getString(R.string.update));
        builder.setMessage(getString(R.string.newversion));

        if(isGooglePlayInstalled(this)) {
            builder.setPositiveButton(getString(R.string.googleplay), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + "com.fieldbook.tracker")));
                }
            });
        } else {
            builder.setPositiveButton(getString(R.string.installnow), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    new downloadUpdate().execute();
                }
            });
        }

        builder.setNeutralButton(getString(R.string.later), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                //TODO add shared pref thats used to later check again
                dialog.dismiss();
            }

        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private class downloadUpdate extends AsyncTask<Void, Void, Void> {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                try {
                    URL u = new URL("http://wheatgenetics.org/appupdates/fieldbook/" + "Field.Book."+ currentServerVersion +".apk");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setRequestMethod("GET");
                    c.setDoOutput(true);
                    c.connect();
                    FileOutputStream f = new FileOutputStream(new File(Constants.UPDATEPATH,"/Field.Book."+ currentServerVersion +".apk"));

                    InputStream in = c.getInputStream();

                    byte[] buffer = new byte[1024];
                    int len1;
                    while ( (len1 = in.read(buffer)) > 0 ) {
                        f.write(buffer,0, len1);
                    }
                    f.close();
                } catch (Exception e) {
                    ErrorLog("VersionUpdateError.txt", "" + e.getMessage());
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            scanFile(new File(Constants.UPDATEPATH,"/Field.Book."+ currentServerVersion +".apk"));
            installUpdate();
        }
    }

    private void installUpdate() {
        if (new File(Constants.UPDATEPATH, "/Field.Book."+ currentServerVersion + ".apk").exists()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(Constants.UPDATEPATH + "/Field.Book."+ currentServerVersion +".apk")), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // without this flag android returned a intent error!
            startActivity(intent);
        }

        //TODO delete downloaded apk
    }

    public static boolean isGooglePlayInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        boolean app_installed;
        try
        {
            PackageInfo info = pm.getPackageInfo("com.android.vending", PackageManager.GET_ACTIVITIES);
            String label = (String) info.applicationInfo.loadLabel(pm);
            app_installed = (label != null && !label.equals("Market"));
        }
        catch (PackageManager.NameNotFoundException e)
        {
            app_installed = false;
        }
        return app_installed;
    }
}