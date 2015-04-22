package com.fieldbook.tracker.Trait;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.InputType;
import android.util.Log;
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
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.fieldbook.tracker.CSVReader;
import com.fieldbook.tracker.CSVWriter;
import com.fieldbook.tracker.Constants;
import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.FileExploreActivity;
import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.Tutorial.TutorialTraitsActivity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

public class TraitEditorActivity extends Activity {

    private static Handler mHandler = new Handler();

    private static final String TRT = ".trt";

    public static ListView traitList;
    public static TraitAdapter mAdapter;
    public static boolean createVisible;

    public static Activity thisActivity;
    public static EditText trait;

    private static String[] mFileList;
    private static String mChosenFile;

    private static SharedPreferences ep;

    private static Dialog importDialog;
    private static OnItemClickListener traitListener;

    private Dialog createDialog;

    String currentId;

    private boolean edit;

    private Spinner format;
    private EditText def;
    private EditText minimum;
    private EditText maximum;
    private EditText details;
    private EditText categories;
    private ToggleButton bool;

    private String oldTrait;

    private LinearLayout defBox;
    private LinearLayout minBox;
    private LinearLayout maxBox;
    private LinearLayout categoryBox;

    int currentPosition;

    private String local;
    private String region;

    private TraitObject o;

    private Menu systemMenu;

