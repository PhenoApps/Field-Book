package com.fieldbook.tracker.traits;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.annotation.Nullable;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;

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

        String on = getContext().getString(R.string.trait_boolean_on);
        String off = getContext().getString(R.string.trait_boolean_off);

        threeStateSeekBar = findViewById(R.id.traitBooleanSeekBar);
        threeStateSeekBar.setOnSeekBarChangeListener(this);

        ImageView onImageView = findViewById(R.id.onImage);
        ImageView offImageView = findViewById(R.id.offImage);

        onImageView.setOnClickListener((View v) -> {
            triggerTts(on);
            threeStateSeekBar.setProgress(ThreeState.ON);
        });

        offImageView.setOnClickListener((View v) -> {
            triggerTts(off);
            threeStateSeekBar.setProgress(ThreeState.OFF);
        });

    }

    @Override
    public void loadLayout() {
        super.loadLayout();

        getEtCurVal().setHint("");
        getEtCurVal().setVisibility(EditText.VISIBLE);
    }

    @Override
    public void afterLoadExists(CollectActivity act, @Nullable String value) {
        super.afterLoadExists(act, value);
        updateSeekBarState(value);
    }

    @Override
    public void afterLoadDefault(CollectActivity act) {
        super.afterLoadDefault(act);
        String defaultValue = getCurrentTrait().getDefaultValue().trim();
        updateSeekBarState(defaultValue);
    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
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

        String newVal = "TRUE";

        if (state == ThreeState.OFF) newVal = "FALSE";
        //else if (state == ThreeState.NEUTRAL) newVal = "unset";

        updateTrait(getCurrentTrait().getTrait(), "boolean", newVal);
        getEtCurVal().setText(newVal);
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