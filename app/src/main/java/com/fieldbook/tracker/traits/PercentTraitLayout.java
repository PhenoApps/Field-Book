package com.fieldbook.tracker.traits;

import static com.fieldbook.tracker.traits.NumericTraitLayout.isOver;
import static com.fieldbook.tracker.traits.NumericTraitLayout.isUnder;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.traits.formats.Formats;
import com.fieldbook.tracker.traits.formats.Scannable;
import com.fieldbook.tracker.traits.formats.TraitFormat;

public class PercentTraitLayout extends BaseTraitLayout {
    private SeekBar seekBar;
    private TextView mininmumTv;
    private TextView maximumTv;
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
    public int layoutId() {
        return R.layout.trait_percent;
    }

    @Override
    public void init(Activity act) {
        // Progress bar
        seekBar = act.findViewById(R.id.seekbar);
        seekBar.setMax(100);

        mininmumTv = act.findViewById(R.id.trait_percent_minimum_tv);
        maximumTv = act.findViewById(R.id.trait_percent_maximum_tv);

        seekListener = new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar sb, int progress, boolean arg2) {
                int minimum = 0;
                if (getCurrentTrait() != null) {
                    try {
                        minimum = Integer.parseInt(getCurrentTrait().getMinimum());
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
                if (sb.getProgress() < minimum)
                    sb.setProgress(minimum);

                setCurrentValueText(sb.getProgress(), Color.parseColor(getDisplayColor()));
            }

            public void onStartTrackingTouch(SeekBar sb) {
            }

            public void onStopTrackingTouch(SeekBar sb) {
                updateObservation(getCurrentTrait(), String.valueOf(seekBar.getProgress()));
                triggerTts(String.valueOf(sb.getProgress()));
            }
        };

        seekBar.setOnSeekBarChangeListener(seekListener);

        seekBar.requestFocus();
    }

    @Override
    public void loadLayout() {

        //getCollectInputView().removeTextChangedListener();

        super.loadLayout();

        seekBar.setEnabled(!isLocked);

        setupMinMaxUi();
    }

    private void setupMinMaxUi() {

        try {

            TraitObject percentTrait = getCurrentTrait();
            mininmumTv.setText(percentTrait.getMinimum());
            maximumTv.setText(percentTrait.getMaximum());

        } catch (Exception e) {

            e.printStackTrace();

            Toast.makeText(getContext(), R.string.trait_percent_ui_min_max_error, Toast.LENGTH_LONG).show();

        }
    }

    @Override
    public void refreshLayout(Boolean onNew) {

        seekBar.setOnSeekBarChangeListener(null);
        seekBar.setProgress(0);
        seekBar.setOnSeekBarChangeListener(seekListener);

        ObservationModel model = getCurrentObservation();
        if (model != null) {
            if (model.getValue().equals("NA")) {
                getCollectInputView().setText("NA");
                getSeekBar().setProgress(0);
            } else if (!model.getValue().isEmpty()) {
                setSeekBarProgress(model.getValue());
            } else {
                super.refreshLayout(onNew);
            }
        }
    }

    @Override
    public void afterLoadExists(CollectActivity act, String value) {
        super.afterLoadExists(act, value);

        if (value != null && !value.equals("NA") && !value.isEmpty()) {

            // Default to max 100 if maximum is not set
            String maxString = getCurrentTrait().getMaximum();
            setSeekBarMax(maxString);

            int textColor = value.equals(getDefaultValue()) ? Color.BLACK : Color.parseColor(getDisplayColor());
            setCurrentValueText(value, textColor);

            seekBar.setOnSeekBarChangeListener(null);
            setSeekBarProgress(value);
            seekBar.setOnSeekBarChangeListener(seekListener);

        } else if (value != null && value.equals("NA")) {
            getCollectInputView().setText("NA");
            getCollectInputView().setTextColor(Color.parseColor(getDisplayColor()));
            seekBar.setProgress(0);
        }
    }

    @Override
    public void afterLoadNotExists(CollectActivity act) {
        super.afterLoadNotExists(act);
//        updateLoadBarValue("");
        updateLoadBar();
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

    @NonNull
    @Override
    public Boolean validate(String data) {
        try {
            TraitObject trait = getCurrentTrait();
            return !(isUnder(trait, data) || isOver(trait, data));
        } catch (Exception e) {
            return false;
        }
    }

    private void updateLoadBar() {
        String max = getCurrentTrait().getMaximum();
        if (!max.isEmpty()) {
            seekBar.setMax(Integer.parseInt(max));
        }
        seekBar.setOnSeekBarChangeListener(null);
        setSeekBarProgress(getDefaultValue());
        seekBar.setOnSeekBarChangeListener(seekListener);
    }

    private void updateLoadBarValue(String value) {
        setCurrentValueText(value, Color.BLACK);
        updateLoadBar();
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
        getCollectInputView().setTextColor(color);
        if (value.isEmpty())
            getCollectInputView().setText(value);
        else
            getCollectInputView().setText(value + "%");
    }

    @Override
    public void deleteTraitListener() {
        removeTrait(getCurrentTrait());
        super.deleteTraitListener();
        ObservationModel model = getCurrentObservation();
        seekBar.setOnSeekBarChangeListener(null);
        if (model != null) {
            setCurrentValueText(model.getValue(), Color.BLACK);
            setSeekBarProgress(model.getValue());
        } else {
            String defaultValue = getDefaultValue();
            setCurrentValueText(defaultValue, Color.BLACK);
            setSeekBarProgress(defaultValue);
        }

        seekBar.setOnSeekBarChangeListener(seekListener);
    }

    private void setSeekBarProgress(String value) {

        ((CollectActivity) controller.getContext()).runOnUiThread(() -> {
            try {
                seekBar.setProgress(Integer.parseInt(value));
                return;
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
            seekBar.setProgress(0);
        });
    }

    private void setSeekBarMax(String max) {
        ((CollectActivity) controller.getContext()).runOnUiThread(() -> {
            try {
                if (max != null && !max.isEmpty()) {
                    seekBar.setMax(Integer.parseInt(max));
                }
                return;
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
            seekBar.setMax(100);
        });
    }
}