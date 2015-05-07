package com.fieldbook.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
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
import com.fieldbook.tracker.Map.*;
import com.fieldbook.tracker.Search.*;
import com.fieldbook.tracker.Trait.*;
import com.fieldbook.tracker.Tutorial.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Main entry point. All main screen logic resides here
 */
public class MainActivity extends Activity implements OnClickListener {

    public static DataHelper dt;

    public static boolean searchReload;

    public static final int MESSAGE_CHECK_BTN_STILL_PRESSED = 1;

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

    private TextView traitDetails;
    private ArrayList<String> photoLocation;

    private TraitObject currentTrait;

    private Handler repeatHandler;

    String mCurrentPhotoPath;

    Button[] rustBtnArray;

    private EditText eNum;
    private EditText pNum;
    private EditText tNum;

    private SeekBar seekBar;

    private Spinner traitType;
    private boolean gridCreated;

    private ImageView eImg;

    private String[] traits;

    public int[] rangeID;
    String inputPlotId = "";

    private int paging;

    private TextWatcher eNumUpdate;
    private TextWatcher tNumUpdate;

    // Size of grid shown on device
    private final int GRIDSIZE = 5;

    // Size of bitmap grids saved to disk
    // Beware large values can cause out of memory exceptions
    private final int EXPORTGRIDSIZE = 18;

    private String[] myList1;
    private String[] myList2;
    private String[] myList3;

    Dialog goToId;

    private EditText range;
    private EditText plot;

    private TextView tvRange;
    private TextView tvPlot;

    private InputMethodManager imm;

    private Handler mHandler = new Handler();
    private GalleryImageAdapter photoAdapter;

    private LinearLayout datePicker;
    private LinearLayout qPicker;
    private LinearLayout kb;

    private TextView month;
    private TextView day;

    private SharedPreferences ep;

    private AutoResizeTextView drop3;
    private AutoResizeTextView drop2;
    private AutoResizeTextView drop1;


    private Spinner drop1prefix;
    private Spinner drop2prefix;
    private Spinner drop3prefix;

    private int drop1Selection;
    private int drop2Selection;
    private int drop3Selection;

    private boolean savePrefix;

    private TextView rangeName;
    private TextView plotName;

    final Button buttonArray[] = new Button[12];
    String curCat = "";

    private Button clearGeneric;
    private Button clearBoolean;
    private Button clearDate;

    private Button clearCounterBtn;
    private TextView counterTv;

    Button rust0,rust5,rust10,rust15,rust20,rust25,rust30,rust35,rust40,rust45,rust50,rust55,rust60,rust65,rust70,rust75,rust80,rust85,rust90,rust95,rust100,rustR,rustMR,rustMS,rustS,rustDelim,rustClear;

    private ImageView traitLeft;
    private ImageView traitRight;

    private ImageView mapUp;
    private ImageView mapDown;
    private ImageView mapLeft;
    private ImageView mapRight;

    private int tempMonth;

    private OnSeekBarChangeListener seekListener;

    private GridLayout map;

    private int mapIndex;

    private ArrayList<Drawable> drawables;

    private boolean analyze;

    private String local;
    private String region;

    private HashMap<Integer, String> analyzeRange;

    private ImageView rangeLeft;
    private ImageView rangeRight;

    private Button mapAnalyze;
    private Button mapParameter;
    private Button mapClose;
    private Button mapExport;
    private Button mapSummary;

    private Button doRecord;
    private Button doPlay;
    private Button clearRecord;

    private Button capture;
    private Button captureClear;

    private String mGeneratedName;

    private MediaRecorder mRecorder;
    private MediaPlayer mPlayer;

    private File mRecordingLocation;

    private boolean mRecording;
    private boolean mListening = false;

    private TextView mapSegment;
    private TextView mapSegmenth;
    private TextView mapPlot;
    private TextView mapRange;
    private TextView mapValue;

    private Spinner mapTrait;

    private Menu systemMenu;

    private MapData[] mapData;

    private ImageView prevCell;

    private float sNumeric;
    private float bNumeric;

    private int exportRowSize;

    private boolean redraw;

    private String mapExportfilename;

    private int mapScrollSegment;
    private int currentMapScrollSegment;
    private int scrollSegmentSize;

    private File mPath = Constants.MPATH;

    private EditText exportFile;

    private final int TRIGGER_SERACH = 1;
    private final long SEARCH_TRIGGER_DELAY_IN_MS = 750;

    private TextView dpi;

    private boolean[] init;

    private Gallery photo;

    private String[] prefixTraits;

