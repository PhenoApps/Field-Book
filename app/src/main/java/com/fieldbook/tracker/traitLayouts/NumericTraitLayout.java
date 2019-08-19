package com.fieldbook.tracker.traitLayouts;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.SeekBar;

import com.fieldbook.tracker.traits.TraitObject;

import java.util.HashMap;

public class NumericTraitLayout extends TraitLayout {

    public NumericTraitLayout(Context context) {
        super(context);
        throw new RuntimeException("Stub!");
    }


    public void loadLayout(EditText etCurVal, DataWrapper dataWrapper, HashMap newTraits,
                           TraitObject currentTrait, String displayColor, TextWatcher cvNum,
                           TextWatcher cvText, SeekBar seekBar, SeekBar.OnSeekBarChangeListener seekListener, Handler mHandler){

        etCurVal.setVisibility(EditText.VISIBLE);

        if (newTraits.containsKey(currentTrait.trait)) {
            etCurVal.removeTextChangedListener(cvNum);
            etCurVal.setText(newTraits.get(currentTrait.trait).toString());
            etCurVal.setTextColor(Color.parseColor(displayColor));
            etCurVal.addTextChangedListener(cvNum);
        } else {
            etCurVal.removeTextChangedListener(cvNum);
            etCurVal.setText("");
            etCurVal.setTextColor(Color.BLACK);

            if (currentTrait.defaultValue != null && currentTrait.defaultValue.length() > 0) {
                etCurVal.setText(currentTrait.defaultValue);
                //updateTrait(currentTrait.trait, currentTrait.format, etCurVal.getText().toString());
            }

            etCurVal.addTextChangedListener(cvNum);
        }
    }
}
