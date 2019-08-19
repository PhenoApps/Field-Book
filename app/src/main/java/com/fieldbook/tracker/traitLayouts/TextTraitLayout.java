package com.fieldbook.tracker.traitLayouts;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.SeekBar;

import com.fieldbook.tracker.traits.TraitObject;

import java.util.HashMap;

public class TextTraitLayout extends TraitLayout {
    public TextTraitLayout(Context context) {
        super(context);
        throw new RuntimeException("Stub!");
    }


    public void loadLayout(final EditText etCurVal, DataWrapper dataWrapper, HashMap newTraits,
                           TraitObject currentTrait, String displayColor, TextWatcher cvNum,
                           TextWatcher cvText, SeekBar seekBar, SeekBar.OnSeekBarChangeListener seekListener, Handler mHandler){

        etCurVal.setVisibility(EditText.VISIBLE);
        etCurVal.setSelection(etCurVal.getText().length());
        etCurVal.setEnabled(true);

        if (newTraits.containsKey(currentTrait.trait)) {
            etCurVal.removeTextChangedListener(cvText);
            etCurVal.setText(newTraits.get(currentTrait.trait).toString());
            etCurVal.setTextColor(Color.parseColor(displayColor));
            etCurVal.addTextChangedListener(cvText);
            etCurVal.setSelection(etCurVal.getText().length());
        } else {
            etCurVal.removeTextChangedListener(cvText);
            etCurVal.setText("");
            etCurVal.setTextColor(Color.BLACK);

            if (currentTrait.defaultValue != null && currentTrait.defaultValue.length() > 0) {
                etCurVal.setText(currentTrait.defaultValue);
                //updateTrait(currentTrait.trait, currentTrait.format, etCurVal.getText().toString());
            }

            etCurVal.addTextChangedListener(cvText);
            etCurVal.setSelection(etCurVal.getText().length());
        }

        // This is needed to fix a keyboard bug
        mHandler.postDelayed(new Runnable() {
            public void run() {
                etCurVal.dispatchTouchEvent(MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_DOWN, 0, 0, 0));
                etCurVal.dispatchTouchEvent(MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_UP, 0, 0, 0));
                etCurVal.setSelection(etCurVal.getText().length());
            }
        }, 300);
    }
}
