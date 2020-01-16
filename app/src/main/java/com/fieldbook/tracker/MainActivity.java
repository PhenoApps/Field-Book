package com.fieldbook.tracker;

import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuInflater;

import com.fieldbook.tracker.barcodes.*;
import com.fieldbook.tracker.brapi.Observation;
import com.fieldbook.tracker.layoutConfig.SelectorLayoutConfigurator;
import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.search.*;
import com.fieldbook.tracker.traitLayouts.AngleTraitLayout;
import com.fieldbook.tracker.traitLayouts.AudioTraitLayout;
import com.fieldbook.tracker.traitLayouts.BarcodeTraitLayout;
import com.fieldbook.tracker.traitLayouts.BooleanTraitLayout;
import com.fieldbook.tracker.traitLayouts.CategoricalTraitLayout;
import com.fieldbook.tracker.traitLayouts.CounterTraitLayout;
import com.fieldbook.tracker.traitLayouts.DateTraitLayout;
import com.fieldbook.tracker.traitLayouts.DiseaseRatingTraitLayout;
import com.fieldbook.tracker.traitLayouts.LocationTraitLayout;
import com.fieldbook.tracker.traitLayouts.MultiCatTraitLayout;
import com.fieldbook.tracker.traitLayouts.NumericTraitLayout;
import com.fieldbook.tracker.traitLayouts.PercentTraitLayout;
import com.fieldbook.tracker.traitLayouts.PhotoTraitLayout;
import com.fieldbook.tracker.traitLayouts.TextTraitLayout;
import com.fieldbook.tracker.traitLayouts.LabelPrintTraitLayout;
import com.fieldbook.tracker.traitLayouts.TraitLayout;
import com.fieldbook.tracker.traits.*;
import com.fieldbook.tracker.tutorial.*;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.utilities.Utils;

import org.threeten.bp.OffsetDateTime;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static com.fieldbook.tracker.ConfigActivity.dt;

/**
 * All main screen logic resides here
 */

@SuppressLint("ClickableViewAccessibility")
public class MainActivity extends AppCompatActivity {

    private SharedPreferences ep;
    private static String displayColor = "#d50000";
    private int paging;

    private String inputPlotId = "";
    private int[] rangeID;

    private AlertDialog goToId;

    private int delay = 100;
    private int count = 1;

    private Object lock;

    public static boolean searchReload;
    public static String searchRange;
    public static String searchPlot;
    public static boolean reloadData;
    public static boolean partialReload;

    private RangeObject cRange;
    private String lastRange = "";

    public static Activity thisActivity;

    public static String TAG = "Field Book";
    private Handler repeatHandler;

    /**
     * Main screen elements
     */

    private Menu systemMenu;

    private SelectorLayoutConfigurator selectorLayoutConfigurator;

    private TextView rangeName;
    private TextView plotName;

    private EditText range;
    private EditText plot;
    private TextView tvRange;
    private TextView tvPlot;

    private ImageView rangeLeft;
    private ImageView rangeRight;
    
    private TraitBox traitBox;

    /**
     * Trait-related elements
     */
    private EditText etCurVal;
    private TextWatcher cvNum;
    private TextWatcher cvText;

    private InputMethodManager imm;

    ImageButton deleteValue;
    ImageButton missingValue;

    /**
     * Trait layouts
     */
    LayoutCollections traitLayouts;

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

    private void initRangeAndPlot(){
        range = findViewById(R.id.range);
        plot = findViewById(R.id.plot);

        rangeName = findViewById(R.id.rangeName);
        plotName = findViewById(R.id.plotName);

        tvRange = findViewById(R.id.tvRange);
        tvPlot = findViewById(R.id.tvPlot);

        range.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                // do not do bit check on event, crashes keyboard
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    try {
                        //moveToSearch("range",rangeID,range.getText().toString(),null,null);
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
                        //moveToSearch("plot",rangeID,null,plot.getText().toString(),null);
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
    }

    private void initCurrentVals(){
        // Current value display
        etCurVal = findViewById(R.id.etCurVal);

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

            // if the trait has a range, updateTrait does not work
            public void afterTextChanged(final Editable en) {
	            final String strValue = etCurVal.getText().toString();
	            if(strValue.equals(""))
	            	return;
	            
                Timer timer = new Timer();
                final long DELAY = 750; // in ms
                
                timer.cancel();
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    public void run() {
                        runOnUiThread(createDelayThread(en));
                    }
                }, DELAY);
            }

