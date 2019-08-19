package com.fieldbook.tracker.traitLayouts;

import android.content.Context;
import android.os.Handler;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.traits.TraitObject;

import java.util.HashMap;

public abstract class TraitLayout extends LinearLayout {
    public DataHelper dt = new DataHelper(getContext());

    public TraitLayout(Context context) {
        super(context);
        throw new RuntimeException("Stub!");
    }

    public TraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        throw new RuntimeException("Stub!");
    }

    public TraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        throw new RuntimeException("Stub!");
    }

    public TraitLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        throw new RuntimeException("Stub!");
    }

    public abstract void loadLayout(EditText etCurVal, DataWrapper dataWrapper, HashMap newTraits,
                                   TraitObject currentTrait, String displayColor, TextWatcher cvNum,
                                   TextWatcher cvText, SeekBar seekBar, SeekBar.OnSeekBarChangeListener seekListener, Handler mHandler);
}
