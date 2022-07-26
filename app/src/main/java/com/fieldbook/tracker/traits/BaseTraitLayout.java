package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;

import java.util.Map;

public abstract class BaseTraitLayout extends LinearLayout {

    public BaseTraitLayout(Context context) {
        super(context);
    }

    public BaseTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BaseTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public abstract String type();  // return trait type

    public boolean isTraitType(String trait) {
        return trait.equals(type());
    }

    public abstract void init();

    public void init(Activity act) { /* not implemented */ }

    public abstract void loadLayout();

    public abstract void deleteTraitListener();

    public abstract void setNaTraitsText();

    public Map<String, String> getNewTraits() {
        return ((CollectActivity) getContext()).getNewTraits();
    }

    public TraitObject getCurrentTrait() {
        return ((CollectActivity) getContext()).getCurrentTrait();
    }

    public SharedPreferences getPrefs() {
        return getContext().getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0);
    }

    public RangeObject getCRange() {
        return ((CollectActivity) getContext()).getCRange();
    }

    public EditText getEtCurVal() {
        return ((CollectActivity) getContext()).getEtCurVal();
    }

    public TextWatcher getCvText() {
        return ((CollectActivity) getContext()).getCvText();
    }

    public String getDisplayColor() {

        return String.format("#%06X", (0xFFFFFF & getPrefs().getInt(GeneralKeys.SAVED_DATA_COLOR, Color.parseColor("#d50000"))));
    }

    /**
     * Calls the collect activities db function to insert an observation row.
     * @param traitName the name of the trait s.a "My Height Trait", "Height" (defined by user / FB)
     * @param traitType the type of trait s.a "Numeric", "Categorical" (defined by FB)
     * @param value the Text value to be saved in the row
     */
    public void updateTrait(String traitName, String traitType, String value) {
        ((CollectActivity) getContext()).updateTrait(traitName, traitType, value);
    }

    public void removeTrait(String parent) {
        ((CollectActivity) getContext()).removeTrait(parent);
    }

}