package com.fieldbook.tracker.traitLayouts;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.SeekBar;

import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.traits.TraitObject;

import java.util.HashMap;

public class PercentTraitLayout extends TraitLayout {
    private SeekBar seekBar;
    private SeekBar.OnSeekBarChangeListener seekListener;

    public PercentTraitLayout(Context context) {
        super(context);
    }

    public PercentTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PercentTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SeekBar getSeekBar(){
        return seekBar;
    }

    @Override
    public void init(){
        // Progress bar
        seekBar = findViewById(R.id.seekbar);
        seekBar.setMax(100);

        seekListener = new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar sb, int progress, boolean arg2) {
                if (sb.getProgress() < Integer.parseInt(getCurrentTrait().getMinimum()))
                    sb.setProgress(Integer.parseInt(getCurrentTrait().getMinimum()));

                getEtCurVal().setText(String.valueOf(sb.getProgress()));
            }

            public void onStartTrackingTouch(SeekBar arg0) {
            }

            public void onStopTrackingTouch(SeekBar arg0) {
                updateTrait(getCurrentTrait().getTrait(), "percent", String.valueOf(seekBar.getProgress()));
            }
        };

        seekBar.setOnSeekBarChangeListener(seekListener);

    }

    public void loadLayout(){

        getEtCurVal().setVisibility(EditText.VISIBLE);
        getEtCurVal().removeTextChangedListener(getCvNum());
        getEtCurVal().removeTextChangedListener(getCvText());

        if (getNewTraits().containsKey(getCurrentTrait().getTrait()) && !getNewTraits().get(getCurrentTrait().getTrait()).toString().equals("NA")) {

            getEtCurVal().setTextColor(Color.BLACK);
            seekBar.setMax(Integer.parseInt(getCurrentTrait().getMaximum()));
            seekBar.setOnSeekBarChangeListener(null);

            if (getCurrentTrait().getDefaultValue() != null) {

                if (getCurrentTrait().getDefaultValue().length() > 0) {
                    if (getNewTraits().get(getCurrentTrait().getTrait()).toString()
                            .equals(getCurrentTrait().getDefaultValue()))
                        getEtCurVal().setTextColor(Color.BLACK);
                    else
                        getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
                } else {
                    if (getNewTraits().get(getCurrentTrait().getTrait()).toString().equals("0"))
                        getEtCurVal().setTextColor(Color.BLACK);
                    else
                        getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
                }
            } else {
                if (getNewTraits().get(getCurrentTrait().getTrait()).toString().equals("0"))
                    getEtCurVal().setTextColor(Color.BLACK);
                else
                    getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
            }

            String curVal = getNewTraits().get(getCurrentTrait().getTrait()).toString() + "%";
            getEtCurVal().setText(curVal);
            seekBar.setProgress(Integer.parseInt(getNewTraits().get(getCurrentTrait().getTrait()).toString()));
            seekBar.setOnSeekBarChangeListener(seekListener);

        } else if (getNewTraits().containsKey(getCurrentTrait().getTrait()) && getNewTraits().get(getCurrentTrait().getTrait()).toString().equals("NA")) {
            getEtCurVal().setText("NA");
            getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
            seekBar.setProgress(0);
        } else {
            seekBar.setOnSeekBarChangeListener(null);

            getEtCurVal().setText("");
            seekBar.setProgress(0);
            getEtCurVal().setTextColor(Color.BLACK);

            seekBar.setMax(Integer
                    .parseInt(getCurrentTrait().getMaximum()));

            if (getCurrentTrait().getDefaultValue() != null
                    && getCurrentTrait().getDefaultValue().length() > 0) {
                getEtCurVal().setText(getCurrentTrait().getDefaultValue());
                seekBar.setProgress(Integer
                        .valueOf(getCurrentTrait().getDefaultValue()));
            }

            seekBar.setOnSeekBarChangeListener(seekListener);
        }
    }

    @Override
    public void deleteTraitListener() {
        seekBar.setOnSeekBarChangeListener(null);
        getEtCurVal().setText("");
        seekBar.setProgress(0);
        getEtCurVal().setTextColor(Color.BLACK);

        if (getCurrentTrait().getDefaultValue() != null
                && getCurrentTrait().getDefaultValue().length() > 0) {
            getEtCurVal().setText(getCurrentTrait().getDefaultValue());
            seekBar.setProgress(Integer
                    .valueOf(getCurrentTrait().getDefaultValue()));
        }

        updateTrait(getCurrentTrait().getTrait(), "percent", String.valueOf(seekBar.getProgress()));
        seekBar.setOnSeekBarChangeListener(seekListener);
    }
}
