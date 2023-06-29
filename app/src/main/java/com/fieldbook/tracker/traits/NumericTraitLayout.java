package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;

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
        numberButtons.put(R.id.k1, (Button) act.findViewById(R.id.k1));
        numberButtons.put(R.id.k2, (Button) act.findViewById(R.id.k2));
        numberButtons.put(R.id.k3, (Button) act.findViewById(R.id.k3));
        numberButtons.put(R.id.k4, (Button) act.findViewById(R.id.k4));
        numberButtons.put(R.id.k5, (Button) act.findViewById(R.id.k5));
        numberButtons.put(R.id.k6, (Button) act.findViewById(R.id.k6));
        numberButtons.put(R.id.k7, (Button) act.findViewById(R.id.k7));
        numberButtons.put(R.id.k8, (Button) act.findViewById(R.id.k8));
        numberButtons.put(R.id.k9, (Button) act.findViewById(R.id.k9));
        numberButtons.put(R.id.k10, (Button) act.findViewById(R.id.k10));
        numberButtons.put(R.id.k11, (Button) act.findViewById(R.id.k11));
        numberButtons.put(R.id.k12, (Button) act.findViewById(R.id.k12));
        numberButtons.put(R.id.k13, (Button) act.findViewById(R.id.k13));
        numberButtons.put(R.id.k14, (Button) act.findViewById(R.id.k14));
        numberButtons.put(R.id.k15, (Button) act.findViewById(R.id.k15));
        numberButtons.put(R.id.k16, (Button) act.findViewById(R.id.k16));

        for (Button numButton : numberButtons.values()) {
            numButton.setOnClickListener(new NumberButtonOnClickListener());
        }

        numberButtons.get(R.id.k16).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                getCollectInputView().setText("");
                removeTrait(getCurrentTrait().getTrait());
                return false;
            }
        });

        numberButtons.get(R.id.k1).requestFocus();
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
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
        super.deleteTraitListener();
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
                        updateObservation(getCurrentTrait().getTrait(), getCurrentTrait().getFormat(), value);
                    }
                } else if (numberButtons.containsKey(view.getId())) {
                    value = curText + numberButtons.get(view.getId()).getText().toString();
                    getCollectInputView().setText(value);
                    updateObservation(getCurrentTrait().getTrait(), getCurrentTrait().getFormat(), value);
                }
            }
        }
    }
}