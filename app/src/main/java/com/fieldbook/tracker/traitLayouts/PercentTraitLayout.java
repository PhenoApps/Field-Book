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

        if (newTraits.containsKey(currentTrait.trait) && !newTraits.get(currentTrait.trait).toString().equals("NA")) {

            etCurVal.setTextColor(Color.BLACK);
            seekBar.setMax(Integer.parseInt(currentTrait.maximum));
            seekBar.setOnSeekBarChangeListener(null);

            if (currentTrait.defaultValue != null) {

                if (currentTrait.defaultValue.length() > 0) {
                    if (newTraits.get(currentTrait.trait).toString()
                            .equals(currentTrait.defaultValue))
                        etCurVal.setTextColor(Color.BLACK);
                    else
                        etCurVal.setTextColor(Color.parseColor(displayColor));
                } else {
                    if (newTraits.get(currentTrait.trait).toString().equals("0"))
                        etCurVal.setTextColor(Color.BLACK);
                    else
                        etCurVal.setTextColor(Color.parseColor(displayColor));
                }
            } else {
                if (newTraits.get(currentTrait.trait).toString().equals("0"))
                    etCurVal.setTextColor(Color.BLACK);
                else
                    etCurVal.setTextColor(Color.parseColor(displayColor));
            }

            String curVal = newTraits.get(currentTrait.trait).toString() + "%";
            etCurVal.setText(curVal);
            seekBar.setProgress(Integer.parseInt(newTraits.get(currentTrait.trait).toString()));
            seekBar.setOnSeekBarChangeListener(seekListener);

        } else if (newTraits.containsKey(currentTrait.trait) && newTraits.get(currentTrait.trait).toString().equals("NA")) {
            etCurVal.setText("NA");
            etCurVal.setTextColor(Color.parseColor(displayColor));
            seekBar.setProgress(0);
        } else {
            seekBar.setOnSeekBarChangeListener(null);

            etCurVal.setText("");
            seekBar.setProgress(0);
            etCurVal.setTextColor(Color.BLACK);

            seekBar.setMax(Integer
                    .parseInt(currentTrait.maximum));

            if (currentTrait.defaultValue != null
                    && currentTrait.defaultValue.length() > 0) {
                etCurVal.setText(currentTrait.defaultValue);
                seekBar.setProgress(Integer
                        .valueOf(currentTrait.defaultValue));
            }

            seekBar.setOnSeekBarChangeListener(seekListener);
        }
    }
}
