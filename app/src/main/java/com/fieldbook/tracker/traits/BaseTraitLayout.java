package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.database.dao.ObservationDao;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.views.CollectInputView;
import com.fieldbook.tracker.views.RepeatedValuesView;

import java.util.Arrays;
import java.util.List;
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

    public String decodeValue(String value) { return value; }

    public boolean isTraitType(String trait) {
        return trait.equals(type());
    }

    public abstract void init();

    public void init(Activity act) { /* not implemented */ }

    /**
     * Override to block multi-measure navigation with specific condition
     */
    public boolean block() { return false; }

    /**
     * Useful function when traits have listeners.
     * @param onNew
     */
    public void refreshLayout(Boolean onNew) {

        getCollectInputView().getRepeatView().refresh(onNew);

    }

    public void loadLayout() {

        ((CollectActivity) getContext()).refreshRepeatedValuesToolbarIndicator();

        //right now text entry is disabled in the camera and photo traits
        //uris are too long to be nicely displayed in the current editTexts
        if (isTraitType(PhotoTraitLayout.type)
            || isTraitType(UsbCameraTraitLayout.type)
            || isTraitType(AudioTraitLayout.type)
            || isTraitType(TextTraitLayout.type)
            || isTraitType(LabelPrintTraitLayout.type)) {
            toggleVisibility(View.GONE);
        } else toggleVisibility(View.VISIBLE);

        CollectActivity act = (CollectActivity) getContext();
        isLocked = act.isFrozen() || act.isLocked();

        Map<String, String> observations = getNewTraits();
        TraitObject trait = getCurrentTrait();
        String traitName = trait.getTrait();
        //String traitFormat = trait.getFormat();

        //clear old list of repeated values each time a new trait is loaded
        act.getInputView().getRepeatView().clear();

        act.getInputView().getRepeatView().setDisplayColor(Color.parseColor(getDisplayColor()));

        if (observations.containsKey(traitName)) {

            String value = null;
            if (traitName != null) {
                value = observations.get(traitName);
            }

            if (value != null) {

                ObservationModel[] models = ObservationDao.Companion.getAllRepeatedValues(
                        act.getStudyId(),
                        act.getObservationUnit(),
                        act.getTraitName()
                );

                for (ObservationModel m : models) {
                    if (!m.getValue().isEmpty()) {
                        m.setValue(decodeValue(m.getValue()));
                    }
                }

                act.getInputView().setTextColor(Color.parseColor(getDisplayColor()));

                act.getInputView().prepareObservationsExistMode(Arrays.asList(models));

            }

            afterLoadExists(act, value);

        } else {

            act.getInputView().prepareEmptyObservationsMode();

            getCollectInputView().setTextColor(Color.BLACK);

            checkDefaultValue();
        }
    }

    private void checkDefaultValue() {

        TraitObject trait = getCurrentTrait();
        CollectActivity act = (CollectActivity) getContext();

        if (trait.getDefaultValue() != null && !trait.getDefaultValue().isEmpty()) {

            String defaultValue = trait.getDefaultValue();
            getCollectInputView().setText(defaultValue);
            updateObservation(trait.getTrait(), trait.getFormat(), defaultValue);
            afterLoadDefault(act);

        } else {
            act.getInputView().setHasData(false);
            afterLoadNotExists(act);
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

    /**
     * Handles the repeated value view list state.
     * If this feature is enabled, the list will be modified and updated.
     */
    public void deleteTraitListener() {
        CollectInputView inputView = getCollectInputView();
        if (inputView.isRepeatEnabled()) {
            inputView.getRepeatView().userDeleteCurrentRep();
        }
    }

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
        return getContext().getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0);
    }

    public CollectActivity getCollectActivity() {
        return (CollectActivity) getContext();
    }

    public RangeObject getCurrentRange() {
        return ((CollectActivity) getContext()).getCRange();
    }

    public CollectInputView getCollectInputView() {
        return ((CollectActivity) getContext()).getCollectInputView();
    }

    public String getDisplayColor() {

        return String.format("#%06X", (0xFFFFFF & getPrefs().getInt(GeneralKeys.SAVED_DATA_COLOR, Color.parseColor("#d50000"))));
    }

    /**
     * Function that toggles visibility of the edit text or repeated values view based on preferences
     */
    protected void toggleVisibility(int visibility) {

        CollectInputView inputView = getCollectInputView();

        inputView.setVisibility(visibility);

        RepeatedValuesView repeatView = inputView.getRepeatView();
        EditText editText = inputView.getEditText();

        // Clear hint for NA since a focus change doesn't happen for the numeric trait layout
        if (inputView.isRepeatEnabled()) {
            repeatView.setVisibility(visibility);
        } else {
            editText.setVisibility(visibility);
            editText.setHint("");
        }
    }

    /**
     * Calls the collect activities db function to insert an observation row.
     * @param traitName the name of the trait s.a "My Height Trait", "Height" (defined by user / FB)
     * @param traitType the type of trait s.a "Numeric", "Categorical" (defined by FB)
     * @param value the Text value to be saved in the row
     */
    public void updateObservation(String traitName, String traitType, String value) {
        ((CollectActivity) getContext()).updateObservation(traitName, traitType, value, null);
    }

    public void removeTrait(String parent) {
        ((CollectActivity) getContext()).removeTrait(parent);
    }

    public void triggerTts(String text) {
        ((CollectActivity) getContext()).triggerTts(text);
    }

    protected List<ObservationModel> getObservations() {
        CollectActivity act = getCollectActivity();
        return Arrays.asList(ObservationDao.Companion
                .getAllRepeatedValues(act.getStudyId(), act.getObservationUnit(), act.getTraitName()));
    }

    protected ObservationModel getCurrentObservation() {
        String rep = getCollectInputView().getRep();
        List<ObservationModel> models = getObservations();
        for (ObservationModel m : models) {
            if (rep.equals(m.getRep())) {
                return m;
            }
        }
        return null;
    }
}