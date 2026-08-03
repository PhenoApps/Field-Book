package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.utilities.Utils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

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
        FloatingActionButton addCounterBtn = findTraitView(R.id.addBtn);
        FloatingActionButton minusCounterBtn = findTraitView(R.id.minusBtn);

        // Add counter
        addCounterBtn.setOnClickListener(view -> {
            if (isLocked) return;
            TraitObject trait = getCurrentTrait();
            if (trait != null) {
                if (hasNodeSession()) {
                    // Node session has no ObservationModel — use the live input text.
                    String current = getCollectInputView().getText();
                    boolean emptyOrNa = current == null || current.isEmpty()
                            || current.equalsIgnoreCase("NA");
                    if (emptyOrNa) {
                        getCollectInputView().setText("1");
                    } else {
                        try {
                            getCollectInputView().setText(
                                    Integer.toString(Integer.parseInt(current) + 1));
                        } catch (NumberFormatException e) {
                            getCollectInputView().setText(String.valueOf(1));
                        }
                    }
                } else {
                    // Collect path: obs-first (main parity).
                    ObservationModel obs = getCurrentObservation();
                    if (obs == null || "NA".equals(obs.getValue())) {
                        getCollectInputView().setText("1");
                    } else {
                        try {
                            getCollectInputView().setText(Integer.toString(
                                    Integer.parseInt(getCollectInputView().getText()) + 1));
                        } catch (NumberFormatException e) {
                            getCollectInputView().setText(String.valueOf(1));
                        }
                    }
                }
                String value = getCollectInputView().getText();
                updateObservation(getCurrentTrait(), value);
                triggerTts(value);
            } else {
                Context ctx = getContext();
                Utils.makeToast(ctx, ctx.getString(R.string.trait_counter_layout_failed));
            }
        });

        // Minus counter
        minusCounterBtn.setOnClickListener(view -> {
            if (isLocked) return;
            if (hasNodeSession()) {
                String current = getCollectInputView().getText();
                boolean emptyOrNa = current == null || current.isEmpty()
                        || current.equalsIgnoreCase("NA");
                if (emptyOrNa) {
                    getCollectInputView().setText("-1");
                } else {
                    try {
                        getCollectInputView().setText(
                                Integer.toString(Integer.parseInt(current) - 1));
                    } catch (NumberFormatException e) {
                        getCollectInputView().setText(String.valueOf(-1));
                    }
                }
            } else {
                ObservationModel obs = getCurrentObservation();
                if (obs == null || "NA".equals(obs.getValue())) {
                    getCollectInputView().setText("-1");
                } else {
                    try {
                        getCollectInputView().setText(Integer.toString(
                                Integer.parseInt(getCollectInputView().getText()) - 1));
                    } catch (NumberFormatException e) {
                        getCollectInputView().setText(String.valueOf(-1));
                    }
                }
            }
            String value = getCollectInputView().getText();
            updateObservation(getCurrentTrait(), value);
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
        removeTrait(getCurrentTrait());
        super.deleteTraitListener();
        ObservationModel model = getCurrentObservation();
        if (model != null) {
            getCollectInputView().setText(model.getValue());
        } else {
            getCollectInputView().setText("0");
        }
    }

    @NonNull
    @Override
    public Boolean validate(String data) {
        try {
            Integer.parseInt(data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}