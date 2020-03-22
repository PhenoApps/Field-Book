package com.fieldbook.tracker.traits;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.R;

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
    public void init() {
        numberButtons = new LinkedHashMap<>();
        numberButtons.put(R.id.k1, (Button) findViewById(R.id.k1));
        numberButtons.put(R.id.k2, (Button) findViewById(R.id.k2));
        numberButtons.put(R.id.k3, (Button) findViewById(R.id.k3));
        numberButtons.put(R.id.k4, (Button) findViewById(R.id.k4));
        numberButtons.put(R.id.k5, (Button) findViewById(R.id.k5));
        numberButtons.put(R.id.k6, (Button) findViewById(R.id.k6));
        numberButtons.put(R.id.k7, (Button) findViewById(R.id.k7));
        numberButtons.put(R.id.k8, (Button) findViewById(R.id.k8));
        numberButtons.put(R.id.k9, (Button) findViewById(R.id.k9));
        numberButtons.put(R.id.k10, (Button) findViewById(R.id.k10));
        numberButtons.put(R.id.k11, (Button) findViewById(R.id.k11));
        numberButtons.put(R.id.k12, (Button) findViewById(R.id.k12));
        numberButtons.put(R.id.k13, (Button) findViewById(R.id.k13));
        numberButtons.put(R.id.k14, (Button) findViewById(R.id.k14));
        numberButtons.put(R.id.k15, (Button) findViewById(R.id.k15));
        numberButtons.put(R.id.k16, (Button) findViewById(R.id.k16));

        for (Button numButton : numberButtons.values()) {
            numButton.setOnClickListener(new NumberButtonOnClickListener());
        }

        numberButtons.get(R.id.k16).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                getEtCurVal().setText("");
                removeTrait(getCurrentTrait().getTrait());
                return false;
            }
        });
    }

    @Override
    public void loadLayout() {
        // Clear hint for NA since a focus change doesn't happen for the numeric trait layout
        getEtCurVal().setHint("");
        getEtCurVal().setVisibility(EditText.VISIBLE);

        if (getNewTraits().containsKey(getCurrentTrait().getTrait())) {
            getEtCurVal().setText(getNewTraits().get(getCurrentTrait().getTrait()).toString());
            getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
        } else {
            getEtCurVal().setText("");
            getEtCurVal().setTextColor(Color.BLACK);

            if (getCurrentTrait().getDefaultValue() != null && getCurrentTrait().getDefaultValue().length() > 0) {
                getEtCurVal().setText(getCurrentTrait().getDefaultValue());
                updateTrait(getCurrentTrait().getTrait(), getCurrentTrait().getFormat(), getEtCurVal().getText().toString());
            }
        }
    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
    }

    private class NumberButtonOnClickListener implements OnClickListener {

        @Override
        public void onClick(View view) {
            final String curText = getEtCurVal().getText().toString();
            if (view.getId() == R.id.k16) {        // Backspace Key Pressed
                final int length = curText.length();
                if (length > 0) {
                    getEtCurVal().setText(curText.substring(0, length - 1));
                    updateTrait(getCurrentTrait().getTrait(), getCurrentTrait().getFormat(), getEtCurVal().getText().toString());
                }
            } else if (numberButtons.containsKey(view.getId())) {
                final String v = numberButtons.get(view.getId()).getText().toString();
                getEtCurVal().setText(curText + v);
                updateTrait(getCurrentTrait().getTrait(), getCurrentTrait().getFormat(), getEtCurVal().getText().toString());
            }
        }
    }
}