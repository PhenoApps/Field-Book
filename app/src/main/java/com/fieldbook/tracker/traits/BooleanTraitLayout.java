package com.fieldbook.tracker.traits;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;

import com.fieldbook.tracker.R;

public class BooleanTraitLayout extends BaseTraitLayout implements SeekBar.OnSeekBarChangeListener {

    private SeekBar threeStateSeekBar;

    private static class ThreeState {
        static int OFF = 0;
        //static int NEUTRAL = 1;
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
    public void init() {

        threeStateSeekBar = findViewById(R.id.traitBooleanSeekBar);

        threeStateSeekBar.setOnSeekBarChangeListener(this);

        ImageView onImageView = findViewById(R.id.onImage);

        ImageView offImageView = findViewById(R.id.offImage);

        onImageView.setOnClickListener((View v) -> {

            threeStateSeekBar.setProgress(ThreeState.ON);

        });

        offImageView.setOnClickListener((View v) -> {

            threeStateSeekBar.setProgress(ThreeState.OFF);

        });

    }

    @Override
    public void loadLayout() {

        getEtCurVal().setVisibility(EditText.GONE);
        getEtCurVal().setEnabled(false);

        //if the trait has a default value and this unit has not been observed,
        // set the seek bar to the default value's state
        if (!getNewTraits().containsKey(getCurrentTrait().getTrait())) {

            String defaultValue = getCurrentTrait().getDefaultValue().trim();

            updateSeekBarState(defaultValue);

        } else { //otherwise update the seekbar to the database's current value

            String bval = getNewTraits().get(getCurrentTrait().getTrait()).toString();

            updateSeekBarState(bval);

        }
    }

    @Override
    public void deleteTraitListener() {

        String defaultValue = getCurrentTrait().getDefaultValue().trim();

        updateSeekBarState(defaultValue);

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

        String newVal = "true";

        if (state == ThreeState.OFF) newVal = "false";
        //else if (state == ThreeState.NEUTRAL) newVal = "unset";

        updateTrait(getCurrentTrait().getTrait(), "boolean", newVal);
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