    @Override
    public void onDestroy() {
        try {
            TutorialTraitsActivity.thisActivity.finish();
        } catch (Exception e) {

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        // Enforce internal language change
        local = ep.getString("language", "en");
        region = ep.getString("region", "");
        Locale locale2 = new Locale(local, region);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2, getBaseContext().getResources()
                .getDisplayMetrics());

        setContentView(R.layout.list);

        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        thisActivity = this;

        final String[] data = new String[8];
        final String[] enData = new String[8];

        data[0] = getString(R.string.numeric);
        data[1] = getString(R.string.qualitative);
        data[2] = getString(R.string.date);
        data[3] = getString(R.string.percent);
        data[4] = getString(R.string.bool);
        data[5] = getString(R.string.text);
        data[6] = getString(R.string.photo);
        data[7] = getString(R.string.audio);

        enData[0] = "Numeric";
        enData[1] = "Categorical";
        enData[2] = "Date";
        enData[3] = "Percent";
        enData[4] = "Boolean";
        enData[5] = "Text";
        enData[6] = "Photo";
        enData[7] = "Audio";

        traitList = (ListView) findViewById(R.id.myList);

        createDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        createDialog.setContentView(R.layout.trait);
        createDialog.setTitle(getString(R.string.addtrait));
        createDialog.setCancelable(true);
        createDialog.setCanceledOnTouchOutside(true);

        android.view.WindowManager.LayoutParams params = createDialog.getWindow().getAttributes();
        params.width = LayoutParams.FILL_PARENT;

        createDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
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

        trait = (EditText) createDialog.findViewById(R.id.trait);
        format = (Spinner) createDialog.findViewById(R.id.format);
        def = (EditText) createDialog.findViewById(R.id.def);
        minimum = (EditText) createDialog.findViewById(R.id.minimum);
        maximum = (EditText) createDialog.findViewById(R.id.maximum);
        details = (EditText) createDialog.findViewById(R.id.details);
        categories = (EditText) createDialog.findViewById(R.id.categories);

        defBox = (LinearLayout) createDialog.findViewById(R.id.defbox);
        minBox = (LinearLayout) createDialog.findViewById(R.id.minbox);
        maxBox = (LinearLayout) createDialog.findViewById(R.id.maxbox);
        categoryBox = (LinearLayout) createDialog.findViewById(R.id.categorybox);

        bool = (ToggleButton) createDialog.findViewById(R.id.boolBtn);

        Button saveBtn = (Button) createDialog.findViewById(R.id.saveBtn);
        Button closeBtn = (Button) createDialog.findViewById(R.id.closeBtn);

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

        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<String>(this, R.layout.spinnerlayout, data);
        format.setAdapter(itemsAdapter);

        closeBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                // Prompt the user if fields have been edited

                if (dataChanged()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(TraitEditorActivity.this);

                    builder.setTitle(getString(R.string.close));
                    builder.setMessage(getString(R.string.areyousure));

                    builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            createDialog.dismiss();
                        }

                    });

                    builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {

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
                    Toast.makeText(TraitEditorActivity.this, getString(R.string.mandatorytrait),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // Disallow duplicate traits
                boolean exists = MainActivity.dt.hasTrait(trait.getText().toString().trim());

                if (!edit) {
                    if (exists) {
                        Toast.makeText(TraitEditorActivity.this, getString(R.string.traitexists),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                } else {
                    if (exists & !oldTrait.toLowerCase().equals(trait.getText().toString().trim().toLowerCase())) {
                        Toast.makeText(TraitEditorActivity.this, getString(R.string.traitexists),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                // The checks below are format specific

                if (format.getSelectedItemPosition() == 0) {
                    if (def.getText().toString().length() > 0 & !isNumeric(def.getText().toString(), false)) {
                        Toast.makeText(TraitEditorActivity.this, getString(R.string.notanumber),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (minimum.getText().toString().length() > 0 & !isNumeric(minimum.getText().toString(), false)) {
                        Toast.makeText(TraitEditorActivity.this, getString(R.string.notanumber),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (maximum.getText().toString().length() > 0 & !isNumeric(maximum.getText().toString(), false)) {
                        Toast.makeText(TraitEditorActivity.this, getString(R.string.notanumber),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                }

                if (format.getSelectedItemPosition() == 1) {
                    if (categories.getText().toString().length() == 0) {
                        Toast.makeText(TraitEditorActivity.this, getString(R.string.notemptycategory),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                if (format.getSelectedItemPosition() == 3) {
                    if (def.getText().toString().length() == 0 | !isNumeric(def.getText().toString(), true)) {
                        Toast.makeText(TraitEditorActivity.this, getString(R.string.notanumber),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (minimum.getText().toString().length() == 0 | !isNumeric(minimum.getText().toString(), true)) {
                        Toast.makeText(TraitEditorActivity.this, getString(R.string.notanumber),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (maximum.getText().toString().length() == 0 | !isNumeric(maximum.getText().toString(), true)) {
                        Toast.makeText(TraitEditorActivity.this, getString(R.string.notanumber),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                }

                int pos = MainActivity.dt.getMaxPositionFromTraits() + 1;

                if (format.getSelectedItemPosition() == 4) {
                    if (bool.isChecked())
                        def.setText("true");
                    else
                        def.setText("false");
                }

                if (!edit)
                    MainActivity.dt.insertTraits(trait.getText().toString().trim(),
                            enData[format.getSelectedItemPosition()].toLowerCase(), def.getText().toString(),
                            minimum.getText().toString(), maximum.getText().toString(),
                            details.getText().toString(), categories.getText().toString(),
                            "true", String.valueOf(pos));
                else
                    MainActivity.dt.editTraits(currentId, trait.getText().toString().trim(),
                            format.getSelectedItem().toString().toLowerCase(), def.getText().toString(),
                            minimum.getText().toString(), maximum.getText().toString(),
                            details.getText().toString(), categories.getText().toString());

                Editor ed = ep.edit();
                ed.putBoolean("CreateTraitFinished", true);
                ed.commit();

                loadData();

                MainActivity.reloadData = true;

                createDialog.dismiss();
            }
        });
    }

    // Helper function to see if any fields have been edited
    private boolean dataChanged() {
        if (o != null) {
            String defString = "";

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

    // Reset fields based on the trait type
    private void prepareFields(int position) {
        if (position == 1)
            categoryBox.setVisibility(View.VISIBLE);
        else
            categoryBox.setVisibility(View.GONE);

        def.setVisibility(View.VISIBLE);
        bool.setVisibility(View.GONE);

        defBox.setVisibility(View.VISIBLE);
        minBox.setVisibility(View.VISIBLE);
        maxBox.setVisibility(View.VISIBLE);

        if (position == 1 || position == 5) {
            minBox.setVisibility(View.GONE);
            maxBox.setVisibility(View.GONE);
        } else {
            minBox.setVisibility(View.VISIBLE);
            maxBox.setVisibility(View.VISIBLE);
        }

        if (position == 4) {
            def.setVisibility(View.GONE);
            bool.setVisibility(View.VISIBLE);

            minBox.setVisibility(View.GONE);
            maxBox.setVisibility(View.GONE);
        }

        if (position == 6 || position == 2) {
            defBox.setVisibility(View.GONE);
            minBox.setVisibility(View.GONE);
            maxBox.setVisibility(View.GONE);
        }

        def.setHint(null);
        minimum.setHint(null);
        maximum.setHint(null);

        details.setHint(getString(R.string.optional));

        switch (position) {
            case 0:
                def.setHint(getString(R.string.optional));
                minimum.setHint(getString(R.string.optional));
                maximum.setHint(getString(R.string.optional));
                break;

            case 3:
                //def.setHint(getString(R.string.optional));
                break;

            case 1:
            case 5:
                def.setHint(getString(R.string.optional));
                break;
        }

        switch (position) {
            case 0:
            case 3:
                def.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                minimum.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                maximum.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;

            case 1:
            case 2:
            case 4:
            case 5:
            case 6:
                def.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                minimum.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                maximum.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                break;
        }

    }

    // pad strings
    private static String pad(int c) {
        if (c >= 10)
            return String.valueOf(c);
        else
            return "0" + String.valueOf(c);
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

    // Check if string is boolean
    public static boolean isBoolean(String str) {
        if (!str.equals("true") & !str.equals("false"))
            return false;
        else
            return true;
    }

    // Helper function to load data
    public static void loadData() {
        try {

            HashMap visibility = MainActivity.dt.getTraitVisibility();

            if (!traitList.isShown())
                traitList.setVisibility(ListView.VISIBLE);

            mAdapter = new TraitAdapter(thisActivity, MainActivity.dt.getAllTraitObjects(), traitListener, visibility);

            traitList.setAdapter(mAdapter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(TraitEditorActivity.this).inflate(R.menu.traitmenu, menu);

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
                // Open tips
                Intent intent = new Intent();
                intent.setClassName(TraitEditorActivity.this,
                        TutorialTraitsActivity.class.getName());
                startActivity(intent);
                break;

            case R.id.deleteTrait:
                showDeleteTraitDialog();
                break;

            case R.id.addTrait:
                showCreateTraitDialog();
                break;

            case R.id.sortTrait:
                sortDialog();
                break;

            case R.id.importexport:
                importExportDialog();
                break;

            case R.id.toggleTrait:
                changeAllVisibility();
                break;

            case android.R.id.home:
                finish();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void changeAllVisibility() {
        Boolean globalVis = ep.getBoolean("allTraitsVisible",false);
        String[] allTraits = MainActivity.dt.getTraitColumnData("trait");

        for (int j = 0; j < allTraits.length; j++) {
            MainActivity.dt.updateTraitVisibility(allTraits[j],globalVis);
            Log.d("Field",allTraits[j]);
        }

        globalVis = !globalVis;

        Editor ed = ep.edit();
        ed.putBoolean("allTraitsVisible",globalVis);
        ed.commit();
        loadData();
    }

    private void importExportDialog() {
        final Dialog importExport = new Dialog(TraitEditorActivity.this,
                android.R.style.Theme_Holo_Light_Dialog);
        importExport.setTitle(getString(R.string.importdb));
        importExport.setContentView(R.layout.config);

        android.view.WindowManager.LayoutParams params = importExport.getWindow().getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        importExport.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);

        importExport.setCancelable(true);
        importExport.setCanceledOnTouchOutside(true);

        ListView myList = (ListView) importExport
                .findViewById(R.id.myList);

        String[] sortOptions = new String[2];

        sortOptions[0] = getString(R.string.importdb);
        sortOptions[1] = getString(R.string.export);

        myList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int which, long arg3) {
                switch (which) {
                    case 0:
                        showImportDialog();
                        break;
                    case 1:
                        showExportDialog();
                        break;
                }
                importExport.dismiss();
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.listitem, sortOptions);
        myList.setAdapter(adapter);
        Button sortCloseBtn = (Button) importExport
                .findViewById(R.id.closeBtn);

        sortCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                importExport.dismiss();
            }
        });
        importExport.show();
    }

    private void sortDialog() {
        final Dialog sortDialog = new Dialog(TraitEditorActivity.this,
                android.R.style.Theme_Holo_Light_Dialog);
        sortDialog.setTitle(getString(R.string.sort));
        sortDialog.setContentView(R.layout.config);

        android.view.WindowManager.LayoutParams params = sortDialog.getWindow().getAttributes();
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        sortDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);

        sortDialog.setCancelable(true);
        sortDialog.setCanceledOnTouchOutside(true);

        ListView myList = (ListView) sortDialog
                .findViewById(R.id.myList);

        String[] sortOptions = new String[3];

        sortOptions[0] = getString(R.string.traitname);
        sortOptions[1] = getString(R.string.format);
        sortOptions[2] = getString(R.string.visibility);

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

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.listitem, sortOptions);
        myList.setAdapter(adapter);
        Button sortCloseBtn = (Button) sortDialog
                .findViewById(R.id.closeBtn);

        sortCloseBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                sortDialog.dismiss();
            }
        });
        sortDialog.show();
    }

    private void sortTraitList(String colName) {
        String[] sortList = MainActivity.dt.getTraitColumnData(colName);

        ArrayIndexComparator comparator = new ArrayIndexComparator(sortList);
        Integer[] indexes = comparator.createIndexArray();
        Arrays.sort(indexes, comparator);

        if (colName.equals("isVisible")) {
            Arrays.sort(indexes, Collections.reverseOrder());
        }

        for (int j = 0; j < indexes.length; j++) {
            MainActivity.dt.writeNewPosition(colName, sortList[j], Integer.toString(indexes[j]));
            Log.e("TRAIT", sortList[j] + " " + indexes[j].toString());
        }

        loadData();
    }

    public class ArrayIndexComparator implements Comparator<Integer> {
        private final String[] array;

        public ArrayIndexComparator(String[] array) {
            this.array = array;
        }

        public Integer[] createIndexArray() {
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
        final Dialog exportDialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog);
        exportDialog.setTitle(getString(R.string.exportas));
        exportDialog.setContentView(R.layout.savedb);

        exportDialog.setCancelable(true);
        exportDialog.setCanceledOnTouchOutside(true);

        android.view.WindowManager.LayoutParams langParams = exportDialog.getWindow().getAttributes();
        langParams.width = LayoutParams.FILL_PARENT;
        exportDialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) langParams);

        Button closeBtn = (Button) exportDialog.findViewById(R.id.closeBtn);

        closeBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                exportDialog.dismiss();
            }
        });

        final EditText exportFile = (EditText) exportDialog.findViewById(R.id.fileName);

        // As the export filename uses the import file name as well,
        // we parse it out here
        String fName = "trait";
        SimpleDateFormat timeStamp = new SimpleDateFormat(
                "yyyy.MM.dd", Locale.getDefault());

        exportFile.setText(fName
                + "_export_"
                + timeStamp
                .format(Calendar.getInstance().getTime())
                + ".trt");

        Button exportButton = (Button) exportDialog.findViewById(R.id.saveBtn);

        exportButton.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                exportTable(exportFile.getText().toString());
                exportDialog.dismiss();
            }
        });

        exportDialog.show();
    }

    private void showDeleteTraitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(TraitEditorActivity.this);

        builder.setTitle(getString(R.string.deletealltraits));
        builder.setMessage(getString(R.string.areyousure));

        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Editor ed = ep.edit();
                ed.remove("MAPCONFIGURED");
                ed.commit();
                MainActivity.dt.deleteTable(MainActivity.dt.TRAITS);
                loadData();
                dialog.dismiss();
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

    public void showImportDialog() {
        loadFileList(TRT);
        Intent intent = new Intent();

        intent.setClassName(thisActivity,
                FileExploreActivity.class.getName());
        intent.putExtra("path", Constants.TRAITPATH);
        startActivityForResult(intent, 1);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                mChosenFile = data.getStringExtra("result");
                mHandler.post(importCSV);
            }
            if (resultCode == RESULT_CANCELED) {
            }
        }
    }

    public static void hideImportDialog() {
        try {
            importDialog.dismiss();
        } catch (Exception e) {

        }
    }

    // Creates a list of all files in directory by type
    private static void loadFileList(final String type) {

        File data = new File(Constants.TRAITPATH);

        if (data.exists()) {
            FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String filename) {
                    File sel = new File(dir, filename);
                    return filename.contains(type);
                }
            };
            mFileList = data.list(filter);
        } else {
            mFileList = new String[0];
        }
    }

    // Helper function export data as CSV
    private void exportTable(String exportName) {
        File backup = new File(Constants.TRAITPATH);

        backup.mkdirs();

        File file = new File(Constants.TRAITPATH + "/" + exportName);

        try {
            FileWriter fw = new FileWriter(file);

            CSVWriter csvWriter = new CSVWriter(fw, MainActivity.dt.getAllTraitsForExport());

            csvWriter.writeTraitFile(MainActivity.dt.getTraitColumns());

            csvWriter.close();
        } catch (Exception sqlEx) {
        }

        shareFile(file);

    }

    // Creates a new thread to do importing
    private static Runnable importCSV = new Runnable() {
        public void run() {
            new ImportCSVTask().execute(0);
        }

    };

    private static class ImportCSVTask extends AsyncTask<Integer, Integer, Integer> {
        ProgressDialog dialog;

        boolean fail;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            dialog = new ProgressDialog(thisActivity);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html
                    .fromHtml(thisActivity.getString(R.string.importmsg)));
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

                MainActivity.dt.deleteTable(MainActivity.dt.TRAITS);

                while (data != null) {
                    data = cr.readNext();

                    if (data != null) {
                        MainActivity.dt.insertTraits(data[0], data[1],
                                data[2], data[3], data[4], data[5],
                                data[6], data[7].toLowerCase(), data[8]);

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

            // This makes the map dialog appear again
            ed.remove("MAPCONFIGURED");
            ed.commit();

            loadData();

            MainActivity.reloadData = true;

            if (dialog.isShowing())
                dialog.dismiss();

            if (fail)
                Toast.makeText(thisActivity, thisActivity.getString(R.string.importerror),
                        Toast.LENGTH_LONG).show();
        }
    }

    public static void makeToast(String message) {

        Toast.makeText(TraitEditorActivity.thisActivity, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Scan file to update file list and share exported file
     */
    private void shareFile(File filePath) {
        MediaScannerConnection.scanFile(this, new String[]{filePath.getAbsolutePath()}, null, null);
        Intent intent = new Intent();
        intent.setAction(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(filePath));
        try {
            startActivity(Intent.createChooser(intent, "Sending File..."));
        } finally {
        }
    }
}
