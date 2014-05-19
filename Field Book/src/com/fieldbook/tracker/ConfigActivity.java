package com.fieldbook.tracker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

import jxl.Workbook;
import jxl.WorkbookSettings;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
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
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Spinner;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Settings Screen
 */
public class ConfigActivity extends SherlockActivity {
	Handler mHandler = new Handler();

	private static final int DIALOG_LOAD_TRAITFILE = 1002;
	private static final int DIALOG_LOAD_TRAITS1 = 1003;
	private static final int DIALOG_LOAD_TRAITS2 = 1004;
	private static final int DIALOG_LOAD_TRAITS3 = 1005;

	private static final String CSV = ".csv";
	private static final String XLS = ".xls";
	private static final int DIALOG_LOAD_FIELDFILECSV = 1000;
	private static final int DIALOG_LOAD_FIELDFILEEXCEL = 1001;
	
    public static boolean helpActive;
    public static Activity thisActivity;
	
	private SharedPreferences ep;
	
	private Dialog personDialog;
	private Dialog locationDialog;
	private Dialog rateDialog;
	private Dialog saveDialog;

	private Dialog fieldDialog;
	private Dialog fieldDialog2;
	
	private Dialog fieldCurrentDialog;	
	
	private Dialog advancedDialog;
	
	private Dialog setupDialog;
	
	private Dialog importFieldMapDialog;
	
	private String[] mFileList;
	private String[] importColumns;

	private String mChosenFile;
		
	private ListView rateList;
	private ListView saveList;
	private ListView setupList;
	
	private TextView currentPerson;
	private TextView currentLocation;
	private Location location;
	
	private double lat;
	private double lng;
	
	private EditText exportFile;
	
	private CheckBox checkDB;
	private CheckBox checkExcel;
	
	private boolean isCSV;
	private int idColPosition;
		
	private Workbook wb;
	
	private int uPosition;
	private int fPosition;
	private int sPosition;
	private int ePosition;

	private int action;
	
	private LinearLayout importMain;
		
	private OnItemClickListener mainSettingsListener;
	
	private boolean columnFail;
	
	private ArrayList<String> newRange;
	
	private String local;
		
    private FrameLayout mFrame;
        
    private ListView settingsList;
        
	private Menu systemMenu;
    
	@Override
	public void onDestroy() {
		
		try
		{
			// Always close the tutorial along with the activity
			TutorialSettingsActivity.thisActivity.finish();
		}
		catch (Exception e)
		{
			
		}
		
		super.onDestroy();
	}
	
