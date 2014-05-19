package com.fieldbook.tracker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.LinearLayout.LayoutParams;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView.OnEditorActionListener;

/**
 * Main entry point. All main screen logic resides here
 * v1.6 - We have retained all existing data structures to minimize code breakage, but functions have new parameters
 * added to allow for multiple traits. All affected functions are documented in Datahelper.java
 * 
 * v2.0 - The actionbar is not backward compatible, this is why we're using a 3rd party library
 */ 
public class MainActivity extends SherlockActivity implements OnClickListener {

	public static DataHelper dt;

	public static boolean searchReload;

	public static final int MESSAGE_CHECK_BTN_STILL_PRESSED = 1;
	
	private static String displayColor = "#d50000";
	
    private static Object lock;
	
	private static HashMap newTraits;

	public static String searchRange;
	public static String searchPlot;	
	
	public static RangeObject cRange;
	
	public static boolean reloadData;
	public static boolean partialReload;
	
	public static String resourcePath = Environment.getExternalStorageDirectory()
	+ "/fieldBook/resources";

	public static String audioPath = Environment.getExternalStorageDirectory()
	+ "/fieldBook/plot_data/audio";
	
	public static String traitPath = Environment.getExternalStorageDirectory() 
	+ "/fieldbook/trait";
	
	public static String fieldImportPath = Environment.getExternalStorageDirectory() 
	+ "/fieldbook/field_import";

	public static String fieldExportPath = Environment.getExternalStorageDirectory() 
	+ "/fieldbook/field_export";

	private File mPath = new File(Environment.getExternalStorageDirectory()
			+ "/fieldBook");
	
	public static Activity thisActivity;
    
	private TextView traitDetails;

	private TraitObject currentTrait;

	private EditText eNum;
	private EditText pNum;
	private EditText tNum;

	private SeekBar seekBar;

	private Spinner traitType;

	private ImageView eImg;

	private String[] traits;

	public int[] rangeID;

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

	private EditText range;
	private EditText plot;

	private InputMethodManager imm;

	private Handler mHandler = new Handler();

	private LinearLayout datePicker;
	private LinearLayout qPicker;
	private LinearLayout kb;
	private LinearLayout audio;

	private TextView month;
	private TextView day;

	private SharedPreferences ep;

	private AutoResizeTextView drop3;
	private AutoResizeTextView drop2;
	private AutoResizeTextView drop1;

	private TextView drop3prefix;
	private TextView drop2prefix;
	private TextView drop1prefix;
	
	private TextView rangeName;
	private TextView plotName;
	
	private Button q1;
	private Button q2;
	private Button q3;
	private Button q4;
	private Button q5;
	private Button q6;
	private Button q7;
	private Button q8;
	private Button q9;
	private Button q10;
	private Button q11;
	private Button q12;

	private Button clearGeneric;
	private Button clearBoolean;
	private Button clearDate;
	
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
	
	private boolean analyze;
	
	private String local;
	
	private HashMap<Integer, String> analyzeRange;
	
	private ImageView rangeLeft;
	private ImageView rangeRight;
	
	private Button mapAnalyze;
	private Button mapParameter;
	private Button mapClose;
	private Button mapExport;
	private Button mapSummary;
	
	private Button doRecord;
	private Button clearRecord;
	
    private String mGeneratedName;
    
    private MediaRecorder mRecorder;
    
    private File mRecordingLocation;

    private boolean mRecording;

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
	
	private EditText exportFile;
	
	private TextView dpi;
		
	private boolean[] init;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ep = getSharedPreferences("Settings", 0);
		
        // Enforce internal language change
        local = "en";
        
        switch (ep.getInt("languages", 0))
        {
    	    case 0:
    	    	local = "en";
    	    	break;
    	    	
    	    case 1:
    	    	local = "es";
    	    	break;

    	    case 2:
    	    	local = "de";
    	    	break;
    	    	
        }
        
        Locale locale2 = new Locale(local);  
        Locale.setDefault(locale2); 
        Configuration config2 = new Configuration(); 
        config2.locale = locale2; 
        getBaseContext().getResources().updateConfiguration(config2, getBaseContext().getResources()
        .getDisplayMetrics()); 

        loadScreen();
        
