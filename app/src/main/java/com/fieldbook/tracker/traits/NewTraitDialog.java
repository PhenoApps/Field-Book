package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.fieldbook.tracker.ConfigActivity;
import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;

import java.util.ArrayList;
import java.util.HashMap;

import static com.fieldbook.tracker.fields.FieldEditorActivity.makeToast;
import static com.fieldbook.tracker.traits.TraitEditorActivity.displayBrapiInfo;
import static com.fieldbook.tracker.traits.TraitEditorActivity.loadData;

public class NewTraitDialog extends DialogFragment {
    private TraitEditorActivity originActivity;
    private AlertDialog createDialog;

    private TraitFormatCollection   traitFormats;
    private TraitObject oldTrait;

    // elements of this dialog
    private EditText        trait;
    private Spinner         format;
    private EditText        def;
    private EditText        minimum;
    private EditText        maximum;
    private EditText        details;
    private EditText        categories;
    private TextView        defTv;
    private ToggleButton    bool;
    private LinearLayout    defBox;
    private LinearLayout    minBox;
    private LinearLayout    maxBox;
    private LinearLayout    categoryBox;
    
    private TraitFormat     traitFormat;

    private int currentPosition;
    private boolean createVisible;
    private boolean edit;
    private SharedPreferences ep;
    private boolean brapiDialogShown;

    private TraitAdapter mAdapter;
    
    public NewTraitDialog(View layout, TraitEditorActivity activity) {
        // fields
        originActivity = activity;
        mAdapter = activity.getAdapter();
        traitFormats = new TraitFormatCollection();
        oldTrait = null;
        ep = activity.getPreferences();
        setBrAPIDialogShown(originActivity.getBrAPIDialogShown());
        createVisible = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(originActivity,
                                                        R.style.AppAlertDialog);

        builder.setTitle(R.string.traits_toolbar_add_trait)
                .setCancelable(true)
                .setView(layout);

        createDialog = builder.create();

        // each element of Dialog
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

        format.setOnItemSelectedListener(createFomatSelectionListener());

        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(
                                                TraitEditorActivity.thisActivity,
                                                R.layout.custom_spinnerlayout,
                                                traitFormats.getLocalStringList());
        format.setAdapter(itemsAdapter);

        closeBtn.setOnClickListener(createCloseButtonClickLister());
        saveBtn.setOnClickListener(createSaveButtonClickListener());

    }
    
