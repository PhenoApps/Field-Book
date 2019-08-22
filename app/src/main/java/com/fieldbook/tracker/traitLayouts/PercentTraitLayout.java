package com.fieldbook.tracker.traitLayouts;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.SeekBar;

import com.fieldbook.tracker.traits.TraitObject;

import java.util.HashMap;

public class PercentTraitLayout extends TraitLayout {

    public PercentTraitLayout(Context context) {
        super(context);
        throw new RuntimeException("Stub!");
    }


    public void loadLayout(EditText etCurVal, DataWrapper dataWrapper, HashMap newTraits,
                           TraitObject currentTrait, String displayColor, TextWatcher cvNum,
                           TextWatcher cvText, SeekBar seekBar, SeekBar.OnSeekBarChangeListener seekListener, Handler mHandler){

        etCurVal.setVisibility(EditText.VISIBLE);
        etCurVal.removeTextChangedListener(cvNum);
        etCurVal.removeTextChangedListener(cvText);

        if (newTraits.containsKey(currentTrait.getTrait()) && !newTraits.get(currentTrait.getTrait()).toString().equals("NA")) {

            etCurVal.setTextColor(Color.BLACK);
            seekBar.setMax(Integer.parseInt(currentTrait.getMaximum()));
            seekBar.setOnSeekBarChangeListener(null);

            if (currentTrait.getDefaultValue() != null) {

                if (currentTrait.getDefaultValue().length() > 0) {
                    if (newTraits.get(currentTrait.getTrait()).toString()
                            .equals(currentTrait.getDefaultValue()))
                        etCurVal.setTextColor(Color.BLACK);
                    else
                        etCurVal.setTextColor(Color.parseColor(displayColor));
                } else {
                    if (newTraits.get(currentTrait.getTrait()).toString().equals("0"))
                        etCurVal.setTextColor(Color.BLACK);
                    else
                        etCurVal.setTextColor(Color.parseColor(displayColor));
                }
            } else {
                if (newTraits.get(currentTrait.getTrait()).toString().equals("0"))
                    etCurVal.setTextColor(Color.BLACK);
                else
                    etCurVal.setTextColor(Color.parseColor(displayColor));
            }

            String curVal = newTraits.get(currentTrait.getTrait()).toString() + "%";
            etCurVal.setText(curVal);
            seekBar.setProgress(Integer.parseInt(newTraits.get(currentTrait.getTrait()).toString()));
            seekBar.setOnSeekBarChangeListener(seekListener);

        } else if (newTraits.containsKey(currentTrait.getTrait()) && newTraits.get(currentTrait.getTrait()).toString().equals("NA")) {
            etCurVal.setText("NA");
            etCurVal.setTextColor(Color.parseColor(displayColor));
            seekBar.setProgress(0);
        } else {
            seekBar.setOnSeekBarChangeListener(null);

            etCurVal.setText("");
            seekBar.setProgress(0);
            etCurVal.setTextColor(Color.BLACK);

            seekBar.setMax(Integer
                    .parseInt(currentTrait.getMaximum()));

            if (currentTrait.getDefaultValue() != null
                    && currentTrait.getDefaultValue().length() > 0) {
                etCurVal.setText(currentTrait.getDefaultValue());
                seekBar.setProgress(Integer
                        .valueOf(currentTrait.getDefaultValue()));
            }

            seekBar.setOnSeekBarChangeListener(seekListener);
        }
    }
}