		// If the user hasn't configured range and traits, open settings screen
		if (!ep.getBoolean("ImportFieldFinished", false) | !ep.getBoolean("CreateTraitFinished", false))
		{
			dt.copyFileOrDir(mPath.getAbsolutePath(), "field_import");
			dt.copyFileOrDir(mPath.getAbsolutePath(), "resources");
			dt.copyFileOrDir(mPath.getAbsolutePath(), "trait");		

			Intent intent = new Intent();
			intent.setClassName(MainActivity.this,
					ConfigActivity.class.getName());
			startActivity(intent);										
		}
        
	}

	private void loadScreen()
	{
		setContentView(R.layout.main);
		
		// If the app is just starting up, we must always allow refreshing of
		// data onscreen
		reloadData = true;

		lock = new Object();
		
		//requestWindowFeature(Window.FEATURE_NO_TITLE);

		float brightness = 100;

		// Set brightness to maximum
		Settings.System.putFloat(this.getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS, brightness);

		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.screenBrightness = brightness;
		getWindow().setAttributes(lp);
		        		
		dt = new DataHelper(this);

		thisActivity = this;

		// Keyboard service manager
		imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		createDirs();
		
		audio = (LinearLayout) findViewById(R.id.audio);
				
		range = (EditText) findViewById(R.id.range);
		plot = (EditText) findViewById(R.id.plot);

		drop3 = (AutoResizeTextView) findViewById(R.id.drop3);
		drop2 = (AutoResizeTextView) findViewById(R.id.drop2);
		drop1 = (AutoResizeTextView) findViewById(R.id.drop1);

		drop3prefix = (TextView) findViewById(R.id.drop3prefix);
		drop2prefix = (TextView) findViewById(R.id.drop2prefix);
		drop1prefix = (TextView) findViewById(R.id.drop1prefix);

		dpi = (TextView) findViewById(R.id.dpi);
		
		int pixelSize = getPixelSize();
				
		drop1.setTextSize(pixelSize);
		drop2.setTextSize(pixelSize);
		drop3.setTextSize(pixelSize);

		drop1.setAddEllipsis(true);
		drop2.setAddEllipsis(true);
		drop3.setAddEllipsis(true);
				
		traitType = (Spinner) findViewById(R.id.traitType);
		
		newTraits = new HashMap();

		traitDetails = (TextView) findViewById(R.id.traitDetails);

		range.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
				// do not do bit check on event, crashes keyboard
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					try
					{
						moveRangeTo(rangeID, range.getText().toString(), false);						
						imm.hideSoftInputFromWindow(range.getWindowToken(), 0);						
					}
					catch (Exception e)
					{						
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
					try
					{
						movePlotTo(rangeID, plot.getText().toString(), false);
						imm.hideSoftInputFromWindow(plot.getWindowToken(), 0);												
					}
					catch (Exception e)
					{						
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

        doRecord = (Button) findViewById(R.id.record);
        doRecord.setOnClickListener(this);

        clearRecord = (Button) findViewById(R.id.clearRecord);
        clearRecord.setOnClickListener(this);
        
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

			public void afterTextChanged(Editable en) {
				try {
					int val = Integer.parseInt(eNum.getText().toString());

					if (currentTrait.minimum.length() > 0)
					{
						if (val < Integer.parseInt(currentTrait.minimum)) {
							Toast.makeText(
									MainActivity.this,
									getString(R.string.valueless) + " "
											+ currentTrait.minimum,
									Toast.LENGTH_LONG).show();
	
							en.clear();
							removeTrait(currentTrait.trait);
							return;
						}
					}
					
					if (currentTrait.maximum.length() > 0)
					{
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
				}
				else {
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

				if (en.toString().length() > 0) {
					if (newTraits != null & currentTrait != null)
						updateTrait(currentTrait.trait, "text", en.toString());
				}
				else {
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
		
		//tNum.addTextChangedListener(tNumUpdate);

		// Progress bar
		seekBar = (SeekBar) findViewById(R.id.seekbar);
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
		
		
		eNum.setOnTouchListener(new OnTouchListener(){

			public boolean onTouch(View arg0, MotionEvent arg1) {
				return true;
			}});
		
		// Updates the progressbar value on screen and in memory hashmap
		seekBar.setOnSeekBarChangeListener(seekListener);

		// Datapicker is an area onscreen representing the date controls (month,
		// year, plus / minus buttons, save, clear)
		datePicker = (LinearLayout) findViewById(R.id.datePick);

		kb = (LinearLayout) findViewById(R.id.kb);
		
		month = (TextView) findViewById(R.id.mth);
		day = (TextView) findViewById(R.id.day);

		rangeName = (TextView) findViewById(R.id.rangeName);
		plotName = (TextView) findViewById(R.id.plotName);
		
		clearGeneric = (Button) findViewById(R.id.clearBtn4);
		clearBoolean = (Button) findViewById(R.id.clearBtn5);
		clearDate = (Button) findViewById(R.id.clearDateBtn);
		
		Button addDayBtn = (Button) findViewById(R.id.addDateBtn);
		Button minusDayBtn = (Button) findViewById(R.id.minusDateBtn);
		Button saveDayBtn = (Button) findViewById(R.id.enterBtn);
		//Button clearBtn = (Button) findViewById(R.id.clearBtn);
		Button clearBtn2 = (Button) findViewById(R.id.clearBtn2);

		Button k1 = (Button) findViewById(R.id.k1);
		Button k2 = (Button) findViewById(R.id.k2);
		Button k3 = (Button) findViewById(R.id.k3);
		Button k4 = (Button) findViewById(R.id.k4);
		Button k5 = (Button) findViewById(R.id.k5);
		Button k6 = (Button) findViewById(R.id.k6);
		Button k7 = (Button) findViewById(R.id.k7);
		Button k8 = (Button) findViewById(R.id.k8);
		Button k9 = (Button) findViewById(R.id.k9);
		Button k10 = (Button) findViewById(R.id.k10);
		Button k11 = (Button) findViewById(R.id.k11);
		Button k12 = (Button) findViewById(R.id.k12);
		Button k13 = (Button) findViewById(R.id.k13);
		Button k14 = (Button) findViewById(R.id.k14);
		Button k15 = (Button) findViewById(R.id.k15);
		Button k16 = (Button) findViewById(R.id.k16);
		
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
		
		rangeName.setText(ep.getString("ImportFirstName", getString(R.string.range)) + ":");
		plotName.setText(ep.getString("ImportSecondName", getString(R.string.plot)) + ":");
		
		// Clear function for text, numeric and percent
		clearGeneric.setOnClickListener(new OnClickListener()
		{

			public void onClick(View arg0) 
			{
				if (currentTrait.format.equals("text"))
				{
					tNum.removeTextChangedListener(tNumUpdate);					
					tNum.setText("");
					
					removeTrait(currentTrait.trait);
					
					tNum.addTextChangedListener(tNumUpdate);
				}
				else
					if (currentTrait.format.equals("numeric"))
					{
						eNum.removeTextChangedListener(eNumUpdate);
						eNum.setText("");

						removeTrait(currentTrait.trait);
						
						if (currentTrait.defaultValue != null
								&& currentTrait.defaultValue.length() > 0)
							eNum.setText(currentTrait.defaultValue);
						
						eNum.addTextChangedListener(eNumUpdate);
					}
					else
						if (currentTrait.format.equals("percent"))
						{
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
		clearDate.setOnClickListener(new OnClickListener()
		{

			public void onClick(View arg0) 
			{
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
				}
				else
				{
					//This is used to persist moving between months
					tempMonth = c.get(Calendar.MONTH);
					
					month.setText(getMonthForInt(c.get(Calendar.MONTH)));
					day.setText(String.valueOf(c
							.get(Calendar.DAY_OF_MONTH)));
				}

			}
		});
		
		// Clear function for boolean
		clearBoolean.setOnClickListener(new OnClickListener()
		{

			public void onClick(View arg0) 
			{
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

				if (i + 1 > max)
				{
					tempMonth += 1;
					
					if (tempMonth > 11)
						tempMonth = 0;
					
					day.setText("1");
					month.setText(getMonthForInt(tempMonth));
				}
				else {
					day.setText(String.valueOf(i + 1));
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

				if (i - 1 <= 0)
				{
					tempMonth -= 1;
					
					if (tempMonth <= 0)
						tempMonth = 11;
				
					Calendar calendar = Calendar.getInstance();
					
					calendar.set(Calendar.MONTH, tempMonth);
					
					int max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
					
					day.setText(String.valueOf(max));
					month.setText(getMonthForInt(tempMonth));
				}
				else {
					day.setText(String.valueOf(i - 1));
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

		// Datapicker is an area onscreen representing qualitative controls
		qPicker = (LinearLayout) findViewById(R.id.qPick);

		q1 = (Button) findViewById(R.id.q1);
		q2 = (Button) findViewById(R.id.q2);
		q3 = (Button) findViewById(R.id.q3);
		q4 = (Button) findViewById(R.id.q4);
		q5 = (Button) findViewById(R.id.q5);
		q6 = (Button) findViewById(R.id.q6);
		q7 = (Button) findViewById(R.id.q7);
		q8 = (Button) findViewById(R.id.q8);
		q9 = (Button) findViewById(R.id.q9);
		q10 = (Button) findViewById(R.id.q10);
		q11 = (Button) findViewById(R.id.q11);
		q12 = (Button) findViewById(R.id.q12);

		// Clear button for qualitative
		clearBtn2.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				newTraits.remove(currentTrait.trait);
				dt.deleteTrait(cRange.plot_id, currentTrait.trait);

				q1.setTextColor(Color.BLACK);
				q2.setTextColor(Color.BLACK);
				q3.setTextColor(Color.BLACK);
				q4.setTextColor(Color.BLACK);
				q5.setTextColor(Color.BLACK);
				q6.setTextColor(Color.BLACK);
				q7.setTextColor(Color.BLACK);
				q8.setTextColor(Color.BLACK);
				q9.setTextColor(Color.BLACK);
				q10.setTextColor(Color.BLACK);
				q11.setTextColor(Color.BLACK);
				q12.setTextColor(Color.BLACK);

				q1.setBackgroundResource(android.R.drawable.btn_default);
				q2.setBackgroundResource(android.R.drawable.btn_default);
				q3.setBackgroundResource(android.R.drawable.btn_default);
				q4.setBackgroundResource(android.R.drawable.btn_default);
				q5.setBackgroundResource(android.R.drawable.btn_default);
				q6.setBackgroundResource(android.R.drawable.btn_default);
				q7.setBackgroundResource(android.R.drawable.btn_default);
				q8.setBackgroundResource(android.R.drawable.btn_default);
				q9.setBackgroundResource(android.R.drawable.btn_default);
				q10.setBackgroundResource(android.R.drawable.btn_default);
				q11.setBackgroundResource(android.R.drawable.btn_default);
				q12.setBackgroundResource(android.R.drawable.btn_default);
				
			}
		});

		// Functions to clear all other color except this button's
		q1.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				updateTrait(currentTrait.trait, "qualitative", q1.getText().toString());
				q1.setTextColor(Color.parseColor(displayColor));
				q2.setTextColor(Color.BLACK);
				q3.setTextColor(Color.BLACK);
				q4.setTextColor(Color.BLACK);
				q5.setTextColor(Color.BLACK);
				q6.setTextColor(Color.BLACK);
				q7.setTextColor(Color.BLACK);
				q8.setTextColor(Color.BLACK);
				q9.setTextColor(Color.BLACK);
				q10.setTextColor(Color.BLACK);
				q11.setTextColor(Color.BLACK);
				q12.setTextColor(Color.BLACK);
				
				q1.setBackgroundResource(R.drawable.btn_default);
				q2.setBackgroundResource(android.R.drawable.btn_default);
				q3.setBackgroundResource(android.R.drawable.btn_default);
				q4.setBackgroundResource(android.R.drawable.btn_default);
				q5.setBackgroundResource(android.R.drawable.btn_default);
				q6.setBackgroundResource(android.R.drawable.btn_default);
				q7.setBackgroundResource(android.R.drawable.btn_default);
				q8.setBackgroundResource(android.R.drawable.btn_default);
				q9.setBackgroundResource(android.R.drawable.btn_default);
				q10.setBackgroundResource(android.R.drawable.btn_default);
				q11.setBackgroundResource(android.R.drawable.btn_default);
				q12.setBackgroundResource(android.R.drawable.btn_default);
			}
		});

		// Functions to clear all other color except this button's
		q2.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				updateTrait(currentTrait.trait, "qualitative", q2.getText().toString());
				q1.setTextColor(Color.BLACK);
				q2.setTextColor(Color.parseColor(displayColor));
				q3.setTextColor(Color.BLACK);
				q4.setTextColor(Color.BLACK);
				q5.setTextColor(Color.BLACK);
				q6.setTextColor(Color.BLACK);
				q7.setTextColor(Color.BLACK);
				q8.setTextColor(Color.BLACK);
				q9.setTextColor(Color.BLACK);
				q10.setTextColor(Color.BLACK);
				q11.setTextColor(Color.BLACK);
				q12.setTextColor(Color.BLACK);
				
				q2.setBackgroundResource(R.drawable.btn_default);
				q1.setBackgroundResource(android.R.drawable.btn_default);
				q3.setBackgroundResource(android.R.drawable.btn_default);
				q4.setBackgroundResource(android.R.drawable.btn_default);
				q5.setBackgroundResource(android.R.drawable.btn_default);
				q6.setBackgroundResource(android.R.drawable.btn_default);
				q7.setBackgroundResource(android.R.drawable.btn_default);
				q8.setBackgroundResource(android.R.drawable.btn_default);
				q9.setBackgroundResource(android.R.drawable.btn_default);
				q10.setBackgroundResource(android.R.drawable.btn_default);
				q11.setBackgroundResource(android.R.drawable.btn_default);
				q12.setBackgroundResource(android.R.drawable.btn_default);
			}
		});

		// Functions to clear all other color except this button's
		q3.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				updateTrait(currentTrait.trait, "qualitative", q3.getText().toString());
				q1.setTextColor(Color.BLACK);
				q2.setTextColor(Color.BLACK);
				q3.setTextColor(Color.parseColor(displayColor));
				q4.setTextColor(Color.BLACK);
				q5.setTextColor(Color.BLACK);
				q6.setTextColor(Color.BLACK);
				q7.setTextColor(Color.BLACK);
				q8.setTextColor(Color.BLACK);
				q9.setTextColor(Color.BLACK);
				q10.setTextColor(Color.BLACK);
				q11.setTextColor(Color.BLACK);
				q12.setTextColor(Color.BLACK);
				
				q3.setBackgroundResource(R.drawable.btn_default);
				q2.setBackgroundResource(android.R.drawable.btn_default);
				q1.setBackgroundResource(android.R.drawable.btn_default);
				q4.setBackgroundResource(android.R.drawable.btn_default);
				q5.setBackgroundResource(android.R.drawable.btn_default);
				q6.setBackgroundResource(android.R.drawable.btn_default);
				q7.setBackgroundResource(android.R.drawable.btn_default);
				q8.setBackgroundResource(android.R.drawable.btn_default);
				q9.setBackgroundResource(android.R.drawable.btn_default);
				q10.setBackgroundResource(android.R.drawable.btn_default);
				q11.setBackgroundResource(android.R.drawable.btn_default);
				q12.setBackgroundResource(android.R.drawable.btn_default);
			}
		});

		// Functions to clear all other color except this button's
		q4.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				updateTrait(currentTrait.trait, "qualitative", q4.getText().toString());
				q1.setTextColor(Color.BLACK);
				q2.setTextColor(Color.BLACK);
				q3.setTextColor(Color.BLACK);
				q4.setTextColor(Color.parseColor(displayColor));
				q5.setTextColor(Color.BLACK);
				q6.setTextColor(Color.BLACK);
				q7.setTextColor(Color.BLACK);
				q8.setTextColor(Color.BLACK);
				q9.setTextColor(Color.BLACK);
				q10.setTextColor(Color.BLACK);
				q11.setTextColor(Color.BLACK);
				q12.setTextColor(Color.BLACK);
				
				q4.setBackgroundResource(R.drawable.btn_default);
				q2.setBackgroundResource(android.R.drawable.btn_default);
				q3.setBackgroundResource(android.R.drawable.btn_default);
				q1.setBackgroundResource(android.R.drawable.btn_default);
				q5.setBackgroundResource(android.R.drawable.btn_default);
				q6.setBackgroundResource(android.R.drawable.btn_default);
				q7.setBackgroundResource(android.R.drawable.btn_default);
				q8.setBackgroundResource(android.R.drawable.btn_default);
				q9.setBackgroundResource(android.R.drawable.btn_default);
				q10.setBackgroundResource(android.R.drawable.btn_default);
				q11.setBackgroundResource(android.R.drawable.btn_default);
				q12.setBackgroundResource(android.R.drawable.btn_default);
			}
		});

		// Functions to clear all other color except this button's
		q5.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				updateTrait(currentTrait.trait, "qualitative", q5.getText().toString());
				q1.setTextColor(Color.BLACK);
				q2.setTextColor(Color.BLACK);
				q3.setTextColor(Color.BLACK);
				q4.setTextColor(Color.BLACK);
				q5.setTextColor(Color.parseColor(displayColor));
				q6.setTextColor(Color.BLACK);
				q7.setTextColor(Color.BLACK);
				q8.setTextColor(Color.BLACK);
				q9.setTextColor(Color.BLACK);
				q10.setTextColor(Color.BLACK);
				q11.setTextColor(Color.BLACK);
				q12.setTextColor(Color.BLACK);
				
				q5.setBackgroundResource(R.drawable.btn_default);
				q2.setBackgroundResource(android.R.drawable.btn_default);
				q3.setBackgroundResource(android.R.drawable.btn_default);
				q4.setBackgroundResource(android.R.drawable.btn_default);
				q1.setBackgroundResource(android.R.drawable.btn_default);
				q6.setBackgroundResource(android.R.drawable.btn_default);
				q7.setBackgroundResource(android.R.drawable.btn_default);
				q8.setBackgroundResource(android.R.drawable.btn_default);
				q9.setBackgroundResource(android.R.drawable.btn_default);
				q10.setBackgroundResource(android.R.drawable.btn_default);
				q11.setBackgroundResource(android.R.drawable.btn_default);
				q12.setBackgroundResource(android.R.drawable.btn_default);
			}
		});

		// Functions to clear all other color except this button's
		q6.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				updateTrait(currentTrait.trait, "qualitative", q6.getText().toString());
				q1.setTextColor(Color.BLACK);
				q2.setTextColor(Color.BLACK);
				q3.setTextColor(Color.BLACK);
				q4.setTextColor(Color.BLACK);
				q5.setTextColor(Color.BLACK);
				q6.setTextColor(Color.parseColor(displayColor));
				q7.setTextColor(Color.BLACK);
				q8.setTextColor(Color.BLACK);
				q9.setTextColor(Color.BLACK);
				q10.setTextColor(Color.BLACK);
				q11.setTextColor(Color.BLACK);
				q12.setTextColor(Color.BLACK);
				
				q6.setBackgroundResource(R.drawable.btn_default);
				q2.setBackgroundResource(android.R.drawable.btn_default);
				q3.setBackgroundResource(android.R.drawable.btn_default);
				q4.setBackgroundResource(android.R.drawable.btn_default);
				q5.setBackgroundResource(android.R.drawable.btn_default);
				q1.setBackgroundResource(android.R.drawable.btn_default);
				q7.setBackgroundResource(android.R.drawable.btn_default);
				q8.setBackgroundResource(android.R.drawable.btn_default);
				q9.setBackgroundResource(android.R.drawable.btn_default);
				q10.setBackgroundResource(android.R.drawable.btn_default);
				q11.setBackgroundResource(android.R.drawable.btn_default);
				q12.setBackgroundResource(android.R.drawable.btn_default);
			}
		});

		// Functions to clear all other color except this button's
		q7.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				updateTrait(currentTrait.trait, "qualitative", q7.getText().toString());
				q1.setTextColor(Color.BLACK);
				q2.setTextColor(Color.BLACK);
				q3.setTextColor(Color.BLACK);
				q4.setTextColor(Color.BLACK);
				q5.setTextColor(Color.BLACK);
				q6.setTextColor(Color.BLACK);
				q7.setTextColor(Color.parseColor(displayColor));
				q8.setTextColor(Color.BLACK);
				q9.setTextColor(Color.BLACK);
				q10.setTextColor(Color.BLACK);
				q11.setTextColor(Color.BLACK);
				q12.setTextColor(Color.BLACK);
				
				q7.setBackgroundResource(R.drawable.btn_default);
				q2.setBackgroundResource(android.R.drawable.btn_default);
				q3.setBackgroundResource(android.R.drawable.btn_default);
				q4.setBackgroundResource(android.R.drawable.btn_default);
				q5.setBackgroundResource(android.R.drawable.btn_default);
				q6.setBackgroundResource(android.R.drawable.btn_default);
				q1.setBackgroundResource(android.R.drawable.btn_default);
				q8.setBackgroundResource(android.R.drawable.btn_default);
				q9.setBackgroundResource(android.R.drawable.btn_default);
				q10.setBackgroundResource(android.R.drawable.btn_default);
				q11.setBackgroundResource(android.R.drawable.btn_default);
				q12.setBackgroundResource(android.R.drawable.btn_default);
			}
		});

		// Functions to clear all other color except this button's
		q8.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				updateTrait(currentTrait.trait, "qualitative", q8.getText().toString());
				q1.setTextColor(Color.BLACK);
				q2.setTextColor(Color.BLACK);
				q3.setTextColor(Color.BLACK);
				q4.setTextColor(Color.BLACK);
				q5.setTextColor(Color.BLACK);
				q6.setTextColor(Color.BLACK);
				q7.setTextColor(Color.BLACK);
				q8.setTextColor(Color.parseColor(displayColor));
				q9.setTextColor(Color.BLACK);
				q10.setTextColor(Color.BLACK);
				q11.setTextColor(Color.BLACK);
				q12.setTextColor(Color.BLACK);
				
				q8.setBackgroundResource(R.drawable.btn_default);
				q2.setBackgroundResource(android.R.drawable.btn_default);
				q3.setBackgroundResource(android.R.drawable.btn_default);
				q4.setBackgroundResource(android.R.drawable.btn_default);
				q5.setBackgroundResource(android.R.drawable.btn_default);
				q6.setBackgroundResource(android.R.drawable.btn_default);
				q7.setBackgroundResource(android.R.drawable.btn_default);
				q1.setBackgroundResource(android.R.drawable.btn_default);
				q9.setBackgroundResource(android.R.drawable.btn_default);
				q10.setBackgroundResource(android.R.drawable.btn_default);
				q11.setBackgroundResource(android.R.drawable.btn_default);
				q12.setBackgroundResource(android.R.drawable.btn_default);
			}
		});

		// Functions to clear all other color except this button's
		q9.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				updateTrait(currentTrait.trait, "qualitative", q9.getText().toString());
				q1.setTextColor(Color.BLACK);
				q2.setTextColor(Color.BLACK);
				q3.setTextColor(Color.BLACK);
				q4.setTextColor(Color.BLACK);
				q5.setTextColor(Color.BLACK);
				q6.setTextColor(Color.BLACK);
				q7.setTextColor(Color.BLACK);
				q8.setTextColor(Color.BLACK);
				q9.setTextColor(Color.parseColor(displayColor));
				q10.setTextColor(Color.BLACK);
				q11.setTextColor(Color.BLACK);
				q12.setTextColor(Color.BLACK);
				
				q9.setBackgroundResource(R.drawable.btn_default);
				q2.setBackgroundResource(android.R.drawable.btn_default);
				q3.setBackgroundResource(android.R.drawable.btn_default);
				q4.setBackgroundResource(android.R.drawable.btn_default);
				q5.setBackgroundResource(android.R.drawable.btn_default);
				q6.setBackgroundResource(android.R.drawable.btn_default);
				q7.setBackgroundResource(android.R.drawable.btn_default);
				q8.setBackgroundResource(android.R.drawable.btn_default);
				q1.setBackgroundResource(android.R.drawable.btn_default);
				q10.setBackgroundResource(android.R.drawable.btn_default);
				q11.setBackgroundResource(android.R.drawable.btn_default);
				q12.setBackgroundResource(android.R.drawable.btn_default);
			}
		});

		// Functions to clear all other color except this button's
		q10.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				updateTrait(currentTrait.trait, "qualitative", q10.getText().toString());
				q1.setTextColor(Color.BLACK);
				q2.setTextColor(Color.BLACK);
				q3.setTextColor(Color.BLACK);
				q4.setTextColor(Color.BLACK);
				q5.setTextColor(Color.BLACK);
				q6.setTextColor(Color.BLACK);
				q7.setTextColor(Color.BLACK);
				q8.setTextColor(Color.BLACK);
				q9.setTextColor(Color.BLACK);
				q10.setTextColor(Color.parseColor(displayColor));
				q11.setTextColor(Color.BLACK);
				q12.setTextColor(Color.BLACK);
				
				q10.setBackgroundResource(R.drawable.btn_default);
				q2.setBackgroundResource(android.R.drawable.btn_default);
				q3.setBackgroundResource(android.R.drawable.btn_default);
				q4.setBackgroundResource(android.R.drawable.btn_default);
				q5.setBackgroundResource(android.R.drawable.btn_default);
				q6.setBackgroundResource(android.R.drawable.btn_default);
				q7.setBackgroundResource(android.R.drawable.btn_default);
				q8.setBackgroundResource(android.R.drawable.btn_default);
				q9.setBackgroundResource(android.R.drawable.btn_default);
				q1.setBackgroundResource(android.R.drawable.btn_default);
				q11.setBackgroundResource(android.R.drawable.btn_default);
				q12.setBackgroundResource(android.R.drawable.btn_default);
			}
		});

		// Functions to clear all other color except this button's
		q11.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				updateTrait(currentTrait.trait, "qualitative", q11.getText().toString());
				q1.setTextColor(Color.BLACK);
				q2.setTextColor(Color.BLACK);
				q3.setTextColor(Color.BLACK);
				q4.setTextColor(Color.BLACK);
				q5.setTextColor(Color.BLACK);
				q6.setTextColor(Color.BLACK);
				q7.setTextColor(Color.BLACK);
				q8.setTextColor(Color.BLACK);
				q9.setTextColor(Color.BLACK);
				q10.setTextColor(Color.BLACK);
				q11.setTextColor(Color.parseColor(displayColor));
				q12.setTextColor(Color.BLACK);
				
				q11.setBackgroundResource(R.drawable.btn_default);
				q2.setBackgroundResource(android.R.drawable.btn_default);
				q3.setBackgroundResource(android.R.drawable.btn_default);
				q4.setBackgroundResource(android.R.drawable.btn_default);
				q5.setBackgroundResource(android.R.drawable.btn_default);
				q6.setBackgroundResource(android.R.drawable.btn_default);
				q7.setBackgroundResource(android.R.drawable.btn_default);
				q8.setBackgroundResource(android.R.drawable.btn_default);
				q9.setBackgroundResource(android.R.drawable.btn_default);
				q10.setBackgroundResource(android.R.drawable.btn_default);
				q1.setBackgroundResource(android.R.drawable.btn_default);
				q12.setBackgroundResource(android.R.drawable.btn_default);
			}
		});

		// Functions to clear all other color except this button's
		q12.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				updateTrait(currentTrait.trait, "qualitative", q12.getText().toString());
				q1.setTextColor(Color.BLACK);
				q2.setTextColor(Color.BLACK);
				q3.setTextColor(Color.BLACK);
				q4.setTextColor(Color.BLACK);
				q5.setTextColor(Color.BLACK);
				q6.setTextColor(Color.BLACK);
				q7.setTextColor(Color.BLACK);
				q8.setTextColor(Color.BLACK);
				q9.setTextColor(Color.BLACK);
				q10.setTextColor(Color.BLACK);
				q11.setTextColor(Color.BLACK);
				q12.setTextColor(Color.parseColor(displayColor));
				
				q12.setBackgroundResource(R.drawable.btn_default);
				q2.setBackgroundResource(android.R.drawable.btn_default);
				q3.setBackgroundResource(android.R.drawable.btn_default);
				q4.setBackgroundResource(android.R.drawable.btn_default);
				q5.setBackgroundResource(android.R.drawable.btn_default);
				q6.setBackgroundResource(android.R.drawable.btn_default);
				q7.setBackgroundResource(android.R.drawable.btn_default);
				q8.setBackgroundResource(android.R.drawable.btn_default);
				q9.setBackgroundResource(android.R.drawable.btn_default);
				q10.setBackgroundResource(android.R.drawable.btn_default);
				q11.setBackgroundResource(android.R.drawable.btn_default);
				q1.setBackgroundResource(android.R.drawable.btn_default);
			}
		});

		eImg = (ImageView) findViewById(R.id.eImg);

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

		rangeLeft.setOnTouchListener(new OnTouchListener(){

			public boolean onTouch(View v, MotionEvent event) {
				
				switch (event.getAction()) {
				
	                case MotionEvent.ACTION_DOWN:
	                	rangeLeft.setImageResource(R.drawable.ml_arrows);
	                	rangeLeft.performClick();
	                    // Make this a repeating button, using MessageHandler
	                    Message msg = new Message();
	                    msg.what = MESSAGE_CHECK_BTN_STILL_PRESSED;
	                    msg.arg1 = R.id.rangeLeft;
	                    msg.arg2 = 200; // this btn's repeat time in ms
	                    v.setTag(v); // mark btn as pressed (any non-null)
	                    myGuiHandler.sendMessageDelayed(msg, msg.arg2);	                	
	                    break;
	                case MotionEvent.ACTION_MOVE:
	                    break;
	                case MotionEvent.ACTION_UP:
	                	rangeLeft.setImageResource(R.drawable.ml_arrow);
	                	repeatUpdate();
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

					displayRange(cRange);

					newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
							.clone();

					initWidgets(true);
				}
			}
		});

		rangeRight.setOnTouchListener(new OnTouchListener(){

			public boolean onTouch(View v, MotionEvent event) {
				
				switch (event.getAction()) {
				
	                case MotionEvent.ACTION_DOWN:
	                	rangeRight.setImageResource(R.drawable.mr_arrows);
	                	rangeRight.performClick();
	                    // Make this a repeating button, using MessageHandler
	                    Message msg = new Message();
	                    msg.what = MESSAGE_CHECK_BTN_STILL_PRESSED;
	                    msg.arg1 = R.id.rangeRight;
	                    msg.arg2 = 200; // this btn's repeat time in ms
	                    v.setTag(v); // mark btn as pressed (any non-null)
	                    myGuiHandler.sendMessageDelayed(msg, msg.arg2);	                	
	                    break;
	                case MotionEvent.ACTION_MOVE:
	                    break;
	                case MotionEvent.ACTION_UP:
	                	rangeRight.setImageResource(R.drawable.mr_arrow);
	                	repeatUpdate();
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
				if (rangeID != null && rangeID.length > 0) {
					//index.setEnabled(true);

					// If ignore existing data is enabled, then skip accordingly
					if (ep.getBoolean("IgnoreExisting", false)) {
						int pos = paging;

						if (pos == rangeID.length)
						{
							pos = 1;
							return;
						}
						
						while (pos <= rangeID.length) {
							pos += 1;

							if (pos > rangeID.length)
							{
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

					displayRange(cRange);

					newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
							.clone();

					initWidgets(true);
				}
			}
		});

		traitLeft = (ImageView) findViewById(R.id.traitLeft);
		traitRight = (ImageView) findViewById(R.id.traitRight);

		traitLeft.setOnTouchListener(new OnTouchListener(){

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

				if (pos < 0)
				{
					pos = traitType.getCount() - 1;
					
					if (ep.getBoolean("CycleTraits", false))
						rangeLeft.performClick();
				}

				traitType.setSelection(pos);
			}
		});

		traitRight.setOnTouchListener(new OnTouchListener(){

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

				if (pos > traitType.getCount() - 1)
				{
					pos = 0;
					
					if (ep.getBoolean("CycleTraits", false))
						rangeRight.performClick();
				}

				traitType.setSelection(pos);
			}
		});
	}
	
	// Auto Sizing TextView defaults change with resolution
	// The reported resolution and the layout the device picks mismatch on some devices
	// So what we do is embed the sizing we want into the layout file itself
	// And follow the layout instead of using screenMetrics.density
	private int getPixelSize()
	{
		if (dpi.getText().toString().equals("high"))
			return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 30, 
			getResources().getDisplayMetrics());
		else
			return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 16, 
			getResources().getDisplayMetrics());
	}
	 
	// Create all necessary directories and subdirectories	
	private void createDirs()
	{		
		createDir(mPath.getAbsolutePath());
		createDir(resourcePath);
		createDir(audioPath);
		createDir(traitPath);
		createDir(fieldImportPath);
		createDir(fieldExportPath);				
	}
	
	// Helper function to create a single directory
	private void createDir(String path)
	{
		File dir = new File(path);
		
		if (!dir.exists())
			dir.mkdirs();		
	}
	
	// Simulate range left key press
	private void repeatLeft()
	{
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

			if (cRange.plot_id.length() == 0)
				return;
			
			displayRange(cRange);
			
		}		
	}
	
	// Simulate range right key press
	private void repeatRight()
	{
		if (rangeID != null && rangeID.length > 0) {
			//index.setEnabled(true);

			// If ignore existing data is enabled, then skip accordingly
			if (ep.getBoolean("IgnoreExisting", false)) {
				int pos = paging;

				if (pos == rangeID.length)
				{
					pos = 1;
					return;
				}
				
				while (pos <= rangeID.length) {
					pos += 1;

					if (pos > rangeID.length)
					{
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

			if (cRange.plot_id.length() == 0)
				return;
			
			displayRange(cRange);
			
		}		
	}
	
	// This update should only be called after repeating keypress ends
	private void repeatUpdate()
	{
		if (rangeID == null)
			return;
		
		newTraits = (HashMap) dt.getUserDetail(cRange.plot_id)
				.clone();

		initWidgets(true);
		
	}
	
	// For space that appears on screen, but isn't part of the grid we're drawing
	// Think of it as the background
	private ImageView createBoundarySpace()
	{
		ImageView image; 
		image = new ImageView(MainActivity.this); 
		image.setImageResource(R.drawable.emptysquare);
		
		return image;
	}
	
	// Create a square on grid with specific color
	private ImageView createSquare(final int id, final String mapColor)
	{
		final ImageView image; 
		image = new ImageView(MainActivity.this); 
				
		image.setImageResource(R.drawable.nosquare);
		
		image.setBackgroundColor(Color.parseColor(mapColor));
		image.setTag(id + "-" + mapColor);
		
		image.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				
				try
				{
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
					if (prevCell != null)
					{
						String[] d2 = prevCell.getTag().toString().split("-");
						prevCell.setBackgroundColor(Color.parseColor(d2[1]));
						prevCell.setImageResource(R.drawable.nosquare);
					}
					
					prevCell = (ImageView) v;
					prevCell.setTag(v.getTag().toString());
					
					//((ImageView) v).setBackgroundColor(Color.GREEN);
					((ImageView) v).setImageResource(R.drawable.selectedsquare);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		});
		
		return image;
	}
	
	// Initialize the toggle states of the map
	// Only used for serpentine
	private void initMap(boolean start, int length)
	{
		init = new boolean[length];
		
		for (int i = 0; i < length; i++)
		{
			init[i] = start;
			
			start = !start;
		}
	}
	
	// Create data for serpentine map
	private void processSerpentineMap(HashMap<Integer, String> compare, int mapType)
	{
		switch (mapType)
		{
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
	private void processZigZagMap(HashMap<Integer, String> compare, int mapType)
	{
		switch (mapType)
		{
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
	private void loadSerpentineMap(HashMap<Integer, String> compare, int mapType, String mapColor)
	{
		switch (mapType)
		{
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
	private void loadSerpentineMapExport(HashMap<Integer, String> compare, int mapType, int mapColor, Canvas c)
	{
		
		switch (mapType)
		{
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
	private void loadZigZagMap(HashMap<Integer, String> compare, int mapType, String mapColor)
	{
		switch (mapType)
		{
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
	private void loadZigZagMapExport(HashMap<Integer, String> compare, int mapType, int mapColor, Canvas c)
	{
		switch (mapType)
		{
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

		int pixelSize = getPixelSize();
		
		drop1.setTextSize(pixelSize);
		drop2.setTextSize(pixelSize);
		drop3.setTextSize(pixelSize);
		
		if (ep.getString("DROP1", "").length() == 0)			
			drop1prefix.setText(getString(R.string.drop1) + ": ");
		else
			drop1prefix.setText(ep.getString("DROP1", "") + ": ");

		if (ep.getString("DROP2", "").length() == 0)			
			drop2prefix.setText(getString(R.string.drop2) + ": ");
		else
			drop2prefix.setText(ep.getString("DROP2", "") + ": ");

		if (ep.getString("DROP3", "").length() == 0)			
			drop3prefix.setText(getString(R.string.drop3) + ": ");
		else
			drop3prefix.setText(ep.getString("DROP3", "") + ": ");
				
		myList1 = dt.getDropDownRange(ep.getString("DROP1", ""), cRange.plot_id);
		
		myList2 = dt.getDropDownRange(ep.getString("DROP2", ""), cRange.plot_id);

		myList3 = dt.getDropDownRange(ep.getString("DROP3", ""), cRange.plot_id);
		
		if (myList1 == null | ep.getString("DROP1", "").length() == 0) {			
			drop1.setText(getString(R.string.nodata));			
		}
		else
			drop1.setText(myList1[0]);

		if (myList2 == null | ep.getString("DROP2", "").length() == 0) {			
			drop2.setText(getString(R.string.nodata));
		}
		else
			drop2.setText(myList2[0]);

		if (myList3 == null | ep.getString("DROP3", "").length() == 0) {
			drop3.setText(getString(R.string.nodata));			
		}
		else
			drop3.setText(myList3[0]);
		
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
		
		if (ep.getString("DROP1", "").length() == 0)			
			drop1prefix.setText(getString(R.string.drop1) + ": ");
		else
			drop1prefix.setText(ep.getString("DROP1", "") + ": ");

		if (ep.getString("DROP2", "").length() == 0)			
			drop2prefix.setText(getString(R.string.drop2) + ": ");
		else
			drop2prefix.setText(ep.getString("DROP2", "") + ": ");

		if (ep.getString("DROP3", "").length() == 0)			
			drop3prefix.setText(getString(R.string.drop3) + ": ");
		else
			drop3prefix.setText(ep.getString("DROP3", "") + ": ");
		
		myList1 = dt.getDropDownRange(ep.getString("DROP1", ""), cRange.plot_id);
		
		myList2 = dt.getDropDownRange(ep.getString("DROP2", ""), cRange.plot_id);

		myList3 = dt.getDropDownRange(ep.getString("DROP3", ""), cRange.plot_id);
		
		if (myList1 == null | ep.getString("DROP1", "").length() == 0) {			
			drop1.setText(getString(R.string.nodata));
		}
		else
			drop1.setText(myList1[0]);

		if (myList2 == null | ep.getString("DROP2", "").length() == 0) {			
			drop2.setText(getString(R.string.nodata));
		}
		else
			drop2.setText(myList2[0]);

		if (myList3 == null | ep.getString("DROP3", "").length() == 0) {
			drop3.setText(getString(R.string.nodata));			
		}
		else
			drop3.setText(myList3[0]);
		
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
					this, R.layout.smallspinnerlayout, traits);
			directionArrayAdapter
					.setDropDownViewResource(R.layout.smallspinnerlayout2);
			traitType.setAdapter(directionArrayAdapter);

			traitType.setOnItemSelectedListener(new OnItemSelectedListener() {

				public void onItemSelected(AdapterView<?> arg0, View arg1,
						int arg2, long arg3) {

					// This updates the in memory hashmap from database
					currentTrait = dt.getDetail(traitType.getSelectedItem()
							.toString());

					traitDetails.setText(currentTrait.details);

					if (!rangeSuppress | !currentTrait.format.equals("numeric"))
					{
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
						qPicker.setVisibility(LinearLayout.GONE);

						tNum.setVisibility(EditText.VISIBLE);
						tNum.setEnabled(true);

						kb.setVisibility(View.GONE);
						
						clearGeneric.setVisibility(View.VISIBLE);
						clearBoolean.setVisibility(View.GONE);
						
						eNum.setVisibility(EditText.GONE);
						eNum.removeTextChangedListener(eNumUpdate);
						eNum.setEnabled(false);
						
						pNum.setVisibility(EditText.GONE);

						seekBar.setVisibility(EditText.GONE);
						datePicker.setVisibility(LinearLayout.GONE);
						eImg.setVisibility(EditText.GONE);

						audio.setVisibility(EditText.GONE);
						
						if (newTraits.containsKey(currentTrait.trait)) {
							tNum.removeTextChangedListener(tNumUpdate);
							
							tNum.setText(newTraits.get(currentTrait.trait).toString());																					
							
							tNum.setTextColor(Color.parseColor(displayColor));
							
							tNum.addTextChangedListener(tNumUpdate);
														
						} else {							
							tNum.removeTextChangedListener(tNumUpdate);
							
							tNum.setText("");
							tNum.setTextColor(Color.BLACK);

							if (currentTrait.defaultValue != null
									&& currentTrait.defaultValue.length() > 0)
								tNum.setText(currentTrait.defaultValue);
							
							tNum.addTextChangedListener(tNumUpdate);							
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
							}

						}, 300);

					}					
					else if (currentTrait.format.equals("numeric")) {
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

						audio.setVisibility(EditText.GONE);
						
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

						audio.setVisibility(EditText.GONE);
						
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
								}
								else
								{
									if (newTraits.get(currentTrait.trait).toString().equals("0"))
										pNum.setTextColor(Color.BLACK);
									else
										pNum.setTextColor(Color.parseColor(displayColor));									
								}
							}
							else
							{
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
						qPicker.setVisibility(LinearLayout.GONE);

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

						audio.setVisibility(EditText.GONE);
						
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
							}
							else
							{
								//This is used to persist moving between months
								tempMonth = c.get(Calendar.MONTH);
								
								month.setText(getMonthForInt(c.get(Calendar.MONTH)));
								day.setText(String.valueOf(c
										.get(Calendar.DAY_OF_MONTH)));
							}
						}
					} else if (currentTrait.format.equals("qualitative")) {
						qPicker.setVisibility(LinearLayout.VISIBLE);

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

						audio.setVisibility(EditText.GONE);
						
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
								q1.setVisibility(Button.GONE);
								break;
							case 1:
								q2.setVisibility(Button.GONE);
								break;
							case 2:
								q3.setVisibility(Button.GONE);
								break;
							case 3:
								q4.setVisibility(Button.GONE);
								break;
							case 4:
								q5.setVisibility(Button.GONE);
								break;
							case 5:
								q6.setVisibility(Button.GONE);
								break;
							case 6:
								q7.setVisibility(Button.GONE);
								break;
							case 7:
								q8.setVisibility(Button.GONE);
								break;
							case 8:
								q9.setVisibility(Button.GONE);
								break;
							case 9:
								q10.setVisibility(Button.GONE);
								break;
							case 10:
								q11.setVisibility(Button.GONE);
								break;
							case 11:
								q12.setVisibility(Button.GONE);
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
									q1.setVisibility(Button.INVISIBLE);
									break;
								case 1:
									q2.setVisibility(Button.INVISIBLE);
									break;
								case 2:
									q3.setVisibility(Button.INVISIBLE);
									break;
								case 3:
									q4.setVisibility(Button.INVISIBLE);
									break;
								case 4:
									q5.setVisibility(Button.INVISIBLE);
									break;
								case 5:
									q6.setVisibility(Button.INVISIBLE);
									break;
								case 6:
									q7.setVisibility(Button.INVISIBLE);
									break;
								case 7:
									q8.setVisibility(Button.INVISIBLE);
									break;
								case 8:
									q9.setVisibility(Button.INVISIBLE);
									break;
								case 9:
									q10.setVisibility(Button.INVISIBLE);
									break;
								case 10:
									q11.setVisibility(Button.INVISIBLE);
									break;
								case 11:
									q12.setVisibility(Button.INVISIBLE);
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
									q1.setVisibility(Button.VISIBLE);
									q1.setText(cat[i]);
									q1.setTextColor(Color.parseColor(displayColor));
									q1.setBackgroundResource(R.drawable.btn_default);
									break;
								case 1:
									q2.setVisibility(Button.VISIBLE);
									q2.setText(cat[i]);
									q2.setTextColor(Color.parseColor(displayColor));
									q2.setBackgroundResource(R.drawable.btn_default);
									break;
								case 2:
									q3.setVisibility(Button.VISIBLE);
									q3.setText(cat[i]);
									q3.setTextColor(Color.parseColor(displayColor));
									q3.setBackgroundResource(R.drawable.btn_default);
									break;
								case 3:
									q4.setVisibility(Button.VISIBLE);
									q4.setText(cat[i]);
									q4.setTextColor(Color.parseColor(displayColor));
									q4.setBackgroundResource(R.drawable.btn_default);
									break;
								case 4:
									q5.setVisibility(Button.VISIBLE);
									q5.setText(cat[i]);
									q5.setTextColor(Color.parseColor(displayColor));
									q5.setBackgroundResource(R.drawable.btn_default);
									break;
								case 5:
									q6.setVisibility(Button.VISIBLE);
									q6.setText(cat[i]);
									q6.setTextColor(Color.parseColor(displayColor));
									q6.setBackgroundResource(R.drawable.btn_default);
									break;
								case 6:
									q7.setVisibility(Button.VISIBLE);
									q7.setText(cat[i]);
									q7.setTextColor(Color.parseColor(displayColor));
									q7.setBackgroundResource(R.drawable.btn_default);
									break;
								case 7:
									q8.setVisibility(Button.VISIBLE);
									q8.setText(cat[i]);
									q8.setTextColor(Color.parseColor(displayColor));
									q8.setBackgroundResource(R.drawable.btn_default);
									break;
								case 8:
									q9.setVisibility(Button.VISIBLE);
									q9.setText(cat[i]);
									q9.setTextColor(Color.parseColor(displayColor));
									q9.setBackgroundResource(R.drawable.btn_default);
									break;
								case 9:
									q10.setVisibility(Button.VISIBLE);
									q10.setText(cat[i]);
									q10.setTextColor(Color.parseColor(displayColor));
									q10.setBackgroundResource(R.drawable.btn_default);
									break;
								case 10:
									q11.setVisibility(Button.VISIBLE);
									q11.setText(cat[i]);
									q11.setTextColor(Color.parseColor(displayColor));
									q11.setBackgroundResource(R.drawable.btn_default);
									break;
								case 11:
									q12.setVisibility(Button.VISIBLE);
									q12.setText(cat[i]);
									q12.setTextColor(Color.parseColor(displayColor));
									q12.setBackgroundResource(R.drawable.btn_default);
									break;

								}
							} else {
								switch (i) {
								case 0:
									q1.setVisibility(Button.VISIBLE);
									q1.setText(cat[i]);
									q1.setTextColor(Color.BLACK);
									q1.setBackgroundResource(android.R.drawable.btn_default);
									break;
								case 1:
									q2.setVisibility(Button.VISIBLE);
									q2.setText(cat[i]);
									q2.setTextColor(Color.BLACK);
									q2.setBackgroundResource(android.R.drawable.btn_default);
									break;
								case 2:
									q3.setVisibility(Button.VISIBLE);
									q3.setText(cat[i]);
									q3.setTextColor(Color.BLACK);
									q3.setBackgroundResource(android.R.drawable.btn_default);
									break;
								case 3:
									q4.setVisibility(Button.VISIBLE);
									q4.setText(cat[i]);
									q4.setTextColor(Color.BLACK);
									q4.setBackgroundResource(android.R.drawable.btn_default);
									break;
								case 4:
									q5.setVisibility(Button.VISIBLE);
									q5.setText(cat[i]);
									q5.setTextColor(Color.BLACK);
									q5.setBackgroundResource(android.R.drawable.btn_default);
									break;
								case 5:
									q6.setVisibility(Button.VISIBLE);
									q6.setText(cat[i]);
									q6.setTextColor(Color.BLACK);
									q6.setBackgroundResource(android.R.drawable.btn_default);
									break;
								case 6:
									q7.setVisibility(Button.VISIBLE);
									q7.setText(cat[i]);
									q7.setTextColor(Color.BLACK);
									q7.setBackgroundResource(android.R.drawable.btn_default);
									break;
								case 7:
									q8.setVisibility(Button.VISIBLE);
									q8.setText(cat[i]);
									q8.setTextColor(Color.BLACK);
									q8.setBackgroundResource(android.R.drawable.btn_default);
									break;
								case 8:
									q9.setVisibility(Button.VISIBLE);
									q9.setText(cat[i]);
									q9.setTextColor(Color.BLACK);
									q9.setBackgroundResource(android.R.drawable.btn_default);
									break;
								case 9:
									q10.setVisibility(Button.VISIBLE);
									q10.setText(cat[i]);
									q10.setTextColor(Color.BLACK);
									q10.setBackgroundResource(android.R.drawable.btn_default);
									break;
								case 10:
									q11.setVisibility(Button.VISIBLE);
									q11.setText(cat[i]);
									q11.setTextColor(Color.BLACK);
									q11.setBackgroundResource(android.R.drawable.btn_default);
									break;
								case 11:
									q12.setVisibility(Button.VISIBLE);
									q12.setText(cat[i]);
									q12.setTextColor(Color.BLACK);
									q12.setBackgroundResource(android.R.drawable.btn_default);
									break;

								}
							}
						}

					} else if (currentTrait.format.equals("boolean")) {
						qPicker.setVisibility(LinearLayout.GONE);

						kb.setVisibility(View.GONE);
						
						tNum.setVisibility(EditText.GONE);
						tNum.setEnabled(false);
						
						clearGeneric.setVisibility(View.GONE);
						clearBoolean.setVisibility(View.VISIBLE);
						
						pNum.setVisibility(EditText.GONE);
						eNum.setVisibility(EditText.GONE);
						seekBar.setVisibility(EditText.GONE);
						datePicker.setVisibility(LinearLayout.GONE);
						eImg.setVisibility(EditText.VISIBLE);

						audio.setVisibility(EditText.GONE);
						
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
						qPicker.setVisibility(LinearLayout.GONE);

						kb.setVisibility(View.GONE);
						
						tNum.removeTextChangedListener(tNumUpdate);
						
						tNum.setVisibility(EditText.VISIBLE);
						tNum.setEnabled(false);
						
						clearGeneric.setVisibility(View.GONE);
						clearBoolean.setVisibility(View.GONE);
						
						pNum.setVisibility(EditText.GONE);
						eNum.setVisibility(EditText.GONE);
						seekBar.setVisibility(EditText.GONE);
						datePicker.setVisibility(LinearLayout.GONE);
						eImg.setVisibility(EditText.GONE);

						audio.setVisibility(EditText.VISIBLE);
						
		                rangeLeft.setEnabled(true);
		                rangeRight.setEnabled(true);
		                	                
		                traitLeft.setEnabled(true);
		                traitRight.setEnabled(true);
						
						if (!newTraits.containsKey(currentTrait.trait)) {
							doRecord.setText(getString(R.string.record));
							tNum.setText("");
							//tNum.setText(R.string.nodata);
						}
						else
						{
							mRecordingLocation = new File(newTraits.get(currentTrait.trait).toString());

							doRecord.setText(getString(R.string.record));
							tNum.setText(getString(R.string.stored));
						}
						
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
						
						audio.setVisibility(EditText.GONE);
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
        mRecordingLocation = new File(MainActivity.audioPath,
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
				"yyyy.MM.dd_hh_mm_ss", Locale.getDefault());
        
        Calendar c = Calendar.getInstance();
        		
        try
        {
        	mGeneratedName = MainActivity.cRange.plot_id + " " + timeStamp.format(c.getTime());        		        		
        }
        catch (Exception e)
        {
        	mGeneratedName = "error " + timeStamp.format(c.getTime());
        }
        
        setRecordingLocation(mGeneratedName);
        
        mRecorder.setOutputFile(mRecordingLocation.getAbsolutePath());

        try {
            mRecorder.prepare();
        }
        catch (IllegalStateException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
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
    
    // Delete recording
    private void deleteRecording() {
        if (mRecordingLocation != null && mRecordingLocation.exists()) {
            mRecordingLocation.delete();
        }
    }
	
    // Moving to a plot/range will obey the usual settings, such as ignore existing
    // For search results, this is bypassed e.g. always show result regardless
	private void moveTo(int[] rangeID, String range, String plot, boolean alwaysShow)
	{
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
				if (alwaysShow)
				{
					paging = j;

					// Reload traits based on the selected plot
					displayRange(cRange);

					newTraits = (HashMap) dt.getUserDetail(
							cRange.plot_id).clone();

					initWidgets(false);

					haveData = true;

					break;					
				}
				else
				if (ep.getBoolean("IgnoreExisting", false)) {
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
	private void moveRangeTo(int[] rangeID, String range, boolean alwaysShow)
	{
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
				if (alwaysShow)
				{
					paging = j;

					// Reload traits based on the selected plot
					displayRange(cRange);

					newTraits = (HashMap) dt.getUserDetail(
							cRange.plot_id).clone();

					initWidgets(false);

					haveData = true;

					break;					
				}
				else
				if (ep.getBoolean("IgnoreExisting", false)) {
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
	private void movePlotTo(int[] rangeID, String plot, boolean alwaysShow)
	{
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
				if (alwaysShow)
				{
					paging = j;

					// Reload traits based on the selected plot
					displayRange(cRange);

					newTraits = (HashMap) dt.getUserDetail(
							cRange.plot_id).clone();

					initWidgets(false);

					haveData = true;

					break;					
				}
				else
				if (ep.getBoolean("IgnoreExisting", false)) {
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
				
		try
		{
			// Always close tips / hints along with the main activity
			TutorialMainActivity.thisActivity.finish();
		}
		catch (Exception e)
		{
			
		}
		
		// Always close the database connection when the app ends
		dt.close();

		super.onDestroy();
	}

	@Override
	public void onResume() {
		super.onResume();
		
		// Update menu item visibility
		if (systemMenu != null)
		{
			if (ep.getBoolean("EnableMap", false))
			{
				systemMenu.findItem(R.id.map).setVisible(true);
			}
			else
			{
				systemMenu.findItem(R.id.map).setVisible(false);
			}			
			
			if (ep.getBoolean("Tips", false))
			{
				systemMenu.findItem(R.id.help).setVisible(true);
			}
			else
			{
				systemMenu.findItem(R.id.help).setVisible(false);
			}
		}
		
		// This allows dynamic language change without exiting the app
        switch (ep.getInt("languages", 0))
        {
    	    case 0:
    	    	if (!local.equals("en"))
    	    	{
    	    		local = "en";
    	    		
    	            Locale locale2 = new Locale(local);  
    	            Locale.setDefault(locale2); 
    	            Configuration config2 = new Configuration(); 
    	            config2.locale = locale2; 
    	            getBaseContext().getResources().updateConfiguration(config2, 
    	            getBaseContext().getResources().getDisplayMetrics()); 
    	    		
    	            invalidateOptionsMenu();
    	            
    	    		loadScreen();
    	    	}
    	    	break;
    	    	
    	    case 1:
    	    	if (!local.equals("es"))
    	    	{
    	    		local = "es";
    	    		
    	            Locale locale2 = new Locale(local);  
    	            Locale.setDefault(locale2); 
    	            Configuration config2 = new Configuration(); 
    	            config2.locale = locale2; 
    	            getBaseContext().getResources().updateConfiguration(config2, 
    	            getBaseContext().getResources().getDisplayMetrics()); 
    	    		
    	            invalidateOptionsMenu();
    	            
    	    		loadScreen();
    	    	}
    	    	break;

    	    case 2:
    	    	if (!local.equals("de"))
    	    	{
    	    		local = "de";
    	    		
    	            Locale locale2 = new Locale(local);  
    	            Locale.setDefault(locale2); 
    	            Configuration config2 = new Configuration(); 
    	            config2.locale = locale2; 
    	            getBaseContext().getResources().updateConfiguration(config2, 
    	            getBaseContext().getResources().getDisplayMetrics()); 
    	    		
    	            invalidateOptionsMenu();
    	            
    	    		loadScreen();
    	    	}
    	    	break;
    	    	
        }        
				
		// If reload data is true, it means there was an import operation, and
		// the screen
		// should refresh
		if (reloadData) {
			
			reloadData = false;
			partialReload = false;
			
			paging = 1;

			rangeID = dt.getAllRangeID();

			if (rangeID != null) {
				cRange = dt.getRange(rangeID[0]);

				displayRange(cRange);

				newTraits = (HashMap) dt.getUserDetail(cRange.plot_id).clone();
			}

			mapIndex = 0;
			
			initWidgets(false);

			traitType.setSelection(0);
		}
		else
			if (partialReload)
			{
				partialReload = false;
				
				displayRange(cRange);
				
				initWidgets(false);
			}
			else
				if (searchReload)
				{
					searchReload = false;
					
					paging = 1;

					if (rangeID != null) {
						moveTo(rangeID, searchRange, searchPlot, true);
					}
				}
	}

	// Get size of a row in a map 
	private int getMapRangeRowSize()
	{
		if (mapData == null)
			return 0;
		
		int large = 0;
		
		for(int i = 0; i < mapData.length; i++)
		{
			if (mapData[i].plotCount > large)
				large = mapData[i].plotCount;
		}
		
		return large;
	}
	
	// Helper function to draw grid
	// Simply loop through data, and perform action depending on whether it is doing analyze
	// or just map
	private void zigZagGridLeft(HashMap<Integer, String> compare, int start, String mapColor, boolean ignoreSizing)
	{
		map.removeAllViews();
		
		if (mapData == null)
			return;
		
		int max = mapData.length;
						
		int rowSize = getMapRangeRowSize();

		map.setColumnCount(rowSize);
		
		int actualRows = 0;
		
		if (max - start < GRIDSIZE)
		{
			actualRows = max - start; 
		}
		else
		{
			actualRows = GRIDSIZE;
		}
		
		SearchData[] rowData = null;
		
		int segmentValue = max;
		
		if (!ignoreSizing)
			segmentValue = start + actualRows;
		
		for (int i = start; i < segmentValue; i++)
		{
			rowData = dt.getRowForMapPlot(mapData[i].plot, true);
			
			for (int j = 0; j < rowSize; j++)
			{
				if (j < rowData.length)
				{
					if (compare == null)
					{
						map.addView(createSquare(rowData[j].id, "#FFFFFF"));
					}
					else
					{
						if (!analyze)
						{
							if (compare.containsKey(rowData[j].id))								
								map.addView(createSquare(rowData[j].id, mapColor));
							else
								map.addView(createSquare(rowData[j].id, "#FFFFFF"));
						}
						else
						{
							if (compare.containsKey(rowData[j].id))
							{
								if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
									map.addView(createSquare(rowData[j].id, mapColor));
								else
									colorGradient(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric);
							}
							else
							{
								map.addView(createSquare(rowData[j].id, "#FFFFFF"));

							}
							
						}
					}
				}
				else
				{
					map.addView(createBoundarySpace());
				}
			}
			
		}
	}
	
	// Helper function to export grid
	// Simply loop through data, and perform action depending on whether it is doing analyze
	// or just map	
	private void zigZagGridLeftExport(HashMap<Integer, String> compare, int start, int mapColor, Canvas c)
	{
		if (mapData == null)
			return;
		
		int max = mapData.length;
					
		int rowSize = getMapRangeRowSize();
		
		int actualRows = 0;
		
		if (max - start < GRIDSIZE)
		{
			actualRows = max - start; 
		}
		else
		{
			actualRows = GRIDSIZE;
		}
		
		SearchData[] rowData = null;
		
		int segmentValue = max;
				
		for (int i = start; i < segmentValue; i++)
		{
			rowData = dt.getRowForMapPlot(mapData[i].plot, true);
			
			for (int j = 0; j < rowSize; j++)
			{
				if (j < rowData.length)
				{
					if (compare == null)
					{
						exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
					}
					else
					{
						if (!analyze)
						{
							if (compare.containsKey(rowData[j].id))		
								exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, mapColor);
							else
								exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
						}
						else
						{
							if (compare.containsKey(rowData[j].id))
							{
								if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
									exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, mapColor);
								else								
									colorGradientExport(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric, c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE);
							}
							else
							{
								exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
							}
							
						}
					}
				}
				else
				{
					exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
				}
			}
			
		}
	}

	// Helper function to draw grid
	// Simply loop through data, and perform action depending on whether it is doing analyze
	// or just map	
	private void zigZagGridRight(HashMap<Integer, String> compare, int start, String mapColor, boolean ignoreSizing)
	{
		map.removeAllViews();
	
		if (mapData == null)
			return;
		
		int max = mapData.length;
						
		int rowSize = getMapRangeRowSize();

		map.setColumnCount(rowSize);
		
		int actualRows = 0;
		
		if (max - start < GRIDSIZE)
		{
			actualRows = max - start; 
		}
		else
		{
			actualRows = GRIDSIZE;
		}
		
		SearchData[] rowData = null;
		
		int segmentValue = max;
		
		if (!ignoreSizing)
			segmentValue = start + actualRows;
		
		for (int i = start; i < segmentValue; i++)
		{
			rowData = dt.getRowForMapPlot(mapData[i].plot, false);
			
			for (int j = 0; j < rowSize; j++)
			{
				if (j < rowData.length)
				{
					if (compare == null)
					{
						map.addView(createSquare(rowData[j].id, "#FFFFFF"));
					}
					else
					{
						if (!analyze)
						{
							if (compare.containsKey(rowData[j].id))								
								map.addView(createSquare(rowData[j].id, mapColor));
							else
								map.addView(createSquare(rowData[j].id, "#FFFFFF"));
						}
						else
						{
							if (compare.containsKey(rowData[j].id))
							{
								if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
									map.addView(createSquare(rowData[j].id, mapColor));
								else
									colorGradient(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric);
							}
							else
							{
								map.addView(createSquare(rowData[j].id, "#FFFFFF"));

							}
						}
					}
				}
				else
				{
					map.addView(createBoundarySpace());
				}
			}
		}
	}
	
	// Helper function to export grid
	// Simply loop through data, and perform action depending on whether it is doing analyze
	// or just map	
	private void zigZagGridRightExport(HashMap<Integer, String> compare, int start, int mapColor, Canvas c)
	{
		if (mapData == null)
			return;
		
		int max = mapData.length;
					
		int rowSize = getMapRangeRowSize();
		
		int actualRows = 0;
		
		if (max - start < GRIDSIZE)
		{
			actualRows = max - start; 
		}
		else
		{
			actualRows = GRIDSIZE;
		}
		
		SearchData[] rowData = null;
		
		int segmentValue = max;

		for (int i = start; i < segmentValue; i++)
		{
			rowData = dt.getRowForMapPlot(mapData[i].plot, true);
			
			for (int j = 0; j < rowSize; j++)
			{
				if (j < rowData.length)
				{
					if (compare == null)
					{
						exportSquare(c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
					}
					else
					{
						if (!analyze)
						{
							if (compare.containsKey(rowData[j].id))								
								exportSquare(c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, mapColor);
							else
								exportSquare(c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
						}
						else
						{
							if (compare.containsKey(rowData[j].id))
							{
								if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
									exportSquare(c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, mapColor);
								else
									colorGradientExport(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric, c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE);
							}
							else
							{
								exportSquare(c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
							}
						}
					}
				}
				else
				{
					exportSquare(c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
				}
			}
		}
	}	
	
	// Helper function to draw grid
	// Simply loop through data, and perform action depending on whether it is doing analyze
	// or just map	
	private void serpentineGridLeft(HashMap<Integer, String> compare, int start, String mapColor, boolean ignoreSizing)
	{
		map.removeAllViews();
		
		if (mapData == null)
			return;
		
		int max = mapData.length;
								
		int rowSize = getMapRangeRowSize();

		map.setColumnCount(rowSize);
		
		int actualRows = 0;
		
		if (max - start < GRIDSIZE)
		{
			actualRows = max - start; 
		}
		else
		{
			actualRows = GRIDSIZE;
		}
		
		SearchData[] rowData = null;
		
		int segmentValue = max;
		
		if (!ignoreSizing)
			segmentValue = start + actualRows;
		
		for (int i = start; i < segmentValue; i++)
		{
			rowData = dt.getRowForMapPlot(mapData[i].plot, init[i]);
			
			for (int j = 0; j < rowSize; j++)
			{
				if (j < rowData.length)
				{
					if (compare == null)
					{
						map.addView(createSquare(rowData[j].id, "#FFFFFF"));
					}
					else
					{
						if (!analyze)
						{
							if (compare.containsKey(rowData[j].id))								
								map.addView(createSquare(rowData[j].id, mapColor));
							else
								map.addView(createSquare(rowData[j].id, "#FFFFFF"));
						}
						else
						{							
							if (compare.containsKey(rowData[j].id))
							{
								if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
									map.addView(createSquare(rowData[j].id, mapColor));
								else
									colorGradient(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric);
							}
							else
							{
								map.addView(createSquare(rowData[j].id, "#FFFFFF"));

							}
							
						}
					}
				}
				else
				{
					map.addView(createBoundarySpace());
				}
			}
			
		}
	}

	// Helper function to export grid
	// Simply loop through data, and perform action depending on whether it is doing analyze
	// or just map	
	private void serpentineGridLeftExport(HashMap<Integer, String> compare, int start, int mapColor, Canvas c)
	{
		if (mapData == null)
			return;
		
		int max = mapData.length;
						
		int rowSize = getMapRangeRowSize();
		
		int actualRows = 0;
		
		if (max - start < GRIDSIZE)
		{
			actualRows = max - start; 
		}
		else
		{
			actualRows = GRIDSIZE;
		}
		
		SearchData[] rowData = null;
		
		int segmentValue = max;
				
		for (int i = start; i < segmentValue; i++)
		{
			rowData = dt.getRowForMapPlot(mapData[i].plot, init[i]);
			
			for (int j = 0; j < rowSize; j++)
			{
				if (j < rowData.length)
				{
					if (compare == null)
					{
						exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
					}
					else
					{
						if (!analyze)
						{
							if (compare.containsKey(rowData[j].id))		
								exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, mapColor);
							else
								exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
						}
						else
						{
							if (compare.containsKey(rowData[j].id))
							{
								if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
									exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, mapColor);
								else								
									colorGradientExport(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric, c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE);
							}
							else
							{
								exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
							}
							
						}
					}
				}
				else
				{
					exportSquare(c, j * EXPORTGRIDSIZE, i * EXPORTGRIDSIZE, "#FFFFFF");
				}
			}
		}
	}

	// Helper function to export grid
	// Simply loop through data, and perform action depending on whether it is doing analyze
	// or just map	
	private void serpentineGridRightExport(HashMap<Integer, String> compare, int start, int mapColor, Canvas c)
	{
		if (mapData == null)
			return;
		
		int max = mapData.length;
								
		int rowSize = getMapRangeRowSize();
		
		int actualRows = 0;
		
		if (max - start < GRIDSIZE)
		{
			actualRows = max - start; 
		}
		else
		{
			actualRows = GRIDSIZE;
		}
		
		SearchData[] rowData = null;
		
		int segmentValue = max;

		for (int i = start; i < segmentValue; i++)
		{
			rowData = dt.getRowForMapPlot(mapData[i].plot, !init[i]);
			
			for (int j = 0; j < rowSize; j++)
			{
				if (j < rowData.length)
				{
					if (compare == null)
					{
						exportSquare(c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
					}
					else
					{
						if (!analyze)
						{
							if (compare.containsKey(rowData[j].id))								
								exportSquare(c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, mapColor);
							else
								exportSquare(c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
						}
						else
						{
							if (compare.containsKey(rowData[j].id))
							{
								if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
									exportSquare(c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, mapColor);
								else																
									colorGradientExport(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric, c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE);
							}
							else
							{
								exportSquare(c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
							}
						}
					}
				}
				else
				{
					exportSquare(c, ((rowSize-1) * EXPORTGRIDSIZE) - (j * EXPORTGRIDSIZE), i * EXPORTGRIDSIZE, "#FFFFFF");
				}
			}
		}
	}	
	
	// Helper function to draw grid
	// Simply loop through data, and perform action depending on whether it is doing analyze
	// or just map	
	private void serpentineGridRight(HashMap<Integer, String> compare, int start, String mapColor, boolean ignoreSizing)
	{
		map.removeAllViews();
		
		if (mapData == null)
			return;
		
		int max = mapData.length;
								
		int rowSize = getMapRangeRowSize();

		map.setColumnCount(rowSize);
		
		int actualRows = 0;
		
		if (max - start < GRIDSIZE)
		{
			actualRows = max - start; 
		}
		else
		{
			actualRows = GRIDSIZE;
		}
		
		SearchData[] rowData = null;
		
		int segmentValue = max;
		
		if (!ignoreSizing)
			segmentValue = start + actualRows;
		
		for (int i = start; i < segmentValue; i++)
		{
			rowData = dt.getRowForMapPlot(mapData[i].plot, init[i]);
			
			for (int j = 0; j < rowSize; j++)
			{
				if (j < rowData.length)
				{
					if (compare == null)
					{
						map.addView(createSquare(rowData[j].id, "#FFFFFF"));
					}
					else
					{
						if (!analyze)
						{
							if (compare.containsKey(rowData[j].id))								
								map.addView(createSquare(rowData[j].id, mapColor));
							else
								map.addView(createSquare(rowData[j].id, "#FFFFFF"));
						}
						else
						{
							if (compare.containsKey(rowData[j].id))
							{
								if (MainActivity.dt.getDetail(mapTrait.getSelectedItem().toString()).format.equals("text"))
									map.addView(createSquare(rowData[j].id, mapColor));
								else
									colorGradient(rowData[j].id, compare.get(rowData[j].id).toString(), sNumeric, bNumeric);
							}
							else
							{
								map.addView(createSquare(rowData[j].id, "#FFFFFF"));

							}
						}
					}
				}
				else
				{
					map.addView(createBoundarySpace());
				}
			}
		}
	}	
		
	// Find largest number for a trait; used in map analysis
	private float largestNumeric(String[] plots, String trait)
	{
		float v = 0;
		
		for (String i : plots)
		{
			float g;
			
			try
			{
				g = Float.parseFloat(dt.getSingleValue(i, trait));
			}
			catch (Exception e)
			{
				g = 0;
			}
			
			if (g > v)
				v = g;
		}
		
		return v;
	}

	// Find smallest number for a trait; used in map analysis
	private float smallestNumeric(String[] plots, String trait)
	{
		float v = -1;
		
		for (String i : plots)
		{
			float g;
			
			try
			{
				g = Float.parseFloat(dt.getSingleValue(i, trait));
			}
			catch (Exception e)
			{
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
	private void colorGradient(int id, String value, float sNumeric, float bNumeric)
	{		
		TraitObject colorTrait = dt.getDetail(mapTrait.getSelectedItem().toString());
		
		if (colorTrait.format.equals("numeric"))
		{			
			map.addView(createSquare(id, interpolateColor(Double.parseDouble(value) / bNumeric ))); 
		}
		else
		if (colorTrait.format.equals("percent"))
		{			
			map.addView(createSquare(id, interpolateColor(Double.parseDouble(value) / Double.parseDouble(colorTrait.maximum) ))); 
		}
		else
		if (colorTrait.format.equals("date"))
		{
			Calendar c = Calendar.getInstance();
			
			String[]d = value.split("\\.");
			
			c.set(Integer.parseInt(d[0]), Integer.parseInt(d[1])-1, Integer.parseInt(d[2]));

			Double v = (double) c.get(Calendar.DAY_OF_YEAR);
			
			map.addView(createSquare(id, interpolateColor(v / 365))); 
		}
		else
		if (colorTrait.format.equals("boolean"))
		{
			int v = 0;
			
			if (value.equals("false"))
				v = 0;
			else
				v = 1;
				
			map.addView(createSquare(id, interpolateColor(v)));
		}
		else
		if (colorTrait.format.equals("qualitative"))
		{
			String[] d = colorTrait.categories.split("/");
						
			for (int i = 0; i < d.length; i++)
			{
				if (value.equals(d[i]))
				{
					map.addView(createSquare(id, getColor(i+1)));
					break;
				}
			}
		}
		else
		map.addView(createSquare(id, "#FFFFFF"));

	}

	// Function exports grid square based on trait type
	private void colorGradientExport(int id, String value, float sNumeric, float bNumeric, Canvas cv, int x, int y)
	{		
		TraitObject colorTrait = dt.getDetail(mapTrait.getSelectedItem().toString());
		
		if (colorTrait.format.equals("numeric"))
		{	
			exportSquare(cv, x, y, interpolateColor(Double.parseDouble(value) / bNumeric ));
		}
		else
		if (colorTrait.format.equals("percent"))
		{			
			exportSquare(cv, x, y, interpolateColor(Double.parseDouble(value) / Double.parseDouble(colorTrait.maximum)));
		}
		else
		if (colorTrait.format.equals("date"))
		{
			Calendar c = Calendar.getInstance();
			
			String[]d = value.split("\\.");
			
			c.set(Integer.parseInt(d[0]), Integer.parseInt(d[1])-1, Integer.parseInt(d[2]));

			Double v = (double) c.get(Calendar.DAY_OF_YEAR);
			
			exportSquare(cv, x, y, interpolateColor(v / 365 ));			
		}
		else
		if (colorTrait.format.equals("boolean"))
		{
			int v = 0;
			
			if (value.equals("false"))
				v = 0;
			else
				v = 1;
				
			exportSquare(cv, x, y, interpolateColor(v));
		}
		else
		if (colorTrait.format.equals("qualitative"))
		{
			String[] d = colorTrait.categories.split("/");
						
			for (int i = 0; i < d.length; i++)
			{
				if (value.equals(d[i]))
				{
					if (x >= exportRowSize)
					{
						x = 0;
						y += EXPORTGRIDSIZE;
					}
					
					exportSquare(cv, x, y, getColor(i+1));
					
					x += EXPORTGRIDSIZE;
					
					break;
				}
			}
		}
		else
			exportSquare(cv, x, y, "#FFFFFF");

	}

	// This is used to generate range from green to red
	public static String interpolateColor(double power)
	{		
	    double H = (1 - power) * 120f; // base green
	    double S = 0.9; // Saturation
	    double B = 0.9; // Brightness

	    float[] hsv = new float[3];
	    
	    hsv[0] = (float)H;
	    hsv[1] = (float)S;
	    hsv[2] = (float)B;
	    
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
	    int n = (int)Math.cbrt(index);
	    index -= (n*n*n);
	    int[] p = new int[3];
	    Arrays.fill(p,n);
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
	    p[v      ] = index / n;
	    p[++v % 3] = index % n;
	    return p;
	}
	
	private Runnable doAnalyze = new Runnable() 
	{
		public void run() 
	    {
			new AnalyzeTask().execute(0);			
		}
	};
		
	private class AnalyzeTask extends AsyncTask<Integer, Integer, Integer> 
	{
		ProgressDialog dialog;
		
		@Override
		protected void onPreExecute() 
		{
			super.onPreExecute();
			
			dialog = new ProgressDialog(MainActivity.this);
			dialog.setIndeterminate(true);
			dialog.setCancelable(false);
			dialog.setMessage(getString(R.string.analyzewait));
			  
			dialog.show();			
			
		}		
			
		@Override
		protected Integer doInBackground(Integer... params) 
		{	    
			// redraw is only called if you want to regenerate all the data
			if (redraw)
			{
				analyzeRange = dt.analyze(mapTrait.getSelectedItem().toString());

				mapIndex = 0;

				// Data for analysis
				String[] plots = dt.getAllPlotID();				
				bNumeric = largestNumeric(plots, mapTrait.getSelectedItem().toString());
				sNumeric = smallestNumeric(plots, mapTrait.getSelectedItem().toString());
						
				if (ep.getInt("GRIDPOSITIONORIENTATION", 0) == 0)
				{
					processSerpentineMap(analyzeRange, ep.getInt("GRIDPOSITION", 0));							
				}
				else
				{
					processZigZagMap(analyzeRange, ep.getInt("GRIDPOSITION", 0));
				}

			}
			
			return 0;
		}

		@Override		
		protected void onPostExecute(Integer result) 
		{
			// Move the map the way the user will so all the variables are constant
			if (redraw)
			{
				map.scrollTo(0, 0);
				
				switch (ep.getInt("GRIDPOSITION", 0))
				{
					case 1:
					case 3:
						// Simulate user moving to the plot
						do
						{
							if (mapIndex + GRIDSIZE >= mapData.length)
							{
								break;
							}
							else
							{
								mapIndex += GRIDSIZE;
								
								if (ep.getInt("GRIDPOSITIONORIENTATION", 0) == 0)
								{				
									loadSerpentineMap(analyzeRange, ep.getInt("GRIDPOSITION", 0), "#16e616");
								}
								else
								{
									loadZigZagMap(analyzeRange, ep.getInt("GRIDPOSITION", 0), "#16e616");
								}														
							}
							
						} while (mapIndex + GRIDSIZE < mapData.length);	
						
						break;
						
					case 0:
					case 2:						
						if (ep.getInt("GRIDPOSITIONORIENTATION", 0) == 0)
						{				
							loadSerpentineMap(analyzeRange, ep.getInt("GRIDPOSITION", 0), "#16e616");
						}
						else
						{
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
	
				switch (ep.getInt("GRIDPOSITION", 0))
				{
					case 0:
					case 1:
						mapSegmenth.setText("W: " + currentMapScrollSegment + "/" + mapScrollSegment);
						break;
						
					case 2:
					case 3:
						do
						{
							currentMapScrollSegment += scrollSegmentSize;
							
							if (currentMapScrollSegment > mapScrollSegment)
							{
								currentMapScrollSegment = mapScrollSegment;							
							}
							
							map.scrollBy(520, 0);
							
							mapSegmenth.setText("W: " + currentMapScrollSegment + "/" + mapScrollSegment);
							
						} while (currentMapScrollSegment < mapScrollSegment);
						
						break;
						
				}
				
			}
			else
			{
				if (ep.getInt("GRIDPOSITIONORIENTATION", 0) == 0)
				{				
					loadSerpentineMap(analyzeRange, ep.getInt("GRIDPOSITION", 0), "#16e616");
				}
				else
				{
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

		if (cRange == null || cRange.plot_id.length() == 0)
		{
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

		if (cRange == null || cRange.plot_id.length() == 0)
		{
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
	    	synchronized (lock)
	    	{
		        switch (msg.what) { 
		        case MESSAGE_CHECK_BTN_STILL_PRESSED: 
		            ImageView btn = (ImageView) findViewById(msg.arg1);
		            if (btn.getTag() != null) { // button is still pressed	                
		                Message msg1 = new Message(); // schedule next btn pressed check
		                msg1.copyFrom(msg);
		                if (msg.arg1 == R.id.rangeLeft)
		                {
		                	repeatLeft();
		                }
		                else
		                {
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
		
		getSupportMenuInflater().inflate(R.menu.mainmenu, menu);
		
		systemMenu = menu;
		
		if (ep.getBoolean("EnableMap", false))
		{
			systemMenu.findItem(R.id.map).setVisible(true);
		}
		else
		{
			systemMenu.findItem(R.id.map).setVisible(false);
		}		

		if (ep.getBoolean("Tips", false))
		{
			systemMenu.findItem(R.id.help).setVisible(true);
		}
		else
		{
			systemMenu.findItem(R.id.help).setVisible(false);
		}		
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		Intent intent = new Intent(Intent.ACTION_VIEW);
		
		switch (item.getItemId())
		{
			case R.id.settings:
				try
				{
					TutorialMainActivity.thisActivity.finish();
				}
				catch (Exception e)
				{
					
				}
				
				intent.setClassName(MainActivity.this,
						ConfigActivity.class.getName());
				startActivity(intent);				
				break;
			
			case R.id.search:
				try
				{
					TutorialMainActivity.thisActivity.finish();
				}
				catch (Exception e)
				{
					
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
				startActivity(intent);				
				break;

			case R.id.quit:
				showQuitDialog();
				break;

			case R.id.help:
				Intent helpIntent = new Intent();
				helpIntent.setClassName(MainActivity.this,
						TutorialMainActivity.class.getName());
				startActivity(helpIntent);																		
				break;
		}
		
		return super.onOptionsItemSelected(item);
	}

	public void onClick(View b) {

		String v = "";
		
		switch (b.getId())
		{
			case R.id.record:
	            if (mRecording) {
	            	
	            	// Stop recording
	            	try
	            	{
	                    mRecorder.stop();            		
	            	}
	            	catch (Exception e)
	            	{
	            		e.printStackTrace();
	            	}
	            	
	                releaseRecorder();
	            	                
	            	updateTrait(currentTrait.trait, "audio", mRecordingLocation.getAbsolutePath());
	            	
	            	tNum.setText(getString(R.string.stored));
	            	
	                mRecording = false;
	                doRecord.setText(R.string.record);

	                rangeLeft.setEnabled(true);
	                rangeRight.setEnabled(true);
	                	                
	                traitLeft.setEnabled(true);
	                traitRight.setEnabled(true);
	                
	            }
	            else {        	            	
					if (!newTraits.containsKey(currentTrait.trait)) {
						
						// start recording
						deleteRecording();

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
	            }				
				break;

			case R.id.clearRecord:
				
				// Delete recording
				
				deleteRecording();
				removeTrait(currentTrait.trait);
				tNum.setText("");
				mRecording = false;
                doRecord.setText(R.string.record);				
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
		}
		
		if (b.getId() == R.id.k16)	
		{
			//eNum.setText(eNum.getText().toString().substring(0, eNum.getText().toString().length()-1));
			
			eNum.removeTextChangedListener(eNumUpdate);
			eNum.setText("");
			removeTrait(currentTrait.trait);
			eNum.addTextChangedListener(eNumUpdate);
			
		}
		else
		{
			eNum.setText(eNum.getText().toString() + v);
		}
	}
	
	private void showMapDialog()
	{
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
		
		orientation.setOnItemSelectedListener(new OnItemSelectedListener(){

			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int position, long arg3) {
				
			}

			public void onNothingSelected(AdapterView<?> arg0) {
				
			}
		});
		
		dir.setOnItemSelectedListener(new OnItemSelectedListener(){

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
		
		ArrayAdapter adapter2 = new ArrayAdapter(MainActivity.this, R.layout.spinnerlayout5, directions);
		dir.setAdapter(adapter2);

		String[] orientations = new String[2];
		orientations[0] = getString(R.string.serpentine);
		orientations[1] = getString(R.string.zigzag);
		
		ArrayAdapter adapter3 = new ArrayAdapter(MainActivity.this, R.layout.spinnerlayout5, orientations);
		orientation.setAdapter(adapter3);
		
		Button saveBtn = (Button) configDialog.findViewById(R.id.saveBtn);
		Button clearBtn = (Button) configDialog.findViewById(R.id.clearBtn);
		
		// Map configure, save
		saveBtn.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {

				Editor ed = ep.edit();						
				ed.putInt("GRIDPOSITIONORIENTATION", orientation.getSelectedItemPosition()); 							
				ed.putInt("GRIDPOSITION", dir.getSelectedItemPosition());
				ed.commit();
				
				configDialog.dismiss();

				if (!ep.getBoolean("MAPCONFIGURED", false))
				{							
					ed.putBoolean("MAPCONFIGURED", true);
					ed.commit();
					
					mapDialog.show();							
				}
				else
				{
					redraw = true;
					
					mHandler.post(doAnalyze);								
				}
			}
		});

		// Map configure, clear
		clearBtn.setOnClickListener(new OnClickListener(){

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
		mapSummary.setOnClickListener(new OnClickListener(){

			public void onClick(View arg0) {
				
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
				
				closeBtn.setOnClickListener(new OnClickListener(){

					public void onClick(View v) {
						summaryDialog.dismiss();				
					}
				});
				
				String[] traitList = dt.getAllTraits();
				
				String data = "";
				
				if (cRange != null)
				{
					data = getString(R.string.range) + ": " + cRange.range + "\n";
					data += getString(R.string.plot) + ": " + cRange.plot + "\n";
				}
				
				for (String s : traitList)
				{
					if (newTraits.containsKey(s))
					{
						data += s + ": " + newTraits.get(s).toString() + "\n";
					}									
				}
				
				summaryText.setText(data);
				
				summaryDialog.show();
			}
		});
		
		// Export bitmap to disk
		mapExport.setOnClickListener(new OnClickListener(){

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
				
				try
				{
					mapExportfilename = mapTrait.getSelectedItem().toString();
				}
				catch (Exception e)
				{
					mapExportfilename = "map_error";
				}
				
				exportFile.setText(mapExportfilename + "_" +
						timeStamp.format(Calendar.getInstance().getTime())  
						+ ".jpg");			
				
				saveBtn.setOnClickListener(new OnClickListener(){

					public void onClick(View v) {
						mapExportDialog.dismiss();
														
						mHandler.post(exportMap);
					}
				});
				
				closeBtn.setOnClickListener(new OnClickListener(){

					public void onClick(View v) {
						mapExportDialog.dismiss();				
					}
				});
				
				mapExportDialog.show();
			}
		});
		
		mapClose.setOnClickListener(new OnClickListener(){

			public void onClick(View arg0) {
				mapDialog.dismiss();
			}
		});
		
		mapParameter.setOnClickListener(new OnClickListener(){

			public void onClick(View arg0) {
				orientation.setSelection(ep.getInt("GRIDPOSITIONORIENTATION", 0));
				dir.setSelection(ep.getInt("GRIDPOSITION", 0));
										
				configDialog.show();
			}
		});
		
		mapAnalyze.setOnClickListener(new OnClickListener(){

			public void onClick(View arg0) {
				
				if (dt.getVisibleTrait() == null)
					return;
				
				analyze = !analyze;
				
				if (analyze)
				{
					mapAnalyze.setText(getString(R.string.clear));
				}
				else
				{
					mapAnalyze.setText(getString(R.string.mapanalyze));
				}

				mHandler.post(doAnalyze);
			}
			
		});
		
		mapLeft.setOnTouchListener(new OnTouchListener(){

			public boolean onTouch(View v, MotionEvent event) {
				
				switch (event.getAction()) {
				
	                case MotionEvent.ACTION_DOWN:
	                	mapLeft.setImageResource(R.drawable.left);
	                    break;
	                case MotionEvent.ACTION_MOVE:
	                    break;
	                case MotionEvent.ACTION_UP:
	                	mapLeft.setImageResource(R.drawable.lefts);
	                case MotionEvent.ACTION_CANCEL:
	                    break;
                }
				
                return false; // return true to prevent calling btn onClick handler
			}
		});
		
		mapLeft.setOnClickListener(new OnClickListener(){

			public void onClick(View arg0) {

				if (currentMapScrollSegment == scrollSegmentSize)
					return;
				
				currentMapScrollSegment -= scrollSegmentSize;
				
				if (currentMapScrollSegment < scrollSegmentSize)
				{
					currentMapScrollSegment = scrollSegmentSize;
				}
				
				map.scrollBy(-520, 0);
				
				mapSegmenth.setText("W: " + currentMapScrollSegment + "/" + mapScrollSegment);						
			}
		});

		mapRight.setOnTouchListener(new OnTouchListener(){

			public boolean onTouch(View v, MotionEvent event) {
				
				switch (event.getAction()) {
				
	                case MotionEvent.ACTION_DOWN:
	                	mapRight.setImageResource(R.drawable.right);
	                    break;
	                case MotionEvent.ACTION_MOVE:
	                    break;
	                case MotionEvent.ACTION_UP:
	                	mapRight.setImageResource(R.drawable.rights);
	                case MotionEvent.ACTION_CANCEL:
	                    break;
                }
				
                return false; // return true to prevent calling btn onClick handler
			}
		});
		
		mapRight.setOnClickListener(new OnClickListener(){

			public void onClick(View arg0) {

				if (currentMapScrollSegment == mapScrollSegment)
					return;
				
				currentMapScrollSegment += scrollSegmentSize;
				
				if (currentMapScrollSegment > mapScrollSegment)
				{
					currentMapScrollSegment = mapScrollSegment;							
				}
				
				map.scrollBy(520, 0);
				
				mapSegmenth.setText("W: " + currentMapScrollSegment + "/" + mapScrollSegment);
				
			}
		});

		mapUp.setOnTouchListener(new OnTouchListener(){

			public boolean onTouch(View v, MotionEvent event) {
				
				switch (event.getAction()) {
				
	                case MotionEvent.ACTION_DOWN:
	                	mapUp.setImageResource(R.drawable.up);
	                    break;
	                case MotionEvent.ACTION_MOVE:
	                    break;
	                case MotionEvent.ACTION_UP:
	                	mapUp.setImageResource(R.drawable.ups);
	                case MotionEvent.ACTION_CANCEL:
	                    break;
                }
				
                return false; // return true to prevent calling btn onClick handler
			}
		});
		
		mapUp.setOnClickListener(new OnClickListener(){

			public void onClick(View arg0) {

				mapIndex -= GRIDSIZE;
				
				if (mapIndex <= 0)
				{
					mapIndex = 0;
				}
				
				mHandler.post(doAnalyze);
			}
			
		});

		mapDown.setOnTouchListener(new OnTouchListener(){

			public boolean onTouch(View v, MotionEvent event) {
				
				switch (event.getAction()) {
				
	                case MotionEvent.ACTION_DOWN:
	                	mapDown.setImageResource(R.drawable.down2);
	                    break;
	                case MotionEvent.ACTION_MOVE:
	                    break;
	                case MotionEvent.ACTION_UP:
	                	mapDown.setImageResource(R.drawable.down2s);
	                case MotionEvent.ACTION_CANCEL:
	                    break;
                }
				
                return false; // return true to prevent calling btn onClick handler
			}
		});
		
		mapDown.setOnClickListener(new OnClickListener(){

			public void onClick(View arg0) {												
				if (mapIndex + GRIDSIZE >= mapData.length)
				{
					return;
				}
				else
					mapIndex += GRIDSIZE;
				
				mHandler.post(doAnalyze);
			}
			
		});
		
		if (traits != null) {
			ArrayAdapter<String> directionArrayAdapter = new ArrayAdapter<String>(
					this, R.layout.smallspinnerlayout, traits);
			directionArrayAdapter
					.setDropDownViewResource(R.layout.smallspinnerlayout2);
			mapTrait.setAdapter(directionArrayAdapter);
								
		}
		
		final ImageView mapTraitLeft = (ImageView) mapDialog.findViewById(R.id.traitLeft);
		final ImageView mapTraitRight = (ImageView) mapDialog.findViewById(R.id.traitRight);

		mapTraitLeft.setOnTouchListener(new OnTouchListener(){

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

		mapTraitRight.setOnTouchListener(new OnTouchListener(){

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
		
		if (dt.getVisibleTrait() != null)
		{
			ArrayAdapter adapter = new ArrayAdapter(MainActivity.this, R.layout.spinnerlayout2, dt.getVisibleTrait());
			mapTrait.setAdapter(adapter);
			
			mapTrait.setOnItemSelectedListener(new OnItemSelectedListener(){
	
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
		
		if (!ep.getBoolean("MAPCONFIGURED", false))
		{
			configDialog.show();
		}
		else
		{
			mapDialog.show();
		}
				
	}
	 
	// This is used for exporting a grid square
	private void exportSquare(Canvas c, int x, int y, String color)
	{
		Paint paint = new Paint();	 
		paint.setAlpha(0);
        paint.setAntiAlias(true);
        
        paint.setColor(Color.BLACK);
        c.drawRect(x, y, x + EXPORTGRIDSIZE, y + EXPORTGRIDSIZE, paint);
        
        paint.setColor(Color.parseColor(color));		
		
		c.drawRect(x+1, y+1, x + EXPORTGRIDSIZE -1, y + EXPORTGRIDSIZE -1, paint);
	}

	// This is used for exporting a grid square
	private void exportSquare(Canvas c, int x, int y, int color)
	{
		Paint paint = new Paint();	 
		paint.setAlpha(0);
        paint.setAntiAlias(true);
        
        paint.setColor(Color.BLACK);
        c.drawRect(x, y, x + EXPORTGRIDSIZE, y + EXPORTGRIDSIZE, paint);
        
        paint.setColor(color);		
		
		c.drawRect(x+1, y+1, x + EXPORTGRIDSIZE -1, y + EXPORTGRIDSIZE -1, paint);
	}
	
	private Runnable exportMap = new Runnable() 
	{
		public void run() 
	    {
			new ExportMapTask().execute(0);			
		}
	};
		
	private class ExportMapTask extends AsyncTask<Integer, Integer, Integer> 
	{
		ProgressDialog dialog;
		
		@Override
		protected void onPreExecute() 
		{
			super.onPreExecute();
			  
			dialog = new ProgressDialog(MainActivity.this);
			dialog.setIndeterminate(true);
			dialog.setCancelable(false);
			dialog.setMessage(getString(R.string.mapexportwait));
			  
			dialog.show();			
			
		}		
			
		@Override
		protected Integer doInBackground(Integer... params) 
		{	    
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
			
			if (ep.getInt("GRIDPOSITIONORIENTATION", 0) == 0)
			{
				loadSerpentineMapExport(analyzeRange, ep.getInt("GRIDPOSITION", 0), Color.GREEN, c);
			}
			else
			{
				loadZigZagMapExport(analyzeRange, ep.getInt("GRIDPOSITION", 0), Color.GREEN, c);	
			}
			
			c.save();
			
	        try {
	        	FileOutputStream out = new FileOutputStream(resourcePath + "/" + exportFile.getText().toString());
	        	b.compress(CompressFormat.JPEG, 100, out);
	        	out.flush();
	        	out.close();
	        	b.recycle();
	        } 
	        catch (Exception e) {
	        	e.printStackTrace();
	        }	
	       	       
	        return 0;
		}

		@Override		
		protected void onPostExecute(Integer result) 
		{			
			if (dialog.isShowing())
				dialog.dismiss();
			
		}
	}
	
	private void showQuitDialog()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		
	    builder.setTitle(getString(R.string.quit));
	    builder.setMessage(getString(R.string.areyousure));

	    builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() 
	    {

	        public void onClick(DialogInterface dialog, int which) 
	        {
	        	dialog.dismiss();
	        	
	        	finish();
	        }

	    });

	    builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() 
	    {

	        public void onClick(DialogInterface dialog, int which) 
	        {
	        	dialog.dismiss();
	        }

	    });
	    
	    AlertDialog alert = builder.create();
	    alert.show();
	    		
	}
	
	@Override 
	public boolean onKeyDown(int keyCode, KeyEvent event) 
	{    
		if ((keyCode == KeyEvent.KEYCODE_BACK)) 
		{
			showQuitDialog();
			return true;    
		}    
		return super.onKeyDown(keyCode, event);
	}
		
}