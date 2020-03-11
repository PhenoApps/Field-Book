package com.fieldbook.tracker.traitLayouts;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.traits.TraitObject;

import java.util.Map;

import static com.fieldbook.tracker.MainActivity.thisActivity;

public abstract class TraitLayout extends LinearLayout {
    protected enum BarcodeTarget {
        PlotID, Value
    };
    
    protected BarcodeTarget barcodeTarget;
    
    public TraitLayout(Context context) {
        super(context);
    }

    public TraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    
    public boolean isValidData(String value) { return true; }

    public abstract String type();  // return trait type

    public boolean isTraitType(String trait) {
        return trait.equals(type());
    }

    public void setBarcodeTargetValue()    { barcodeTarget = BarcodeTarget.Value; }
    public void setBarcodeTargetPlotID()   { barcodeTarget = BarcodeTarget.PlotID; }
    public boolean isBarcodeTargetValue()  { return barcodeTarget == BarcodeTarget.Value; }
    public boolean isBarcodeTargetPlotID() { return barcodeTarget == BarcodeTarget.PlotID; }

    public abstract void init();

    public abstract void loadLayout();

    public abstract void deleteTraitListener();

    public abstract void setNaTraitsText();

    public Map getNewTraits() {
        return ((MainActivity) getContext()).getNewTraits();
    }

    public TraitObject getCurrentTrait() {
        return ((MainActivity) getContext()).getCurrentTrait();
    }

    public SharedPreferences getPrefs() {
        return getContext().getSharedPreferences("Settings", 0);
    }

    public RangeObject getCRange() {
        return ((MainActivity) getContext()).getCRange();
    }

    public EditText getEtCurVal() {
        return ((MainActivity) getContext()).getEtCurVal();
    }

    public TextWatcher getCvText() {
        return ((MainActivity) getContext()).getCvText();
    }

    public String getDisplayColor() {
        return ((MainActivity) getContext()).getDisplayColor();
    }

    public void makeToast(String message) {
        Toast.makeText(getContext().getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    public void updateTrait(String parent, String trait, String value) {
        ((MainActivity) getContext()).updateTrait(parent, trait, value);
    }

    public void removeTrait(String parent) {
        ((MainActivity) getContext()).removeTrait(parent);
    }
    
    // if the value is not set to etCurVal, overload this method
    public void setValue(String value) {
        TraitObject trait = ((MainActivity)thisActivity).getCurrentTrait();
        getEtCurVal().setText(value);
        updateTrait(getCurrentTrait().getTrait(), trait.getFormat(), value);
    }
}