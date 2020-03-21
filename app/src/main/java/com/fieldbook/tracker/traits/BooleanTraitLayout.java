package com.fieldbook.tracker.traits;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import com.fieldbook.tracker.R;

public class BooleanTraitLayout extends BaseTraitLayout {

    private ImageView eImg;

    public BooleanTraitLayout(Context context) {
        super(context);
    }

    public BooleanTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BooleanTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ImageView getEImg() {
        return eImg;
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public String type() {
        return "boolean";
    }

    @Override
    public void init() {

        eImg = findViewById(R.id.eImg);

        // Boolean
        eImg.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                String val = getNewTraits().get(getCurrentTrait().getTrait()).toString();

                if (val.equalsIgnoreCase("false")) {
                    val = "true";
                    eImg.setImageResource(R.drawable.trait_boolean_true);
                } else {
                    val = "false";
                    eImg.setImageResource(R.drawable.trait_boolean_false);
                }

                updateTrait(getCurrentTrait().getTrait(), "boolean", val);
            }
        });
    }

    @Override
    public void loadLayout() {

        getEtCurVal().setVisibility(EditText.GONE);
        getEtCurVal().setEnabled(false);

        if (!getNewTraits().containsKey(getCurrentTrait().getTrait())) {
            if (getCurrentTrait().getDefaultValue().trim().equalsIgnoreCase("true")) {
                updateTrait(getCurrentTrait().getTrait(), "boolean", "true");
                eImg.setImageResource(R.drawable.trait_boolean_true);
            } else {
                updateTrait(getCurrentTrait().getTrait(), "boolean", "false");
                eImg.setImageResource(R.drawable.trait_boolean_false);
            }
        } else {
            String bval = getNewTraits().get(getCurrentTrait().getTrait()).toString();

            if (bval.equalsIgnoreCase("false")) {
                eImg.setImageResource(R.drawable.trait_boolean_false);
            } else {
                eImg.setImageResource(R.drawable.trait_boolean_true);
            }

        }

    }

    @Override
    public void deleteTraitListener() {
        if (getCurrentTrait().getDefaultValue().trim().toLowerCase().equals("true")) {
            updateTrait(getCurrentTrait().getTrait(), "boolean", "true");
            eImg.setImageResource(R.drawable.trait_boolean_true);
        } else {
            updateTrait(getCurrentTrait().getTrait(), "boolean", "false");
            eImg.setImageResource(R.drawable.trait_boolean_false);
        }
    }
}