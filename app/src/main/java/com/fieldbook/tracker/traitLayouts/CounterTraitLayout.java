package com.fieldbook.tracker.traitLayouts;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.fieldbook.tracker.R;

public class CounterTraitLayout extends TraitLayout {

    private TextView counterTv;
    private Button addCounterBtn;
    private Button minusCounterBtn;

    public CounterTraitLayout(Context context) {
        super(context);
    }

    public CounterTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CounterTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setNaTraitsText() {
        counterTv.setText("NA");
    }

    @Override
    public String type() {
        return "counter";
    }

    @Override
    public void init() {
        addCounterBtn = findViewById(R.id.addBtn);
        minusCounterBtn = findViewById(R.id.minusBtn);
        counterTv = findViewById(R.id.curCount);


        // Add counter
        addCounterBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                //TODO NullPointerException
                if (getNewTraits().containsKey(getCurrentTrait().getTrait()) && getNewTraits().get(getCurrentTrait().getTrait()).toString().equals("NA")) {
                    counterTv.setText("1");
                } else {
                    counterTv.setText(Integer.toString(Integer.parseInt(counterTv.getText().toString()) + 1));
                }
                updateTrait(getCurrentTrait().getTrait(), "counter", counterTv.getText().toString());
            }
        });

        // Minus counter
        minusCounterBtn.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                //TODO NullPointerException
                if (getNewTraits().containsKey(getCurrentTrait().getTrait()) && getNewTraits().get(getCurrentTrait().getTrait()).toString().equals("NA")) {
                    counterTv.setText("-1");
                } else {
                    counterTv.setText(Integer.toString(Integer.parseInt(counterTv.getText().toString()) - 1));
                }
                updateTrait(getCurrentTrait().getTrait(), "counter", counterTv.getText().toString());
            }
        });

    }

    @Override
    public void loadLayout() {
        getEtCurVal().setVisibility(EditText.GONE);
        getEtCurVal().setEnabled(false);

        if (!getNewTraits().containsKey(getCurrentTrait().getTrait())) {
            counterTv.setText("0");
        } else {
            counterTv.setText(getNewTraits().get(getCurrentTrait().getTrait()).toString());
        }
    }

    @Override
    public void deleteTraitListener() {
        removeTrait(getCurrentTrait().getTrait());
        counterTv.setText("0");
    }
}
