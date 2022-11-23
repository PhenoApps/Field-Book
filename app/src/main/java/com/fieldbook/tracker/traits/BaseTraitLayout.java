package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;

import java.util.Map;

public abstract class BaseTraitLayout extends LinearLayout {

    //tracks if data can be entered or not
    //references the collect activity locked state (locked, unlocked or frozen)
    protected boolean isLocked = false;

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

    public void loadLayout() {

        CollectActivity act = (CollectActivity) getContext();
        isLocked = act.isFrozen() || act.isLocked();

        Map<String, String> observations = getNewTraits();
        TraitObject trait = getCurrentTrait();
        String traitName = trait.getTrait();
        if (observations.containsKey(traitName)) {

            String value = null;
            if (traitName != null) {
                value = observations.get(traitName);
            }

            getEtCurVal().setText(value);
            getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));

            afterLoadExists(act, value);

        } else {
            getEtCurVal().setText("");
            getEtCurVal().setTextColor(Color.BLACK);

            if (trait.getDefaultValue() != null && !trait.getDefaultValue().isEmpty()) {
                getEtCurVal().setText(trait.getDefaultValue());
                updateTrait(trait.getTrait(), trait.getFormat(), getEtCurVal().getText().toString());
                afterLoadDefault(act);
            } else afterLoadNotExists(act);
        }
    }

    public void afterLoadExists(CollectActivity act, @Nullable String value) {
        //lock data if frozen or locked state
        isLocked = act.isFrozen() || act.isLocked();
    }

    public void afterLoadDefault(CollectActivity act) {
        //unlock data only if frozen
        isLocked = act.isLocked();
    }

    public void afterLoadNotExists(CollectActivity act) {
        //unlock data only if frozen
        isLocked = act.isLocked();
    }

    public abstract void deleteTraitListener();

    public abstract void setNaTraitsText();

    public void refreshLock() {
        //((CollectActivity) getContext()).traitLockData();
    }

    public Map<String, String> getNewTraits() {
        return ((CollectActivity) getContext()).getNewTraits();
    }

    public TraitObject getCurrentTrait() {
        return ((CollectActivity) getContext()).getCurrentTrait();
    }

    public SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(getContext());
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
        return String.format("#%06X", (0xFFFFFF & getContext().getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
                .getInt(GeneralKeys.SAVED_DATA_COLOR, resolveThemeColor(R.attr.fb_value_saved_color))));
    }

    public int getButtonTextColor() {
        return resolveThemeColor(R.attr.fb_button_text_color);
    }

    public int getButtonBackgroundColor() {
        return resolveThemeColor(R.attr.fb_button_color_normal);
    }

    public int getButtonPressedColor() {
        return resolveThemeColor(R.attr.fb_trait_categorical_button_press_color);
    }

    public int getTextColor() {
        return resolveThemeColor(R.attr.fb_color_text_dark);
    }

    public int getValueAlteredColor() {
        return resolveThemeColor(R.attr.fb_value_altered_color);
    }

    private int resolveThemeColor(int resid) {
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(resid, value, true);
        return value.data;
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

    public void triggerTts(String text) {
        ((CollectActivity) getContext()).triggerTts(text);
    }
}