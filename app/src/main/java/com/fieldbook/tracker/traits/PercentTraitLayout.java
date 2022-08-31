package com.fieldbook.tracker.traits;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.SeekBar;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;

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

            public void onStartTrackingTouch(SeekBar sb) {
            }

            public void onStopTrackingTouch(SeekBar sb) {
                updateTrait(getCurrentTrait().getTrait(), "percent", String.valueOf(seekBar.getProgress()));
                triggerTts(String.valueOf(sb.getProgress()));
            }
        };

        seekBar.setOnSeekBarChangeListener(seekListener);
    }

    @Override
    public void loadLayout() {

        getEtCurVal().setHint("");
        getEtCurVal().setVisibility(EditText.VISIBLE);
        getEtCurVal().removeTextChangedListener(getCvText());

        super.loadLayout();

        seekBar.setEnabled(!isLocked);
    }

    @Override
    public void afterLoadExists(CollectActivity act, String value) {
        super.afterLoadExists(act, value);

        if (value != null && !value.equals("NA")) {

            seekBar.setMax(Integer.parseInt(getCurrentTrait().getMaximum()));

            int textColor = value.equals(getDefaultValue()) ? Color.BLACK : Color.parseColor(getDisplayColor());
            setCurrentValueText(value, textColor);

            seekBar.setOnSeekBarChangeListener(null);
            seekBar.setProgress(Integer.parseInt(value));
            seekBar.setOnSeekBarChangeListener(seekListener);

        } else if (value != null) {
            getEtCurVal().setText("NA");
            getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
            seekBar.setProgress(0);
        }
    }

    @Override
    public void afterLoadNotExists(CollectActivity act) {
        super.afterLoadNotExists(act);
        updateLoadBarValue("");
    }

    @Override
    public void afterLoadDefault(CollectActivity act) {
        super.afterLoadDefault(act);
        updateLoadBarValue(getDefaultValue());
    }

    @Override
    public void refreshLock() {
        super.refreshLock();
        ((CollectActivity) getContext()).traitLockData();
        try {
            loadLayout();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateLoadBarValue(String value) {
        setCurrentValueText(value, Color.BLACK);
        String max = getCurrentTrait().getMaximum();
        //TODO: had to add this check, system was parsing empty string as max value which caused an error
        if (!max.isEmpty()) {
            seekBar.setMax(Integer.parseInt(max));
        }
        seekBar.setOnSeekBarChangeListener(null);
        seekBar.setProgress(Integer.parseInt(getDefaultValue()));
        seekBar.setOnSeekBarChangeListener(seekListener);
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