package com.fieldbook.tracker.traits;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.EditText;
import com.fieldbook.tracker.activities.CollectActivity;

public class TextTraitLayout extends BaseTraitLayout {

    private Handler mHandler = new Handler();

    public TextTraitLayout(Context context) {
        super(context);
    }

    public TextTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public String type() {
        return "text";
    }

    @Override
    public void init() {

    }

    @Override
    public void loadLayout() {
        EditText valueEditText = getEtCurVal();
        valueEditText.setHint("");
        valueEditText.setVisibility(EditText.VISIBLE);
        valueEditText.setSelection(valueEditText.getText().length());
        valueEditText.setEnabled(true);

        super.loadLayout();

        // This is needed to fix a keyboard bug
        mHandler.postDelayed(() -> {
            valueEditText.dispatchTouchEvent(MotionEvent.obtain(
                    SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_DOWN, 0, 0, 0));
            valueEditText.dispatchTouchEvent(MotionEvent.obtain(
                    SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_UP, 0, 0, 0));
            valueEditText.setSelection(getEtCurVal().getText().length());
        }, 300);

        valueEditText.setEnabled(!isLocked);
    }

    @Override
    public void afterLoadExists(CollectActivity act, String value) {
        super.afterLoadExists(act, value);
        getEtCurVal().removeTextChangedListener(getCvText());
        getEtCurVal().setText(value);
        getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
        getEtCurVal().addTextChangedListener(getCvText());
        getEtCurVal().setSelection(getEtCurVal().getText().length());
    }

    @Override
    public void afterLoadNotExists(CollectActivity act) {
        super.afterLoadNotExists(act);
        getEtCurVal().removeTextChangedListener(getCvText());
        getEtCurVal().setText("");
        getEtCurVal().setTextColor(Color.BLACK);
        getEtCurVal().addTextChangedListener(getCvText());
        getEtCurVal().setSelection(getEtCurVal().getText().length());
    }

    @Override
    public void afterLoadDefault(CollectActivity act) {
        super.afterLoadDefault(act);

        getEtCurVal().addTextChangedListener(getCvText());
        getEtCurVal().setSelection(getEtCurVal().getText().length());
    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
    }
}