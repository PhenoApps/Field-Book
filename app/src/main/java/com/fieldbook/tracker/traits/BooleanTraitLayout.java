package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.enums.ThreeState;
import com.fieldbook.tracker.objects.TraitObject;

public class BooleanTraitLayout extends BaseTraitLayout implements SeekBar.OnSeekBarChangeListener {

    private SeekBar threeStateSeekBar;

    public BooleanTraitLayout(Context context) {
        super(context);
    }

    public BooleanTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BooleanTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    @Override
    public void setNaTraitsText() { }

    @Override
    public String type() {
        return "boolean";
    }

    @Override
    public int layoutId() {
        return R.layout.trait_boolean;
    }

    @Override
    public void init(Activity act) {

        String on = getContext().getString(R.string.trait_boolean_on);
        String off = getContext().getString(R.string.trait_boolean_off);
        String unset = getContext().getString(R.string.trait_boolean_unset);

        threeStateSeekBar = act.findViewById(R.id.traitBooleanSeekBar);
        threeStateSeekBar.setOnSeekBarChangeListener(this);

        ImageView onImageView = act.findViewById(R.id.onImage);
        ImageView offImageView = act.findViewById(R.id.offImage);
        ImageView unsetImageView = act.findViewById(R.id.unsetImage);

        onImageView.setOnClickListener((View v) -> {
            triggerTts(on);
            threeStateSeekBar.setProgress(ThreeState.ON.getValue());
        });

        offImageView.setOnClickListener((View v) -> {
            triggerTts(off);
            threeStateSeekBar.setProgress(ThreeState.OFF.getValue());
        });

        unsetImageView.setOnClickListener((View v) -> {
            triggerTts(unset);
            threeStateSeekBar.setProgress(ThreeState.NEUTRAL.getValue());
        });

        threeStateSeekBar.requestFocus();
    }

    @Override
    public void afterLoadExists(CollectActivity act, @Nullable String value) {
        super.afterLoadExists(act, value);
        updateSeekBarState(value);
    }

    @Override
    public void afterLoadDefault(CollectActivity act) {
        super.afterLoadDefault(act);
        resetToDefault();
    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
        threeStateSeekBar.setProgress(ThreeState.NEUTRAL.getValue());
        super.deleteTraitListener();

        //resetToDefault();
    }

    @NonNull
    @Override
    public Boolean validate(String data) {
        try {
            return data.equalsIgnoreCase(ThreeState.ON.getState()) ||
                    data.equalsIgnoreCase(ThreeState.OFF.getState());
        } catch (Exception e) {
            return false;
        }
    }

    private void resetToDefault() {

        String value = getCurrentTrait().getDefaultValue().trim();

        if (getCurrentObservation() != null && !getCurrentObservation().getValue().isEmpty()) {
            value = getCurrentObservation().getValue();
        }

        if (!value.equals(ThreeState.NEUTRAL.getState())) {
            checkSet(value);
            updateSeekBarState(value);
        } else {
            deleteTraitListener();
        }
    }

    /**
     * When updating view to a new measurement, check that the default value is not already selected,
     * if not handled here, the onProgressChanged will not catch.
     */
    private void checkSet(String value) {

        ThreeState valueStringState = ThreeState.Companion.fromString(value);

        int seekBarValue = threeStateSeekBar.getProgress();

        if (seekBarValue == valueStringState.getValue()) { // only update if the string and value are consistent
            updateObservation(getCurrentTrait(), value);
            getCollectInputView().setText(value);
        }
    }

    private void updateSeekBarState(String state) {
        ThreeState threeState = ThreeState.Companion.fromString(state);

        threeStateSeekBar.setOnSeekBarChangeListener(null);
        threeStateSeekBar.setProgress(threeState.getValue());
        threeStateSeekBar.setOnSeekBarChangeListener(this);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        ThreeState state = ThreeState.Companion.fromPosition(progress);

        if (getCurrentTrait() != null) {
            if (state == ThreeState.NEUTRAL) {
                deleteTraitListener();
            } else {
                String newVal = state.getState();
                updateObservation(getCurrentTrait(), newVal);
                getCollectInputView().setText(newVal);
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        //not implemented
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        //not implemented
    }

    /**
     * Overriding the BaseTraitLayout's method to avoid storing "UNSET"
     */
    @Override
    public void updateObservation(TraitObject trait, String value) {
        if (value.equals(ThreeState.NEUTRAL.getState()))  return;
        Log.d("TAG", "updateObservation: " + value);

        super.updateObservation(trait, value);
    }
}