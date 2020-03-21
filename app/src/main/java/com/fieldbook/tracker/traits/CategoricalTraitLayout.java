package com.fieldbook.tracker.traits;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.fieldbook.tracker.activities.ConfigActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.objects.TraitObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CategoricalTraitLayout extends BaseTraitLayout {

    private List<Button> buttonArray;

    public CategoricalTraitLayout(Context context) {
        super(context);
    }

    public CategoricalTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CategoricalTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public List<Button> getButtonArray() {
        return buttonArray;
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public String type() {
        return "categorical";
    }

    public boolean isTraitType(String trait) {
        return trait.equals("categorical") || trait.equals("qualitative");
    }

    public void init() {
        buttonArray = new ArrayList<>();
        buttonArray.add((Button) findViewById(R.id.q1));
        buttonArray.add((Button) findViewById(R.id.q2));
        buttonArray.add((Button) findViewById(R.id.q3));
        buttonArray.add((Button) findViewById(R.id.q4));
        buttonArray.add((Button) findViewById(R.id.q5));
        buttonArray.add((Button) findViewById(R.id.q6));
        buttonArray.add((Button) findViewById(R.id.q7));
        buttonArray.add((Button) findViewById(R.id.q8));
        buttonArray.add((Button) findViewById(R.id.q9));
        buttonArray.add((Button) findViewById(R.id.q10));
        buttonArray.add((Button) findViewById(R.id.q11));
        buttonArray.add((Button) findViewById(R.id.q12));

        // Clear all other color except this button's
        for (final Button btn : buttonArray) {
            // Functions to clear all other color except this button's
            btn.setOnClickListener(new OnClickListener() {
                public void onClick(View arg0) {
                    if (checkButton(btn, getNewTraits(), getCurrentTrait())) {
                        return;
                    }
                    updateTrait(getCurrentTrait().getTrait(), getCurrentTrait().getFormat(), btn.getText().toString());
                    setCategoricalButtons(btn);
                }

            });
        }
    }

    public void setCategoricalButtons(Button choice) {
        for (Button button : buttonArray) {
            if (button == choice) {
                button.setTextColor(Color.parseColor(getDisplayColor()));
                button.setBackgroundColor(getResources().getColor(R.color.button_pressed));
            } else {
                button.setTextColor(Color.BLACK);
                button.setBackgroundColor(getResources().getColor(R.color.button_normal));
            }
        }
    }

    private Boolean checkButton(Button button, Map newTraits, TraitObject currentTrait) {
        String curCat = "";
        if (newTraits.containsKey(currentTrait.getTrait())) {
            curCat = newTraits.get(currentTrait.getTrait()).toString();
        }
        if (button.getText().toString().equals(curCat)) {
            newTraits.remove(currentTrait.getTrait());
            ConfigActivity.dt.deleteTrait(getCRange().plot_id, currentTrait.getTrait());
            setCategoricalButtons(null);
            return true;
        }
        return false;
    }

    @Override
    public void loadLayout() {

        getEtCurVal().setVisibility(EditText.GONE);
        getEtCurVal().setEnabled(false);

        String lastQualitative = "";

        if (getNewTraits().containsKey(getCurrentTrait().getTrait())) {
            lastQualitative = getNewTraits().get(getCurrentTrait().getTrait())
                    .toString();
        }

        String[] cat = getCurrentTrait().getCategories().split("/");

        // Hide unused buttons
        for (int i = cat.length; i < 12; i++) {
            buttonArray.get(i).setVisibility(Button.GONE);
        }

        // Reset button visibility for items in the last row
        if (12 - cat.length > 0) {
            for (int i = 11; i >= cat.length; i--) {
                buttonArray.get(i).setVisibility(Button.INVISIBLE);
            }
        }

        // Set the color and visibility for the right buttons
        for (int i = 0; i < cat.length; i++) {
            if (cat[i].equals(lastQualitative)) {
                buttonArray.get(i).setVisibility(Button.VISIBLE);
                buttonArray.get(i).setText(cat[i]);
                buttonArray.get(i).setTextColor(Color.parseColor(getDisplayColor()));
                buttonArray.get(i).setBackgroundColor(getResources().getColor(R.color.button_pressed));
            } else {
                //TODO debug number of buttons, maybe add validation when creating categorical trait
                buttonArray.get(i).setVisibility(Button.VISIBLE);
                buttonArray.get(i).setText(cat[i]);
                buttonArray.get(i).setTextColor(Color.BLACK);
                buttonArray.get(i).setBackgroundColor(getResources().getColor(R.color.button_normal));
            }
        }
    }

    @Override
    public void deleteTraitListener() {
        getNewTraits().remove(getCurrentTrait().getTrait());
        ConfigActivity.dt.deleteTrait(getCRange().plot_id, getCurrentTrait().getTrait());
        setCategoricalButtons(null);
    }
}