    LinearLayout traitBoolean;
    LinearLayout traitAudio;
    LinearLayout traitCategorical;
    LinearLayout traitDate;
    LinearLayout traitNumeric;
    LinearLayout traitPercent;
    LinearLayout traitText;
    LinearLayout traitPhoto;
    LinearLayout traitCounter;
    LinearLayout traitRustRating;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        // Enforce internal language change
        local = ep.getString("language", "en");
        region = ep.getString("region", "");
        Locale locale2 = new Locale(local);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2, getBaseContext().getResources()
                .getDisplayMetrics());

        loadScreen();

        /*
        *
        * Get screen size and density
        *
        *
        if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_LARGE) {
            Toast.makeText(this, "Large screen", Toast.LENGTH_LONG).show();
        }
        else if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_NORMAL) {
            Toast.makeText(this, "Normal sized screen", Toast.LENGTH_LONG).show();
        }
        else if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_SMALL) {
            Toast.makeText(this, "Small sized screen", Toast.LENGTH_LONG).show();
        }
        else {
            Toast.makeText(this, "Screen size is neither large, normal or small", Toast.LENGTH_LONG).show();
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int density = metrics.densityDpi;

        if (density == DisplayMetrics.DENSITY_HIGH) {
            Toast.makeText(this, "DENSITY_HIGH... Density is " + String.valueOf(density), Toast.LENGTH_LONG).show();
        }
        else if (density == DisplayMetrics.DENSITY_MEDIUM) {
            Toast.makeText(this, "DENSITY_MEDIUM... Density is " + String.valueOf(density), Toast.LENGTH_LONG).show();
        }
        else if (density == DisplayMetrics.DENSITY_LOW) {
            Toast.makeText(this, "DENSITY_LOW... Density is " + String.valueOf(density), Toast.LENGTH_LONG).show();
        }
        else {
            Toast.makeText(this, "Density is neither HIGH, MEDIUM OR LOW.  Density is " + String.valueOf(density), Toast.LENGTH_LONG).show();
        }

        */

        // If the user hasn't configured range and traits, open settings screen
        if (!ep.getBoolean("ImportFieldFinished", false) | !ep.getBoolean("CreateTraitFinished", false)) {
            dt.copyFileOrDir(mPath.getAbsolutePath(), "field_import");
            dt.copyFileOrDir(mPath.getAbsolutePath(), "resources");
            dt.copyFileOrDir(mPath.getAbsolutePath(), "trait");

            Intent intent = new Intent();
            intent.setClassName(MainActivity.this,
                    ConfigActivity.class.getName());
            startActivity(intent);
        }

        SharedPreferences.Editor ed = ep.edit();

        if (ep.getInt("UpdateVersion", -1) < getVersion()) {
            ed.putInt("UpdateVersion", getVersion());
            ed.commit();
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, ChangelogActivity.class);
            startActivity(intent);
        }

    }

    private void loadScreen() {
        setContentView(R.layout.main);

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

        capture = (Button) traitPhoto.findViewById(R.id.capture);
        capture.setOnClickListener(this);

        captureClear = (Button) traitPhoto.findViewById(R.id.clearPhoto);
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
                    final int val = Integer.parseInt(eNum.getText().toString());
                    timer.cancel();
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        public void run() {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    if (currentTrait.minimum.length() > 0) {
                                        if (val < Integer.parseInt(currentTrait.minimum)) {
                                            en.clear();
                                            removeTrait(currentTrait.trait);
                                            return;
                                        }
                                    }
                                }
                            });
                        }
                    }, DELAY);

                    if (currentTrait.maximum.length() > 0) {
                        if (val > Integer.parseInt(currentTrait.maximum)) {
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

                }

                if (en.toString().length() > 0) {
                    if (newTraits != null & currentTrait != null)
                        updateTrait(currentTrait.trait, "numeric", en.toString());
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
                tNum.setSelection(tNum.getText().length());

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

        datePicker = (LinearLayout) traitDate.findViewById(R.id.datePick);

        kb = (LinearLayout) traitNumeric.findViewById(R.id.kb);



        month = (TextView) traitDate.findViewById(R.id.mth);
        day = (TextView) traitDate.findViewById(R.id.day);

        rangeName = (TextView) findViewById(R.id.rangeName);
        plotName = (TextView) findViewById(R.id.plotName);

        clearGeneric = (Button) findViewById(R.id.clearBtn4);
        clearBoolean = (Button) findViewById(R.id.clearBtn5);
        clearDate = (Button) traitDate.findViewById(R.id.clearDateBtn);

        Button addDayBtn = (Button) traitDate.findViewById(R.id.addDateBtn);
        Button minusDayBtn = (Button) traitDate.findViewById(R.id.minusDateBtn);
        Button saveDayBtn = (Button) traitDate.findViewById(R.id.enterBtn);

        clearDate = (Button) traitDate.findViewById(R.id.clearDateBtn);

        Button addCounterBtn = (Button) traitCounter.findViewById(R.id.addBtn);
        Button minusCounterBtn = (Button) traitCounter.findViewById(R.id.minusBtn);
        clearCounterBtn = (Button) traitCounter.findViewById(R.id.clearCounterBtn);
        counterTv = (TextView) traitCounter.findViewById(R.id.curCount);

        //Button clearBtn = (Button) findViewById(R.id.clearBtn);
        Button clearBtn2 = (Button) traitCategorical.findViewById(R.id.clearBtn2);

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
        String token1 = "";
        Scanner inFile1 = null;

        try {
            inFile1 = new Scanner(new File(Constants.TRAITPATH + "/severity.txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if(inFile1!=null) {

            while (inFile1.hasNext()) {
                // find next line
                token1 = inFile1.next();
                temps.add(token1);
            }
            inFile1.close();

            String[] tempsArray = temps.toArray(new String[0]);

            for (String s : tempsArray) {
                System.out.println(s);
            }

            //Trim list to 21 since only 21 buttons
            int k = temps.size();
            if ( k > 21 ) {
                temps.subList(21, k).clear();
            }

            for (int i = 0; i < temps.size(); i++) {
                rustBtnArray[i].setVisibility(View.VISIBLE);
                rustBtnArray[i].setText(tempsArray[i]);
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

        // Clear function for text, numeric and percent
        clearGeneric.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                if (currentTrait.format.equals("text")) {
                    tNum.removeTextChangedListener(tNumUpdate);
                    tNum.setText("");
                    removeTrait(currentTrait.trait);
                    tNum.addTextChangedListener(tNumUpdate);
                } else if (currentTrait.format.equals("numeric")) {
                    eNum.removeTextChangedListener(eNumUpdate);
                    eNum.setText("");

                    removeTrait(currentTrait.trait);

                    if (currentTrait.defaultValue != null
                            && currentTrait.defaultValue.length() > 0)
                        eNum.setText(currentTrait.defaultValue);

                    eNum.addTextChangedListener(eNumUpdate);
                } else if (currentTrait.format.equals("percent")) {
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

        // Clear function for boolean
        clearBoolean.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                if (currentTrait.defaultValue.trim().toLowerCase()
                        .equals("true")) {
                    updateTrait(currentTrait.trait, "boolean", "true");
                    eImg.setImageResource(R.drawable.keep);
                } else {
                    updateTrait(currentTrait.trait, "boolean", "false");
                    eImg.setImageResource(R.drawable.trash);
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

        // Saving date data
        saveDayBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.MONTH, tempMonth);
                calendar.set(Calendar.MONTH, tempMonth);
                Integer i = Integer.parseInt(day.getText().toString());
                calendar.set(Calendar.DAY_OF_MONTH, i);

                updateTrait(currentTrait.trait,
                        "date",
                        calendar.get(Calendar.YEAR) + "."
                                + (calendar.get(Calendar.MONTH) + 1) + "."
                                + calendar.get(Calendar.DAY_OF_MONTH));

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

        // Datapicker is an area onscreen representing qualitative controls
        qPicker = (LinearLayout) traitCategorical.findViewById(R.id.qPick);

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

        curCat = "";

        // Clear button for qualitative
        clearBtn2.setOnClickListener(new OnClickListener() {
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

        eImg = (ImageView) traitBoolean.findViewById(R.id.eImg);

        // Boolean
        eImg.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                String val = newTraits.get(currentTrait.trait).toString();

                if (val.equals("false")) {
                    val = "true";
                    eImg.setImageResource(R.drawable.keep);
                } else {
                    val = "false";
                    eImg.setImageResource(R.drawable.trash);
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

                        if (repeatHandler != null) return true;
                        repeatHandler = new Handler();
                        repeatHandler.postDelayed(mActionLeft, 750);

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
                if(ep.getBoolean("DisableEntryNav",false)==true && !newTraits.containsKey(currentTrait.trait)) {

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
                        ed.commit();

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

                                        ;
                                    });
                                } catch (Exception e) {
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

                if(ep.getBoolean("DisableEntryNav",false)==true && !newTraits.containsKey(currentTrait.trait)) {

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
                        ed.commit();

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

                                        ;
                                    });
                                } catch (Exception e) {
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

                }

                try {
                    imm.hideSoftInputFromWindow(tNum.getWindowToken(), 0);
                } catch (Exception e) {

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

                }

                try {
                    imm.hideSoftInputFromWindow(tNum.getWindowToken(), 0);
                } catch (Exception e) {

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

    Runnable mActionRight = new Runnable() {
        @Override public void run() {
            repeatRight();
            repeatHandler.postDelayed(this, 100);
        }
    };

    Runnable mActionLeft = new Runnable() {
        @Override public void run() {
            repeatLeft();
            repeatHandler.postDelayed(this, 100);
        }
    };

    private void setCategoricalButtons(Button[] buttonList, Button choice) {
        for (int i = 0; i < buttonList.length; i++) {
            if (buttonList[i] == choice) {
                buttonList[i].setTextColor(Color.parseColor(displayColor));
                buttonList[i].setBackgroundResource(R.drawable.btn_default);
            } else {
                buttonList[i].setTextColor(Color.BLACK);
                buttonList[i].setBackgroundResource(android.R.drawable.btn_default);
            }
        }
    }

    private Boolean checkButton(Button button) {
        if (newTraits.containsKey(currentTrait.trait)) {
            curCat = newTraits.get(currentTrait.trait)
                    .toString();
        }
        if (button.getText().toString().equals(curCat)) {
            newTraits.remove(currentTrait.trait);
            dt.deleteTrait(cRange.plot_id, currentTrait.trait);
            setCategoricalButtons(buttonArray, null);
            curCat = "";
            return true;
        }
        return false;
    }

    // Auto Sizing TextView defaults change with resolution
    // The reported resolution and the layout the device picks mismatch on some devices
    // So what we do is embed the sizing we want into the layout file itself
    // And follow the layout instead of using screenMetrics.density
    private int getPixelSize() {
        if (dpi.getText().toString().equals("high"))
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 30,
                    getResources().getDisplayMetrics());
        else
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 16,
                    getResources().getDisplayMetrics());
    }

    // Create all necessary directories and subdirectories	
    private void createDirs() {
        createDir(mPath.getAbsolutePath());
        createDir(Constants.RESOURCEPATH);
        createDir(Constants.PLOTDATAPATH);
        createDir(Constants.TRAITPATH);
        createDir(Constants.FIELDIMPORTPATH);
        createDir(Constants.FIELDEXPORTPATH);
        createDir(Constants.BACKUPPATH);
        createDir(Constants.ERRORPATH);

        scanSampleFiles();
    }

    private void scanSampleFiles() {
        String[] fileList = {Constants.TRAITPATH + "/trait_sample.trt", Constants.FIELDIMPORTPATH + "/field_sample.csv", Constants.FIELDIMPORTPATH + "/field_sample.xls", Constants.TRAITPATH + "/severity.txt"};

        for (int i=0; i<fileList.length; i++) {
            File temp = new File(fileList[i]);
            if(temp.exists()){
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
            ed.commit();

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
            ed.commit();

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

    // For space that appears on screen, but isn't part of the grid we're drawing
    // Think of it as the background
    private ImageView createBoundarySpace() {
        ImageView image;
        image = new ImageView(MainActivity.this);
        image.setImageResource(R.drawable.emptysquare);

        return image;
    }

    // Create a square on grid with specific color
    private ImageView createSquare(final int id, final String mapColor) {
        final ImageView image;
        image = new ImageView(MainActivity.this);

        image.setImageResource(R.drawable.nosquare);

        image.setBackgroundColor(Color.parseColor(mapColor));
        image.setTag(id + "-" + mapColor);

        image.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {

                try {
                    // On click display the plot's values
                    String[] d = v.getTag().toString().split("-");

                    paging = Integer.parseInt(d[0]);

                    cRange = dt.getRange(paging);

                    mapPlot.setText(cRange.plot);
                    mapRange.setText(cRange.range);

                    displayRange(cRange);

                    newTraits = (HashMap) dt.getUserDetail(cRange.plot_id).clone();

                    if (newTraits.containsKey(mapTrait.getSelectedItem().toString()))
                        mapValue.setText(newTraits.get(mapTrait.getSelectedItem().toString()).toString());

                    initWidgets(false);

                    // Now that the user has selected a new plot, change the look
                    // of the previous plot back to original
                    if (prevCell != null) {
                        String[] d2 = prevCell.getTag().toString().split("-");
                        prevCell.setBackgroundColor(Color.parseColor(d2[1]));
                        prevCell.setImageResource(R.drawable.nosquare);
                    }

                    prevCell = (ImageView) v;
                    prevCell.setTag(v.getTag().toString());

                    //((ImageView) v).setBackgroundColor(Color.GREEN);
                    ((ImageView) v).setImageResource(R.drawable.selectedsquare);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        return image;
    }

    // Initialize the toggle states of the map
    // Only used for serpentine
    private void initMap(boolean start, int length) {
        init = new boolean[length];

        for (int i = 0; i < length; i++) {
            init[i] = start;

            start = !start;
        }
    }

    // Create data for serpentine map
    private void processSerpentineMap(HashMap<Integer, String> compare, int mapType) {
        switch (mapType) {
            //top left
            case 0:
                mapData = dt.arrangePlotByRow(true);
                initMap(true, mapData.length);
                break;

            // btm left
            case 1:
                mapData = dt.arrangePlotByRow(false);
                initMap(false, mapData.length);
                break;

            //top right
            case 2:
                mapData = dt.arrangePlotByRow(true);
                initMap(false, mapData.length);
                break;

            // btm right
            case 3:
                mapData = dt.arrangePlotByRow(false);
                initMap(true, mapData.length);
                break;

            default:
                mapData = dt.arrangePlotByRow(true);
                initMap(true, mapData.length);
                break;
        }

    }

    // Create data for Zig Zag map
    private void processZigZagMap(HashMap<Integer, String> compare, int mapType) {
        switch (mapType) {
            //top left
            case 0:
                mapData = dt.arrangePlotByRow(true);
                break;

            // btm left
            case 1:
                mapData = dt.arrangePlotByRow(false);
                break;

            //top right
            case 2:
                mapData = dt.arrangePlotByRow(true);
                break;

            // btm right
            case 3:
                mapData = dt.arrangePlotByRow(false);
                break;

            default:
                mapData = dt.arrangePlotByRow(true);
                break;
        }

    }

    // Display the map accordingly
    private void loadSerpentineMap(HashMap<Integer, String> compare, int mapType, String mapColor) {
        switch (mapType) {
            // top left
            case 0:
                serpentineGridLeft(compare, mapIndex, mapColor, false);
                break;

            // btm left
            case 1:
                serpentineGridRight(compare, mapIndex, mapColor, false);
                break;

            // top right
            case 2:
                serpentineGridRight(compare, mapIndex, mapColor, false);
                break;

            // btm right
            case 3:
                serpentineGridLeft(compare, mapIndex, mapColor, false);
                break;

            default:
                serpentineGridLeft(compare, mapIndex, mapColor, false);
                break;
        }

    }

    // Export data accordingly
    private void loadSerpentineMapExport(HashMap<Integer, String> compare, int mapType, int mapColor, Canvas c) {

        switch (mapType) {
            // top left
            case 0:
                serpentineGridLeftExport(compare, 0, mapColor, c);
                break;

            // btm left
            case 1:
                serpentineGridRightExport(compare, 0, mapColor, c);
                break;

            // top right
            case 2:
                serpentineGridRightExport(compare, 0, mapColor, c);
                break;

            // btm right
            case 3:
                serpentineGridLeftExport(compare, 0, mapColor, c);
                break;

            default:
                serpentineGridLeftExport(compare, 0, mapColor, c);
                break;
        }

    }

    // Display the map accordingly	
    private void loadZigZagMap(HashMap<Integer, String> compare, int mapType, String mapColor) {
        switch (mapType) {
            //top left
            case 0:
                zigZagGridLeft(compare, mapIndex, mapColor, false);
                break;

            // btm left
            case 1:
                zigZagGridLeft(compare, mapIndex, mapColor, false);
                break;

            //top right
            case 2:
                zigZagGridRight(compare, mapIndex, mapColor, false);
                break;

            // btm right
            case 3:
                zigZagGridRight(compare, mapIndex, mapColor, false);
                break;

            default:
                zigZagGridLeft(compare, mapIndex, mapColor, false);
                break;
        }

    }

    // Export data accordingly
    private void loadZigZagMapExport(HashMap<Integer, String> compare, int mapType, int mapColor, Canvas c) {
        switch (mapType) {
            //top left
            case 0:
                zigZagGridLeftExport(compare, 0, mapColor, c);
                break;

            // btm left
            case 1:
                zigZagGridLeftExport(compare, 0, mapColor, c);
                break;

            //top right
            case 2:
                zigZagGridRightExport(compare, 0, mapColor, c);
                break;

            // btm right 
            case 3:
                zigZagGridRightExport(compare, 0, mapColor, c);
                break;

            default:
                zigZagGridLeftExport(compare, 0, mapColor, c);
                break;
        }

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
                int spinnerPosition = prefixArrayAdapter.getPosition(ep.getString("DROP1", ep.getString("ImportUniqueName","")));
                drop1prefix.setSelection(spinnerPosition);
                spinnerPosition = 0;
            }

            drop2prefix.setAdapter(prefixArrayAdapter);
            drop2prefix.setSelection(drop2Selection);

            if (!drop2prefix.equals(null)) {
                int spinnerPosition = prefixArrayAdapter.getPosition(ep.getString("DROP2", ep.getString("ImportFirstName","")));
                drop2prefix.setSelection(spinnerPosition);
                spinnerPosition = 0;
            }

            drop3prefix.setAdapter(prefixArrayAdapter);
            drop3prefix.setSelection(drop3Selection);

            if (!drop3prefix.equals(null)) {
                int spinnerPosition = prefixArrayAdapter.getPosition(ep.getString("DROP3", ep.getString("ImportSecondName","")));
                drop3prefix.setSelection(spinnerPosition);
                spinnerPosition = 0;
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
                            e.commit();
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
                            e.commit();
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
                            e.commit();
                    } catch (Exception e) {
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

        traits = dt.getVisibleTrait();

        int traitPosition = 0;

        try {
            traitPosition = traitType.getSelectedItemPosition();
        } catch (Exception f) {
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

                        }

                        try {
                            imm.hideSoftInputFromWindow(tNum.getWindowToken(), 0);
                        } catch (Exception e) {

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

                        qPicker.setVisibility(LinearLayout.GONE);

                        tNum.setVisibility(EditText.VISIBLE);
                        tNum.setSelection(tNum.getText().length());
                        tNum.setEnabled(true);

                        kb.setVisibility(View.GONE);

                        clearGeneric.setVisibility(View.GONE);
                        clearBoolean.setVisibility(View.GONE);

                        eNum.setVisibility(EditText.GONE);
                        eNum.removeTextChangedListener(eNumUpdate);
                        eNum.setEnabled(false);

                        pNum.setVisibility(EditText.GONE);

                        seekBar.setVisibility(EditText.GONE);
                        datePicker.setVisibility(LinearLayout.GONE);
                        eImg.setVisibility(EditText.GONE);

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

                        qPicker.setVisibility(LinearLayout.GONE);

                        kb.setVisibility(View.VISIBLE);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        eNum.setVisibility(EditText.VISIBLE);
                        eNum.setEnabled(true);

                        clearGeneric.setVisibility(View.GONE);
                        clearBoolean.setVisibility(View.GONE);

                        pNum.setVisibility(EditText.GONE);

                        seekBar.setVisibility(EditText.GONE);
                        datePicker.setVisibility(LinearLayout.GONE);
                        eImg.setVisibility(EditText.GONE);

                        traitAudio.setVisibility(EditText.GONE);
                        traitPhoto.setVisibility(View.GONE);

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

                        qPicker.setVisibility(LinearLayout.GONE);

                        kb.setVisibility(View.GONE);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        pNum.setVisibility(EditText.VISIBLE);

                        clearGeneric.setVisibility(View.VISIBLE);
                        clearBoolean.setVisibility(View.GONE);

                        eNum.setVisibility(EditText.GONE);
                        eNum.removeTextChangedListener(eNumUpdate);
                        eNum.setEnabled(false);

                        seekBar.setVisibility(EditText.VISIBLE);
                        datePicker.setVisibility(LinearLayout.GONE);
                        eImg.setVisibility(EditText.GONE);

                        traitAudio.setVisibility(EditText.GONE);
                        traitPhoto.setVisibility(View.GONE);

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

                        qPicker.setVisibility(LinearLayout.GONE);
                        traitPhoto.setVisibility(View.GONE);

                        kb.setVisibility(View.GONE);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        clearGeneric.setVisibility(View.GONE);
                        clearBoolean.setVisibility(View.GONE);

                        pNum.setVisibility(EditText.GONE);
                        eNum.setVisibility(EditText.GONE);
                        seekBar.setVisibility(EditText.GONE);
                        datePicker.setVisibility(LinearLayout.VISIBLE);
                        eImg.setVisibility(EditText.GONE);

                        traitAudio.setVisibility(EditText.GONE);

                        final Calendar c = Calendar.getInstance();

                        if (newTraits.containsKey(currentTrait.trait)) {
                            String[] d = newTraits.get(currentTrait.trait).toString()
                                    .split("\\.");

                            month.setTextColor(Color.parseColor(displayColor));
                            day.setTextColor(Color.parseColor(displayColor));

                            //This is used to persist moving between months
                            tempMonth = Integer.parseInt(d[1]) - 1;

                            month.setText(getMonthForInt(Integer.parseInt(d[1]) - 1));
                            day.setText(d[2]);
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

                        qPicker.setVisibility(LinearLayout.VISIBLE);
                        traitPhoto.setVisibility(View.GONE);

                        kb.setVisibility(View.GONE);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        clearGeneric.setVisibility(View.GONE);
                        clearBoolean.setVisibility(View.GONE);

                        pNum.setVisibility(EditText.GONE);
                        eNum.setVisibility(EditText.GONE);
                        seekBar.setVisibility(EditText.GONE);
                        datePicker.setVisibility(LinearLayout.GONE);
                        eImg.setVisibility(EditText.GONE);

                        traitAudio.setVisibility(EditText.GONE);

                        String lastQualitative = "";

                        if (newTraits.containsKey(currentTrait.trait)) {
                            lastQualitative = newTraits.get(currentTrait.trait)
                                    .toString();
                        }

                        String[] cat = currentTrait.categories.split("/");

                        int i = 0;

                        // Hide all unused buttons
                        // For example, there are only 7 items
                        // items 10, 11, 12 will be removed totally
                        for (i = cat.length; i < 12; i++) {
                            switch (i) {
                                case 0:
                                    buttonArray[0].setVisibility(Button.GONE);
                                    break;
                                case 1:
                                    buttonArray[1].setVisibility(Button.GONE);
                                    break;
                                case 2:
                                    buttonArray[2].setVisibility(Button.GONE);
                                    break;
                                case 3:
                                    buttonArray[3].setVisibility(Button.GONE);
                                    break;
                                case 4:
                                    buttonArray[4].setVisibility(Button.GONE);
                                    break;
                                case 5:
                                    buttonArray[5].setVisibility(Button.GONE);
                                    break;
                                case 6:
                                    buttonArray[6].setVisibility(Button.GONE);
                                    break;
                                case 7:
                                    buttonArray[7].setVisibility(Button.GONE);
                                    break;
                                case 8:
                                    buttonArray[8].setVisibility(Button.GONE);
                                    break;
                                case 9:
                                    buttonArray[9].setVisibility(Button.GONE);
                                    break;
                                case 10:
                                    buttonArray[10].setVisibility(Button.GONE);
                                    break;
                                case 11:
                                    buttonArray[11].setVisibility(Button.GONE);
                                    break;

                            }
                        }

                        // Reset button visibility for items in the last row
                        // For example, there are only 7 items
                        // items 8, 9 will be invisible
                        if (12 - cat.length > 0) {
                            for (i = 11; i >= cat.length; i--) {
                                switch (i) {
                                    case 0:
                                        buttonArray[0].setVisibility(Button.INVISIBLE);
                                        break;
                                    case 1:
                                        buttonArray[1].setVisibility(Button.INVISIBLE);
                                        break;
                                    case 2:
                                        buttonArray[2].setVisibility(Button.INVISIBLE);
                                        break;
                                    case 3:
                                        buttonArray[3].setVisibility(Button.INVISIBLE);
                                        break;
                                    case 4:
                                        buttonArray[4].setVisibility(Button.INVISIBLE);
                                        break;
                                    case 5:
                                        buttonArray[5].setVisibility(Button.INVISIBLE);
                                        break;
                                    case 6:
                                        buttonArray[6].setVisibility(Button.INVISIBLE);
                                        break;
                                    case 7:
                                        buttonArray[7].setVisibility(Button.INVISIBLE);
                                        break;
                                    case 8:
                                        buttonArray[8].setVisibility(Button.INVISIBLE);
                                        break;
                                    case 9:
                                        buttonArray[9].setVisibility(Button.INVISIBLE);
                                        break;
                                    case 10:
                                        buttonArray[10].setVisibility(Button.INVISIBLE);
                                        break;
                                    case 11:
                                        buttonArray[11].setVisibility(Button.INVISIBLE);
                                        break;

                                }
                            }
                        }

                        // Set the textcolor and visibility for the right
                        // buttons
                        for (i = 0; i < cat.length; i++) {
                            if (cat[i].equals(lastQualitative)) {
                                switch (i) {
                                    case 0:
                                        buttonArray[0].setVisibility(Button.VISIBLE);
                                        buttonArray[0].setText(cat[i]);
                                        buttonArray[0].setTextColor(Color.parseColor(displayColor));
                                        buttonArray[0].setBackgroundResource(R.drawable.btn_default);
                                        break;
                                    case 1:
                                        buttonArray[1].setVisibility(Button.VISIBLE);
                                        buttonArray[1].setText(cat[i]);
                                        buttonArray[1].setTextColor(Color.parseColor(displayColor));
                                        buttonArray[1].setBackgroundResource(R.drawable.btn_default);
                                        break;
                                    case 2:
                                        buttonArray[2].setVisibility(Button.VISIBLE);
                                        buttonArray[2].setText(cat[i]);
                                        buttonArray[2].setTextColor(Color.parseColor(displayColor));
                                        buttonArray[2].setBackgroundResource(R.drawable.btn_default);
                                        break;
                                    case 3:
                                        buttonArray[3].setVisibility(Button.VISIBLE);
                                        buttonArray[3].setText(cat[i]);
                                        buttonArray[3].setTextColor(Color.parseColor(displayColor));
                                        buttonArray[3].setBackgroundResource(R.drawable.btn_default);
                                        break;
                                    case 4:
                                        buttonArray[4].setVisibility(Button.VISIBLE);
                                        buttonArray[4].setText(cat[i]);
                                        buttonArray[4].setTextColor(Color.parseColor(displayColor));
                                        buttonArray[4].setBackgroundResource(R.drawable.btn_default);
                                        break;
                                    case 5:
                                        buttonArray[5].setVisibility(Button.VISIBLE);
                                        buttonArray[5].setText(cat[i]);
                                        buttonArray[5].setTextColor(Color.parseColor(displayColor));
                                        buttonArray[5].setBackgroundResource(R.drawable.btn_default);
                                        break;
                                    case 6:
                                        buttonArray[6].setVisibility(Button.VISIBLE);
                                        buttonArray[6].setText(cat[i]);
                                        buttonArray[6].setTextColor(Color.parseColor(displayColor));
                                        buttonArray[6].setBackgroundResource(R.drawable.btn_default);
                                        break;
                                    case 7:
                                        buttonArray[7].setVisibility(Button.VISIBLE);
                                        buttonArray[7].setText(cat[i]);
                                        buttonArray[7].setTextColor(Color.parseColor(displayColor));
                                        buttonArray[7].setBackgroundResource(R.drawable.btn_default);
                                        break;
                                    case 8:
                                        buttonArray[8].setVisibility(Button.VISIBLE);
                                        buttonArray[8].setText(cat[i]);
                                        buttonArray[8].setTextColor(Color.parseColor(displayColor));
                                        buttonArray[8].setBackgroundResource(R.drawable.btn_default);
                                        break;
                                    case 9:
                                        buttonArray[9].setVisibility(Button.VISIBLE);
                                        buttonArray[9].setText(cat[i]);
                                        buttonArray[9].setTextColor(Color.parseColor(displayColor));
                                        buttonArray[9].setBackgroundResource(R.drawable.btn_default);
                                        break;
                                    case 10:
                                        buttonArray[10].setVisibility(Button.VISIBLE);
                                        buttonArray[10].setText(cat[i]);
                                        buttonArray[10].setTextColor(Color.parseColor(displayColor));
                                        buttonArray[10].setBackgroundResource(R.drawable.btn_default);
                                        break;
                                    case 11:
                                        buttonArray[11].setVisibility(Button.VISIBLE);
                                        buttonArray[11].setText(cat[i]);
                                        buttonArray[11].setTextColor(Color.parseColor(displayColor));
                                        buttonArray[11].setBackgroundResource(R.drawable.btn_default);
                                        break;

                                }
                            } else {
                                switch (i) {
                                    case 0:
                                        buttonArray[0].setVisibility(Button.VISIBLE);
                                        buttonArray[0].setText(cat[i]);
                                        buttonArray[0].setTextColor(Color.BLACK);
                                        buttonArray[0].setBackgroundResource(android.R.drawable.btn_default);
                                        break;
                                    case 1:
                                        buttonArray[1].setVisibility(Button.VISIBLE);
                                        buttonArray[1].setText(cat[i]);
                                        buttonArray[1].setTextColor(Color.BLACK);
                                        buttonArray[1].setBackgroundResource(android.R.drawable.btn_default);
                                        break;
                                    case 2:
                                        buttonArray[2].setVisibility(Button.VISIBLE);
                                        buttonArray[2].setText(cat[i]);
                                        buttonArray[2].setTextColor(Color.BLACK);
                                        buttonArray[2].setBackgroundResource(android.R.drawable.btn_default);
                                        break;
                                    case 3:
                                        buttonArray[3].setVisibility(Button.VISIBLE);
                                        buttonArray[3].setText(cat[i]);
                                        buttonArray[3].setTextColor(Color.BLACK);
                                        buttonArray[3].setBackgroundResource(android.R.drawable.btn_default);
                                        break;
                                    case 4:
                                        buttonArray[4].setVisibility(Button.VISIBLE);
                                        buttonArray[4].setText(cat[i]);
                                        buttonArray[4].setTextColor(Color.BLACK);
                                        buttonArray[4].setBackgroundResource(android.R.drawable.btn_default);
                                        break;
                                    case 5:
                                        buttonArray[5].setVisibility(Button.VISIBLE);
                                        buttonArray[5].setText(cat[i]);
                                        buttonArray[5].setTextColor(Color.BLACK);
                                        buttonArray[5].setBackgroundResource(android.R.drawable.btn_default);
                                        break;
                                    case 6:
                                        buttonArray[6].setVisibility(Button.VISIBLE);
                                        buttonArray[6].setText(cat[i]);
                                        buttonArray[6].setTextColor(Color.BLACK);
                                        buttonArray[6].setBackgroundResource(android.R.drawable.btn_default);
                                        break;
                                    case 7:
                                        buttonArray[7].setVisibility(Button.VISIBLE);
                                        buttonArray[7].setText(cat[i]);
                                        buttonArray[7].setTextColor(Color.BLACK);
                                        buttonArray[7].setBackgroundResource(android.R.drawable.btn_default);
                                        break;
                                    case 8:
                                        buttonArray[8].setVisibility(Button.VISIBLE);
                                        buttonArray[8].setText(cat[i]);
                                        buttonArray[8].setTextColor(Color.BLACK);
                                        buttonArray[8].setBackgroundResource(android.R.drawable.btn_default);
                                        break;
                                    case 9:
                                        buttonArray[9].setVisibility(Button.VISIBLE);
                                        buttonArray[9].setText(cat[i]);
                                        buttonArray[9].setTextColor(Color.BLACK);
                                        buttonArray[9].setBackgroundResource(android.R.drawable.btn_default);
                                        break;
                                    case 10:
                                        buttonArray[10].setVisibility(Button.VISIBLE);
                                        buttonArray[10].setText(cat[i]);
                                        buttonArray[10].setTextColor(Color.BLACK);
                                        buttonArray[10].setBackgroundResource(android.R.drawable.btn_default);
                                        break;
                                    case 11:
                                        buttonArray[11].setVisibility(Button.VISIBLE);
                                        buttonArray[11].setText(cat[i]);
                                        buttonArray[11].setTextColor(Color.BLACK);
                                        buttonArray[11].setBackgroundResource(android.R.drawable.btn_default);
                                        break;
                                }
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

                        qPicker.setVisibility(LinearLayout.GONE);

                        clearBoolean.setVisibility(View.VISIBLE);

                        kb.setVisibility(View.GONE);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);
                        eImg.setVisibility(EditText.VISIBLE);
                        clearGeneric.setVisibility(View.GONE);

                        pNum.setVisibility(EditText.GONE);
                        eNum.setVisibility(EditText.GONE);
                        seekBar.setVisibility(EditText.GONE);
                        datePicker.setVisibility(LinearLayout.GONE);

                        if (!newTraits.containsKey(currentTrait.trait)) {
                            if (currentTrait.defaultValue.trim().toLowerCase()
                                    .equals("true")) {
                                updateTrait(currentTrait.trait, "boolean", "true");
                                eImg.setImageResource(R.drawable.keep);
                            } else {
                                updateTrait(currentTrait.trait, "boolean", "false");
                                eImg.setImageResource(R.drawable.trash);
                            }
                        } else {
                            String bval = newTraits.get(currentTrait.trait).toString();

                            if (bval.equals("false")) {
                                eImg.setImageResource(R.drawable.trash);
                            } else {
                                eImg.setImageResource(R.drawable.keep);
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

                        qPicker.setVisibility(LinearLayout.GONE);

                        kb.setVisibility(View.GONE);

                        tNum.removeTextChangedListener(tNumUpdate);

                        tNum.setVisibility(EditText.VISIBLE);
                        tNum.setEnabled(false);

                        clearGeneric.setVisibility(View.GONE);

                        pNum.setVisibility(EditText.GONE);
                        eNum.setVisibility(EditText.GONE);
                        seekBar.setVisibility(EditText.GONE);
                        datePicker.setVisibility(LinearLayout.GONE);

                        rangeLeft.setEnabled(true);
                        rangeRight.setEnabled(true);

                        traitLeft.setEnabled(true);
                        traitRight.setEnabled(true);

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

                        qPicker.setVisibility(LinearLayout.GONE);

                        kb.setVisibility(View.GONE);

                        tNum.removeTextChangedListener(tNumUpdate);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        clearGeneric.setVisibility(View.GONE);
                        clearBoolean.setVisibility(View.GONE);

                        pNum.setVisibility(EditText.GONE);
                        eNum.setVisibility(EditText.GONE);
                        seekBar.setVisibility(EditText.GONE);
                        datePicker.setVisibility(LinearLayout.GONE);
                        eImg.setVisibility(EditText.GONE);

                        rangeLeft.setEnabled(true);
                        rangeRight.setEnabled(true);

                        traitLeft.setEnabled(true);
                        traitRight.setEnabled(true);

                        // Always set to null as default, then fill in with trait value
                        photoLocation = new ArrayList<String>();
                        drawables = new ArrayList<Drawable>();

                        File img = new File(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/" + "/photos/");

                        if (img.listFiles() != null) {
                            photoLocation = dt.getPlotPhotos(cRange.plot_id);

                            for (int i = 0; i < photoLocation.size(); i++) {
                                drawables.add(new BitmapDrawable(displayScaledSavedPhoto(photoLocation.get(i))));
                            }

                            photoAdapter = new GalleryImageAdapter(MainActivity.this, drawables);

                            photo.setAdapter(photoAdapter);
                            photo.setSelection(photo.getCount()-1);
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

                            if (!img.exists())
                                img.mkdirs();
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

                        tNum.removeTextChangedListener(tNumUpdate);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        clearGeneric.setVisibility(View.GONE);
                        clearBoolean.setVisibility(View.GONE);

                        pNum.setVisibility(EditText.GONE);
                        eNum.setVisibility(EditText.GONE);
                        seekBar.setVisibility(EditText.GONE);
                        datePicker.setVisibility(LinearLayout.GONE);
                        eImg.setVisibility(EditText.GONE);

                        rangeLeft.setEnabled(true);
                        rangeRight.setEnabled(true);

                        traitLeft.setEnabled(true);
                        traitRight.setEnabled(true);

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

                        tNum.removeTextChangedListener(tNumUpdate);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        eNum.setVisibility(EditText.VISIBLE);
                        eNum.setEnabled(true);

                        clearGeneric.setVisibility(View.GONE);
                        clearBoolean.setVisibility(View.GONE);

                        pNum.setVisibility(EditText.GONE);

                        seekBar.setVisibility(EditText.GONE);
                        datePicker.setVisibility(LinearLayout.GONE);
                        eImg.setVisibility(EditText.GONE);

                        traitAudio.setVisibility(EditText.GONE);
                        traitPhoto.setVisibility(View.GONE);



                        // create Scanner inFile1

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

                    } else {
                        qPicker.setVisibility(LinearLayout.GONE);

                        kb.setVisibility(View.GONE);

                        tNum.setVisibility(EditText.GONE);
                        tNum.setEnabled(false);

                        clearGeneric.setVisibility(View.GONE);
                        clearBoolean.setVisibility(View.GONE);

                        pNum.setVisibility(EditText.GONE);
                        eNum.setVisibility(EditText.GONE);

                        pNum.setTextColor(Color.BLACK);
                        eNum.setTextColor(Color.BLACK);

                        eNum.setEnabled(true);
                        seekBar.setVisibility(EditText.GONE);
                        datePicker.setVisibility(LinearLayout.GONE);
                        eImg.setVisibility(EditText.GONE);

                        traitAudio.setVisibility(EditText.GONE);
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

        try {
            mGeneratedName = MainActivity.cRange.plot_id + " " + timeStamp.format(c.getTime());
        } catch (Exception e) {
            mGeneratedName = "error " + timeStamp.format(c.getTime());
        }

        setRecordingLocation(mGeneratedName);

        mRecorder.setOutputFile(mRecordingLocation.getAbsolutePath());

        try {
            mRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
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
            e.printStackTrace();
        }

        mPlayer.start();
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
                mListening = false;
                doRecord.setText(R.string.play);
                clearRecord.setEnabled(true);
            }
        });
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

        int j = 1;
        boolean haveData = false;

        // Loop through all existing records
        // Find the first one that matches plot
        // However, if ignore existing data is enabled, then
        // move to the appropriate plot
        for (j = 1; j <= rangeID.length; j++) {
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

        int j = 1;
        boolean haveData = false;

        // Loop through all existing records
        // Find the first one that matches plot
        // However, if ignore existing data is enabled, then
        // move to the appropriate plot
        for (j = 1; j <= rangeID.length; j++) {
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

        int j = 1;
        boolean haveData = false;

        // Loop through all existing records
        // Find the first one that matches plot
        // However, if ignore existing data is enabled, then
        // move to the appropriate plot
        for (j = 1; j <= rangeID.length; j++) {
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
    public void onDestroy() {

        //save last plot id
        Editor ed = ep.edit();
        ed.putString("lastplot",cRange.plot_id);
        ed.commit();

        try {
            // Always close tips / hints along with the main activity
            TutorialMainActivity.thisActivity.finish();
        } catch (Exception e) {

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
            if (ep.getBoolean("EnableMap", false)) {
                systemMenu.findItem(R.id.map).setVisible(true);
            } else {
                systemMenu.findItem(R.id.map).setVisible(false);
            }

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

            //TODO add datagrid

        }

        // This allows dynamic language change without exiting the app
        local = ep.getString("language", "en");
        region = ep.getString("region", "");
        Locale locale2 = new Locale(local, region);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2,
                getBaseContext().getResources().getDisplayMetrics());
        invalidateOptionsMenu();

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

            mapIndex = 0;

            prefixTraits = MainActivity.dt.getRangeColumnsWithoutOrganizer();

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
            prefixTraits = MainActivity.dt.getRangeColumnsWithoutOrganizer();
            initWidgets(false);

        } else if (searchReload) {
            searchReload = false;

            paging = 1;

            if (rangeID != null) {
                moveTo(rangeID, searchRange, searchPlot, true);
            }
        }
    }

    // Get size of a row in a map 
    private int getMapRangeRowSize() {
        if (mapData == null)
            return 0;

        int large = 0;

        for (int i = 0; i < mapData.length; i++) {
            if (mapData[i].plotCount > large)
                large = mapData[i].plotCount;
        }

        return large;
    }

    // Helper function to draw grid
    // Simply loop through data, and perform action depending on whether it is doing analyze
    // or just map
    private void zigZagGridLeft(HashMap<Integer, String> compare, int start, String mapColor, boolean ignoreSizing) {
        map.removeAllViews();

        if (mapData == null)
            return;

        int max = mapData.length;

        int rowSize = getMapRangeRowSize();

        map.setColumnCount(rowSize);

        int actualRows = 0;

        if (max - start < GRIDSIZE) {
            actualRows = max - start;
        } else {
            actualRows = GRIDSIZE;
        }

        SearchData[] rowData = null;

        int segmentValue = max;

        if (!ignoreSizing)
            segmentValue = start + actualRows;

        for (int i = start; i < segmentValue; i++) {
            rowData = dt.getRowForMapPlot(mapData[i].plot, true);

            for (int j = 0; j < rowSize; j++) {
                if (j < rowData.length) {
                    if (compare == null) {
                        map.addView(createSquare(rowData[j].id, "#FFFFFF"));
                    } else {
                        if (!analyze) {
                            if (compare.containsKey(rowData[j].id))
                                map.addView(createSquare(rowData[j].id, mapColor));
                            else
                                map.addView(createSquare(rowData[j].id, "#FFFFFF"));
                        } else {
                            if (compare.containsKey(rowData[j].id)) {
                                if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
                                    map.addView(createSquare(rowData[j].id, mapColor));
                                else
                                    colorGradient(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric);
                            } else {
                                map.addView(createSquare(rowData[j].id, "#FFFFFF"));

                            }

                        }
                    }
                } else {
                    map.addView(createBoundarySpace());
                }
            }

        }
    }

    // Helper function to export grid
    // Simply loop through data, and perform action depending on whether it is doing analyze
    // or just map	
    private void zigZagGridLeftExport(HashMap<Integer, String> compare, int start, int mapColor, Canvas c) {
        if (mapData == null)
            return;

        int max = mapData.length;

        int rowSize = getMapRangeRowSize();

        int actualRows = 0;

        if (max - start < GRIDSIZE) {
            actualRows = max - start;
        } else {
            actualRows = GRIDSIZE;
        }

        SearchData[] rowData = null;

        int segmentValue = max;

        for (int i = start; i < segmentValue; i++) {
            rowData = dt.getRowForMapPlot(mapData[i].plot, true);

            for (int j = 0; j < rowSize; j++) {
                if (j < rowData.length) {
                    if (compare == null) {
                        exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
                    } else {
                        if (!analyze) {
                            if (compare.containsKey(rowData[j].id))
                                exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, mapColor);
                            else
                                exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
                        } else {
                            if (compare.containsKey(rowData[j].id)) {
                                if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
                                    exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, mapColor);
                                else
                                    colorGradientExport(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric, c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE);
                            } else {
                                exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
                            }

                        }
                    }
                } else {
                    exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
                }
            }

        }
    }

    // Helper function to draw grid
    // Simply loop through data, and perform action depending on whether it is doing analyze
    // or just map	
    private void zigZagGridRight(HashMap<Integer, String> compare, int start, String mapColor, boolean ignoreSizing) {
        map.removeAllViews();

        if (mapData == null)
            return;

        int max = mapData.length;

        int rowSize = getMapRangeRowSize();

        map.setColumnCount(rowSize);

        int actualRows = 0;

        if (max - start < GRIDSIZE) {
            actualRows = max - start;
        } else {
            actualRows = GRIDSIZE;
        }

        SearchData[] rowData = null;

        int segmentValue = max;

        if (!ignoreSizing)
            segmentValue = start + actualRows;

        for (int i = start; i < segmentValue; i++) {
            rowData = dt.getRowForMapPlot(mapData[i].plot, false);

            for (int j = 0; j < rowSize; j++) {
                if (j < rowData.length) {
                    if (compare == null) {
                        map.addView(createSquare(rowData[j].id, "#FFFFFF"));
                    } else {
                        if (!analyze) {
                            if (compare.containsKey(rowData[j].id))
                                map.addView(createSquare(rowData[j].id, mapColor));
                            else
                                map.addView(createSquare(rowData[j].id, "#FFFFFF"));
                        } else {
                            if (compare.containsKey(rowData[j].id)) {
                                if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
                                    map.addView(createSquare(rowData[j].id, mapColor));
                                else
                                    colorGradient(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric);
                            } else {
                                map.addView(createSquare(rowData[j].id, "#FFFFFF"));

                            }
                        }
                    }
                } else {
                    map.addView(createBoundarySpace());
                }
            }
        }
    }

    // Helper function to export grid
    // Simply loop through data, and perform action depending on whether it is doing analyze
    // or just map	
    private void zigZagGridRightExport(HashMap<Integer, String> compare, int start, int mapColor, Canvas c) {
        if (mapData == null)
            return;

        int max = mapData.length;

        int rowSize = getMapRangeRowSize();

        int actualRows = 0;

        if (max - start < GRIDSIZE) {
            actualRows = max - start;
        } else {
            actualRows = GRIDSIZE;
        }

        SearchData[] rowData = null;

        int segmentValue = max;

        for (int i = start; i < segmentValue; i++) {
            rowData = dt.getRowForMapPlot(mapData[i].plot, true);

            for (int j = 0; j < rowSize; j++) {
                if (j < rowData.length) {
                    if (compare == null) {
                        exportSquare(c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
                    } else {
                        if (!analyze) {
                            if (compare.containsKey(rowData[j].id))
                                exportSquare(c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, mapColor);
                            else
                                exportSquare(c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
                        } else {
                            if (compare.containsKey(rowData[j].id)) {
                                if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
                                    exportSquare(c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, mapColor);
                                else
                                    colorGradientExport(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric, c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE);
                            } else {
                                exportSquare(c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
                            }
                        }
                    }
                } else {
                    exportSquare(c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
                }
            }
        }
    }

    // Helper function to draw grid
    // Simply loop through data, and perform action depending on whether it is doing analyze
    // or just map	
    private void serpentineGridLeft(HashMap<Integer, String> compare, int start, String mapColor, boolean ignoreSizing) {
        map.removeAllViews();

        if (mapData == null)
            return;

        int max = mapData.length;

        int rowSize = getMapRangeRowSize();

        map.setColumnCount(rowSize);

        int actualRows = 0;

        if (max - start < GRIDSIZE) {
            actualRows = max - start;
        } else {
            actualRows = GRIDSIZE;
        }

        SearchData[] rowData = null;

        int segmentValue = max;

        if (!ignoreSizing)
            segmentValue = start + actualRows;

        for (int i = start; i < segmentValue; i++) {
            rowData = dt.getRowForMapPlot(mapData[i].plot, init[i]);

            for (int j = 0; j < rowSize; j++) {
                if (j < rowData.length) {
                    if (compare == null) {
                        map.addView(createSquare(rowData[j].id, "#FFFFFF"));
                    } else {
                        if (!analyze) {
                            if (compare.containsKey(rowData[j].id))
                                map.addView(createSquare(rowData[j].id, mapColor));
                            else
                                map.addView(createSquare(rowData[j].id, "#FFFFFF"));
                        } else {
                            if (compare.containsKey(rowData[j].id)) {
                                if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
                                    map.addView(createSquare(rowData[j].id, mapColor));
                                else
                                    colorGradient(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric);
                            } else {
                                map.addView(createSquare(rowData[j].id, "#FFFFFF"));

                            }

                        }
                    }
                } else {
                    map.addView(createBoundarySpace());
                }
            }

        }
    }

    // Helper function to export grid
    // Simply loop through data, and perform action depending on whether it is doing analyze
    // or just map	
    private void serpentineGridLeftExport(HashMap<Integer, String> compare, int start, int mapColor, Canvas c) {
        if (mapData == null)
            return;

        int max = mapData.length;

        int rowSize = getMapRangeRowSize();

        int actualRows = 0;

        if (max - start < GRIDSIZE) {
            actualRows = max - start;
        } else {
            actualRows = GRIDSIZE;
        }

        SearchData[] rowData = null;

        int segmentValue = max;

        for (int i = start; i < segmentValue; i++) {
            rowData = dt.getRowForMapPlot(mapData[i].plot, init[i]);

            for (int j = 0; j < rowSize; j++) {
                if (j < rowData.length) {
                    if (compare == null) {
                        exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
                    } else {
                        if (!analyze) {
                            if (compare.containsKey(rowData[j].id))
                                exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, mapColor);
                            else
                                exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
                        } else {
                            if (compare.containsKey(rowData[j].id)) {
                                if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
                                    exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, mapColor);
                                else
                                    colorGradientExport(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric, c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE);
                            } else {
                                exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
                            }

                        }
                    }
                } else {
                    exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
                }
            }
        }
    }

    // Helper function to export grid
    // Simply loop through data, and perform action depending on whether it is doing analyze
    // or just map	
    private void serpentineGridRightExport(HashMap<Integer, String> compare, int start, int mapColor, Canvas c) {
        if (mapData == null)
            return;

        int max = mapData.length;

        int rowSize = getMapRangeRowSize();

        int actualRows = 0;

        if (max - start < GRIDSIZE) {
            actualRows = max - start;
        } else {
            actualRows = GRIDSIZE;
        }

        SearchData[] rowData = null;

        int segmentValue = max;

        for (int i = start; i < segmentValue; i++) {
            rowData = dt.getRowForMapPlot(mapData[i].plot, !init[i]);

            for (int j = 0; j < rowSize; j++) {
                if (j < rowData.length) {
                    if (compare == null) {
                        exportSquare(c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
                    } else {
                        if (!analyze) {
                            if (compare.containsKey(rowData[j].id))
                                exportSquare(c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, mapColor);
                            else
                                exportSquare(c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
                        } else {
                            if (compare.containsKey(rowData[j].id)) {
                                if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
                                    exportSquare(c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, mapColor);
                                else
                                    colorGradientExport(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric, c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE);
                            } else {
                                exportSquare(c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
                            }
                        }
                    }
                } else {
                    exportSquare(c, ((rowSize - 1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
                }
            }
        }
    }

    // Helper function to draw grid
    // Simply loop through data, and perform action depending on whether it is doing analyze
    // or just map	
    private void serpentineGridRight(HashMap<Integer, String> compare, int start, String mapColor, boolean ignoreSizing) {
        map.removeAllViews();

        if (mapData == null)
            return;

        int max = mapData.length;

        int rowSize = getMapRangeRowSize();

        map.setColumnCount(rowSize);

        int actualRows = 0;

        if (max - start < GRIDSIZE) {
            actualRows = max - start;
        } else {
            actualRows = GRIDSIZE;
        }

        SearchData[] rowData = null;

        int segmentValue = max;

        if (!ignoreSizing)
            segmentValue = start + actualRows;

        for (int i = start; i < segmentValue; i++) {
            rowData = dt.getRowForMapPlot(mapData[i].plot, init[i]);

            for (int j = 0; j < rowSize; j++) {
                if (j < rowData.length) {
                    if (compare == null) {
                        map.addView(createSquare(rowData[j].id, "#FFFFFF"));
                    } else {
                        if (!analyze) {
                            if (compare.containsKey(rowData[j].id))
                                map.addView(createSquare(rowData[j].id, mapColor));
                            else
                                map.addView(createSquare(rowData[j].id, "#FFFFFF"));
                        } else {
                            if (compare.containsKey(rowData[j].id)) {
                                if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
                                    map.addView(createSquare(rowData[j].id, mapColor));
                                else
                                    colorGradient(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric);
                            } else {
                                map.addView(createSquare(rowData[j].id, "#FFFFFF"));

                            }
                        }
                    }
                } else {
                    map.addView(createBoundarySpace());
                }
            }
        }
    }

    // Find largest number for a trait; used in map analysis
    private float largestNumeric(String[] plots, String trait) {
        float v = 0;

        for (String i : plots) {
            float g;

            try {
                g = Float.parseFloat(dt.getSingleValue(i, trait));
            } catch (Exception e) {
                g = 0;
            }

            if (g > v)
                v = g;
        }

        return v;
    }

    // Find smallest number for a trait; used in map analysis
    private float smallestNumeric(String[] plots, String trait) {
        float v = -1;

        for (String i : plots) {
            float g;

            try {
                g = Float.parseFloat(dt.getSingleValue(i, trait));
            } catch (Exception e) {
                g = 0;
            }

            if (v == -1)
                v = g;

            if (g < v)
                v = g;
        }

        return v;
    }

    // Function colors analysis depending on trait type
    private void colorGradient(int id, String value, float sNumeric, float bNumeric) {
        TraitObject colorTrait = dt.getDetail(mapTrait.getSelectedItem().toString());

        if (colorTrait.format.equals("numeric")) {
            map.addView(createSquare(id, interpolateColor(Double.parseDouble(value) / bNumeric)));
        } else if (colorTrait.format.equals("percent")) {
            map.addView(createSquare(id, interpolateColor(Double.parseDouble(value) / Double.parseDouble(colorTrait.maximum))));
        } else if (colorTrait.format.equals("date")) {
            Calendar c = Calendar.getInstance();

            String[] d = value.split("\\.");

            c.set(Integer.parseInt(d[0]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[2]));

            Double v = (double) c.get(Calendar.DAY_OF_YEAR);

            map.addView(createSquare(id, interpolateColor(v / 365)));
        } else if (colorTrait.format.equals("boolean")) {
            int v = 0;

            if (value.equals("false"))
                v = 0;
            else
                v = 1;

            map.addView(createSquare(id, interpolateColor(v)));
        } else if (colorTrait.format.equals("categorical")) {
            String[] d = colorTrait.categories.split("/");

            for (int i = 0; i < d.length; i++) {
                if (value.equals(d[i])) {
                    map.addView(createSquare(id, getColor(i + 1)));
                    break;
                }
            }
        } else
            map.addView(createSquare(id, "#FFFFFF"));

    }

    // Function exports grid square based on trait type
    private void colorGradientExport(int id, String value, float sNumeric, float bNumeric, Canvas cv, int x, int y) {
        TraitObject colorTrait = dt.getDetail(mapTrait.getSelectedItem().toString());

        if (colorTrait.format.equals("numeric")) {
            exportSquare(cv, x, y, interpolateColor(Double.parseDouble(value) / bNumeric));
        } else if (colorTrait.format.equals("percent")) {
            exportSquare(cv, x, y, interpolateColor(Double.parseDouble(value) / Double.parseDouble(colorTrait.maximum)));
        } else if (colorTrait.format.equals("date")) {
            Calendar c = Calendar.getInstance();

            String[] d = value.split("\\.");

            c.set(Integer.parseInt(d[0]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[2]));

            Double v = (double) c.get(Calendar.DAY_OF_YEAR);

            exportSquare(cv, x, y, interpolateColor(v / 365));
        } else if (colorTrait.format.equals("boolean")) {
            int v = 0;

            if (value.equals("false"))
                v = 0;
            else
                v = 1;

            exportSquare(cv, x, y, interpolateColor(v));
        } else if (colorTrait.format.equals("qualitative") | colorTrait.format.equals("categorical")) {
            String[] d = colorTrait.categories.split("/");

            for (int i = 0; i < d.length; i++) {
                if (value.equals(d[i])) {
                    if (x >= exportRowSize) {
                        x = 0;
                        y += EXPORTGRIDSIZE;
                    }

                    exportSquare(cv, x, y, getColor(i + 1));

                    x += EXPORTGRIDSIZE;

                    break;
                }
            }
        } else
            exportSquare(cv, x, y, "#FFFFFF");

    }

    // This is used to generate range from green to red
    public static String interpolateColor(double power) {
        double H = (1 - power) * 120f; // base green
        double S = 0.9; // Saturation
        double B = 0.9; // Brightness

        float[] hsv = new float[3];

        hsv[0] = (float) H;
        hsv[1] = (float) S;
        hsv[2] = (float) B;

        String hexColor = String.format("#%06X", (0xFFFFFF & Color.HSVToColor(hsv)));

        return hexColor;
    }

    // This is used for qualitative
    // I won't go through the formula but it will generate hundreds of unique colors
    // and ensure that adjacent colors are not similar e.g. light green, dark green
    public static String getColor(int i) {

        String hexColor = String.format("#%06X", (0xFFFFFF & getRGB(i)));

        return hexColor;
    }

    // As above
    public static int getRGB(int index) {
        int[] p = getPattern(index);
        return getElement(p[0]) << 16 | getElement(p[1]) << 8 | getElement(p[2]);
    }

    // As above	
    public static int getElement(int index) {
        int value = index - 1;
        int v = 0;
        for (int i = 0; i < 8; i++) {
            v = v | (value & 1);
            v <<= 1;
            value >>= 1;
        }
        v >>= 1;
        return v & 0xFF;
    }

    // As above	
    public static int[] getPattern(int index) {
        int n = (int) Math.cbrt(index);
        index -= (n * n * n);
        int[] p = new int[3];
        Arrays.fill(p, n);
        if (index == 0) {
            return p;
        }
        index--;
        int v = index % 3;
        index = index / 3;
        if (index < n) {
            p[v] = index % n;
            return p;
        }
        index -= n;
        p[v] = index / n;
        p[++v % 3] = index % n;
        return p;
    }

    private Runnable doAnalyze = new Runnable() {
        public void run() {
            new AnalyzeTask().execute(0);
        }
    };

    private class AnalyzeTask extends AsyncTask<Integer, Integer, Integer> {
        ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            dialog = new ProgressDialog(MainActivity.this);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(getString(R.string.analyzewait));

            dialog.show();

        }

        @Override
        protected Integer doInBackground(Integer... params) {
            // redraw is only called if you want to regenerate all the data
            if (redraw) {
                analyzeRange = dt.analyze(mapTrait.getSelectedItem().toString());

                mapIndex = 0;

                // Data for analysis
                String[] plots = dt.getAllPlotID();
                bNumeric = largestNumeric(plots, mapTrait.getSelectedItem().toString());
                sNumeric = smallestNumeric(plots, mapTrait.getSelectedItem().toString());

                if (ep.getInt("GRIDPOSITIONORIENTATION", 0) == 0) {
                    processSerpentineMap(analyzeRange, ep.getInt("GRIDPOSITION", 0));
                } else {
                    processZigZagMap(analyzeRange, ep.getInt("GRIDPOSITION", 0));
                }

            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            // Move the map the way the user will so all the variables are constant
            if (redraw) {
                map.scrollTo(0, 0);

                switch (ep.getInt("GRIDPOSITION", 0)) {
                    case 1:
                    case 3:
                        // Simulate user moving to the plot
                        do {
                            if (mapIndex + GRIDSIZE >= mapData.length) {
                                break;
                            } else {
                                mapIndex += GRIDSIZE;

                                if (ep.getInt("GRIDPOSITIONORIENTATION", 0) == 0) {
                                    loadSerpentineMap(analyzeRange, ep.getInt("GRIDPOSITION", 0), "#16e616");
                                } else {
                                    loadZigZagMap(analyzeRange, ep.getInt("GRIDPOSITION", 0), "#16e616");
                                }
                            }

                        } while (mapIndex + GRIDSIZE < mapData.length);

                        break;

                    case 0:
                    case 2:
                        if (ep.getInt("GRIDPOSITIONORIENTATION", 0) == 0) {
                            loadSerpentineMap(analyzeRange, ep.getInt("GRIDPOSITION", 0), "#16e616");
                        } else {
                            loadZigZagMap(analyzeRange, ep.getInt("GRIDPOSITION", 0), "#16e616");
                        }
                        break;
                }

                // Display the number of segments you can scroll up /down, left / right

                mapScrollSegment = getMapRangeRowSize();

                if (mapScrollSegment >= 13)
                    scrollSegmentSize = 13;
                else
                    scrollSegmentSize = mapScrollSegment;

                currentMapScrollSegment = scrollSegmentSize;

                switch (ep.getInt("GRIDPOSITION", 0)) {
                    case 0:
                    case 1:
                        mapSegmenth.setText("W: " + currentMapScrollSegment + "/" + mapScrollSegment);
                        break;

                    case 2:
                    case 3:
                        do {
                            currentMapScrollSegment += scrollSegmentSize;

                            if (currentMapScrollSegment > mapScrollSegment) {
                                currentMapScrollSegment = mapScrollSegment;
                            }

                            map.scrollBy(520, 0);

                            mapSegmenth.setText("W: " + currentMapScrollSegment + "/" + mapScrollSegment);

                        } while (currentMapScrollSegment < mapScrollSegment);

                        break;

                }

            } else {
                if (ep.getInt("GRIDPOSITIONORIENTATION", 0) == 0) {
                    loadSerpentineMap(analyzeRange, ep.getInt("GRIDPOSITION", 0), "#16e616");
                } else {
                    loadZigZagMap(analyzeRange, ep.getInt("GRIDPOSITION", 0), "#16e616");
                }

            }

            if (mapIndex + GRIDSIZE >= mapData.length)
                mapSegment.setText("H: " + mapData.length + "/" + mapData.length);
            else
                mapSegment.setText("H: " + String.valueOf(mapIndex + GRIDSIZE) + "/" + mapData.length);

            if (redraw)
                redraw = false;

            if (dialog.isShowing())
                dialog.dismiss();
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

        if (newTraits.containsKey(parent))
            newTraits.remove(parent);

        newTraits.put(parent, value);

        // Always remove existing trait before inserting again
        // Based on plot_id, prevent duplicates
        dt.deleteTrait(cRange.plot_id, parent);
        dt.insertUserTraits(cRange.plot_id, parent, trait, value);

    }

    // Delete trait, including from database
    private void removeTrait(String parent) {

        if (cRange == null || cRange.plot_id.length() == 0) {
            return;
        }

        gridCreated = false;

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
                    case MESSAGE_CHECK_BTN_STILL_PRESSED:
                        ImageView btn = (ImageView) findViewById(msg.arg1);
                        if (btn.getTag() != null) { // button is still pressed	                
                            Message msg1 = new Message(); // schedule next btn pressed check
                            msg1.copyFrom(msg);
                            if (msg.arg1 == R.id.rangeLeft) {
                                repeatLeft();
                            } else {
                                repeatRight();
                            }
                            myGuiHandler.removeMessages(MESSAGE_CHECK_BTN_STILL_PRESSED);
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

        if (ep.getBoolean("EnableMap", false)) {
            systemMenu.findItem(R.id.map).setVisible(true);
        } else {
            systemMenu.findItem(R.id.map).setVisible(false);
        }

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

        //TODO add logic for datagrid
        systemMenu.findItem(R.id.datagrid).setVisible(false);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Intent intent = new Intent(Intent.ACTION_VIEW);

        switch (item.getItemId()) {
            case R.id.settings:
                try {
                    TutorialMainActivity.thisActivity.finish();
                } catch (Exception e) {

                }

                intent.setClassName(MainActivity.this,
                        ConfigActivity.class.getName());
                startActivity(intent);
                break;

            case R.id.search:
                try {
                    TutorialMainActivity.thisActivity.finish();
                } catch (Exception e) {

                }

                intent.setClassName(MainActivity.this,
                        SearchActivity.class.getName());
                startActivity(intent);
                break;

            case R.id.map:
                showMapDialog();
                break;

            case R.id.resources:
                intent.setClassName(MainActivity.this,
                        FileExploreActivity.class.getName());
                intent.putExtra("path", Constants.RESOURCEPATH);
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
                makeToast(ep.getString("lastplot", "nope"));
                break;
            case R.id.summary:
                showSummary();
                break;
            case R.id.datagrid:
                try {
                    TutorialMainActivity.thisActivity.finish();
                } catch (Exception e) {

                }

                intent.setClassName(MainActivity.this,
                        DatagridActivity.class.getName());
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void moveToPlotID() {
        goToId = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        goToId.setTitle(getString(R.string.jumptoplotidbutton));
        goToId.setContentView(R.layout.gotobarcode);

        goToId.setCancelable(true);
        goToId.setCanceledOnTouchOutside(true);

        android.view.WindowManager.LayoutParams langParams = goToId.getWindow().getAttributes();
        langParams.width = LayoutParams.FILL_PARENT;
        goToId.getWindow().setAttributes((android.view.WindowManager.LayoutParams) langParams);

        final EditText barcodeId = (EditText) goToId.findViewById(R.id.barcodeid);
        Button exportButton = (Button) goToId.findViewById(R.id.saveBtn);
        Button closeBtn = (Button) goToId.findViewById(R.id.closeBtn);
        Button camBtn = (Button) goToId.findViewById(R.id.camBtn);

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
        cRange = dt.getRange(rangeID[paging - 1]);
        displayRange(cRange);
        lastRange = cRange.range;
        newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
                .clone();
        initWidgets(true);
    }

    public void onClick(View b) {

        String v = "";

            switch (b.getId())
            {
                // Photo capture
                case R.id.capture:
                    try
                    {
                        int m = 0;

                        try
                        {
                            m = Integer.parseInt(currentTrait.details);
                        }
                        catch (Exception n)
                        {
                            m = 0;
                        }

                        // Do not take photos if limit is reached
                        if (m == 0 || photoLocation.size() < m)
                        {
                            takePicture();
                        }
                        else
                            Toast.makeText(MainActivity.this, getString(R.string.maxphotos), Toast.LENGTH_LONG).show();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();

                        ErrorLog("CameraError.txt", e.getMessage());

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

        if(traitRustRating.getVisibility() == View.VISIBLE && eNum.getText().length()>0 && !v.equals("/") && !eNum.getText().toString().substring(eNum.getText().length()-1).equals("/"))  {
            v = ":" + v;
        }

        if (b.getId() == R.id.k16 || b.getId() == R.id.clearRustBtn) {
            //eNum.setText(eNum.getText().toString().substring(0, eNum.getText().toString().length()-1));
            eNum.removeTextChangedListener(eNumUpdate);
            eNum.setText("");
            removeTrait(currentTrait.trait);
            eNum.addTextChangedListener(eNumUpdate);
        } else {
            if(eNum.getText().toString().matches(".*\\d.*") && v.matches(".*\\d.*") && traitRustRating.getVisibility() == View.VISIBLE && !eNum.getText().toString().contains("/") ){
                makeToast(getString(R.string.rustwarning));
                v = "";
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
            // Create the File where the photo should go
            File photoFile = null;
            photoFile = file;

            // Continue only if the File was successfully created
            if (photoFile != null) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(photoFile));
                startActivityForResult(takePictureIntent, 252);
            }

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

    private void showMapDialog() {
        final Dialog configDialog = new Dialog(MainActivity.this, android.R.style.Theme_Holo_Light_Dialog);
        configDialog.setTitle(getString(R.string.mapconfig));
        configDialog.setContentView(R.layout.parameter);

        android.view.WindowManager.LayoutParams params = configDialog.getWindow().getAttributes();
        params.width = LayoutParams.FILL_PARENT;

        configDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);

        configDialog.setCancelable(true);
        configDialog.setCanceledOnTouchOutside(true);

        final Dialog mapDialog = new Dialog(MainActivity.this, android.R.style.Theme_Holo_Light_Dialog);

        mapDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        mapDialog.setContentView(R.layout.map);

        android.view.WindowManager.LayoutParams params2 = configDialog.getWindow().getAttributes();
        params2.width = LayoutParams.FILL_PARENT;

        mapDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);

        mapDialog.setCancelable(true);
        mapDialog.setCanceledOnTouchOutside(true);

        final Spinner dir = (Spinner) configDialog.findViewById(R.id.direction);
        final Spinner orientation = (Spinner) configDialog.findViewById(R.id.orientation);

        orientation.setOnItemSelectedListener(new OnItemSelectedListener() {

            public void onItemSelected(AdapterView<?> arg0, View arg1,
                                       int position, long arg3) {

            }

            public void onNothingSelected(AdapterView<?> arg0) {

            }
        });

        dir.setOnItemSelectedListener(new OnItemSelectedListener() {

            public void onItemSelected(AdapterView<?> arg0, View arg1,
                                       int position, long arg3) {

            }

            public void onNothingSelected(AdapterView<?> arg0) {

            }
        });

        String[] directions = new String[4];
        directions[0] = getString(R.string.topleft);
        directions[1] = getString(R.string.btmleft);
        directions[2] = getString(R.string.topright);
        directions[3] = getString(R.string.btmright);

        ArrayAdapter adapter2 = new ArrayAdapter(MainActivity.this, R.layout.spinnerlayout, directions);
        dir.setAdapter(adapter2);

        String[] orientations = new String[2];
        orientations[0] = getString(R.string.serpentine);
        orientations[1] = getString(R.string.zigzag);

        ArrayAdapter adapter3 = new ArrayAdapter(MainActivity.this, R.layout.spinnerlayout, orientations);
        orientation.setAdapter(adapter3);

        Button saveBtn = (Button) configDialog.findViewById(R.id.saveBtn);
        Button clearBtn = (Button) configDialog.findViewById(R.id.clearBtn);

        // Map configure, save
        saveBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {

                Editor ed = ep.edit();
                ed.putInt("GRIDPOSITIONORIENTATION", orientation.getSelectedItemPosition());
                ed.putInt("GRIDPOSITION", dir.getSelectedItemPosition());
                ed.commit();

                configDialog.dismiss();

                if (!ep.getBoolean("MAPCONFIGURED", false)) {
                    ed.putBoolean("MAPCONFIGURED", true);
                    ed.commit();

                    mapDialog.show();
                } else {
                    redraw = true;

                    mHandler.post(doAnalyze);
                }
            }
        });

        // Map configure, clear
        clearBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {

                Editor ed = ep.edit();
                ed.remove("GRIDPOSITION");
                ed.remove("GRIDPOSITIONORIENTATION");
                ed.remove("MAPCONFIGURED");
                ed.commit();

                configDialog.dismiss();

                redraw = true;

                mHandler.post(doAnalyze);
            }
        });


        map = (GridLayout) mapDialog.findViewById(R.id.map);
        mapUp = (ImageView) mapDialog.findViewById(R.id.upBtn);
        mapDown = (ImageView) mapDialog.findViewById(R.id.downBtn);
        mapLeft = (ImageView) mapDialog.findViewById(R.id.leftBtn);
        mapRight = (ImageView) mapDialog.findViewById(R.id.rightBtn);

        mapAnalyze = (Button) mapDialog.findViewById(R.id.mapAnalyze);
        mapParameter = (Button) mapDialog.findViewById(R.id.mapParameter);
        mapClose = (Button) mapDialog.findViewById(R.id.mapClose);
        mapExport = (Button) mapDialog.findViewById(R.id.mapExport);
        mapSummary = (Button) mapDialog.findViewById(R.id.mapSummary);

        mapSegment = (TextView) mapDialog.findViewById(R.id.segment);
        mapSegmenth = (TextView) mapDialog.findViewById(R.id.segmenth);

        mapPlot = (TextView) mapDialog.findViewById(R.id.plot);
        mapRange = (TextView) mapDialog.findViewById(R.id.range);
        mapValue = (TextView) mapDialog.findViewById(R.id.value);

        mapTrait = (Spinner) mapDialog.findViewById(R.id.mapTrait);

        // Map Summary 
        mapSummary.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {

                showSummary();
            }
        });

        // Export bitmap to disk
        mapExport.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {

                final Dialog mapExportDialog = new Dialog(MainActivity.this, android.R.style.Theme_Holo_Light_Dialog);
                mapExportDialog.setTitle(getString(R.string.exportas));
                mapExportDialog.setContentView(R.layout.savedb);

                android.view.WindowManager.LayoutParams params2 = mapExportDialog.getWindow().getAttributes();
                params2.width = LayoutParams.FILL_PARENT;
                mapExportDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params2);

                mapExportDialog.setCancelable(true);
                mapExportDialog.setCanceledOnTouchOutside(true);

                Button closeBtn = (Button) mapExportDialog.findViewById(R.id.closeBtn);
                Button saveBtn = (Button) mapExportDialog.findViewById(R.id.saveBtn);

                exportFile = (EditText) mapExportDialog.findViewById(R.id.fileName);

                SimpleDateFormat timeStamp = new SimpleDateFormat(
                        "yyyy.MM.dd", Locale.getDefault());


                mapExportfilename = "";

                try {
                    mapExportfilename = mapTrait.getSelectedItem().toString();
                } catch (Exception e) {
                    mapExportfilename = "map_error";
                }

                exportFile.setText(mapExportfilename + "_" +
                        timeStamp.format(Calendar.getInstance().getTime())
                        + ".jpg");

                saveBtn.setOnClickListener(new OnClickListener() {

                    public void onClick(View v) {
                        mapExportDialog.dismiss();

                        mHandler.post(exportMap);
                    }
                });

                closeBtn.setOnClickListener(new OnClickListener() {

                    public void onClick(View v) {
                        mapExportDialog.dismiss();
                    }
                });

                mapExportDialog.show();
            }
        });

        mapClose.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                mapDialog.dismiss();
            }
        });

        mapParameter.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                orientation.setSelection(ep.getInt("GRIDPOSITIONORIENTATION", 0));
                dir.setSelection(ep.getInt("GRIDPOSITION", 0));

                configDialog.show();
            }
        });

        mapAnalyze.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {

                if (dt.getVisibleTrait() == null)
                    return;

                analyze = !analyze;

                if (analyze) {
                    mapAnalyze.setText(getString(R.string.clear));
                } else {
                    mapAnalyze.setText(getString(R.string.mapanalyze));
                }

                mHandler.post(doAnalyze);
            }

        });

        mapLeft.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        mapLeft.setImageResource(R.drawable.lefts);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        mapLeft.setImageResource(R.drawable.left);
                    case MotionEvent.ACTION_CANCEL:
                        break;
                }

                return false; // return true to prevent calling btn onClick handler
            }
        });

        mapLeft.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {

                if (currentMapScrollSegment == scrollSegmentSize)
                    return;

                currentMapScrollSegment -= scrollSegmentSize;

                if (currentMapScrollSegment < scrollSegmentSize) {
                    currentMapScrollSegment = scrollSegmentSize;
                }

                map.scrollBy(-520, 0);

                mapSegmenth.setText("W: " + currentMapScrollSegment + "/" + mapScrollSegment);
            }
        });

        mapRight.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        mapRight.setImageResource(R.drawable.rights);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        mapRight.setImageResource(R.drawable.right);
                    case MotionEvent.ACTION_CANCEL:
                        break;
                }

                return false; // return true to prevent calling btn onClick handler
            }
        });

        mapRight.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {

                if (currentMapScrollSegment == mapScrollSegment)
                    return;

                currentMapScrollSegment += scrollSegmentSize;

                if (currentMapScrollSegment > mapScrollSegment) {
                    currentMapScrollSegment = mapScrollSegment;
                }

                map.scrollBy(520, 0);

                mapSegmenth.setText("W: " + currentMapScrollSegment + "/" + mapScrollSegment);

            }
        });

        mapUp.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        mapUp.setImageResource(R.drawable.ups);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        mapUp.setImageResource(R.drawable.up);
                    case MotionEvent.ACTION_CANCEL:
                        break;
                }

                return false; // return true to prevent calling btn onClick handler
            }
        });

        mapUp.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {

                mapIndex -= GRIDSIZE;

                if (mapIndex <= 0) {
                    mapIndex = 0;
                }

                mHandler.post(doAnalyze);
            }

        });

        mapDown.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        mapDown.setImageResource(R.drawable.downs);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        mapDown.setImageResource(R.drawable.down);
                    case MotionEvent.ACTION_CANCEL:
                        break;
                }

                return false; // return true to prevent calling btn onClick handler
            }
        });

        mapDown.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                if (mapIndex + GRIDSIZE >= mapData.length) {
                    return;
                } else
                    mapIndex += GRIDSIZE;

                mHandler.post(doAnalyze);
            }

        });

        if (traits != null) {
            ArrayAdapter<String> directionArrayAdapter = new ArrayAdapter<String>(
                    this, R.layout.spinnerlayout, traits);
            directionArrayAdapter
                    .setDropDownViewResource(R.layout.spinnerlayout);
            mapTrait.setAdapter(directionArrayAdapter);

        }

        final ImageView mapTraitLeft = (ImageView) mapDialog.findViewById(R.id.traitLeft);
        final ImageView mapTraitRight = (ImageView) mapDialog.findViewById(R.id.traitRight);

        mapTraitLeft.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        mapTraitLeft.setImageResource(R.drawable.l_arrows);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        mapTraitLeft.setImageResource(R.drawable.l_arrow);
                    case MotionEvent.ACTION_CANCEL:
                        break;
                }

                return false; // return true to prevent calling btn onClick handler
            }
        });

        // Go to previous trait
        mapTraitLeft.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {

                int mapPos = mapTrait.getSelectedItemPosition() - 1;

                if (mapPos < 0)
                    mapPos = 0;

                mapTrait.setSelection(mapPos);
            }
        });

        mapTraitRight.setOnTouchListener(new OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        mapTraitRight.setImageResource(R.drawable.r_arrows);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        break;
                    case MotionEvent.ACTION_UP:
                        mapTraitRight.setImageResource(R.drawable.r_arrow);
                    case MotionEvent.ACTION_CANCEL:
                        break;
                }

                return false; // return true to prevent calling btn onClick handler
            }
        });

        // Go to next trait
        mapTraitRight.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {

                int mapPos = mapTrait.getSelectedItemPosition() + 1;

                if (mapPos > mapTrait.getCount() - 1)
                    mapPos = 0;

                mapTrait.setSelection(mapPos);
            }
        });

        if (dt.getVisibleTrait() != null) {
            ArrayAdapter adapter = new ArrayAdapter(MainActivity.this, R.layout.spinnerlayout, dt.getVisibleTrait());
            mapTrait.setAdapter(adapter);

            mapTrait.setOnItemSelectedListener(new OnItemSelectedListener() {

                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int arg2, long arg3) {

                    mapRange.setText("");
                    mapPlot.setText("");
                    mapValue.setText("");

                    redraw = true;

                    mHandler.post(doAnalyze);
                }

                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });
        }

        redraw = true;
        analyze = false;

        if (!ep.getBoolean("MAPCONFIGURED", false)) {
            configDialog.show();
        } else {
            mapDialog.show();
        }

    }

    private void showSummary() {
        final Dialog summaryDialog = new Dialog(MainActivity.this, android.R.style.Theme_Holo_Light_Dialog);
        summaryDialog.setTitle(getString(R.string.mapsummary));
        summaryDialog.setContentView(R.layout.summary);

        android.view.WindowManager.LayoutParams params2 = summaryDialog.getWindow().getAttributes();
        params2.width = LayoutParams.FILL_PARENT;
        summaryDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params2);

        summaryDialog.setCancelable(true);
        summaryDialog.setCanceledOnTouchOutside(true);

        Button closeBtn = (Button) summaryDialog.findViewById(R.id.closeBtn);

        TextView summaryText = (TextView) summaryDialog.findViewById(R.id.text1);

        closeBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                summaryDialog.dismiss();
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
        summaryDialog.show();
    }

    // This is used for exporting a grid square
    private void exportSquare(Canvas c, int x, int y, String color) {
        Paint paint = new Paint();
        paint.setAlpha(0);
        paint.setAntiAlias(true);

        paint.setColor(Color.BLACK);
        c.drawRect(x, y, x + EXPORTGRIDSIZE, y + EXPORTGRIDSIZE, paint);

        paint.setColor(Color.parseColor(color));

        c.drawRect(x + 1, y + 1, x + EXPORTGRIDSIZE - 1, y + EXPORTGRIDSIZE - 1, paint);
    }

    // This is used for exporting a grid square
    private void exportSquare(Canvas c, int x, int y, int color) {
        Paint paint = new Paint();
        paint.setAlpha(0);
        paint.setAntiAlias(true);

        paint.setColor(Color.BLACK);
        c.drawRect(x, y, x + EXPORTGRIDSIZE, y + EXPORTGRIDSIZE, paint);

        paint.setColor(color);

        c.drawRect(x + 1, y + 1, x + EXPORTGRIDSIZE - 1, y + EXPORTGRIDSIZE - 1, paint);
    }

    private Runnable exportMap = new Runnable() {
        public void run() {
            new ExportMapTask().execute(0);
        }
    };

    private class ExportMapTask extends AsyncTask<Integer, Integer, Integer> {
        ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            dialog = new ProgressDialog(MainActivity.this);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(getString(R.string.mapexportwait));

            dialog.show();

        }

        @Override
        protected Integer doInBackground(Integer... params) {
            Bitmap b = null;

            // serpentine
            exportRowSize = getMapRangeRowSize() * EXPORTGRIDSIZE;

            b = Bitmap.createBitmap(exportRowSize, mapData.length * EXPORTGRIDSIZE, Bitmap.Config.ARGB_8888);

            Canvas c = new Canvas(b);

            Paint paint = new Paint();
            paint.setAlpha(0);
            paint.setAntiAlias(true);
            paint.setColor(Color.parseColor("#FFFFFF"));

            c.drawRect(0, 0, exportRowSize * EXPORTGRIDSIZE, mapData.length * EXPORTGRIDSIZE, paint);
            c.save();

            if (ep.getInt("GRIDPOSITIONORIENTATION", 0) == 0) {
                loadSerpentineMapExport(analyzeRange, ep.getInt("GRIDPOSITION", 0), Color.GREEN, c);
            } else {
                loadZigZagMapExport(analyzeRange, ep.getInt("GRIDPOSITION", 0), Color.GREEN, c);
            }

            c.save();

            try {
                FileOutputStream out = new FileOutputStream(Constants.RESOURCEPATH + "/" + exportFile.getText().toString());
                b.compress(CompressFormat.JPEG, 100, out);
                out.flush();
                out.close();
                b.recycle();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (dialog.isShowing())
                dialog.dismiss();

        }
    }

    public int getVersion() {
        int v = 0;
        try {
            v = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
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

    private void capturePhoto(Intent intent) {
        Bundle extras = intent.getExtras();
        Bitmap bitmap = (Bitmap) extras.get("data");

        if (bitmap == null)
            return;

        SimpleDateFormat timeStamp = new SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss", Locale.getDefault());

        File dir = new File(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/photos/");

        dir.mkdirs();

        String generatedName = MainActivity.cRange.plot_id + "_" + timeStamp.format(Calendar.getInstance().getTime()) + ".jpg";

        Log.w("File", Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/photos/" + generatedName);

        // Save photo capture with timestamp as filename
        File file = new File(Constants.PLOTDATAPATH + "/" + ep.getString("FieldFile", "") + "/photos/",
                generatedName);

        try {
            file.createNewFile();
            FileOutputStream ostream = new FileOutputStream(file);
            bitmap.compress(CompressFormat.JPEG, 100, ostream);
            ostream.close();

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

        } catch (Exception e) {
            e.printStackTrace();
        }

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

        dt.insertUserTraits(cRange.plot_id, parent, trait, value);

        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss",
                Locale.getDefault());

        Editor ed = ep.edit();
        ed.putString("Dataset_" + ep.getString("FieldFile", "") + "_" + cRange.range + "_" + cRange.plot, timeStamp.format(Calendar.getInstance().getTime()));
        ed.commit();
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

            // Get the dimensions of the bitmap
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;

            BitmapFactory.decodeFile(path, bmOptions);
            int photoW = bmOptions.outWidth;
            int photoH = bmOptions.outHeight;

            int targetW = 0;
            int targetH = 0;

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

            Bitmap bitmap = BitmapFactory.decodeFile(path, bmOptions);
            return bitmap;

        } catch (Exception e) {
            e.printStackTrace();
            Toast toast = Toast.makeText(MainActivity.this, getString(R.string.photodecodefail), Toast.LENGTH_LONG);
            toast.setGravity(Gravity.TOP, 0, 0);
            toast.show();

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
                if (resultCode == RESULT_CANCELED) {
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
            goToId.dismiss();
        }
    }

    public void ErrorLog(String sFileName, String sErrMsg)
    {
        try
        {
            SimpleDateFormat lv_parser = new SimpleDateFormat("dd-MM-yyyy h:mm:ss a");
            lv_parser.setTimeZone(TimeZone.getTimeZone("UTC"));

            File file = new File(Constants.ERRORPATH, sFileName);

            FileWriter filewriter = new FileWriter(file, true);
            BufferedWriter out = new BufferedWriter(filewriter);

            out.write(lv_parser.format(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime()) + " " + sErrMsg + "\n");
            out.flush();
            out.close();
        }
        catch (Exception e)
        {

        }
    }

    private void scanFile(File filePath) {
        MediaScannerConnection.scanFile(this, new String[]{filePath.getAbsolutePath()}, null, null);
    }
}