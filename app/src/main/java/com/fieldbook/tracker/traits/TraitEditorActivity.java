package com.fieldbook.tracker.traits;

import android.annotation.SuppressLint;
import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;

import com.fieldbook.tracker.ConfigActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.widget.Toolbar;

import android.text.Html;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.fieldbook.tracker.io.CSVReader;
import com.fieldbook.tracker.io.CSVWriter;
import com.fieldbook.tracker.preferences.PreferencesActivity;
//import com.fieldbook.tracker.utilities.ApiKeys;
import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.FileExploreActivity;
import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.tutorial.TutorialTraitsActivity;
import com.fieldbook.tracker.dragsort.DragSortListView;
import com.fieldbook.tracker.dragsort.DragSortController;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

public class TraitEditorActivity extends AppCompatActivity {

    private static Handler mHandler = new Handler();

    public static DragSortListView traitList;
    public static TraitAdapter mAdapter;
    public static boolean createVisible;

    public static Activity thisActivity;
    public static EditText trait;

    private static String mChosenFile;

    private static SharedPreferences ep;

    private static OnItemClickListener traitListener;

    private AlertDialog createDialog;

    String currentId;

    private boolean edit;

    private Spinner format;
    private EditText def;
    private EditText minimum;
    private EditText maximum;
    private EditText details;
    private EditText categories;
    private TextView defTv;
    private ToggleButton bool;

    private String oldTrait;

    private LinearLayout defBox;
    private LinearLayout minBox;
    private LinearLayout maxBox;
    private LinearLayout categoryBox;

    int currentPosition;

    private TraitObject o;

    private Menu systemMenu;

    @Override
    public void onDestroy() {
        try {
            thisActivity.finish();
        } catch (Exception e) {
            String TAG = "Field Book";
            Log.e(TAG, "" + e.getMessage());
        }

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (systemMenu != null) {
            if (ep.getBoolean("Tips", false)) {
                systemMenu.findItem(R.id.help).setVisible(true);
            } else {
                systemMenu.findItem(R.id.help).setVisible(false);
            }
        }
        loadData();
    }

