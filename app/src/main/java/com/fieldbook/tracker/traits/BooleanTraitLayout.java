package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.annotation.Nullable;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;

public class BooleanTraitLayout extends BaseTraitLayout implements SeekBar.OnSeekBarChangeListener {

    private SeekBar threeStateSeekBar;

    private static class ThreeState {
        static int OFF = 0;
        static int NEUTRAL = -1;
        static int ON = 1;
    }

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

        threeStateSeekBar = act.findViewById(R.id.traitBooleanSeekBar);
        threeStateSeekBar.setOnSeekBarChangeListener(this);

        ImageView onImageView = act.findViewById(R.id.onImage);
        ImageView offImageView = act.findViewById(R.id.offImage);

        onImageView.setOnClickListener((View v) -> {
            triggerTts(on);
            threeStateSeekBar.setProgress(ThreeState.ON);
        });

        offImageView.setOnClickListener((View v) -> {
            triggerTts(off);
            threeStateSeekBar.setProgress(ThreeState.OFF);
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
        super.deleteTraitListener();

        //resetToDefault();
    }

    private void resetToDefault() {

        String value = getCurrentTrait().getDefaultValue().trim();

        if (getCurrentObservation() != null && !getCurrentObservation().getValue().isEmpty()) {
            value = getCurrentObservation().getValue();
        }

        checkSet(value);
        updateSeekBarState(value);
    }

    /**
     * When updating view to a new measurement, check that the default value is not already selected,
     * if not handled here, the onProgressChanged will not catch.
     */
    private void checkSet(String value) {

        int state = threeStateSeekBar.getProgress();

        boolean flag = (value.equalsIgnoreCase("true") && state == ThreeState.ON)
                || (value.equalsIgnoreCase("false") && state == ThreeState.OFF);

        if (flag) {
            updateObservation(getCurrentTrait().getTrait(), type(), value);
            getCollectInputView().setText(value);
        }
    }

    private void updateSeekBarState(String state) {

        if (state.equalsIgnoreCase("true")) {

            threeStateSeekBar.setProgress(ThreeState.ON);

        } else if (state.equalsIgnoreCase("false")) {

            threeStateSeekBar.setProgress(ThreeState.OFF);

        } //else threeStateSeekBar.setProgress(ThreeState.NEUTRAL);

    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        //every time the progress changes, update the database
        int state = threeStateSeekBar.getProgress();

        String newVal = "TRUE";

        if (state == ThreeState.OFF) newVal = "FALSE";
        //else if (state == ThreeState.NEUTRAL) newVal = "unset";

        if (getCurrentTrait() != null) {
            updateObservation(getCurrentTrait().getTrait(), type(), newVal);
            getCollectInputView().setText(newVal);
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
}