    public void show(boolean edit_) {
        edit = edit_;
        createDialog.show();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        android.view.WindowManager.LayoutParams params = createDialog.getWindow().getAttributes();
        params.width = LinearLayout.LayoutParams.MATCH_PARENT;

        createDialog.getWindow().setAttributes(params);
        createDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface arg0) {
                createVisible = false;
            }
        });
        createDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface arg0) {
                createVisible = false;
            }
        });

        return createDialog;
    }

    private AdapterView.OnItemSelectedListener createFomatSelectionListener() {
        return new AdapterView.OnItemSelectedListener() {
            @Override
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

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        };
    }

    private View.OnClickListener createCloseButtonClickLister() {
        return new View.OnClickListener() {

            public void onClick(View arg0) {
                // Prompt the user if fields have been edited

                if (dataChanged(oldTrait)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(originActivity, R.style.AppAlertDialog);

                    builder.setTitle(getResString(R.string.dialog_close));
                    builder.setMessage(getResString(R.string.dialog_confirm));

                    builder.setPositiveButton(getResString(R.string.dialog_yes), new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            createDialog.dismiss();
                        }

                    });

                    builder.setNegativeButton(getResString(R.string.dialog_no), new DialogInterface.OnClickListener() {

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
        };
    }

    private View.OnClickListener createSaveButtonClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                final int index = format.getSelectedItemPosition();
                final TraitFormat traitFormat = traitFormats.getTraitFormatByIndex(index);
                final String errorMessage = traitFormat.ValidateItems();
                if (errorMessage.length() > 0) {    // not valid
                    originActivity.makeToast(errorMessage);
                    return;
                }
                
                final int pos = ConfigActivity.dt.getMaxPositionFromTraits() + 1;
                final int booleanIndex = traitFormats.findIndexByEnglishString("Boolean");
                if (format.getSelectedItemPosition() == booleanIndex) {
                    def.setText(bool.isChecked() ? "true" : "false");
                }

                if (!edit) {
                    TraitObject t = createTraitObjectByDialogItems(pos);
                    if (t == null)
                        return;

                    // TODO: Add the local trait data_source name into other trait editing/inserting db functions.
                    t.setTraitDataSource("local");
                    ConfigActivity.dt.insertTraits(t);
                } else {
                    restoreDialogItemsByTraitObject(oldTrait);
                }

                SharedPreferences.Editor ed = ep.edit();
                ed.putBoolean("CreateTraitFinished", true);
                ed.putBoolean("TraitsExported", false);
                ed.apply();

                // Display our BrAPI dialog if it has not been show already
                // Get our dialog state from our adapter to see if a trait has been selected
                // brapiDialogShown = mAdapter.infoDialogShown;
                setBrAPIDialogShown(mAdapter.infoDialogShown);
                if (!brapiDialogShown) {
                    // brapiDialogShown = displayBrapiInfo(originActivity,
                    //                          ConfigActivity.dt, null, true);
                    setBrAPIDialogShown(displayBrapiInfo(originActivity,
                                                ConfigActivity.dt, null, true));
                }

                loadData();

                MainActivity.reloadData = true;
                createDialog.dismiss();

            }
        };
    }

    public void initTrait() {
        oldTrait = null;

        trait.setText("");
        format.setSelection(0);
        def.setText("");
        minimum.setText("");
        maximum.setText("");
        details.setText("");
        categories.setText("");

        edit = false;

        createVisible = true;
    }
    
    private TraitObject createTraitObjectByDialogItems(int pos) {
        /*MainActivity.dt.insertTraits(trait.getText().toString().trim(),
                enData[format.getSelectedItemPosition()].toLowerCase(), def.getText().toString(),
                minimum.getText().toString(), maximum.getText().toString(),
                details.getText().toString(), categories.getText().toString(),
                "true", String.valueOf(pos));*/
        final int i = format.getSelectedItemPosition();
        final String englishFormat = traitFormats.getEnglishString(i);
        TraitObject t = new TraitObject();
        t.setTrait(trait.getText().toString().trim());
        t.setFormat(englishFormat.toLowerCase());
        t.setDefaultValue(def.getText().toString());
        t.setMinimum(minimum.getText().toString());
        t.setMaximum(maximum.getText().toString());
        t.setDetails(details.getText().toString());
        t.setCategories(categories.getText().toString());
        t.setVisible(true);
        t.setRealPosition(String.valueOf(pos));
        return t;
    }
    
    private void restoreDialogItemsByTraitObject(TraitObject t) {
                    // TODO: Add the trait_data_source variable into the edit.
        final int i = format.getSelectedItemPosition();
        final String englishFormat = traitFormats.getEnglishString(i);
        ConfigActivity.dt.editTraits(t.getId(),
                        trait.getText().toString().trim(),
                        englishFormat.toLowerCase(),
                        def.getText().toString(),
                        minimum.getText().toString(),
                        maximum.getText().toString(),
                        details.getText().toString(),
                        categories.getText().toString());
    }

    public void setTraitObject(TraitObject traitObject) {
        oldTrait = traitObject;
        trait.setText(traitObject.getTrait());
        currentPosition = traitFormats.findIndexByEnglishString(traitObject.getFormat());
        format.setSelection(currentPosition);
        prepareFields(currentPosition);

        def.setText(traitObject.getDefaultValue());
        bool.setChecked(traitObject.getDefaultValue().equals("true"));
        minimum.setText(traitObject.getMinimum());
        maximum.setText(traitObject.getMaximum());
        details.setText(traitObject.getDetails());
        categories.setText(traitObject.getCategories());
    }

    public void prepareFields(int index) {
        TraitFormat traitFormat = traitFormats.getTraitFormatByIndex(index);
        
        final String optional = getResString(R.string.traits_create_optional);
        details.setHint(optional);
        def.setHint(traitFormat.displaysDefaultHint() ? optional : null);
        minimum.setHint(traitFormat.displaysMinimumHint() ? optional : null);
        maximum.setHint(traitFormat.displaysMaximumHint() ? optional : null);

        defBox.setVisibility(visibility(traitFormat.isDefBoxVisible()));
        def.setVisibility(visibility(traitFormat.isDefaultVisible()));
        minBox.setVisibility(visibility(traitFormat.isMinBoxVisible()));
        maxBox.setVisibility(visibility(traitFormat.isMaxBoxVisible()));
        bool.setVisibility(visibility(traitFormat.isBooleanVisible()));
        categoryBox.setVisibility(visibility(traitFormat.isCategoryVisible()));

        if(traitFormat.isNumericInputType()) {
            final int inputType = InputType.TYPE_CLASS_NUMBER |
                                  InputType.TYPE_NUMBER_FLAG_DECIMAL;
            def.setInputType(inputType);
            minimum.setInputType(inputType);
            maximum.setInputType(inputType);
        }
    }

    private int visibility(Boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }

    // Helper function to see if any fields have been edited
    private boolean dataChanged(TraitObject o) {
        // not use a magic number
        final int booleanIndex = traitFormats.findIndexByEnglishString("Boolean");
        if (o != null) {
            final String defString = bool.isChecked() ? "true" : "false";

            if (!trait.getText().toString().equals(o.getTrait()))
                return true;

            if (format.getSelectedItemPosition() == booleanIndex) {
                if (!def.getText().toString().equals(defString))
                    return true;
            } else {
                if (!def.getText().toString().equals(o.getDefaultValue()))
                    return true;
            }

            return (!minimum.getText().toString().equals(o.getMinimum())) ||
                   (!maximum.getText().toString().equals(o.getMaximum())) ||
                   (!details.getText().toString().equals(o.getDetails())) ||
                   (!categories.getText().toString().equals(o.getCategories()));

        } else {
            if (trait.getText().toString().length() > 0 ||
                        def.getText().toString().length() > 0 ||
                        minimum.getText().toString().length() > 0 ||
                        maximum.getText().toString().length() > 0 ||
                        details.getText().toString().length() > 0 ||
                        categories.getText().toString().length() > 0)
                return true;

            if (format.getSelectedItemPosition() == booleanIndex) {
                if (bool.isChecked())
                    return true;
            }
            return false;
        }
    }
    
    private String getResString(int id) {
        return originActivity.getResourceString(id);
    }
    
    // when this value changes in this class,
    // the value in TraitEditorActivity must change
    private void setBrAPIDialogShown(boolean b) {
        brapiDialogShown = b;
        originActivity.setBrAPIDialogShown(b);
    }
    
    // Non negative numbers only
    public static boolean isNumeric(String str, boolean positive) {
        if (str.length() == 0)
            return false;

        try {
            double d = Double.parseDouble(str);

            return !positive || d >= 0;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }
    
    public static boolean isNumericOrEmpty(String str, boolean positive) {
        return str.length() == 0 || isNumeric(str, positive);
    }

    ///// Classes to absorb format differences /////
    
    // If you want add a new format, you create a class which extends TraitFormat
    private abstract class TraitFormat {
        // whether is each item of this dialog visible or not 
        abstract public boolean isDefBoxVisible();
        abstract public boolean isDefaultVisible();
        abstract public boolean isBooleanVisible();
        abstract public boolean isMinBoxVisible();
        abstract public boolean isMaxBoxVisible();
        abstract public boolean isCategoryVisible();
        
        // whether does each item of this dialog display "option"
        abstract public boolean displaysDefaultHint();
        abstract public boolean displaysMinimumHint();
        abstract public boolean displaysMaximumHint();
        
        // whether is the input type each item of this dialog number
        abstract public boolean isNumericInputType();
        
        abstract public String getEnglishString();
        abstract public int getResourceId();
        public String getLocalString() {
            return getResString(getResourceId());
        }
        
        public String ValidateItems() {
            final String validation = ValidateItemsCommon();
            if (validation.length() > 0) {
                return validation;
            }
            else {
                return ValidateItemsIndividual();
            }
        }
        
        protected String ValidateItemsCommon() {
            // Trait name is mandatory
            if (trait.getText().toString().length() == 0) {
                return getResString(R.string.traits_create_warning_name_blank);
            }

            // Disallow duplicate traits
            final String traitName = trait.getText().toString().trim();
            final boolean exists = ConfigActivity.dt.hasTrait(traitName);
            if (!edit) {
                if (exists) {
                    return getResString(R.string.traits_create_warning_duplicate);
                }
            } else {
                if (exists & !oldTrait.getTrait().toLowerCase().equals(traitName.toLowerCase())) {
                    return getResString(R.string.traits_create_warning_duplicate);
                }
            }
            return "";  // OK until here
        }
        
        abstract public String ValidateItemsIndividual();
    }
    
    abstract private class TraitFormatNotValue extends TraitFormat {
        public boolean isDefBoxVisible() { return false; }
        public boolean isDefaultVisible() { return false; }
        public boolean isBooleanVisible() { return false; }
        public boolean isMinBoxVisible() { return false; }
        public boolean isMaxBoxVisible() { return false; }
        public boolean isCategoryVisible() { return false; }
        
        public boolean displaysDefaultHint() { return false; }
        public boolean displaysMinimumHint() { return false; }
        public boolean displaysMaximumHint() { return false; }
        
        public boolean isNumericInputType() { return false; }
        
        public String ValidateItemsIndividual() { return ""; }
    }
    
    abstract private class TraitFormatWithRange extends TraitFormat {
        public boolean isDefBoxVisible() { return true; }
        public boolean isDefaultVisible() { return true; }
        public boolean isBooleanVisible() { return false; }
        public boolean isMinBoxVisible() { return true; }
        public boolean isMaxBoxVisible() { return true; }
        public boolean isCategoryVisible() { return false; }
        
        public boolean isNumericInputType() { return false; }
        
        public String getEnglishString() { return "Numeric"; }
        public int getResourceId() { return R.string.traits_format_numeric; }
        
        abstract public boolean allowsNegative();
        
        public String ValidateItemsIndividual() {
            final boolean b = !allowsNegative();
            if ((!isNumericOrEmpty(def.getText().toString(), b)) ||
                    (!isNumericOrEmpty(minimum.getText().toString(), b)) ||
                    (!isNumericOrEmpty(maximum.getText().toString(), b))) {
                return getResString(R.string.traits_create_warning_numeric_required);
            }
            
            // minimum <= def <= maximum
            if (!isValidMagnitudeRelation(minimum, def) ||
                    !isValidMagnitudeRelation(def, maximum) ||
                    !isValidMagnitudeRelation(minimum, maximum)) {
                return "Magnitude Relation Error";
            }
            return "";
        }
        
        private boolean isValidMagnitudeRelation(final EditText e1, final EditText e2) {
            final String s1 = e1.getText().toString();
            final String s2 = e2.getText().toString();
            return s1.length() == 0 || s2.length() == 0 ||
                        Double.parseDouble(s1) <= Double.parseDouble(s2);
        }
    }
    
    private class TraitFormatNumeric extends TraitFormatWithRange {
        public boolean displaysDefaultHint() { return true; }
        public boolean displaysMinimumHint() { return true; }
        public boolean displaysMaximumHint() { return true; }
        
        public String getEnglishString() { return "Numeric"; }
        public int getResourceId() { return R.string.traits_format_numeric; }
        
        public boolean allowsNegative() { return true; }
    }
    
    private class TraitFormatCategorical extends TraitFormat {
        public boolean isDefBoxVisible() { return false; }
        public boolean isDefaultVisible() { return false; }
        public boolean isBooleanVisible() { return false; }
        public boolean isMinBoxVisible() { return false; }
        public boolean isMaxBoxVisible() { return false; }
        public boolean isCategoryVisible() { return true; }
        
        public boolean displaysDefaultHint() { return false; }
        public boolean displaysMinimumHint() { return false; }
        public boolean displaysMaximumHint() { return false; }
        
        public boolean isNumericInputType() { return false; }
        
        public String getEnglishString() { return "Categorical"; }
        public int getResourceId() { return R.string.traits_format_categorical; }
        
        public String ValidateItemsIndividual() {
            if (categories.getText().toString().length() == 0) {
                return getResString(R.string.traits_create_warning_categories_required);
            }
            return "";
        }
    }
    
    private class TraitFormatDate extends TraitFormatNotValue {
        public String getEnglishString() { return "Date"; }
        public int getResourceId() { return R.string.traits_format_date; }
    }
    
    private class TraitFormatPercent extends TraitFormatWithRange {
        public boolean displaysDefaultHint() { return false; }
        public boolean displaysMinimumHint() { return false; }
        public boolean displaysMaximumHint() { return false; }
        
        public String getEnglishString() { return "Percent"; }
        public int getResourceId() { return R.string.traits_format_percent; }
        
        public boolean isNumericInputType() { return true; }
        
        public boolean allowsNegative() { return false; }
    }
    
    private class TraitFormatBoolean extends TraitFormat {
        public boolean isDefBoxVisible() { return true; }
        public boolean isDefaultVisible() { return false; }
        public boolean isBooleanVisible() { return true; }
        public boolean isMinBoxVisible() { return false; }
        public boolean isMaxBoxVisible() { return false; }
        public boolean isCategoryVisible() { return false; }
        
        public boolean displaysDefaultHint() { return false; }
        public boolean displaysMinimumHint() { return false; }
        public boolean displaysMaximumHint() { return false; }
        
        public boolean isNumericInputType() { return false; }
        
        public String getEnglishString() { return "Boolean"; }
        public int getResourceId() { return R.string.traits_format_boolean; }
        
        public String ValidateItemsIndividual() { return ""; }
    }
    
    private class TraitFormatText extends TraitFormat {
        public boolean isDefBoxVisible() { return true; }
        public boolean isDefaultVisible() { return true; }
        public boolean isBooleanVisible() { return false; }
        public boolean isMinBoxVisible() { return false; }
        public boolean isMaxBoxVisible() { return false; }
        public boolean isCategoryVisible() { return false; }
        
        public boolean displaysDefaultHint() { return true; }
        public boolean displaysMinimumHint() { return false; }
        public boolean displaysMaximumHint() { return false; }
        
        public boolean isNumericInputType() { return false; }
        
        public String getEnglishString() { return "Text"; }
        public int getResourceId() { return R.string.traits_format_text; }
        
        public String ValidateItemsIndividual() { return ""; }
    }
    
    private class TraitFormatPhoto extends TraitFormatNotValue {
        public String getEnglishString() { return "Photo"; }
        public int getResourceId() { return R.string.traits_format_photo; }
    }
    
    private class TraitFormatAudio extends TraitFormatNotValue {
        public String getEnglishString() { return "Audio"; }
        public int getResourceId() { return R.string.traits_format_audio; }
    }
    
    private class TraitFormatCounter extends TraitFormatNotValue {
        public String getEnglishString() { return "Counter"; }
        public int getResourceId() { return R.string.traits_format_counter; }
    }
    
    private class TraitFormatDiseaseRating extends TraitFormatNotValue {
        public String getEnglishString() { return "Disease Rating"; }
        public int getResourceId() {
            return R.string.traits_format_disease_rating;
        }
    }
    
    private class TraitFormatMulticat extends TraitFormatNotValue {
        public String getEnglishString() { return "Multicat"; }
        public int getResourceId() {
            return R.string.traits_format_multicategorical;
        }
    }
    
    private class TraitFormatLocation extends TraitFormatNotValue {
        public String getEnglishString() { return "Location"; }
        public int getResourceId() { return R.string.traits_format_location; }
    }
    
    private class TraitFormatBarcode extends TraitFormatNotValue {
        public String getEnglishString() { return "Barcode"; }
        public int getResourceId() { return R.string.traits_format_barcode; }
    }
    
    private class TraitFormatZebraLablePrint extends TraitFormatNotValue {
        public String getEnglishString() { return "Zebra Label Print"; }
        public int getResourceId() { return R.string.traits_format_labelprint; }
    }
    
    private class TraitFormatCollection {
        private ArrayList<TraitFormat>  traitFormatList;
        
        public TraitFormatCollection() {
            traitFormatList = new ArrayList<>();
            traitFormatList.add(new TraitFormatNumeric());
            traitFormatList.add(new TraitFormatCategorical());
            traitFormatList.add(new TraitFormatDate());
            traitFormatList.add(new TraitFormatPercent());
            traitFormatList.add(new TraitFormatBoolean());
            traitFormatList.add(new TraitFormatText());
            traitFormatList.add(new TraitFormatPhoto());
            traitFormatList.add(new TraitFormatAudio());
            traitFormatList.add(new TraitFormatCounter());
            traitFormatList.add(new TraitFormatDiseaseRating());
            traitFormatList.add(new TraitFormatMulticat());
            traitFormatList.add(new TraitFormatLocation());
            traitFormatList.add(new TraitFormatBarcode());
            traitFormatList.add(new TraitFormatZebraLablePrint());
        }
        
        public int size() { return traitFormatList.size(); }
        
        public TraitFormat getTraitFormatByIndex(int index)
                                throws ArrayIndexOutOfBoundsException {
            if (index < 0 || index >= traitFormatList.size()) {
                throw new ArrayIndexOutOfBoundsException("");
            }
            
            return traitFormatList.get(index);
        }
        
        public String getEnglishString(int index) {
            return getTraitFormatByIndex(index).getEnglishString();
        }
        
        public String[] getLocalStringList() {
            String[] array = new String[size()];
            for (int i = 0; i < size(); ++i) {
                array[i] = traitFormatList.get(i).getLocalString();
            };
            return array;
        }
        
        public int findIndexByEnglishString(String format) {
            for (int i = 0; i < size(); ++i) {
                if (getEnglishString(i).toLowerCase().equals(
                                                format.toLowerCase())) {
                    return i;
                }
            }
            return 0;
        }
    }
}