    private static DragSortListView.DropListener onDrop = new DragSortListView.DropListener() {

        @Override
        public void drop(int from, int to) {

            // The new logic is that the drop point is now the center of the list. Any items before / after are reordered based on the drop point's position.
            // This means while the realposition may not start from zero, the ordering will always be correct.

            Log.w("ReorderStart", "start");

            if (from != to) {
                if (to > from) {
                    Log.w("Downward", "drag");

                    try {
                        // e.g. 4
                        String prevID = mAdapter.getItem(from).id;

                        // e.g. 6
                        String currentID = mAdapter.getItem(to).id;
                        String currentPosition = mAdapter.getItem(to).realPosition;

                        ConfigActivity.dt.updateTraitPosition(currentID, currentPosition);
                        ConfigActivity.dt.updateTraitPosition(prevID, String.valueOf(Integer.parseInt(currentPosition) + 1));

                        // Push everything below down by 1
                        int newCount = 2;

                        for (int i = to + 1; i < mAdapter.getCount(); i++) {
                            ConfigActivity.dt.updateTraitPosition(mAdapter.getItem(i).id, String.valueOf(Integer.parseInt(currentPosition) + newCount));
                            newCount++;
                        }

                        MainActivity.reloadData = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.w("Upward", "drag");

                    try {
                        // upward drag
                        // e.g. 4
                        String prevID = mAdapter.getItem(from).id;

                        // e.g. 6
                        String currentPosition = mAdapter.getItem(to).realPosition;

                        if (Integer.parseInt(currentPosition) - to >= 0) {
                            Log.w("Reorder", "top1");

                            // Reorder everything above
                            int newCount = Integer.parseInt(currentPosition) - to;

                            for (int i = 0; i < to; i++) {
                                ConfigActivity.dt.updateTraitPosition(mAdapter.getItem(i).id, String.valueOf(newCount));
                                newCount++;
                            }

                            Log.w("Reorder", "current");

                            ConfigActivity.dt.updateTraitPosition(prevID, currentPosition);

                        } else {
                            // We hit a -1, might as well do a full zero based reorder
                            // Reorder everything above

                            Log.w("Reorder", "top2");

                            for (int i = 0; i < to; i++) {
                                ConfigActivity.dt.updateTraitPosition(mAdapter.getItem(i).id, String.valueOf(i));
                            }

                            Log.w("Reorder", "current");

                            ConfigActivity.dt.updateTraitPosition(prevID, String.valueOf(to));

                            // Reset current position as well, otherwise we don't know where it points to
                            currentPosition = String.valueOf(to);
                        }

                        Log.w("Reorder", "below");

                        // Push everything below down by 1
                        int newCount = 1;

                        // last pulled position is from field

                        for (int i = to; i < mAdapter.getCount(); i++) {
                            if (i != from) {
                                ConfigActivity.dt.updateTraitPosition(mAdapter.getItem(i).id, String.valueOf(Integer.parseInt(currentPosition) + newCount));
                                newCount++;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }

            MainActivity.reloadData = true;
            loadData();
        }
    };

    private static DragSortListView.RemoveListener onRemove = new DragSortListView.RemoveListener() {
        @Override
        public void remove(int which) {
            mAdapter.list.remove(which);
        }
    };


    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        setContentView(R.layout.activity_traits);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setTitle(null);
        getSupportActionBar().getThemedContext();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        if (getActionBar() != null) {
            getActionBar().setHomeButtonEnabled(true);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        thisActivity = this;

        final String[] data = new String[]{getString(R.string.traits_format_numeric), getString(R.string.traits_format_categorical), getString(R.string.traits_format_date), getString(R.string.traits_format_percent), getString(R.string.traits_format_boolean),
                getString(R.string.traits_format_text), getString(R.string.traits_format_photo), getString(R.string.traits_format_audio), getString(R.string.traits_format_counter), getString(R.string.traits_format_disease_rating), getString(R.string.traits_format_multicategorical),
                getString(R.string.traits_format_location)};

        final String[] enData = new String[]{"Numeric", "Categorical", "Date", "Percent", "Boolean", "Text", "Photo", "Audio", "Counter", "Disease Rating", "Multicat", "Location"};

        HashMap visibility = ConfigActivity.dt.getTraitVisibility();
        traitList = findViewById(R.id.myList);

        if (!traitList.isShown())
            traitList.setVisibility(ListView.VISIBLE);

        mAdapter = new TraitAdapter(thisActivity, R.layout.listitem_trait, ConfigActivity.dt.getAllTraitObjects(), traitListener, visibility);

        traitList.setAdapter(mAdapter);
        traitList.setDropListener(onDrop);
        traitList.setRemoveListener(onRemove);

        DragSortController controller = new DragSortController(traitList);
        controller.setDragHandleId(R.id.dragSort);
        controller.setRemoveEnabled(false);
        controller.setSortEnabled(true);
        controller.setDragInitMode(1);

        traitList.setFloatViewManager(controller);
        traitList.setOnTouchListener(controller);
        traitList.setDragEnabled(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_new_trait, null);

        builder.setTitle(R.string.traits_toolbar_add_trait)
                .setCancelable(true)
                .setView(layout);

        createDialog = builder.create();

        android.view.WindowManager.LayoutParams params = createDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;

        createDialog.getWindow().setAttributes(params);
        createDialog.setOnCancelListener(new OnCancelListener() {
            public void onCancel(DialogInterface arg0) {
                createVisible = false;
            }
        });
        createDialog.setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface arg0) {
                createVisible = false;
            }
        });

        trait = layout.findViewById(R.id.trait);
        format = layout.findViewById(R.id.format);
        def = layout.findViewById(R.id.def);
        minimum = layout.findViewById(R.id.minimum);
        maximum = layout.findViewById(R.id.maximum);
        details = layout.findViewById(R.id.details);
        categories = layout.findViewById(R.id.categories);

        defBox = layout.findViewById(R.id.defbox);
        minBox = layout.findViewById(R.id.minbox);
        maxBox = layout.findViewById(R.id.maxbox);
        categoryBox = layout.findViewById(R.id.categorybox);

        bool = layout.findViewById(R.id.boolBtn);
        defTv = layout.findViewById(R.id.defTv);

        Button saveBtn = layout.findViewById(R.id.saveBtn);
        Button closeBtn = layout.findViewById(R.id.closeBtn);

        trait.isFocused();

        traitListener = new AdapterView.OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                // When a trait is selected, alter the layout of the edit dialog accordingly

                o = mAdapter.getItem(position);
                currentId = o.id;
                trait.setText(o.trait);
                oldTrait = o.trait;

                for (int i = 0; i < data.length; i++) {
                    if (data[i].toLowerCase().equals(o.format.toLowerCase())) {
                        currentPosition = i;
                        format.setSelection(i, true);
                        prepareFields(i);
                        break;
                    }
                }

                def.setText(o.defaultValue);

                if (o.defaultValue.equals("true"))
                    bool.setChecked(true);
                else
                    bool.setChecked(false);

                minimum.setText(o.minimum);
                maximum.setText(o.maximum);
                details.setText(o.details);
                categories.setText(o.categories);

                edit = true;
                createVisible = true;
                loadData();
                createDialog.show();
            }
        };

        format.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> av, View arg1, int position, long arg3) {

                // Change the layout of the dialog based on the trait
                if (position != currentPosition) {
                    def.setText("");
                    minimum.setText("");
                    maximum.setText("");
                    details.setText("");
                    categories.setText("");
                    bool.setChecked(false);
                    currentPosition = position;
                    format.setSelection(currentPosition);
                    prepareFields(currentPosition);
                }
            }

            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(this, R.layout.custom_spinnerlayout, data);
        format.setAdapter(itemsAdapter);

        closeBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                // Prompt the user if fields have been edited

                if (dataChanged()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(TraitEditorActivity.this, R.style.AppAlertDialog);

                    builder.setTitle(getString(R.string.dialog_close));
                    builder.setMessage(getString(R.string.dialog_confirm));

                    builder.setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            createDialog.dismiss();
                        }

                    });

                    builder.setNegativeButton(getString(R.string.dialog_no), new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }

                    });

                    AlertDialog alert = builder.create();
                    alert.show();
                } else {
                    createDialog.dismiss();
                }
            }

        });

        saveBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                // Trait name is mandatory
                if (trait.getText().toString().length() == 0) {
                    makeToast(getString(R.string.traits_create_warning_name_blank));
                    return;
                }

                // Disallow duplicate traits
                boolean exists = ConfigActivity.dt.hasTrait(trait.getText().toString().trim());

                if (!edit) {
                    if (exists) {
                        makeToast(getString(R.string.traits_create_warning_duplicate));
                        return;
                    }
                } else {
                    if (exists & !oldTrait.toLowerCase().equals(trait.getText().toString().trim().toLowerCase())) {
                        makeToast(getString(R.string.traits_create_warning_duplicate));
                        return;
                    }
                }

                // The checks below are format specific

                if (format.getSelectedItemPosition() == 0) {
                    if (def.getText().toString().length() > 0 & !isNumeric(def.getText().toString(), false)) {
                        makeToast(getString(R.string.traits_create_warning_numeric_required));
                        return;
                    }

                    if (minimum.getText().toString().length() > 0 & !isNumeric(minimum.getText().toString(), false)) {
                        makeToast(getString(R.string.traits_create_warning_numeric_required));
                        return;
                    }

                    if (maximum.getText().toString().length() > 0 & !isNumeric(maximum.getText().toString(), false)) {
                        makeToast(getString(R.string.traits_create_warning_numeric_required));
                        return;
                    }

                }

                if (format.getSelectedItemPosition() == 1) {
                    if (categories.getText().toString().length() == 0) {
                        makeToast(getString(R.string.traits_create_warning_categories_required));
                        return;
                    }
                }

                if (format.getSelectedItemPosition() == 3) {
                    if (def.getText().toString().length() == 0 | !isNumeric(def.getText().toString(), true)) {
                        makeToast(getString(R.string.traits_create_warning_numeric_required));
                        return;
                    }

                    if (minimum.getText().toString().length() == 0 | !isNumeric(minimum.getText().toString(), true)) {
                        makeToast(getString(R.string.traits_create_warning_numeric_required));
                        return;
                    }

                    if (maximum.getText().toString().length() == 0 | !isNumeric(maximum.getText().toString(), true)) {
                        makeToast(getString(R.string.traits_create_warning_numeric_required));
                        return;
                    }

                }

                int pos = ConfigActivity.dt.getMaxPositionFromTraits() + 1;

                if (format.getSelectedItemPosition() == 4) {
                    if (bool.isChecked())
                        def.setText("true");
                    else
                        def.setText("false");
                }

                if (!edit) {
                    /*MainActivity.dt.insertTraits(trait.getText().toString().trim(),
                            enData[format.getSelectedItemPosition()].toLowerCase(), def.getText().toString(),
                            minimum.getText().toString(), maximum.getText().toString(),
                            details.getText().toString(), categories.getText().toString(),
                            "true", String.valueOf(pos));*/
                    TraitObject t = new TraitObject();
                    t.trait = trait.getText().toString().trim();
                    t.format = enData[format.getSelectedItemPosition()].toLowerCase();
                    t.defaultValue = def.getText().toString();
                    t.minimum = minimum.getText().toString();
                    t.maximum = maximum.getText().toString();
                    t.details = details.getText().toString();
                    t.categories = categories.getText().toString();
                    t.visible = true;
                    t.realPosition = String.valueOf(pos);
                    ConfigActivity.dt.insertTraits(t);
                } else {
                    ConfigActivity.dt.editTraits(currentId, trait.getText().toString().trim(),
                            enData[format.getSelectedItemPosition()].toLowerCase(), def.getText().toString(),
                            minimum.getText().toString(), maximum.getText().toString(),
                            details.getText().toString(), categories.getText().toString());
                }

                Editor ed = ep.edit();
                ed.putBoolean("CreateTraitFinished", true);
                ed.putBoolean("TraitsExported", false);
                ed.apply();

                loadData();

                MainActivity.reloadData = true;
                createDialog.dismiss();
            }
        });

        FloatingActionButton fab = findViewById(R.id.newTrait);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreateTraitDialog();
            }
        });
    }

    // Helper function to see if any fields have been edited
    private boolean dataChanged() {
        if (o != null) {
            String defString;

            if (bool.isChecked()) {
                defString = "true";
            } else {
                defString = "false";
            }

            if (!trait.getText().toString().equals(o.trait))
                return true;

            if (format.getSelectedItemPosition() == 4) {
                if (!def.getText().toString().equals(defString))
                    return true;
            } else {
                if (!def.getText().toString().equals(o.defaultValue))
                    return true;
            }

            if (!minimum.getText().toString().equals(o.minimum))
                return true;

            if (!maximum.getText().toString().equals(o.maximum))
                return true;

            if (!details.getText().toString().equals(o.details))
                return true;

            if (!categories.getText().toString().equals(o.categories))
                return true;

        } else {
            if (trait.getText().toString().length() > 0)
                return true;

            if (def.getText().toString().length() > 0)
                return true;

            if (minimum.getText().toString().length() > 0)
                return true;

            if (maximum.getText().toString().length() > 0)
                return true;

            if (details.getText().toString().length() > 0)
                return true;

            if (categories.getText().toString().length() > 0)
                return true;

            if (format.getSelectedItemPosition() == 4) {
                if (bool.isChecked())
                    return true;
            }
        }
        return false;
    }

    private void prepareFields(int position) {
        details.setHint(getString(R.string.traits_create_optional));
        def.setHint(null);
        minimum.setHint(null);
        maximum.setHint(null);

        defBox.setVisibility(View.GONE);
        minBox.setVisibility(View.GONE);
        maxBox.setVisibility(View.GONE);
        bool.setVisibility(View.GONE);
        categoryBox.setVisibility(View.GONE);

        switch (position) {
            case 0: //numeric
                defBox.setVisibility(View.VISIBLE);
                def.setVisibility(View.VISIBLE);
                bool.setVisibility(View.GONE);
                minBox.setVisibility(View.VISIBLE);
                maxBox.setVisibility(View.VISIBLE);
                categoryBox.setVisibility(View.GONE);

                def.setHint(getString(R.string.traits_create_optional));
                minimum.setHint(getString(R.string.traits_create_optional));
                maximum.setHint(getString(R.string.traits_create_optional));
                break;
            case 1: //categorical
                defBox.setVisibility(View.GONE);
                bool.setVisibility(View.GONE);
                minBox.setVisibility(View.GONE);
                maxBox.setVisibility(View.GONE);
                categoryBox.setVisibility(View.VISIBLE);
                break;
            case 2: //date
                defBox.setVisibility(View.GONE);
                bool.setVisibility(View.GONE);
                minBox.setVisibility(View.GONE);
                maxBox.setVisibility(View.GONE);
                categoryBox.setVisibility(View.GONE);
                break;
            case 3: //percent
                defBox.setVisibility(View.VISIBLE);
                def.setVisibility(View.VISIBLE);
                bool.setVisibility(View.GONE);
                minBox.setVisibility(View.VISIBLE);
                maxBox.setVisibility(View.VISIBLE);
                categoryBox.setVisibility(View.GONE);

                def.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                minimum.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                maximum.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case 4: //boolean
                defBox.setVisibility(View.VISIBLE);
                def.setVisibility(View.GONE);
                defTv.setVisibility(View.VISIBLE);
                bool.setVisibility(View.VISIBLE);
                minBox.setVisibility(View.GONE);
                maxBox.setVisibility(View.GONE);
                categoryBox.setVisibility(View.GONE);
                break;
            case 5: //text
                defBox.setVisibility(View.VISIBLE);
                def.setVisibility(View.VISIBLE);
                minBox.setVisibility(View.GONE);
                maxBox.setVisibility(View.GONE);
                bool.setVisibility(View.GONE);
                categoryBox.setVisibility(View.GONE);

                def.setHint(getString(R.string.traits_create_optional));
                break;
            case 6: //photo
                defBox.setVisibility(View.GONE);
                minBox.setVisibility(View.GONE);
                maxBox.setVisibility(View.GONE);
                bool.setVisibility(View.GONE);
                categoryBox.setVisibility(View.GONE);
                break;
            case 7: //audio
                defBox.setVisibility(View.GONE);
                minBox.setVisibility(View.GONE);
                maxBox.setVisibility(View.GONE);
                bool.setVisibility(View.GONE);
                categoryBox.setVisibility(View.GONE);
                break;
            case 8: //counter
                defBox.setVisibility(View.GONE);
                minBox.setVisibility(View.GONE);
                maxBox.setVisibility(View.GONE);
                bool.setVisibility(View.GONE);
                categoryBox.setVisibility(View.GONE);
                break;
            case 9: //rust rating
                defBox.setVisibility(View.GONE);
                minBox.setVisibility(View.GONE);
                maxBox.setVisibility(View.GONE);
                bool.setVisibility(View.GONE);
                categoryBox.setVisibility(View.GONE);
                break;
            case 10: //multicategorical
                defBox.setVisibility(View.GONE);
                bool.setVisibility(View.GONE);
                minBox.setVisibility(View.GONE);
                maxBox.setVisibility(View.GONE);
                categoryBox.setVisibility(View.VISIBLE);
                break;
            case 11: //location
                defBox.setVisibility(View.GONE);
                minBox.setVisibility(View.GONE);
                maxBox.setVisibility(View.GONE);
                bool.setVisibility(View.GONE);
                categoryBox.setVisibility(View.GONE);
                break;
        }
    }

    // Non negative numbers only
    public static boolean isNumeric(String str, boolean positive) {
        if (str.length() == 0)
            return false;

        try {
            double d = Double.parseDouble(str);

            if (positive & d < 0)
                return false;
        } catch (NumberFormatException nfe) {
            return false;
        }

        return true;
    }

    // Helper function to load data
    public static void loadData() {
        try {

            HashMap visibility = ConfigActivity.dt.getTraitVisibility();

            if (!traitList.isShown())
                traitList.setVisibility(ListView.VISIBLE);

            mAdapter = new TraitAdapter(thisActivity, R.layout.listitem_trait, ConfigActivity.dt.getAllTraitObjects(), traitListener, visibility);

            traitList.setAdapter(mAdapter);
            traitList.setDropListener(onDrop);
            traitList.setRemoveListener(onRemove);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(TraitEditorActivity.this).inflate(R.menu.menu_traits, menu);

        systemMenu = menu;

        // Check to see if visibility should be toggled
        if (systemMenu != null) {

            if (ep.getBoolean("Tips", false)) {
                systemMenu.findItem(R.id.help).setVisible(true);
            } else {
                systemMenu.findItem(R.id.help).setVisible(false);
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.help:
                Intent intent = new Intent();
                intent.setClassName(TraitEditorActivity.this,
                        TutorialTraitsActivity.class.getName());
                startActivity(intent);
                break;

            case R.id.deleteTrait:
                showDeleteTraitDialog();
                break;

            case R.id.sortTrait:
                sortDialog();
                break;

            case R.id.importexport:
                importExportDialog();
                break;

            case R.id.addTrait:
                showCreateTraitDialog();
                break;

            case R.id.toggleTrait:
                changeAllVisibility();
                break;

            case android.R.id.home:
                MainActivity.reloadData = true;
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void changeAllVisibility() {
        Boolean globalVis = ep.getBoolean("allTraitsVisible", false);
        String[] allTraits = ConfigActivity.dt.getTraitColumnData("trait");

        if (allTraits == null) {
            makeToast(getString(R.string.warning_traits_missing_modify));
            return;
        }

        for (String allTrait : allTraits) {
            ConfigActivity.dt.updateTraitVisibility(allTrait, globalVis);
            Log.d("Field", allTrait);
        }

        globalVis = !globalVis;

        Editor ed = ep.edit();
        ed.putBoolean("allTraitsVisible", globalVis);
        ed.apply();
        loadData();
    }

    private void importExportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list, null);

        builder.setTitle(R.string.settings_traits)
                .setCancelable(true)
                .setView(layout);

        final AlertDialog importExport = builder.create();


        android.view.WindowManager.LayoutParams params = importExport.getWindow().getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        importExport.getWindow().setAttributes(params);

        ListView myList = (ListView) layout.findViewById(R.id.myList);

        String[] sortOptions = new String[2];

        sortOptions[0] = getString(R.string.dialog_import);
        sortOptions[1] = getString(R.string.traits_dialog_export);

        myList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                switch (which) {
                    case 0:
                        if (ep.getBoolean("TraitsExported", false)) {
                            showFileDialog();
                        } else {
                            checkTraitExportDialog();
                        }
                        break;
                    case 1:
                        showExportDialog();
                        break;
                }
                importExport.dismiss();
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, sortOptions);
        myList.setAdapter(adapter);
        Button sortCloseBtn = layout.findViewById(R.id.closeBtn);

        sortCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                importExport.dismiss();
            }
        });
        importExport.show();
    }

    private void showFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list, null);

        builder.setTitle(R.string.import_dialog_title)
                .setCancelable(true)
                .setView(layout);

        final AlertDialog importDialog = builder.create();

        android.view.WindowManager.LayoutParams params = importDialog.getWindow().getAttributes();
        params.width = LayoutParams.MATCH_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        importDialog.getWindow().setAttributes(params);

        ListView myList = layout.findViewById(R.id.myList);

        String[] importArray = new String[2];
        importArray[0] = getString(R.string.import_source_local);
        importArray[1] = getString(R.string.import_source_dropbox);

        //TODO add google drive (requires Google Play Services)
        //importArray[2] = getString(R.string.importgoogle);

        myList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                Intent intent = new Intent();
                switch (which) {
                    case 0:
                        intent.setClassName(thisActivity,
                                FileExploreActivity.class.getName());
                        intent.putExtra("path", Constants.TRAITPATH);
                        intent.putExtra("include", new String[]{"trt"});
                        intent.putExtra("title", getString(R.string.traits_dialog_import));
                        startActivityForResult(intent, 1);
                        break;
                    case 1:
                        //DbxChooser mChooser = new DbxChooser(ApiKeys.DROPBOX_APP_KEY);
                        //mChooser.forResultType(DbxChooser.ResultType.FILE_CONTENT).launch(thisActivity, 3);
                        break;
                }
                importDialog.dismiss();
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, importArray);
        myList.setAdapter(adapter);
        Button importCloseBtn = layout.findViewById(R.id.closeBtn);
        importCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                importDialog.dismiss();
            }
        });
        importDialog.show();
    }

    private void checkTraitExportDialog() {
        String[] allTraits = ConfigActivity.dt.getTraitColumnData("trait");

        if (allTraits == null) {
            showFileDialog();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(TraitEditorActivity.this, R.style.AppAlertDialog);
        builder.setMessage(getString(R.string.traits_export_check));

        builder.setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                showExportDialog();
                dialog.dismiss();
            }

        });

        builder.setNegativeButton(getString(R.string.dialog_no), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                showFileDialog();
                dialog.dismiss();
            }

        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void sortDialog() {
        String[] allTraits = ConfigActivity.dt.getTraitColumnData("trait");

        if (allTraits == null) {
            makeToast(getString(R.string.warning_traits_missing_modify));
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list, null);

        builder.setTitle(R.string.traits_sort_title)
                .setCancelable(true)
                .setView(layout);

        final AlertDialog sortDialog = builder.create();

        android.view.WindowManager.LayoutParams params = sortDialog.getWindow().getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        sortDialog.getWindow().setAttributes(params);

        ListView myList = layout.findViewById(R.id.myList);

        String[] sortOptions = new String[3];

        sortOptions[0] = getString(R.string.traits_sort_name);
        sortOptions[1] = getString(R.string.traits_sort_format);
        sortOptions[2] = getString(R.string.traits_sort_visibility);

        myList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                switch (which) {
                    case 0:
                        sortTraitList("trait");
                        break;
                    case 1:
                        sortTraitList("format");
                        break;
                    case 2:
                        sortTraitList("isVisible");
                        break;
                }
                sortDialog.dismiss();
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, sortOptions);
        myList.setAdapter(adapter);
        Button sortCloseBtn = layout.findViewById(R.id.closeBtn);

        sortCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                sortDialog.dismiss();
            }
        });
        sortDialog.show();
    }

    private void sortTraitList(String colName) {
        String[] sortList = ConfigActivity.dt.getTraitColumnData(colName);

        ArrayIndexComparator comparator = new ArrayIndexComparator(sortList);
        Integer[] indexes = comparator.createIndexArray();
        Arrays.sort(indexes, comparator);

        if (colName.equals("isVisible")) {
            Arrays.sort(indexes, Collections.reverseOrder());
        }

        for (int j = 0; j < indexes.length; j++) {
            ConfigActivity.dt.writeNewPosition(colName, sortList[j], Integer.toString(indexes[j]));
            Log.e("TRAIT", sortList[j] + " " + indexes[j].toString());
        }

        loadData();
    }

    private class ArrayIndexComparator implements Comparator<Integer> {
        private final String[] array;

        ArrayIndexComparator(String[] array) {
            this.array = array;
        }

        Integer[] createIndexArray() {
            Arrays.sort(array);
            Integer[] indexes = new Integer[array.length];
            for (int i = 0; i < array.length; i++) {
                indexes[i] = i;
            }
            return indexes;
        }

        @Override
        public int compare(Integer index1, Integer index2) {
            return array[index1].compareTo(array[index2]);
        }
    }

    private void showExportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppAlertDialog);

        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_save_database, null);

        builder.setTitle(R.string.traits_dialog_export)
                .setCancelable(true)
                .setView(layout);

        final AlertDialog exportDialog = builder.create();

        android.view.WindowManager.LayoutParams langParams = exportDialog.getWindow().getAttributes();
        langParams.width = LayoutParams.MATCH_PARENT;
        exportDialog.getWindow().setAttributes(langParams);

        Button closeBtn = layout.findViewById(R.id.closeBtn);

        closeBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                exportDialog.dismiss();
            }
        });

        final EditText exportFile = layout.findViewById(R.id.fileName);

        // As the export filename uses the import file name as well,
        // we parse it out here
        SimpleDateFormat timeStamp = new SimpleDateFormat(
                "yyyy-MM-dd-hh-mm-ss", Locale.getDefault());

        String exportName = "trait_export_"
                + timeStamp.format(Calendar.getInstance().getTime())
                + ".trt";

        exportFile.setText(exportName);

        Button exportButton = (Button) layout.findViewById(R.id.saveBtn);

        exportButton.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                exportTable(exportFile.getText().toString());
                Editor ed = ep.edit();
                ed.putBoolean("TraitsExported", true);
                ed.apply();
                exportDialog.dismiss();
            }
        });

        exportDialog.show();
    }

    private void showDeleteTraitDialog() {
        String[] allTraits = ConfigActivity.dt.getTraitColumnData("trait");

        if (allTraits == null) {
            makeToast(getString(R.string.warning_traits_missing_modify));
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(TraitEditorActivity.this, R.style.AppAlertDialog);

        builder.setTitle(getString(R.string.traits_toolbar_delete_all));
        builder.setMessage(getString(R.string.dialog_confirm));

        builder.setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                ConfigActivity.dt.deleteTable(DataHelper.TRAITS);
                loadData();
                dialog.dismiss();
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

    private void showCreateTraitDialog() {
        o = null;

        trait.setText("");
        format.setSelection(0);
        def.setText("");
        minimum.setText("");
        maximum.setText("");
        details.setText("");
        categories.setText("");

        edit = false;

        createVisible = true;

        createDialog.show();

        prepareFields(0);
    }

    public void onBackPressed() {
        MainActivity.reloadData = true;
        finish();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                mChosenFile = data.getStringExtra("result");
                mHandler.post(importCSV);
            }
        }
    }

    // Helper function export data as CSV
    private void exportTable(String exportName) {
        File backup = new File(Constants.TRAITPATH);
        backup.mkdirs();

        File file = new File(Constants.TRAITPATH + "/" + exportName);

        try {
            FileWriter fw = new FileWriter(file);
            CSVWriter csvWriter = new CSVWriter(fw, ConfigActivity.dt.getAllTraitsForExport());
            csvWriter.writeTraitFile(ConfigActivity.dt.getTraitColumns());

            csvWriter.close();
        } catch (Exception ignore) {
        }

        shareFile(file);
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

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            dialog = new ProgressDialog(thisActivity);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html
                    .fromHtml(thisActivity.getString(R.string.import_dialog_importing)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            try {
                String[] data;
                String[] columns;

                FileReader fr = new FileReader(mChosenFile);

                CSVReader cr = new CSVReader(fr);

                columns = cr.readNext();

                data = columns;

                if (ConfigActivity.dt.isTableExists(DataHelper.TRAITS)) {
                    ConfigActivity.dt.deleteTable(DataHelper.TRAITS);
                }

                while (data != null) {
                    data = cr.readNext();

                    if (data != null) {
                        TraitObject t = new TraitObject();
                        t.trait = data[0];
                        t.format = data[1];
                        t.defaultValue = data[2];
                        t.minimum = data[3];
                        t.maximum = data[4];
                        t.details = data[5];
                        t.categories = data[6];
                        //t.visible = data[7].toLowerCase();
                        t.realPosition = data[8];
                        if (data[7].toLowerCase().equals("true")) {
                            t.visible = true;
                        } else {
                            t.visible = false;
                        }
                        ConfigActivity.dt.insertTraits(t);
                    }
                }

                try {
                    cr.close();
                } catch (Exception ignore) {
                }

                try {
                    fr.close();
                } catch (Exception ignore) {
                }

                ConfigActivity.dt.close();
                ConfigActivity.dt.open();

                File newDir = new File(mChosenFile);

                newDir.mkdirs();

            } catch (Exception e) {
                e.printStackTrace();
                fail = true;
            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            Editor ed = ep.edit();
            ed.putBoolean("CreateTraitFinished", true);
            ed.apply();

            loadData();

            MainActivity.reloadData = true;

            if (dialog.isShowing())
                dialog.dismiss();

            if (fail)
                makeToast(thisActivity.getString(R.string.import_error_general));
        }
    }

    public void makeToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private static void scanFile(File filePath) {
        MediaScannerConnection.scanFile(thisActivity, new String[]{filePath.getAbsolutePath()}, null, null);
    }

    /**
     * Scan file to update file list and share exported file
     */
    private void shareFile(File filePath) {
        MediaScannerConnection.scanFile(this, new String[]{filePath.getAbsolutePath()}, null, null);

        if (!ep.getBoolean(PreferencesActivity.DISABLE_SHARE, false)) {
            Intent intent = new Intent();
            intent.setAction(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".fileprovider", filePath));
            startActivity(Intent.createChooser(intent, "Sending File..."));
        }
    }
}
