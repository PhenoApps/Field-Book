package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.utilities.Utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class NumericTraitLayout extends BaseTraitLayout {

    private Map<Integer, Button> numberButtons;

    public NumericTraitLayout(Context context) {
        super(context);
    }

    public NumericTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NumericTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public String type() {
        return "numeric";
    }

    @Override
    public int layoutId() {
        return R.layout.trait_numeric;
    }

    @Override
    public void init(Activity act) {
        //void v = inflate(getContext(), layoutId(), null);
        numberButtons = new LinkedHashMap<>();
        numberButtons.put(R.id.k1, act.findViewById(R.id.k1));
        numberButtons.put(R.id.k2, act.findViewById(R.id.k2));
        numberButtons.put(R.id.k3, act.findViewById(R.id.k3));
        numberButtons.put(R.id.k4, act.findViewById(R.id.k4));
        numberButtons.put(R.id.k5, act.findViewById(R.id.k5));
        numberButtons.put(R.id.k6, act.findViewById(R.id.k6));
        numberButtons.put(R.id.k7, act.findViewById(R.id.k7));
        numberButtons.put(R.id.k8, act.findViewById(R.id.k8));
        numberButtons.put(R.id.k9, act.findViewById(R.id.k9));
        numberButtons.put(R.id.k10, act.findViewById(R.id.k10));
        numberButtons.put(R.id.k11, act.findViewById(R.id.k11));
        numberButtons.put(R.id.k12, act.findViewById(R.id.k12));
        numberButtons.put(R.id.k13, act.findViewById(R.id.k13));
        numberButtons.put(R.id.k14, act.findViewById(R.id.k14));
        numberButtons.put(R.id.k15, act.findViewById(R.id.k15));
        numberButtons.put(R.id.k16, act.findViewById(R.id.k16));

        for (Button numButton : numberButtons.values()) {
            numButton.setOnClickListener(new NumberButtonOnClickListener());
        }

        numberButtons.get(R.id.k16).setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                getCollectInputView().setText("");
                removeTrait(getCurrentTrait());
                return false;
            }
        });

        numberButtons.get(R.id.k1).requestFocus();
    }

    private void updateButtonVisibility(TraitObject traitObject) {
        boolean mathSymbolsEnabled = traitObject.getMathSymbolsEnabled();

        Button[] mathButtons = {
                numberButtons.get(R.id.k1),
                numberButtons.get(R.id.k5),
                numberButtons.get(R.id.k9),
                numberButtons.get(R.id.k13)
        };

        int visibility = mathSymbolsEnabled ? View.VISIBLE : View.GONE;
        for (Button button : mathButtons) {
            if (button != null) {
                button.setVisibility(visibility);
            }
        }
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

    @Override
    public void loadLayout() {
        super.loadLayout();

        updateButtonVisibility(getCurrentTrait());
    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
        super.deleteTraitListener();
    }

    @NonNull
    @Override
    public Boolean validate(String data) {

        TraitObject trait = getCurrentTrait();

        if (isUnder(trait, data) || isOver(trait, data)) {

            String message;
            if (isOver(trait, data)) {
                message = controller.getContext().getString(R.string.trait_error_maximum_value)
                        + ": " + getCurrentTrait().getMaximum();
            } else {
                message = controller.getContext().getString(R.string.trait_error_minimum_value)
                        + ": " + getCurrentTrait().getMinimum();
            }

            getCollectActivity().runOnUiThread(() -> Utils.makeToast(controller.getContext(), message));
            return false;

        }

        int maxDecimalPlaces = Integer.parseInt(trait.getMaxDecimalPlaces());

        if (!trait.getMathSymbolsEnabled() && containsMathematicalSymbols(data)) {

            String message = controller.getContext().getString(R.string.trait_error_mathematical_symbols_disabled);

            getCollectActivity().runOnUiThread(() -> Utils.makeToast(controller.getContext(), message));
            return false;

        } else if (maxDecimalPlaces >= 0) { // validate decimal places
            if (!isValidDecimalPlaces(data, maxDecimalPlaces)) {

                String message;
                if (maxDecimalPlaces == 0) {
                    message = controller.getContext().getString(R.string.trait_error_integers_only);
                } else {
                    message = controller.getContext().getString(R.string.trait_error_decimal_places, maxDecimalPlaces);
                }

                getCollectActivity().runOnUiThread(() -> Utils.makeToast(controller.getContext(), message));
                return false;

            }
        }

        return true;
    }

    public static boolean isUnder(TraitObject trait, final String s) {

        String minimum = trait.getMinimum();

        if (!minimum.isEmpty()) {
            try {
                final double v = Double.parseDouble(s);
                final double lowerValue = Double.parseDouble(minimum);
                return v < lowerValue;
            } catch (NumberFormatException e) {
                return true;
            }
        } else {
            return false;
        }
    }

    public static boolean isOver(TraitObject trait, final String s) {

        String maximum = trait.getMaximum();

        if (!maximum.isEmpty()) {
            try {
                final double v = Double.parseDouble(s);
                final double upperValue = Double.parseDouble(maximum);
                return v > upperValue;
            } catch (NumberFormatException e) {
                return true;
            }
        } else {
            return false;
        }
    }

    private boolean isValidDecimalPlaces(String value, int maxDecimalPlaces) {
        if (value.isEmpty()) return true;

        try {
            double doubleValue = Double.parseDouble(value);
            if (maxDecimalPlaces == 0) { // must be an integer
                return doubleValue == (double) ((int) doubleValue);
            } else {
                int decimalIndex = value.indexOf('.');
                if (decimalIndex == -1) return true; // no decimal point present

                String decimalPart = value.substring(decimalIndex + 1);
                return decimalPart.length() <= maxDecimalPlaces;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean containsMathematicalSymbols(String data) {
        if (data == null || data.isEmpty()) return false;

        return data.contains("+") || data.contains("-") || data.contains("*") || data.contains(";");
    }

    private class NumberButtonOnClickListener implements OnClickListener {

        @Override
        public void onClick(View view) {
            if (!isLocked) {
                final String backspaceTts = getContext().getString(R.string.trait_numeric_backspace_tts);
                final String curText = getCollectInputView().getText();
                Button button = (Button) view;
                triggerTts(button.getText().toString());
                String value;
                if (view.getId() == R.id.k16) {        // Backspace Key Pressed
                    triggerTts(backspaceTts);
                    final int length = curText.length();
                    if (length > 0) {
                        value = curText.substring(0, length - 1);
                        getCollectInputView().setText(value);
                        updateObservation(getCurrentTrait(), value);
                    }
                } else if (numberButtons.containsKey(view.getId())) {
                    value = curText + numberButtons.get(view.getId()).getText().toString();
                    getCollectInputView().setText(value);
                    updateObservation(getCurrentTrait(), value);
                }
            }
        }
    }
}