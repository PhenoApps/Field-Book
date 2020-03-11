package com.fieldbook.tracker.traitLayouts;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.barcodes.IntentIntegrator;

import static com.fieldbook.tracker.MainActivity.thisActivity;

public class TextTraitLayout extends TraitLayout {

    private TraitLayout thisLayout;
    private Handler mHandler = new Handler();

    public TextTraitLayout(Context context) {
        super(context);
        thisLayout = this;
    }

    public TextTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        thisLayout = this;
    }

    public TextTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        thisLayout = this;
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
        Button button = (Button) findViewById(R.id.barcode_for_value);
        button.setOnClickListener(new NumberButtonOnClickListener());
    }

    @Override
    public void loadLayout() {
        getEtCurVal().setHint("");
        getEtCurVal().setVisibility(EditText.VISIBLE);
        getEtCurVal().setSelection(getEtCurVal().getText().length());
        getEtCurVal().setEnabled(true);

        if (getNewTraits().containsKey(getCurrentTrait().getTrait())) {
            getEtCurVal().removeTextChangedListener(getCvText());
            getEtCurVal().setText(getNewTraits().get(getCurrentTrait().getTrait()).toString());
            getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
            getEtCurVal().addTextChangedListener(getCvText());
            getEtCurVal().setSelection(getEtCurVal().getText().length());
        } else {
            getEtCurVal().removeTextChangedListener(getCvText());
            getEtCurVal().setText("");
            getEtCurVal().setTextColor(Color.BLACK);

            if (getCurrentTrait().getDefaultValue() != null && getCurrentTrait().getDefaultValue().length() > 0) {
                getEtCurVal().setText(getCurrentTrait().getDefaultValue());
                updateTrait(getCurrentTrait().getTrait(), getCurrentTrait().getFormat(), getEtCurVal().getText().toString());
            }

            getEtCurVal().addTextChangedListener(getCvText());
            getEtCurVal().setSelection(getEtCurVal().getText().length());
        }

        // This is needed to fix a keyboard bug
        mHandler.postDelayed(new Runnable() {
            public void run() {
                getEtCurVal().dispatchTouchEvent(MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_DOWN, 0, 0, 0));
                getEtCurVal().dispatchTouchEvent(MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_UP, 0, 0, 0));
                getEtCurVal().setSelection(getEtCurVal().getText().length());
            }
        }, 300);
    }

    @Override
    public void deleteTraitListener() {
        ((MainActivity) getContext()).removeTrait();
    }
    
    private class NumberButtonOnClickListener implements OnClickListener {
        @Override
        public void onClick(View view) {
            final String curText = getEtCurVal().getText().toString();
            IntentIntegrator integrator = new IntentIntegrator(thisActivity);
            integrator.initiateScan();
            thisLayout.setBarcodeTargetValue();
        }
    }
}