            public void beforeTextChanged(CharSequence arg0,
            								int arg1, int arg2, int arg3) {
            }

            public void onTextChanged(CharSequence arg0,
            								int arg1, int arg2, int arg3) {
            }

        };

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
    
    private Runnable createDelayThread(final Editable en) {
        return new Runnable() {
            @Override public void run() {
                final String strValue = etCurVal.getText().toString();
                final TraitObject currentTrait = traitBox.getCurrentTrait();
                final String trait = currentTrait.getTrait();
                if (currentTrait.isValidValue(strValue)) {
                    if (traitBox.existsNewTraits() & currentTrait != null)
                        updateTrait(trait, currentTrait.getFormat(), strValue);
                }
                else {
                    if (!currentTrait.isNotOver(strValue)) {
                        makeToast(getString(R.string.trait_error_maximum_value)
                        					+ " " + currentTrait.getMaximum());
                    }
                    en.clear();
                    removeTrait(trait);
                }
            }
        };
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

        initRangeAndPlot();
        initCurrentVals();

        selectorLayoutConfigurator = new SelectorLayoutConfigurator(this, ep.getInt(PreferencesActivity.INFOBAR_NUMBER, 3), (RecyclerView) findViewById(R.id.selectorList));

        traitLayouts = new LayoutCollections();
		
		traitBox = new TraitBox();

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

    }

    private void moveEntryLeft() {
        if (ep.getBoolean(PreferencesActivity.DISABLE_ENTRY_ARROW_LEFT, false)
	        										&& !traitBox.existsTrait()) {
            playSound("error");
        } else {
            if (rangeID != null && rangeID.length > 0) {
                //index.setEnabled(true);
				paging = decrementPaging(paging);
				refreshMain();
            }
        }
    }

    private void moveEntryRight() {
        if(ep.getBoolean(PreferencesActivity.DISABLE_ENTRY_ARROW_RIGHT, false)
        											&& !traitBox.existsTrait()) {
            playSound("error");
        } else {
            if (rangeID != null && rangeID.length > 0) {
                //index.setEnabled(true);

				paging = incrementPaging(paging);
				refreshMain();
            }
        }
    }
    
    private int decrementPaging(int pos) {
		return movePaging(pos, -1);
	}
    
    private int incrementPaging(int pos) {
		return movePaging(pos, 1);
	}
	
	private int movePaging(int pos, int step) {
        // If ignore existing data is enabled, then skip accordingly
        if (ep.getBoolean(PreferencesActivity.HIDE_ENTRIES_WITH_DATA, false)) {
            if (step == 1 && pos == rangeID.length) {
                return 1;
            }

            TraitObject trait = traitBox.getCurrentTrait();
            while (0 <= pos && pos <= rangeID.length) {
                pos += step;
                if (pos < 1) {
					return pos - step;
				}
                else if (pos > rangeID.length) {
                    return 1;
                }
                else if (!dt.getTraitExists(rangeID[pos-1], trait.getTrait(),
                										trait.getFormat())) {
					return pos;
                }
            }
            return pos;
        } else {
            pos += step;
            if (pos > rangeID.length) {
                return 1;
            }
            else if(pos < 1) {
				return rangeID.length;
			}
			else {
				return pos;
			}
        }
	}
	
	private void refreshMain() {
        // Refresh onscreen controls
        cRange = dt.getRange(rangeID[paging - 1]);

        saveLastPlot();

        displayRange(cRange);
        if (ep.getBoolean(PreferencesActivity.PRIMARY_SOUND, false)) {
            if (!cRange.range.equals(lastRange) && !lastRange.equals("")) {
                lastRange = cRange.range;
                playSound("plonk");
            }
        }
        traitBox.setNewTraits(cRange.plot_id);

        initWidgets(true);
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

    //TODO
    private boolean validateData() {
        //get rules

        //get data

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

        deleteValue = toolbarBottom.findViewById(R.id.deleteValue);
        deleteValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // if a brapi observation that has been synced, don't allow deleting
				TraitObject currentTrait = traitBox.getCurrentTrait();
                if (dt.isBrapiSynced(cRange.plot_id, currentTrait.getTrait())) {
                    if (currentTrait.getFormat().equals("photo")) {
						// I want to use abstract method
						Map newTraits = traitBox.getNewTraits();
						PhotoTraitLayout traitPhoto = traitLayouts.getPhotoTrait();
                        traitPhoto.brapiDelete(newTraits);
                    }
                    else {
                        brapiDelete(currentTrait.getTrait(), false);
                    }
                }
                else {
					traitLayouts.deleteTraitListener(currentTrait.getFormat());
                }
            }
        });

    }

    Runnable mActionRight = new Runnable() {
        @Override public void run() {
            repeatKeyPress("right");

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
            repeatKeyPress("left");

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

    // Simulate range right key press
    private void repeatKeyPress(String directionStr) {
        boolean left = directionStr.equalsIgnoreCase("left");
        if (rangeID != null && rangeID.length > 0) {

            // If ignore existing data is enabled, then skip accordingly
            if (ep.getBoolean(PreferencesActivity.HIDE_ENTRIES_WITH_DATA, false)) {
                int pos = left ? paging - 1 : paging + 1;

                
                while (pos != paging) {
                    TraitObject trait = traitBox.getCurrentTrait();
                    if (!ConfigActivity.dt.getTraitExists(rangeID[pos - 1],
                    					trait.getTrait(), trait.getFormat())) {
                        paging = pos;
                        break;
                    }
                    pos = left ? pos - 1 : pos + 1;

                    if (pos > rangeID.length)
                        pos = 1;
                    else if (pos < 1)
                        pos = rangeID.length;
                }
            } else {
                paging = left ? paging - 1 : paging + 1;

                if (paging > rangeID.length)
                    paging = 1;
                else if (paging < 1)
                    paging = rangeID.length;
            }

            // Refresh onscreen controls
            cRange = dt.getRange(rangeID[paging - 1]);
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
            traitBox.setNewTraits(cRange.plot_id);

            initWidgets(true);
        }
    }

    // This update should only be called after repeating keypress ends
    private void repeatUpdate() {
        if (rangeID == null)
            return;

        traitBox.setNewTraits(cRange.plot_id);

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

        if (!dt.isTableEmpty(DataHelper.RANGE)) {
            selectorLayoutConfigurator.configureDropdownArray(cRange.plot_id);
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

    @Override
    public void onPause() {
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

            rangeID = dt.getAllRangeID();

            if (rangeID != null) {
                cRange = dt.getRange(rangeID[0]);

                //TODO NullPointerException
                lastRange = cRange.range;
                displayRange(cRange);

                traitBox.setNewTraits(cRange.plot_id);
            }

            traitBox.setPrefixTraits();

            initWidgets(false);
            traitBox.setSelection(0);

            // try to go to last saved plot
            if(ep.getString("lastplot",null)!=null) {
                rangeID = dt.getAllRangeID();
                //moveToSearch("id",rangeID,null,null,ep.getString("lastplot",null));
            }

        } else if (partialReload) {
            partialReload = false;
            displayRange(cRange);
            traitBox.setPrefixTraits();
            initWidgets(false);

        } else if (searchReload) {
            searchReload = false;
            paging = 1;

            //if (rangeID != null) {
                //moveToSearch("search",rangeID, searchRange, searchPlot, null);
            //}
        }
    }

    /**
     * Helper function update user data in the memory based hashmap as well as
     * the database
     */
    public void updateTrait(String parent, String trait, String value) {

        if (cRange == null || cRange.plot_id.length() == 0) {
            return;
        }

		Log.w(parent, value);
        traitBox.update(parent, value);

        Observation observation = dt.getObservation(cRange.plot_id, parent);
        String observationDbId = observation.getDbId();
        OffsetDateTime lastSyncedTime = observation.getLastSyncedTime();

        // Always remove existing trait before inserting again
        // Based on plot_id, prevent duplicates
        dt.deleteTrait(cRange.plot_id, parent);

        String exp_id = Integer.toString(ep.getInt("ExpID", 0));
        dt.insertUserTraits(cRange.plot_id, parent, trait, value, ep.getString("FirstName", "") + " " + ep.getString("LastName", ""), ep.getString("Location", ""), "", exp_id, observationDbId, lastSyncedTime); //TODO add notes and exp_id
    }

    private void brapiDelete(String parent, Boolean hint) {
        Toast.makeText(getApplicationContext(), getString(R.string.brapi_delete_message), Toast.LENGTH_LONG).show();
        TraitObject trait = traitBox.getCurrentTrait();
        updateTrait(parent, trait.getFormat(), getString(R.string.brapi_na));
        if (hint) {
            setNaTextBrapiEmptyField();
        }
        else {
            setNaText();
        }
    }

    // Delete trait, including from database
    public void removeTrait(String parent) {
        if (cRange == null || cRange.plot_id.length() == 0) {
            return;
        }

        TraitObject trait = traitBox.getCurrentTrait();
        if (dt.isBrapiSynced(cRange.plot_id, trait.getTrait())) {
            brapiDelete(parent, true);
        }
        else {
            // Always remove existing trait before inserting again
            // Based on plot_id, prevent duplicate
            traitBox.remove(parent, cRange.plot_id);
        }
    }
    
    // for format without specific control
    public void removeTrait() {
        traitBox.remove(traitBox.getCurrentTrait(), cRange.plot_id);
        etCurVal.setText("");
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
                                repeatKeyPress("left");
                            } else {
                                repeatKeyPress("right");
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

        traitLayouts.lockData(dataLocked);

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
                traitLayouts.lockData(dataLocked);
                break;
            case android.R.id.home:
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
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
                rangeID = dt.getAllRangeID();
                //moveToSearch("id",rangeID,null,null,inputPlotId);
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

            TraitObject trait = traitBox.getCurrentTrait();
            if (!dt.getTraitExists(rangeID[pos - 1], trait.getTrait(),
                    								trait.getFormat())) {
                paging = pos;
                break;
            }
        }
        cRange = dt.getRange(rangeID[paging - 1]);
        displayRange(cRange);
        lastRange = cRange.range;
        traitBox.setNewTraits(cRange.plot_id);
        initWidgets(true);
    }

    private void saveLastPlot() {
        Editor ed = ep.edit();
        ed.putString("lastplot", cRange.plot_id);
        ed.apply();
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

        summaryText.setText(traitBox.createSummaryText(cRange.plot_id));

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
                    traitBox.moveTrait("right");
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
                    rangeID = dt.getAllRangeID();
                    //moveToSearch("id",rangeID,null,null,inputPlotId);
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

        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scanResult != null) {
            inputPlotId = scanResult.getContents();
            rangeID = ConfigActivity.dt.getAllRangeID();
            //moveToSearch("id",rangeID,null,null,inputPlotId);
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

    public Map getNewTraits(){
        return traitBox.getNewTraits();
    }
    public void setNewTraits(Map newTraits){
        traitBox.setNewTraits(newTraits);
    }
    public TraitObject getCurrentTrait(){
        return traitBox.getCurrentTrait();
    }
    public RangeObject getCRange(){
        return cRange;
    }
    public EditText getEtCurVal(){
        return etCurVal;
    }
    public TextWatcher getCvText(){
        return cvText;
    }
    public TextWatcher getCvNum(){
        return cvNum;
    }
    public String getDisplayColor(){
        return displayColor;
    }

    public ImageButton getDeleteValue(){
        return deleteValue;
    }
    public ImageView getTraitLeft(){
        return traitBox.getTraitLeft();
    }
    public ImageView getTraitRight(){
        return traitBox.getTraitRight();
    }
    public ImageView getRangeLeft(){
        return rangeLeft;
    }
    public ImageView getRangeRight(){
        return rangeRight;
    }

    public Boolean isDataLocked() {
        return dataLocked;
    }
	
	private class LayoutCollections {
        ArrayList<TraitLayout> traitLayouts;
        
        public LayoutCollections() {
			int[] traitIDs = {
        		R.id.angleLayout, R.id.audioLayout, R.id.barcodeLayout,
        		R.id.booleanLayout, R.id.categoricalLayout, R.id.counterLayout,
        	 	R.id.dateLayout, R.id.diseaseLayout, R.id.locationLayout,
        		R.id.multicatLayout, R.id.numericLayout, R.id.percentLayout,
        		R.id.photoLayout, R.id.textLayout, R.id.labelprintLayout
        	};
        	
        	traitLayouts = new ArrayList<TraitLayout>();
        	for (int traitID : traitIDs) {
				TraitLayout layout = findViewById(traitID);
				layout.init();
				traitLayouts.add(layout);
			}
        }

    	public TraitLayout getTraitLayout(final String trait) {
			for(TraitLayout layout : traitLayouts) {
				if (layout.isTraitType(trait)) {
					return layout;
				}
			}
			return getTraitLayout("text");
        }
        
        public PhotoTraitLayout getPhotoTrait() {
			return (PhotoTraitLayout)getTraitLayout("photo");
		}
        
	    public void hideLayouts() {
			for (TraitLayout layout : traitLayouts) {
				layout.setVisibility(View.GONE);
	        }
	    }
	    
	    public void deleteTraitListener(String format) {
			getTraitLayout(format).deleteTraitListener();
		}
	    
	    public void setNaTraitsText(String format) {
			getTraitLayout(format).setNaTraitsText();
		}
		
	    private void lockData(Boolean lock) {
	        if(lock) {
	            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_lock);
	            missingValue.setEnabled(false);
	            deleteValue.setEnabled(false);
	            etCurVal.setEnabled(false);

	            for(LinearLayout traitLayout : traitLayouts) {
	                disableViews(traitLayout);
	            }

	        } else {
	            systemMenu.findItem(R.id.lockData).setIcon(R.drawable.ic_tb_unlock);
	            missingValue.setEnabled(true);
	            deleteValue.setEnabled(true);

	            for(LinearLayout traitLayout : traitLayouts) {
	                enableViews(traitLayout);
	            }
	        }
	    }
	}
	
	// traitLeft, traitType, and traitRight
	private class TraitBox {
	    private String[] prefixTraits;
	    private TraitObject currentTrait;
		
	    private Spinner traitType;
	    private TextView traitDetails;
	    private ImageView traitLeft;
	    private ImageView traitRight;
		
	    private Map newTraits;
		
		public TraitBox() {
    	    prefixTraits = null;
    	    newTraits = new HashMap();
        	
	        traitType = findViewById(R.id.traitType);
	        traitLeft = findViewById(R.id.traitLeft);
	        traitRight = findViewById(R.id.traitRight);
        	traitDetails = findViewById(R.id.traitDetails);
			
	        traitLeft.setOnTouchListener(createTraitOnTouchListener(traitLeft,
	        						R.drawable.main_trait_left_arrow_pressed));

	        // Go to previous trait
	        traitLeft.setOnClickListener(new OnClickListener() {

	            public void onClick(View arg0) {
	                moveTrait("left");
	            }
	        });

	        traitRight.setOnTouchListener(createTraitOnTouchListener(traitRight,
		        							R.drawable.main_trait_right_pressed));

	        // Go to next trait
	        traitRight.setOnClickListener(new OnClickListener() {

	            public void onClick(View arg0) {
	                moveTrait("right");
	            }
	        });
        }
        
        public void initTraitDetails() {
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
	    }
		
		public void initTraitType(ArrayAdapter<String> adaptor,
											final boolean rangeSuppress) {
            final int traitPosition = getSelectedItemPosition();
            traitType.setAdapter(adaptor);

            traitType.setOnItemSelectedListener(new OnItemSelectedListener() {

                public void onItemSelected(AdapterView<?> arg0, View arg1,
                                           int arg2, long arg3) {

                    // This updates the in memory hashmap from database
                    currentTrait = dt.getDetail(traitType.getSelectedItem()
                            .toString());

                    imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
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
                    TraitLayout currentTraitLayout =
                    	traitLayouts.getTraitLayout(currentTrait.getFormat());
                    currentTraitLayout.setVisibility(View.VISIBLE);

                    //Call specific load layout code for the current trait layout
                    if(currentTraitLayout != null) {
                        currentTraitLayout.loadLayout();
                    }else {
                        etCurVal.removeTextChangedListener(cvText);
                        etCurVal.setVisibility(EditText.VISIBLE);
                        etCurVal.setEnabled(true);
                    }
                }

                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });
			
            traitBox.setSelection(traitPosition);
		}
		
		public Map getNewTraits() { return newTraits; }
		public ImageView getTraitLeft() { return traitLeft; }
		public ImageView getTraitRight() { return traitRight; }
		public boolean existsNewTraits() { return newTraits != null; }
		
		public void setPrefixTraits() {
            prefixTraits = dt.getRangeColumnNames();
		}
		
		public void setNewTraits(final String plotID) {
	        newTraits = (HashMap) dt.getUserDetail(plotID).clone();
		}
	    public void setNewTraits(Map newTraits){
	        this.newTraits = newTraits;
	    }
	    public void setSelection(int pos) { traitType.setSelection(pos); }
		
		public int getSelectedItemPosition() {
	        try {
	            return traitType.getSelectedItemPosition();
	        } catch (Exception f) {
				return 0;
	        }
		}
		
		public final TraitObject getCurrentTrait() {
			return currentTrait;
		}
		
		public final String getCurrentFormat() {
			return currentTrait.getFormat();
		}
		
		public boolean existsTrait() {
			return newTraits.containsKey(currentTrait.getTrait());
		}
		
		public final String createSummaryText(final String plotID) {
	        String[] traitList = dt.getAllTraits();
	        StringBuilder data = new StringBuilder();

	        //TODO this test crashes app
	        if (cRange != null) {
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
		
		public void remove(String traitName, String plotID) {
			if (newTraits.containsKey(traitName))
		        newTraits.remove(traitName);
	        dt.deleteTrait(plotID, traitName);
		}
		
		public void remove(TraitObject trait, String plotID) {
			remove(trait.getTrait(), plotID);
		}
		
	    private OnTouchListener createTraitOnTouchListener(final ImageView arrow,
	    													final int imageID) {
	    	return new OnTouchListener() {
	            public boolean onTouch(View v, MotionEvent event) {
	                switch (event.getAction()) {

	                    case MotionEvent.ACTION_DOWN:
	                        arrow.setImageResource(imageID);
	                        break;
	                    case MotionEvent.ACTION_MOVE:
	                        break;
	                    case MotionEvent.ACTION_UP:
	                        arrow.setImageResource(imageID);
	                    case MotionEvent.ACTION_CANCEL:
	                        break;
	                }

	                // return true to prevent calling btn onClick handler
	                return false;
	            }
	        };
	    }

	    public void moveTrait(String direction) {
	        int pos = 0;

	        if(!validateData()) {
	            return;
	        }

	        // Force the keyboard to be hidden to handle bug
	        try {
	            imm.hideSoftInputFromWindow(etCurVal.getWindowToken(), 0);
	        } catch (Exception ignore) {
	        }

	        if (direction.equals("left")) {
	            pos = traitType.getSelectedItemPosition() - 1;

	            if (pos < 0) {
	                pos = traitType.getCount() - 1;

	                if (ep.getBoolean(PreferencesActivity.CYCLING_TRAITS_ADVANCES, false))
	                    rangeLeft.performClick();
	            }
	        }
	        else if (direction.equals("right")) {
	            pos = traitType.getSelectedItemPosition() + 1;

	            if (pos > traitType.getCount() - 1) {
	                pos = 0;

	                if (ep.getBoolean(PreferencesActivity.CYCLING_TRAITS_ADVANCES, false))
	                    rangeRight.performClick();
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

}