	@Override
	public void onResume() {
		super.onResume();

		if (systemMenu != null)
		{
			// Always reload item visibility in the menu in the event settings has changed 
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
				
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		ep = getSharedPreferences("Settings", 0);

        // Enforce internal language change
        local = "en";
        
        thisActivity = this;
        
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
        getBaseContext().getResources().updateConfiguration(config2, 
        getBaseContext().getResources().getDisplayMetrics()); 
        
        loadScreen();
        
        helpActive = false;
	}

	private void loadScreen()
	{
		//requestWindowFeature(Window.FEATURE_NO_TITLE);

		getSupportActionBar().setHomeButtonEnabled(true);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		float brightness = 100;

		// Set brightness to maximum
		Settings.System.putFloat(this.getContentResolver(),
				Settings.System.SCREEN_BRIGHTNESS, brightness);

		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.screenBrightness = brightness;
		getWindow().setAttributes(lp);

		mFrame = new FrameLayout(this);

		mFrame.addView(LayoutInflater.from(getBaseContext()).inflate(R.layout.config, null));
        setContentView(mFrame);

		// Import Field book
		importFieldMapDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
		importFieldMapDialog.setContentView(R.layout.importchoose);
		importFieldMapDialog.setTitle(getString(R.string.importfields));
		importFieldMapDialog.setCancelable(true);
		importFieldMapDialog.setCanceledOnTouchOutside(true);

		importMain = (LinearLayout) importFieldMapDialog.findViewById(R.id.main);
		
		Button startImport = (Button) importFieldMapDialog.findViewById(R.id.okBtn);
		
		startImport.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				
				if (checkImportColumnNames())
				{					
					importFieldMapDialog.dismiss();
					
					if (isCSV)
						mHandler.post(importCSV);
					else
						mHandler.post(importExcel);
				}
			}
		});

		// Advanced Settings
		advancedDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
		advancedDialog.setTitle(getString(R.string.advanced));
		advancedDialog.setContentView(R.layout.advanced);

		android.view.WindowManager.LayoutParams params = advancedDialog.getWindow().getAttributes();
        params.width = LayoutParams.FILL_PARENT;
        advancedDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
        
        advancedDialog.setCancelable(true);
        advancedDialog.setCanceledOnTouchOutside(true);
		
        CheckBox tips = (CheckBox) advancedDialog.findViewById(R.id.tips);
        CheckBox cycle = (CheckBox) advancedDialog.findViewById(R.id.cycle);
        CheckBox enableMap = (CheckBox) advancedDialog.findViewById(R.id.enableMap);
        CheckBox ignoreEntries = (CheckBox) advancedDialog.findViewById(R.id.ignoreExisting);
        CheckBox useDay = (CheckBox) advancedDialog.findViewById(R.id.useDay);
                
        Button advCloseBtn = (Button)  advancedDialog.findViewById(R.id.closeBtn);
        
        advCloseBtn.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				advancedDialog.dismiss();
			}
		});
        
        // Set default values for advanced settings
        tips.setChecked(ep.getBoolean("Tips", false));
        cycle.setChecked(ep.getBoolean("CycleTraits", false));
        enableMap.setChecked(ep.getBoolean("EnableMap", false));
		ignoreEntries.setChecked(ep.getBoolean("IgnoreExisting", false));
		useDay.setChecked(ep.getBoolean("UseDay", false));

		tips
		.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			public void onCheckedChanged(CompoundButton arg0,
					boolean checked) {
				Editor e = ep.edit();
				e.putBoolean("Tips", checked);
				e.commit();
				
				// This is important - menu items won't change their language if 
				// this isn't called
				invalidateOptionsMenu();
			}
		});

		cycle
		.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			public void onCheckedChanged(CompoundButton arg0,
					boolean checked) {
				Editor e = ep.edit();
				e.putBoolean("CycleTraits", checked);
				e.commit();
			}
		});

		enableMap
		.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			public void onCheckedChanged(CompoundButton arg0,
					boolean checked) {
				Editor e = ep.edit();
				e.putBoolean("EnableMap", checked);
				e.commit();
			}
		});
		
		ignoreEntries
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					public void onCheckedChanged(CompoundButton arg0,
							boolean checked) {
						Editor e = ep.edit();
						e.putBoolean("IgnoreExisting", checked);
						e.commit();
					}
				});
        
		useDay
		.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			public void onCheckedChanged(CompoundButton arg0,
					boolean checked) {
				Editor e = ep.edit();
				e.putBoolean("UseDay", checked);
				e.commit();
			}
		});
        
		// Dialog for choosing which traits to rate
		rateDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
		rateDialog.setTitle(getString(R.string.choosetraits));
		rateDialog.setContentView(R.layout.rate);

		android.view.WindowManager.LayoutParams params5 = rateDialog.getWindow().getAttributes();
        params5.width = LayoutParams.FILL_PARENT;
        rateDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params5);
        
		rateDialog.setCancelable(true);
		rateDialog.setCanceledOnTouchOutside(true);

		// This is the list displayed on the choose traits screen
		rateList = (ListView) rateDialog.findViewById(R.id.myList);

		// Allow multiple selection
		rateList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		rateList.setItemsCanFocus(false);

		// When you save your changes for choosing traits, this function is
		// called
		Button rateButton = (Button) rateDialog.findViewById(R.id.saveBtn);

		rateButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				try {
					// Go through every item in list, and reset the trait
					// visibility accordingly
					int count = rateList.getAdapter().getCount();

					for (int i = 0; i < count; i++) {
						
						String currentItem = (String) rateList.getAdapter()
								.getItem(i);

						if (rateList.isItemChecked(i)) {
							MainActivity.dt.updateTraitVisibility(currentItem,
									true);
						} else
						{
							MainActivity.dt.updateTraitVisibility(currentItem,
									false);
						}
					}
				} catch (Exception e) {

				}

				MainActivity.reloadData = true;
				
				updateSetupList();
				
				rateDialog.dismiss();
			}
		});

		// Export Field book
		saveDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
		saveDialog.setTitle(getString(R.string.exportas));
		saveDialog.setContentView(R.layout.savefile);

		android.view.WindowManager.LayoutParams params2 = saveDialog.getWindow().getAttributes();
        params2.width = LayoutParams.FILL_PARENT;
        saveDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params2);
		
		saveDialog.setCancelable(true);
		saveDialog.setCanceledOnTouchOutside(true);

		Button closeBtn = (Button) saveDialog.findViewById(R.id.closeBtn);
		
		closeBtn.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				saveDialog.dismiss();				
			}
		});
		
		exportFile = (EditText) saveDialog.findViewById(R.id.fileName);

		saveList = (ListView) saveDialog.findViewById(R.id.myList);
		
		saveList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		saveList.setItemsCanFocus(false);
		
		// Export DB Format
		checkDB = (CheckBox) saveDialog.findViewById(R.id.formatDB);
		
		// Export Excel Format
		checkExcel = (CheckBox) saveDialog.findViewById(R.id.formatExcel);
		
		checkDB.setOnCheckedChangeListener(new OnCheckedChangeListener()
		{

			public void onCheckedChanged(CompoundButton arg0, boolean arg1) 
			{
			}
		});

		checkExcel.setOnCheckedChangeListener(new OnCheckedChangeListener()
		{

			public void onCheckedChanged(CompoundButton arg0, boolean arg1) 
			{
			}
		});
						
		Button exportButton = (Button) saveDialog.findViewById(R.id.saveBtn);

		exportButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				
				int count = saveList.getAdapter().getCount();

				// Ensure at least one export type is checked
				if (!checkDB.isChecked() & !checkExcel.isChecked())
				{
					Toast.makeText(ConfigActivity.this, getString(R.string.noexportcheck), 
					Toast.LENGTH_LONG).show();
					return;					
				}
					
				newRange = new ArrayList<String>();
				
				// Get the columns selected by the user
				for (int i = 0; i < count; i++) {
					String currentItem = (String) saveList.getAdapter()
							.getItem(i);

					if (saveList.isItemChecked(i)) {
						newRange.add(currentItem);
					}
				}

				// If there are no columns selected, inform the user
				if (newRange.size() == 0)
				{
					Toast.makeText(ConfigActivity.this, getString(R.string.nocolumns), 
					Toast.LENGTH_LONG).show();
					return;
				}				

				saveDialog.dismiss();
								
				mHandler.post(exportData);
			}
		});

		//setup
		setupDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
		setupDialog.setTitle(getString(R.string.setup));
		setupDialog.setContentView(R.layout.config);

		setupDialog.setCancelable(true);
		setupDialog.setCanceledOnTouchOutside(true);
		
		// This is the list of items shown on the settings screen itself
		setupList = (ListView) setupDialog.findViewById(R.id.myList);				

		Button setupCloseBtn = (Button) setupDialog.findViewById(R.id.closeBtn);
		
		setupCloseBtn.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				setupDialog.dismiss();				
			}
		});
		
		// To configure first name, last name
		personDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
		personDialog.setTitle(getString(R.string.personsetup));
		personDialog.setContentView(R.layout.person);

		personDialog.setCancelable(true);
		personDialog.setCanceledOnTouchOutside(true);

		currentPerson = (TextView) personDialog
				.findViewById(R.id.currentPerson);

		final EditText firstName = (EditText) personDialog
				.findViewById(R.id.firstName);
		final EditText lastName = (EditText) personDialog
				.findViewById(R.id.lastName);

		Button yesButton = (Button) personDialog.findViewById(R.id.saveBtn);

		yesButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				Editor e = ep.edit();

				e.putString("FirstName", firstName.getText().toString());
				e.putString("LastName", lastName.getText().toString());

				e.commit();

				updateSetupList();
				
				personDialog.dismiss();
			}
		});

		// choose type of field import 
		fieldDialog2 = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
		fieldDialog2.setTitle(getString(R.string.fields));
		fieldDialog2.setContentView(R.layout.genericdialog);

		fieldDialog2.setCancelable(true);
		fieldDialog2.setCanceledOnTouchOutside(true);

		ListView fieldList2 = (ListView) fieldDialog2.findViewById(R.id.myList);

		Button fdCloseBtn2 = (Button) fieldDialog2.findViewById(R.id.closeBtn);
		
		fdCloseBtn2.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				fieldDialog2.dismiss();				
			}
		});
		
		String[] items6 = new String[] { getString(R.string.importcsv), 
		getString(R.string.importexcel)};

		OnItemClickListener csvListener = new OnItemClickListener() {

			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {

				fieldDialog2.dismiss();
				
				switch (position) {
					case 0:
						createDialog(DIALOG_LOAD_FIELDFILECSV);
						break;
						
					case 1:
						createDialog(DIALOG_LOAD_FIELDFILEEXCEL);
						break;
				}

			}
		};

		fieldList2.setAdapter(new GenericArrayAdapter(this, R.layout.listitem_a, items6, csvListener));
		
		// list current field values
		fieldCurrentDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
		fieldCurrentDialog.setTitle(getString(R.string.fields));
		fieldCurrentDialog.setContentView(R.layout.currentsettings);

		fieldCurrentDialog.setCancelable(true);
		fieldCurrentDialog.setCanceledOnTouchOutside(true);
		
		Button fdCloseBtn3 = (Button) fieldCurrentDialog.findViewById(R.id.closeBtn);
		
		fdCloseBtn3.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				fieldCurrentDialog.dismiss();				
			}
		});
		
		final TextView fieldColumns = (TextView) fieldCurrentDialog.findViewById(R.id.text);
				
		// field dialog
		
		fieldDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
		fieldDialog.setTitle(getString(R.string.fields));
		fieldDialog.setContentView(R.layout.genericdialog);

		fieldDialog.setCancelable(true);
		fieldDialog.setCanceledOnTouchOutside(true);
		
		Button fdCloseBtn = (Button) fieldDialog.findViewById(R.id.closeBtn);
		
		fdCloseBtn.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				fieldDialog.dismiss();				
			}
		});
		
		ListView fieldList = (ListView) fieldDialog.findViewById(R.id.myList);

		String[] items5 = new String[] { getString(R.string.importfields), 
		getString(R.string.listfieldimport)};

		OnItemClickListener fieldListener = new OnItemClickListener() {

			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {

				fieldDialog.dismiss();

				switch (position) {
					case 0:
						fieldDialog2.show();						
						break;
						
					case 1:
						if (MainActivity.dt.getRangeColumnsAsString() != null)
							fieldColumns.setText(MainActivity.dt.getRangeColumnsAsString());
						else
							fieldColumns.setText(getString(R.string.none));
						
						fieldCurrentDialog.show();
						break;
				}

			}
		};
		
		fieldList.setAdapter(new GenericArrayAdapter(this, R.layout.listitem_a,
				items5, fieldListener));

		// To configure location
		locationDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
		locationDialog.setTitle(getString(R.string.locationsetup));
		locationDialog.setContentView(R.layout.location);

		android.view.WindowManager.LayoutParams langParams = locationDialog.getWindow().getAttributes();
		langParams.width = LayoutParams.FILL_PARENT;
		locationDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) langParams);
		
		locationDialog.setCancelable(true);
		locationDialog.setCanceledOnTouchOutside(true);
		
		currentLocation = (TextView) locationDialog
				.findViewById(R.id.currentLocation);

		Button findLocation = (Button) locationDialog
				.findViewById(R.id.getLctnBtn);
		Button yesLocation = (Button) locationDialog.findViewById(R.id.saveBtn);

		final TextView longitude = (TextView) locationDialog
				.findViewById(R.id.longitude);
		final TextView latitude = (TextView) locationDialog
				.findViewById(R.id.latitude);

		yesLocation.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				Editor e = ep.edit();

				e.putString("Latitude", latitude.getText().toString());
				e.putString("Longitude", longitude.getText().toString());

				e.commit();

				updateSetupList();
				
				locationDialog.dismiss();
			}
		});

		final LocationListener locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				lat = location.getLatitude();
				lng = location.getLongitude();
			}

			public void onProviderDisabled(String provider) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}
		};

		LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 100,
				locationListener);

		findLocation.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				
				latitude.setText(truncateDecimalString(String.valueOf(lat)));
				longitude.setText(truncateDecimalString(String.valueOf(lng)));
			}
		});

		// This is the list of items shown on the settings screen itself
		settingsList = (ListView) findViewById(R.id.myList);

		Button mainCloseBtn = (Button) findViewById(R.id.closeBtn);
		
		mainCloseBtn.setVisibility(View.GONE);
		
		String[] items2 = new String[] { getString(R.string.setup), getString(R.string.fields), 
		getString(R.string.traits), getString(R.string.export), getString(R.string.advanced), 
		getString(R.string.language), getString(R.string.tutorial)};

		mainSettingsListener = new OnItemClickListener() {

			public void onItemClick(AdapterView<?> arg0, View arg1,
					int position, long arg3) {
				// Determines the action to perform based on list selection
				switch (position) {
				case 0:
					showSetupDialog();
					break;
					
				case 1:
					fieldDialog2.show();	
					break;

				case 2:
					Intent intent = new Intent(); 
					intent.setClassName(ConfigActivity.this,
							TraitEditorActivity.class.getName());
					startActivity(intent);												
					break;
					
				case 3:
					if (!ep.getBoolean("ImportFieldFinished", false))
						return;
					
					showSaveDialog();
					break;

				case 4:
					advancedDialog.show();
					break;
				
				case 5:
					showLanguageDialog();
					break;
					
				case 6:
					showTutorialDialog();
					break;
					
				}
				
			}
		};

		settingsList.setAdapter(new GenericArrayAdapter(this, R.layout.listitemhighlight,
				items2, mainSettingsListener));
		
		settingsList.setOnItemClickListener(mainSettingsListener);
		
		if (!ep.getBoolean("UpdateShown", false))
		{
			// Show latest updates
			AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this);
			
		    builder.setTitle(getString(R.string.updatemsg));
		    builder.setMessage(readRawTextFile(ConfigActivity.this, R.raw.changelog));

		    builder.setPositiveButton(getString(R.string.okay), new DialogInterface.OnClickListener() 
		    {

		        public void onClick(DialogInterface dialog, int which) 
		        {
		        	Editor ed = ep.edit();
		        	ed.putBoolean("UpdateShown", true);				        	
		        	ed.commit();
		        	
		        	dialog.dismiss();	
		        }

		    });
	    
		    AlertDialog alert = builder.create();
		    alert.show();			
		}		
	}
	
	// Only used for truncating lat long values
	private String truncateDecimalString(String v)
	{
		int count = 0;
		
		boolean found = false;
		
		String truncated = "";
		
		for (int i = 0; i < v.length(); i++)
		{
			if (found)
			{
				count += 1;
				
				if (count == 5)
					break;
			}
			
			if (v.charAt(i) == '.')
			{
				found = true;
			}
			
			truncated += v.charAt(i);
		}
		
		return truncated;
	}
	
	private void showAboutDialog()
	{
		final Dialog aboutDialog = new Dialog(ConfigActivity.this,
				android.R.style.Theme_Holo_Light_Dialog);
		aboutDialog.setTitle(getString(R.string.about));
		aboutDialog.setContentView(R.layout.about);

		android.view.WindowManager.LayoutParams langParams = aboutDialog.getWindow().getAttributes();
		langParams.width = LayoutParams.FILL_PARENT;
        aboutDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) langParams);
		
		aboutDialog.setCancelable(true);
		aboutDialog.setCanceledOnTouchOutside(true);

		TextView version = (TextView) aboutDialog
				.findViewById(R.id.version);
		Button closeBtn = (Button) aboutDialog
				.findViewById(R.id.closeBtn);

		closeBtn.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				aboutDialog.dismiss();
			}
		});

		try {
			version.setText("Version: "
					+ getPackageManager().getPackageInfo(
							getPackageName(), 0).versionName);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}

		aboutDialog.show();						
	}
	
	private void showTutorialDialog()
	{		
		// Do not show tutorial when tips / hints is open
		if (helpActive)
			return;
			
        if (ep.getBoolean("Tips", false))
        {
        	try
        	{
        		settingsList.setVisibility(View.INVISIBLE);
        		
		        final View tutView = LayoutInflater.from(getBaseContext()).inflate(R.layout.help, null);
		        mFrame.addView(tutView);
		        
		        Button button = (Button) findViewById(R.id.completeButton);
	            
	            button.setOnClickListener(new View.OnClickListener() {
	            	
	            	public void onClick(final View v) {
	                    mFrame.removeView(tutView);
	                    settingsList.setVisibility(View.VISIBLE);
	            	}
	            });
        	}
        	catch (Exception e)
        	{
        		e.printStackTrace();
        	}
        }
        else
        {
        	try
        	{
		        final View tutView = LayoutInflater.from(getBaseContext()).inflate(R.layout.help2, null);
		        mFrame.addView(tutView);
		        
		        Button button = (Button) findViewById(R.id.completeButton);
	            
	            button.setOnClickListener(new View.OnClickListener() {
	            	
	            	public void onClick(final View v) {
	                    mFrame.removeView(tutView);
	            	}
	            });
        	}
        	catch (Exception e)
        	{
        		e.printStackTrace();
        	}        	
        }
	}
	
	// Validate that columns are unique
	private boolean checkImportColumnNames()
	{
		idColPosition = findColValue(importMain, 0, false);
		
		if (idColPosition == -1)
		{
			Toast.makeText(ConfigActivity.this, getString(R.string.chooseunique), 
			Toast.LENGTH_LONG).show();
			return false;
		}								
		
		uPosition = findColValue(importMain, 0, false);
		fPosition = findColValue(importMain, 1, false);
		
		if (fPosition == -1)
		{
			Toast.makeText(ConfigActivity.this, getString(R.string.choosefirst), 
			Toast.LENGTH_LONG).show();
			return false;
		}								
		
		sPosition = findColValue(importMain, 2, false);
		
		if (sPosition == -1)
		{
			Toast.makeText(ConfigActivity.this, getString(R.string.choosesecond), 
			Toast.LENGTH_LONG).show();
			return false;
		}								
		
		ePosition = findColValue(importMain, 3, false);
				
		return true;
	}
	
	private Runnable exportData = new Runnable() 
	{
		public void run() 
	    {
			new ExportDataTask().execute(0);			
		}
	};
		
	private class ExportDataTask extends AsyncTask<Integer, Integer, Integer> 
	{
		boolean fail;
		
		ProgressDialog dialog;
		
		@Override
		protected void onPreExecute() 
		{
			super.onPreExecute();
			fail = false;
			  
			dialog = new ProgressDialog(ConfigActivity.this);
			dialog.setIndeterminate(true);
			dialog.setCancelable(false);
			dialog.setMessage(Html
					.fromHtml(getString(R.string.exportmsg)));
			dialog.show();
		}		
			
		@Override
		protected Integer doInBackground(Integer... params) 
		{	        
			String[] newRanges = newRange.toArray(new String[newRange.size()]);
			
			if (checkDB.isChecked() & !checkExcel.isChecked())
			{
				// Retrieves the data needed for export
				Cursor exportData = MainActivity.dt.getExportDBData(newRanges);

				// Do not proceed if there is no data
				// So it will not override existing files
				if (exportData.getCount() > 0) {
					try {
						File file = new File(MainActivity.fieldExportPath, 
						exportFile.getText().toString() + "_database.csv");

						if (file.exists())
							file.delete();

						FileWriter fw = new FileWriter(file);

						CSVWriter csvWriter = new CSVWriter(fw, exportData);

						csvWriter.writeFile(newRange, ep.getString("FirstName", "") + "_"
						+ ep.getString("LastName", ""), ep.getString("Latitude", "") + ";" + 
						ep.getString("Longitude", ""), ep.getBoolean("UseDay", false));

					} catch (Exception e) {
						fail = true;
					}
				}
			}
			else
			if (checkExcel.isChecked() & !checkDB.isChecked())
			{
				// Retrieves the data needed for export
				Cursor exportData = MainActivity.dt.getExportExcelData(newRanges);

				// Do not proceed if there is no data
				// So it will not override existing files
				if (exportData.getCount() > 0) {
					try {
						File file = new File(MainActivity.fieldExportPath, 
						exportFile.getText().toString() + "_table.csv");

						if (file.exists())
							file.delete();

						FileWriter fw = new FileWriter(file);

						CSVWriter csvWriter = new CSVWriter(fw, exportData);
							
						// Total number of columns to write
						String[] range = newRanges;
						
						String[] traits = MainActivity.dt.getAllTraits();
						
						csvWriter.writeFile2(concat(range, traits), range.length, 
						MainActivity.dt.findRangeColumns(ep.getString("ImportUniqueName", ""), range), 
						traits, ep.getBoolean("UseDay", false));

					} catch (Exception e) {
						fail = true;
					}
				}
			}
			else
			{
				// Retrieves the data needed for export
				Cursor exportData = MainActivity.dt.getExportDBData(newRanges);

				// Do not proceed if there is no data
				// So it will not override existing files
				if (exportData.getCount() > 0) {
					try {
						File file = new File(MainActivity.fieldExportPath, 
						exportFile.getText().toString() + "_database.csv");

						if (file.exists())
							file.delete();

						FileWriter fw = new FileWriter(file);

						CSVWriter csvWriter = new CSVWriter(fw, exportData);

						csvWriter.writeFile(newRange, ep.getString("FirstName", "") + "_"
						+ ep.getString("LastName", ""), ep.getString("Latitude", "") + ";" + 
						ep.getString("Longitude", ""), ep.getBoolean("UseDay", false));

					} catch (Exception e) {
						fail = true;
					}
				}
				
				if (fail)
					return 0;
				
				// Retrieves the data needed for export
				exportData = MainActivity.dt.getExportExcelData(newRanges);

				// Do not proceed if there is no data
				// So it will not override existing files
				if (exportData.getCount() > 0) {
					try {
						File file = new File(MainActivity.fieldExportPath, 
						exportFile.getText().toString() +  "_table.csv");

						if (file.exists())
							file.delete();

						FileWriter fw = new FileWriter(file);

						CSVWriter csvWriter = new CSVWriter(fw, exportData);
							
						// Total number of columns to write
						//String[] range = MainActivity.dt.getRangeColumns();
						String[] range = newRanges;
						
						String[] traits = MainActivity.dt.getAllTraits();
						
						csvWriter.writeFile2(concat(range, traits), range.length, 
						MainActivity.dt.findRangeColumns(ep.getString("ImportUniqueName", ""), range), 
						traits, ep.getBoolean("UseDay", false));

					} catch (Exception e) {
						fail = true;
					}
				}
				
			}
				
			return 0;
		}

		@Override		
		protected void onPostExecute(Integer result) 
		{
			newRange.clear();

			if (dialog.isShowing())
				dialog.dismiss();

			if (fail)
			{
				Toast.makeText(ConfigActivity.this,
						getString(R.string.exporterror), Toast.LENGTH_LONG)
						.show();
			}
						
		}
	}
	
	// Helper function to read changelog file
	public static String readRawTextFile(Context contex, int resId)
	{
	    InputStream inputStream = contex.getResources().openRawResource(resId);

	    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

	    int i;
	    
	    try {
	        i = inputStream.read();
	        while (i != -1)
	        {
	            byteArrayOutputStream.write(i);
	            i = inputStream.read();
	        }
	        inputStream.close();
	    } 
	    catch (IOException e) {
	        return "";
	    }
	    
	    return byteArrayOutputStream.toString();
	}
	
	/**
	 * Creates a list of all files in directory by type 
	 */
	private void loadFileList(final String type) {
		
		File importPath = new File(MainActivity.fieldImportPath);
		
		if (importPath.exists()) {
			FilenameFilter filter = new FilenameFilter() {
				public boolean accept(File dir, String filename) {
					File sel = new File(dir, filename);
					return filename.contains(type);
				}
			};
			mFileList = importPath.list(filter);
		} 
		else {
			mFileList = new String[0];
		}
	}
	
	// Because the import dialog layout is created dynamically, this function
	// loops through child views and checks spinner values for duplicate values
	private int findColValue(LinearLayout parent, int value, boolean allowDuplicate)
	{
		int count = 0;
		int found = -1;
		
		for (int i = 0; i < parent.getChildCount(); i++)
		{
			LinearLayout child = (LinearLayout) parent.getChildAt(i);
			
			Spinner c = (Spinner) child.findViewById(R.id.map);
			TextView v = (TextView) child.findViewById(R.id.column);
			
			if (allowDuplicate)
			{
				if (c.getSelectedItemPosition() == value)
					return i;
			}
			else
			{
				if (c.getSelectedItemPosition() == value)
				{
					if (found == -1)
						found = i;
				
					count += 1;
				}
			}
		}
		
		if (allowDuplicate)
			return -1;
		else
		{
			if (count > 1)
				return -1;
			else
				return found;
		}
	}

	// Helper function to see if the import column matches any of the spinner values
	// If it does, set it to the appropriate value
	private void findMatchingColumn(Spinner s, String col)
	{
		if (col.toLowerCase().trim().contains("plot_id"))
			s.setSelection(0);
		else
			if (col.toLowerCase().trim().contains("range"))
				s.setSelection(1);
			else
				if (col.toLowerCase().trim().contains("plot"))
					s.setSelection(2);
				else
					s.setSelection(3);
	}
	
	// Ensure the values in the key column are unique for Excel workbooks
	private boolean verifyUniqueColumnExcel(Workbook wb)
	{
		HashMap<String, String> check = new HashMap<String, String>();
		
		for(int s = 0; s < wb.getSheet(0).getRows(); s++)
		{
			String value = wb.getSheet(0).getCell(idColPosition, s).getContents();
			
			if (check.containsKey(value))
			{
				return false;
			}
			else
				check.put(value, value);
		}

		return true;
	}
	
	// Ensure the key column has unique values for CSV files
	private boolean verifyUniqueColumnCSV(String path)
	{
		try
		{
			HashMap<String, String> check = new HashMap<String, String>();
			
			FileReader fr = new FileReader(path);
			
			CSVReader cr = new CSVReader(fr);
	
			String[] columns = cr.readNext();
						
			while (columns != null)
			{
				columns = cr.readNext();
				
				if (columns != null)
				{
					if (check.containsKey(columns[idColPosition]))
					{
						cr.close();
						return false;
					}
					else
						check.put(columns[idColPosition], columns[idColPosition]);
				}
			}
			
			return true;
		}
		catch (Exception n)
		{
			n.printStackTrace();
			return false;
		}
	}
	
	// Helper function to merge arrays
	String[] concat(String[] a1, String[] a2) {
		String[] n = new String[a1.length + a2.length];
		System.arraycopy(a1, 0, n, 0, a1.length);
		System.arraycopy(a2, 0, n, a1.length, a2.length);

		return n;
	}

	// Helper function to set spinner adapter and listener
	private void setSpinner(Spinner spinner, String[] data, OnItemSelectedListener listener)
	{
		GenericSpinnerAdapter adapter = new GenericSpinnerAdapter(
				ConfigActivity.this, R.layout.smallspinnerlayout3, R.layout.spinnerlayout4, data, listener);
		spinner.setAdapter(adapter);
	}
	
	public String getLocation() {
		String latStr = Double.toString(lat);
		String lngStr = Double.toString(lng);
		return "latStr" + ";" + "lngStr";
	}

	/*
	 * Helper function to create the necessary dialog screens. This is only
	 * useful for simple dialogs. Complex dialogs such as choosing traits to
	 * rate must be customized and so is not located here.
	 */
	private void createDialog(int id) {

		columnFail = false;		
		
		switch (id) {
			case DIALOG_LOAD_FIELDFILECSV:
			case DIALOG_LOAD_TRAITFILE:
				loadFileList(CSV);
				break;
			
			case DIALOG_LOAD_FIELDFILEEXCEL:
				loadFileList(XLS);
				break;
		}
	
		switch (id) {
		
			case DIALOG_LOAD_FIELDFILECSV:
				isCSV = true;
				showFieldFileCSVDialog();
				break;
	
			case DIALOG_LOAD_FIELDFILEEXCEL:
				isCSV = false;
				showFieldFileExcelDialog();
				break;
				
			case DIALOG_LOAD_TRAITFILE:
				showTraitFileDialog();
				break;
					
			case DIALOG_LOAD_TRAITS1:			
				final String[] traits1 = MainActivity.dt.getRangeColumns();
				showTrait1Dialog(traits1);
				break;
	
			case DIALOG_LOAD_TRAITS2:
				final String[] traits2 = MainActivity.dt.getRangeColumns();
				showTrait2Dialog(traits2);
				break;
	
			case DIALOG_LOAD_TRAITS3:
				final String[] traits3 = MainActivity.dt.getRangeColumns();
				showTrait3Dialog(traits3);
				break;
			
		}

	}

	private void showLanguageDialog()
	{
		final Dialog languageDialog = new Dialog(ConfigActivity.this,
				android.R.style.Theme_Holo_Light_Dialog);
		languageDialog.setTitle(getString(R.string.language));
		languageDialog.setContentView(R.layout.genericdialog);

		android.view.WindowManager.LayoutParams params = languageDialog.getWindow().getAttributes();
        params.width = LayoutParams.FILL_PARENT;
        languageDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
		
        languageDialog.setCancelable(true);
        languageDialog.setCanceledOnTouchOutside(true);

		ListView myList = (ListView) languageDialog
				.findViewById(R.id.myList);
		
		String[] langArray = new String[3];
		
		langArray[0] = getString(R.string.english);
		langArray[1] = getString(R.string.spanish);
		langArray[2] = getString(R.string.german);
		
		OnItemClickListener listener = new OnItemClickListener() {

			public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
				
				Editor ed = ep.edit();
				ed.putInt("languages", which);
				ed.commit();
				
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
		    	            getBaseContext().getResources().
		    	            updateConfiguration(config2, getBaseContext().getResources()
		    	            .getDisplayMetrics()); 
		    	    		
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
		    	            getBaseContext().getResources().
		    	            updateConfiguration(config2, getBaseContext().getResources()
		    	            .getDisplayMetrics()); 
		    	    		
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
		    	            getBaseContext().getResources().
		    	            updateConfiguration(config2, getBaseContext().getResources()
		    	            .getDisplayMetrics()); 
		    	    		
		    	            invalidateOptionsMenu();
		    	            
		    	    		loadScreen();
		    	    	}
		    	    	break;
		    	    	
		        }        
										
				languageDialog.dismiss();
			}
		};
						
		GenericArrayAdapter itemsAdapter = new GenericArrayAdapter(ConfigActivity.this, 
		R.layout.listitem_a, langArray, listener);
		
		myList.setAdapter(itemsAdapter);	
		
		Button langCloseBtn = (Button) languageDialog
				.findViewById(R.id.closeBtn);

		langCloseBtn.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				languageDialog.dismiss();
			}
		});		
		
		languageDialog.show();		
	}
	
	private String[] prepareSetup()
	{
		String tagName = "";
		String tagLocation = "";
		String tagTraits = "";
		String tagDrop1 = "";
		String tagDrop2 = "";
		String tagDrop3 = "";
							
		if (ep.getString("FirstName", "").length() > 0 | ep.getString("LastName", "").length() > 0)
			tagName += getString(R.string.person) + ": " + ep.getString("FirstName", "") 
			+ " " + ep.getString("LastName", "");
		else
			tagName += getString(R.string.person) + ": " + getString(R.string.none);
		
		if (ep.getString("Latitude", "").length() > 0 | ep.getString("Longitude", "").length() > 0)
			tagLocation += getString(R.string.location) + ": " + ep.getString("Latitude", "") 
			+ "," + ep.getString("Longitude", "");
		else
			tagLocation += getString(R.string.location) + ": " + getString(R.string.none);
																					
		if (MainActivity.dt.getTraitColumnsAsString() != null)
			tagTraits = getString(R.string.traits) + ": " + MainActivity.dt.getTraitColumnsAsString2();
		else
			tagTraits = getString(R.string.traits) + ": " + getString(R.string.none);
		
		if (ep.getString("DROP1", "").length() > 0)
			tagDrop1 = getString(R.string.drop1) + ": " + ep.getString("DROP1", "");
		else
			tagDrop1 = getString(R.string.drop1) + ": " + getString(R.string.none);

		if (ep.getString("DROP2", "").length() > 0)
			tagDrop2 = getString(R.string.drop2) + ": " + ep.getString("DROP2", "");
		else
			tagDrop2 = getString(R.string.drop2) + ": " + getString(R.string.none);

		if (ep.getString("DROP3", "").length() > 0)
			tagDrop3 = getString(R.string.drop3) + ": " + ep.getString("DROP3", "");
		else
			tagDrop3 = getString(R.string.drop3) + ": " + getString(R.string.none);
		
		return new String[] { tagName, tagLocation, tagTraits, 
			tagDrop1, tagDrop2, tagDrop3, getString(R.string.clearsettings)};		
	}
	
	private void updateSetupList()
	{
		GenericArrayAdapter ga = (GenericArrayAdapter) setupList.getAdapter();
		
		ga.data = prepareSetup();
		ga.notifyDataSetChanged();
	}
		
	private void showSaveDialog()
	{
		final OnItemClickListener saveListener = new OnItemClickListener(){

			public void onItemClick(AdapterView<?> av, View v, int position,
					long arg3) {
				
				if (checkExcel.isChecked())
				{
					if (saveList.getAdapter().getItem(position).toString().toUpperCase().
					equals(ep.getString("ImportUniqueName", "").toUpperCase()))
					{
						saveList.setItemChecked(position, true);
						return;
					}
				}
			}
		};
				
		// As the export filename uses the import file name as well,
		// we parse it out here
		String fName = "";
		SimpleDateFormat timeStamp = new SimpleDateFormat(
				"yyyy.MM.dd", Locale.getDefault());

		if (ep.getString("FieldFile", "").length() > 0) {
			int index = ep.getString("FieldFile", "").lastIndexOf(
					'.');

			if (index > 0
					&& index <= ep.getString("FieldFile", "")
							.length() - 2) {
				fName = ep.getString("FieldFile", "").substring(0,
						index);
			}

		}

		String fFile = ep.getString("FieldFile", "");
		
		if (fFile.length() > 4 & fFile.toLowerCase().endsWith(".csv"))
		{
			fFile = fFile.substring(0, fFile.length() -4);
		}
		
		exportFile.setText(timeStamp.format(Calendar.getInstance().getTime()) + "_" + fFile);			
		
		saveList.setAdapter(new GenericCheckedArrayAdapter(ConfigActivity.this, 
		R.layout.listitem3_a, MainActivity.dt.getRangeColumns(), saveListener));
		
		for (int i = 0; i < saveList.getAdapter().getCount(); i++) {
			
			saveList.setItemChecked(i, true);
		}
		
		saveDialog.show();		
	}
	
	private void showSetupDialog()
	{
		String[] items = prepareSetup();
		
		final OnItemClickListener setupListener = new OnItemClickListener(){

			public void onItemClick(AdapterView<?> arg0, View arg1, int position,
					long arg3) {
				
				switch (position)
				{
				case 0:
					showPersonDialog();
					break;
					
				case 1:
					showLocationDialog();
					break;
					
				case 2:
					showRateDialog();
					break;		
					
				case 3:
					if (MainActivity.dt.getRangeColumns() == null)
						return;
					
					createDialog(DIALOG_LOAD_TRAITS1);
					break;
					
				case 4:
					if (MainActivity.dt.getRangeColumns() == null)
						return;
					
					createDialog(DIALOG_LOAD_TRAITS2);
					break;
					
				case 5:
					if (MainActivity.dt.getRangeColumns() == null)
						return;
					
					createDialog(DIALOG_LOAD_TRAITS3);
					break;	
					
				case 6:
					showClearSettingsDialog();
					break;
					
				}
			}
		};		
		
		setupList.setAdapter(new GenericArrayAdapter(ConfigActivity.this, R.layout.listitem_a,
				items, setupListener));
		
		setupDialog.show();		
	}
	
	private void showRateDialog()
	{
		// Gets all traits from database
		
		if (MainActivity.dt.getAllTraits() == null)
			return;
		
		String[] traits = MainActivity.dt.getAllTraits();

		HashMap visibility = MainActivity.dt.getTraitVisibility();

		// Check or uncheck the list items based on existing
		// visibility
		if (traits != null) {
			rateList.setAdapter(new GenericCheckedArrayAdapter(
					ConfigActivity.this, R.layout.listitem2_a, traits, null));

			int count = rateList.getAdapter().getCount();

			for (int i = 0; i < count; i++) {
				String currentItem = (String) rateList.getAdapter()
						.getItem(i);

				if (visibility.get(currentItem).equals("true")) {
					rateList.setItemChecked(i, true);
				}
			}
		}

		rateDialog.show();		
	}
	
	private void showPersonDialog()
	{
		if (ep.getString("FirstName", "").length() > 0
				|| ep.getString("LastName", "").length() > 0)
			currentPerson.setText("Current: "
					+ ep.getString("FirstName", "") + " "
					+ ep.getString("LastName", ""));
		else
			currentPerson.setText(getString(R.string.current) + ": " + getString(R.string.none));

		personDialog.show();		
	}
	
	private void showLocationDialog()
	{
		LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);

		boolean enabled = service
				.isProviderEnabled(LocationManager.GPS_PROVIDER);

		if (!enabled) {
			Intent intent = new Intent(
					Settings.ACTION_LOCATION_SOURCE_SETTINGS);
			startActivity(intent);
		}
		if (ep.getString("Latitude", "").length() > 0
				|| ep.getString("Longitude", "").length() > 0)
			currentLocation.setText("Current:\n" + " Latitude: "
					+ ep.getString("Latitude", "")
					+ "\n Longitude: "
					+ ep.getString("Longitude", ""));
		else
			currentLocation.setText(getString(R.string.current) + ": " + getString(R.string.none));

		locationDialog.show();		
	}
	
	private void showClearSettingsDialog()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(ConfigActivity.this);
		
	    builder.setTitle(getString(R.string.clearsettings));
	    builder.setMessage(getString(R.string.areyousure));

	    builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() 
	    {

	        public void onClick(DialogInterface dialog, int which) 
	        {
	        	setupDialog.dismiss();
	        	
	        	dialog.dismiss();
	        	
				Editor ed = ep.edit();
				ed.putString("FirstName", "");
				ed.putString("LastName", "");
				ed.putString("Latitude", "");
				ed.putString("Longitude", "");
				ed.putString("DROP1", "");
				ed.putString("DROP2", "");
				ed.putString("DROP3", "");
				ed.commit();
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
	
	private void showTrait3Dialog(final String[] traits)
	{
		final Dialog dialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);

		dialog.setTitle(getString(R.string.currentColumn) + ": " + ep.getString("DROP3", ""));
		dialog.setContentView(R.layout.genericdialog);
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(true);

		ListView csvList = (ListView) dialog.findViewById(R.id.myList);
		Button csvButton = (Button) dialog.findViewById(R.id.closeBtn);
		
		csvButton.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				dialog.dismiss();
			}
		});

		OnItemClickListener listener = new OnItemClickListener() {

			public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
				dialog.dismiss();
				
				Editor e = ep.edit();

				e.putString("DROP3", traits[which]);
				e.commit();
				
				MainActivity.partialReload = true;
				
				updateSetupList();
			}
		};
						
		GenericArrayAdapter itemsAdapter = new GenericArrayAdapter(ConfigActivity.this, 
		R.layout.listitem_a, traits, listener);
		
		csvList.setAdapter(itemsAdapter);				
	
		dialog.show();
	}
	
	private void showTrait2Dialog(final String[] traits)
	{
		final Dialog dialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);

		dialog.setTitle(getString(R.string.currentColumn) + ": " + ep.getString("DROP2", ""));
		dialog.setContentView(R.layout.genericdialog);
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(true);

		ListView csvList = (ListView) dialog.findViewById(R.id.myList);
		Button csvButton = (Button) dialog.findViewById(R.id.closeBtn);
		
		csvButton.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				dialog.dismiss();
			}
		});

		OnItemClickListener listener = new OnItemClickListener() {

			public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
				dialog.dismiss();
				
				Editor e = ep.edit();

				e.putString("DROP2", traits[which]);
				e.commit();
				
				MainActivity.partialReload = true;
				
				updateSetupList();
			}
		};
						
		GenericArrayAdapter itemsAdapter = new GenericArrayAdapter(ConfigActivity.this, 
		R.layout.listitem_a, traits, listener);
		
		csvList.setAdapter(itemsAdapter);				
	
		dialog.show();
	}
		
	private void showTrait1Dialog(final String[] traits)
	{
		final Dialog dialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);

		dialog.setTitle(getString(R.string.currentColumn) + ": " + ep.getString("DROP1", ""));
		dialog.setContentView(R.layout.genericdialog);
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(true);

		ListView csvList = (ListView) dialog.findViewById(R.id.myList);
		Button csvButton = (Button) dialog.findViewById(R.id.closeBtn);
		
		csvButton.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				dialog.dismiss();
			}
		});

		OnItemClickListener listener = new OnItemClickListener() {

			public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
				dialog.dismiss();
				
				Editor e = ep.edit();

				e.putString("DROP1", traits[which]);
				e.commit();
				
				MainActivity.partialReload = true;
				
				updateSetupList();
			}
		};
						
		GenericArrayAdapter itemsAdapter = new GenericArrayAdapter(ConfigActivity.this, 
		R.layout.listitem_a, traits, listener);
		
		csvList.setAdapter(itemsAdapter);				
	
		dialog.show();
	}
	
	private void showTraitFileDialog()
	{
		final Dialog dialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);

		dialog.setTitle(getString(R.string.choosetraitfile));
		dialog.setContentView(R.layout.genericdialog);
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(true);

		ListView csvList = (ListView) dialog.findViewById(R.id.myList);
		Button csvButton = (Button) dialog.findViewById(R.id.closeBtn);
		
		csvButton.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				dialog.dismiss();
			}
		});

		OnItemClickListener listener = new OnItemClickListener() {

			public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
				dialog.dismiss();
				
				mChosenFile = mFileList[which];
				action = DIALOG_LOAD_TRAITFILE;

				mHandler.post(importCSV);
			}
		};
						
		GenericArrayAdapter itemsAdapter = new GenericArrayAdapter(ConfigActivity.this, 
		R.layout.listitem_a, mFileList, listener);
		
		csvList.setAdapter(itemsAdapter);				
	
		dialog.show();
	}
	
	private void showFieldFileCSVDialog()
	{
		final Dialog dialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);

		dialog.setTitle(getString(R.string.choosefieldfile));
		dialog.setContentView(R.layout.genericdialog);
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(true);

		ListView csvList = (ListView) dialog.findViewById(R.id.myList);
		Button csvButton = (Button) dialog.findViewById(R.id.closeBtn);
		
		csvButton.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				dialog.dismiss();
			}
		});


		OnItemClickListener listener = new OnItemClickListener() {

			public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
				dialog.dismiss();
				
				mChosenFile = mFileList[which];

				Editor e = ep.edit();

				e.putString("FieldFile", mChosenFile);

				e.commit();

				action = DIALOG_LOAD_FIELDFILECSV;

				try
				{
					importMain.removeAllViews();
					
					FileReader fr = new FileReader(MainActivity.fieldImportPath + "/"
							+ mChosenFile);
					CSVReader cr = new CSVReader(fr);

					importColumns = cr.readNext();
					
					for (String s : importColumns)
					{
						if (DataHelper.hasSpecialChars(s))
						{
							columnFail = true;
							break;
						}
						else
							addRow(importMain, s);									
					}
				}
				catch (Exception n)
				{
					
				}
				
				if (columnFail)
					Toast.makeText(ConfigActivity.this, getString(R.string.columnfail), Toast.LENGTH_LONG).show();
				else
					importFieldMapDialog.show();
				
				//mHandler.post(importCSV);				
			}
		};
						
		GenericArrayAdapter itemsAdapter = new GenericArrayAdapter(ConfigActivity.this, 
		R.layout.listitem_a, mFileList, listener);
		
		csvList.setAdapter(itemsAdapter);				
	
		dialog.show();
	}

	private void showFieldFileExcelDialog()
	{
		final Dialog dialog = new Dialog(ConfigActivity.this, android.R.style.Theme_Holo_Light_Dialog);

		dialog.setTitle(getString(R.string.choosefieldfile));
		dialog.setContentView(R.layout.genericdialog);
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(true);

		ListView csvList = (ListView) dialog.findViewById(R.id.myList);
		Button csvButton = (Button) dialog.findViewById(R.id.closeBtn);
		
		csvButton.setOnClickListener(new OnClickListener(){

			public void onClick(View v) {
				dialog.dismiss();
			}
		});


		OnItemClickListener listener = new OnItemClickListener() {

			public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
				dialog.dismiss();
				
				mChosenFile = mFileList[which];

				Editor e = ep.edit();

				e.putString("FieldFile", mChosenFile);

				e.commit();

				action = DIALOG_LOAD_FIELDFILEEXCEL;

				WorkbookSettings wbSettings = new WorkbookSettings();
				wbSettings.setUseTemporaryFileDuringWrite(true);  
				
				try
				{
					importMain.removeAllViews();
					
					wb = Workbook.getWorkbook(new File (MainActivity.fieldImportPath + "/"
							+ mChosenFile),wbSettings);
					
					importColumns = new String[wb.getSheet(0).getColumns()];
					
					for(int s = 0; s < wb.getSheet(0).getColumns(); s++)
					{
						importColumns[s] = wb.getSheet(0).getCell(s,0).getContents();
					}
					
					for (String s : importColumns)
					{
						if (DataHelper.hasSpecialChars(s))
						{
							columnFail = true;
							break;
						}
						else									
							addRow(importMain, s);									
					}
				}
				catch (Exception n)
				{
					
				}

				if (columnFail)
					Toast.makeText(ConfigActivity.this, getString(R.string.columnfail), 
					Toast.LENGTH_LONG).show();
				else							
					importFieldMapDialog.show();
			}
		};
						
		GenericArrayAdapter itemsAdapter = new GenericArrayAdapter(ConfigActivity.this, 
		R.layout.listitem_a, mFileList, listener);
		
		csvList.setAdapter(itemsAdapter);				
	
		dialog.show();
	}
	
	// Helper function to add rows to import dialog
	public void addRow(LinearLayout parent, String text)
	{
		LayoutInflater vi = (LayoutInflater) getApplicationContext().
		getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		View v = vi.inflate(R.layout.importrow, null);

		TextView c = (TextView) v.findViewById(R.id.column);
		final Spinner s = (Spinner) v.findViewById(R.id.map);
		
		c.setText(text);
		
		String[] likes = new String[4];
				
		likes[0] = getString(R.string.importunique);
		likes[1] = getString(R.string.importfirst);
		likes[2] = getString(R.string.importsecond);
		likes[3] = getString(R.string.importextra);
		
		OnItemSelectedListener listener = new OnItemSelectedListener() {

			public void onItemSelected(AdapterView<?> av, View view,
					int position, long id) {
				
				s.setSelection(position);
				
				checkImportColumnNames();
			}

			public void onNothingSelected(AdapterView<?> arg0) {
			}
		};
		
		setSpinner(s, likes, listener);
		
		findMatchingColumn(s, text);
		
		parent.addView(v);
		
	}
	
	// Creates a new thread to do importing
	private Runnable importCSV = new Runnable() {
		public void run() {
			new ImportCSVTask().execute(0);
		}
	
	};
	
	private class ImportCSVTask extends AsyncTask<Integer, Integer, Integer> {
		ProgressDialog dialog;
	
		boolean fail;
		boolean uniqueFail;
	
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
	
			dialog = new ProgressDialog(ConfigActivity.this);
			dialog.setIndeterminate(true);
			dialog.setCancelable(false);
			dialog.setMessage(Html
					.fromHtml(getString(R.string.importmsg)));
			dialog.show();
		}
	
		@Override
		protected Integer doInBackground(Integer... params) {
			try {
				String[] data;
	
				String[] columns;
	
				//verify unique
				if (!verifyUniqueColumnCSV(MainActivity.fieldImportPath + "/" + mChosenFile))
				{
					uniqueFail = true;
					
					return 0;
				}
				
				FileReader fr = new FileReader(MainActivity.fieldImportPath + "/"
						+ mChosenFile);
				
				CSVReader cr = new CSVReader(fr);
	
				columns = cr.readNext();
				
				if (action == DIALOG_LOAD_FIELDFILECSV) {
					MainActivity.dt.dropRange();
					MainActivity.dt.createRange(columns);
	
					Editor e = ep.edit();
	
					e.putString("DROP1", "");
					e.putString("DROP2", "");
					e.putString("DROP3", "");
	
					e.commit();
				} else {
					MainActivity.dt.deleteTable(MainActivity.dt.TRAITS);
				}
	
				data = columns;
	
				while (data != null) {
					data = cr.readNext();
	
					if (data != null) {
						switch (action) {
						case DIALOG_LOAD_FIELDFILECSV:
							MainActivity.dt.insertRange(columns, data);
							break;
	
						case DIALOG_LOAD_TRAITFILE:
							MainActivity.dt.insertTraits(data[0], data[1],
									data[2], data[3], data[4], data[5],
									data[6], "true", "");
	
							String[] plots = MainActivity.dt.getPlotID();
	
							// For boolean data, always remove existing data
							// first
							// Then reinsert the default value
							if (data[1].equals("boolean")) {
								if (plots != null) {
									MainActivity.dt.deleteAllBoolean(data[0]);
	
									for (String plot : plots) {
										MainActivity.dt.insertUserTraits(plot, data[0],
												"boolean", "false");
									}
								}
							}
							break;
						}
					}
				}
	
				try {
					cr.close();
				} catch (Exception e) {
	
				}
	
				try {
					fr.close();
				} catch (Exception e) {
	
				}
	
				// These 2 lines are necessary due to importing of range data.
				// As the table is dropped and recreated,
				// changes are not visible until you refresh the database
				// connection
				MainActivity.dt.close();
				MainActivity.dt.open();
	
				File newDir = new File (MainActivity.fieldImportPath + "/"
						+ mChosenFile);
						
				newDir.mkdirs();
				
			} catch (Exception e) {
				e.printStackTrace();
				fail = true;
				
				//recreate empty default table on fail
				MainActivity.dt.defaultFieldTable();
				
				MainActivity.dt.close();
				MainActivity.dt.open();			
			}
	
			return 0;
		}
	
		@Override
		protected void onPostExecute(Integer result) {
			if (dialog.isShowing())
				dialog.dismiss();
	
			if (fail)
				Toast.makeText(ConfigActivity.this, getString(R.string.importerror),
						Toast.LENGTH_LONG).show();
			else
				if (uniqueFail)
					Toast.makeText(ConfigActivity.this, getString(R.string.importuniqueerror),
							Toast.LENGTH_LONG).show();
				else
				{
					Editor ed = ep.edit();
					ed.putString("ImportUniquePosition", String.valueOf(uPosition));
					ed.putString("ImportFirstPosition", String.valueOf(fPosition));
					ed.putString("ImportSecondPosition", String.valueOf(sPosition));
					ed.putString("ImportExtraPosition", String.valueOf(ePosition));
					
					ed.putString("ImportUniqueName", importColumns[uPosition]);
					ed.putString("ImportFirstName", importColumns[fPosition]);
					ed.putString("ImportSecondName", importColumns[sPosition]);				
					//ed.putString("ImportExtraName", importColumns[ePosition]);
					
					ed.putBoolean("ImportFieldFinished", true);
					
					ed.commit();
					
					MainActivity.reloadData = true;
				}
		}
	}

	private Runnable importExcel = new Runnable() {
		public void run() {
			new ImportExcelTask().execute(0);
		}
	
	};
	
	private class ImportExcelTask extends AsyncTask<Integer, Integer, Integer> {
		ProgressDialog dialog;
	
		boolean fail;
		boolean uniqueFail;
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
	
			dialog = new ProgressDialog(ConfigActivity.this);
			dialog.setIndeterminate(true);
			dialog.setCancelable(false);
			dialog.setMessage(Html
					.fromHtml(getString(R.string.importmsg)));
			dialog.show();
		}
	
		@Override
		protected Integer doInBackground(Integer... params) {
			try {
				String[] data;
	
				String[] columns;
	
				//verify unique
				if (!verifyUniqueColumnExcel(wb))
				{
					uniqueFail = true;
					
					return 0;
				}
				
				columns = new String[wb.getSheet(0).getColumns()];
				
				for(int s = 0; s < wb.getSheet(0).getColumns(); s++)
				{
					columns[s] = wb.getSheet(0).getCell(s,0).getContents();
				}
				
				if (action == DIALOG_LOAD_FIELDFILEEXCEL) {
					MainActivity.dt.dropRange();
					MainActivity.dt.createRange(columns);
	
					Editor e = ep.edit();
	
					e.putString("DROP1", "");
					e.putString("DROP2", "");
					e.putString("DROP3", "");
	
					e.commit();
				} else {
					MainActivity.dt.deleteTable(MainActivity.dt.TRAITS);
				}
	
				int row = 1;
				
				while (row < wb.getSheet(0).getRows()) {
					data = new String[wb.getSheet(0).getColumns()];
					
					for(int s = 0; s < wb.getSheet(0).getColumns(); s++)
					{
						data[s] = wb.getSheet(0).getCell(s,row).getContents();
					}
	
					row += 1;
					
					if (data != null) {
						switch (action) {
						case DIALOG_LOAD_FIELDFILEEXCEL:
							MainActivity.dt.insertRange(columns, data);
							break;
	
						case DIALOG_LOAD_TRAITFILE:
							MainActivity.dt.insertTraits(data[0], data[1],
									data[2], data[3], data[4], data[5],
									data[6], "true", "");
	
							String[] plots = MainActivity.dt.getPlotID();
	
							// For boolean data, always remove existing data
							// first
							// Then reinsert the default value
							if (data[1].equals("boolean")) {
								if (plots != null) {
									MainActivity.dt.deleteAllBoolean(data[0]);
	
									for (String plot : plots) {
										MainActivity.dt.insertUserTraits(plot, data[0],
												"boolean", "false");
									}
								}
							}
							break;
						}
					}
				}
	
				// These 2 lines are necessary due to importing of range data.
				// As the table is dropped and recreated,
				// changes are not visible until you refresh the database
				// connection
				MainActivity.dt.close();
				MainActivity.dt.open();
	
				File newDir = new File (MainActivity.fieldImportPath + "/"
						+ mChosenFile);
						
				newDir.mkdirs();
				
			} catch (Exception e) {
				e.printStackTrace();
				fail = true;
				
				//recreate empty default table on fail
				MainActivity.dt.defaultFieldTable();
				
				MainActivity.dt.close();
				MainActivity.dt.open();				
			}
	
			return 0;
		}
	
		@Override
		protected void onPostExecute(Integer result) {
			if (dialog.isShowing())
				dialog.dismiss();
	
			if (fail)
				Toast.makeText(ConfigActivity.this, getString(R.string.importerror),
						Toast.LENGTH_LONG).show();
			else
				if (uniqueFail)
					Toast.makeText(ConfigActivity.this, getString(R.string.importuniqueerror),
							Toast.LENGTH_LONG).show();
				else
				{
					Editor ed = ep.edit();
					ed.putString("ImportUniquePosition", String.valueOf(uPosition));
					ed.putString("ImportFirstPosition", String.valueOf(fPosition));
					ed.putString("ImportSecondPosition", String.valueOf(sPosition));
					ed.putString("ImportExtraPosition", String.valueOf(ePosition));
					
					ed.putString("ImportUniqueName", importColumns[uPosition]);
					ed.putString("ImportFirstName", importColumns[fPosition]);
					ed.putString("ImportSecondName", importColumns[sPosition]);				
					//ed.putString("ImportExtraName", importColumns[ePosition]);
					
					ed.putBoolean("ImportFieldFinished", true);
					
					ed.commit();
					
					MainActivity.reloadData = true;
				}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		new MenuInflater(ConfigActivity.this).inflate(R.menu.configmenu, menu);
						
		systemMenu = menu;
		
		if (systemMenu != null)
		{
			
			if (ep.getBoolean("Tips", false))
			{
				systemMenu.findItem(R.id.help).setVisible(true);
			}
			else
			{
				systemMenu.findItem(R.id.help).setVisible(false);
			}
		}
		
		return true;
	}
		
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		Intent intent = new Intent(Intent.ACTION_VIEW);
				
		switch (item.getItemId())
		{
			case R.id.help:
				intent.setClassName(ConfigActivity.this,
						TutorialSettingsActivity.class.getName());
				startActivity(intent);														
				break;
		
			case R.id.resources:
				intent.setClassName(ConfigActivity.this,
						FileExploreActivity.class.getName());
				startActivity(intent);				
				break;

			case R.id.about:
				showAboutDialog();
				break;

			case R.id.quit:
				Editor e = ep.edit();
				e.putString("Latitude", "");
				e.putString("Longitude", "");
				e.putString("FirstName", "");
				e.putString("LastName", "");
				e.commit();
				finish();				
				break;
			
			case android.R.id.home:
				finish();
				break;
		}
		
		return super.onOptionsItemSelected(item);
	}	
	
}
