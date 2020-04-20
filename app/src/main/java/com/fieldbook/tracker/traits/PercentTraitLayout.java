package com.fieldbook.tracker.traits;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.SeekBar;

import com.fieldbook.tracker.R;

public class PercentTraitLayout extends BaseTraitLayout {
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

    public SeekBar getSeekBar() {
        return seekBar;
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public String type() {
        return "percent";
    }

    @Override
    public void init() {
        // Progress bar
        seekBar = findViewById(R.id.seekbar);
        seekBar.setMax(100);

        seekListener = new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar sb, int progress, boolean arg2) {
                if (sb.getProgress() < Integer.parseInt(getCurrentTrait().getMinimum()))
                    sb.setProgress(Integer.parseInt(getCurrentTrait().getMinimum()));

                setCurrentValueText(sb.getProgress(), Color.parseColor(getDisplayColor()));
            }

            public void onStartTrackingTouch(SeekBar arg0) {
            }

            public void onStopTrackingTouch(SeekBar arg0) {
                updateTrait(getCurrentTrait().getTrait(), "percent", String.valueOf(seekBar.getProgress()));
            }
        };

        seekBar.setOnSeekBarChangeListener(seekListener);
    }

    public void loadLayout() {

        getEtCurVal().setHint("");
        getEtCurVal().setVisibility(EditText.VISIBLE);
        getEtCurVal().removeTextChangedListener(getCvText());

        if (getNewTraits().containsKey(getCurrentTrait().getTrait())
                && !getNewTraits().get(getCurrentTrait().getTrait()).toString().equals("NA")) {
            String currentValue = getNewTraits().get(getCurrentTrait().getTrait()).toString();
            seekBar.setMax(Integer.parseInt(getCurrentTrait().getMaximum()));

            int textColor = currentValue.equals(getDefaultValue()) ? Color.BLACK : Color.parseColor(getDisplayColor());
            setCurrentValueText(currentValue, textColor);

            seekBar.setOnSeekBarChangeListener(null);
            seekBar.setProgress(Integer.parseInt(currentValue));
            seekBar.setOnSeekBarChangeListener(seekListener);

        } else if (getNewTraits().containsKey(getCurrentTrait().getTrait())
                && getNewTraits().get(getCurrentTrait().getTrait()).toString().equals("NA")) {
            getEtCurVal().setText("NA");
            getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
            seekBar.setProgress(0);
        } else {
            String loadValue = "";
            if (getCurrentTrait().getDefaultValue() != null
                    && !getCurrentTrait().getDefaultValue().isEmpty()) {
                loadValue = getDefaultValue();
            }

            setCurrentValueText(loadValue, Color.BLACK);
            seekBar.setMax(Integer.parseInt(getCurrentTrait().getMaximum()));
            seekBar.setOnSeekBarChangeListener(null);
            seekBar.setProgress(Integer.parseInt(getDefaultValue()));
            seekBar.setOnSeekBarChangeListener(seekListener);
        }
    }

    private String getDefaultValue() {
        String defaultValue = "0";
        if (getCurrentTrait().getDefaultValue() != null
                && !getCurrentTrait().getDefaultValue().isEmpty()) {
            defaultValue = getCurrentTrait().getDefaultValue();
        }
        return defaultValue;
    }

    private void setCurrentValueText(int value, int color) {
        setCurrentValueText(String.valueOf(value), color);
    }

    private void setCurrentValueText(String value, int color) {
        getEtCurVal().setTextColor(color);
        if (value.isEmpty())
            getEtCurVal().setText(value);
        else
            getEtCurVal().setText(value + "%");
    }

    @Override
    public void deleteTraitListener() {
        removeTrait(getCurrentTrait().getTrait());
        setCurrentValueText("", Color.BLACK);
        seekBar.setOnSeekBarChangeListener(null);
        seekBar.setProgress(Integer.parseInt(getDefaultValue()));
        seekBar.setOnSeekBarChangeListener(seekListener);
    }
}