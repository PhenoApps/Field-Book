package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;

import com.fieldbook.tracker.R;

public class BarcodeTraitLayout extends BaseTraitLayout {

    public BarcodeTraitLayout(Context context) {
        super(context);
    }

    public BarcodeTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BarcodeTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public String type() {
        return "barcode";
    }

    @Override
    public int layoutId() {
        return R.layout.trait_barcode;
    }

    @Override
    public void init(Activity act) {

    }

    @Override
    public void deleteTraitListener() {

    }
}