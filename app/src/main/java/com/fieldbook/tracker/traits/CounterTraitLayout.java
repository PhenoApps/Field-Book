package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.utilities.Utils;

public class CounterTraitLayout extends BaseTraitLayout {

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
        getCollectInputView().setText("NA");
    }

    @Override
    public String type() {
        return "counter";
    }

    @Override
    public int layoutId() {
        return R.layout.trait_counter;
    }

    @Override
    public void init(Activity act) {
        Button addCounterBtn = act.findViewById(R.id.addBtn);
        Button minusCounterBtn = act.findViewById(R.id.minusBtn);

        // Add counter
        addCounterBtn.setOnClickListener(view -> {
            TraitObject trait = getCurrentTrait();
            if (trait != null) {
                if (getCurrentObservation() == null || getCurrentObservation().getValue().equals("NA")) {
                    getCollectInputView().setText("1");
                } else {
                    try {
                        getCollectInputView().setText(Integer.toString(Integer.parseInt(getCollectInputView().getText()) + 1));
                    } catch (NumberFormatException e) {
                        getCollectInputView().setText(String.valueOf(1));
                    }
                }
                String value = getCollectInputView().getText();
                updateObservation(getCurrentTrait().getTrait(), "counter", value);
                triggerTts(value);
            } else {
                Context ctx = getContext();
                Utils.makeToast(ctx, ctx.getString(R.string.trait_counter_layout_failed));
            }
        });

        // Minus counter
        minusCounterBtn.setOnClickListener(view -> {
            if (getCurrentObservation() == null || getCurrentObservation().getValue().equals("NA")) {
                getCollectInputView().setText("-1");
            } else {
                try {
                    getCollectInputView().setText(Integer.toString(Integer.parseInt(getCollectInputView().getText()) - 1));
                }
                catch (NumberFormatException e) {
                    getCollectInputView().setText(String.valueOf(-1));
                }
            }
            String value = getCollectInputView().getText();
            updateObservation(getCurrentTrait().getTrait(), "counter", value);
            triggerTts(value);
        });

        addCounterBtn.requestFocus();
    }

    @Override
    public void afterLoadExists(CollectActivity act, String value) {
        super.afterLoadExists(act, value);
        if (value != null) {
            getCollectInputView().setText(value);
        }
    }

    @Override
    public void afterLoadNotExists(CollectActivity act) {
        super.afterLoadNotExists(act);
        getCollectInputView().setText("0");
    }

    @Override
    public void deleteTraitListener() {
        removeTrait(getCurrentTrait().getTrait());
        super.deleteTraitListener();
        ObservationModel model = getCurrentObservation();
        if (model != null) {
            getCollectInputView().setText(model.getValue());
        } else {
            getCollectInputView().setText("0");
        }
    }
}