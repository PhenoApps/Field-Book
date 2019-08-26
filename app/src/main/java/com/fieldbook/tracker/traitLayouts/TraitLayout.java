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

public abstract class TraitLayout extends LinearLayout {
    public TraitLayout(Context context) {
        super(context);
    }

    public TraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public abstract void init();
    public abstract void loadLayout();
    public abstract void deleteTraitListener();

    public Map getNewTraits(){
        return ((MainActivity) getContext()).getNewTraits();
    }
    public TraitObject getCurrentTrait(){
        return ((MainActivity) getContext()).getCurrentTrait();
    }
    public SharedPreferences getPrefs(){
        return getContext().getSharedPreferences("Settings", 0);
    }
    public RangeObject getCRange(){
        return ((MainActivity) getContext()).getCRange();
    }
    public EditText getEtCurVal(){
        return ((MainActivity) getContext()).getEtCurVal();
    }
    public TextWatcher getCvText(){
        return ((MainActivity) getContext()).getCvText();
    }
    public TextWatcher getCvNum(){
        return ((MainActivity) getContext()).getCvNum();
    }
    public String getDisplayColor(){
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
}
