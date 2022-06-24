package com.fieldbook.tracker.traits;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.CategoryJsonUtil;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class CategoricalTraitLayout extends BaseTraitLayout {

    //todo this can eventually be merged with multicattraitlayout when we can support a switch in traits on how many categories to allow user to select

    //private StaggeredGridView gridMultiCat;
    private RecyclerView gridMultiCat;

    public CategoricalTraitLayout(Context context) {
        super(context);
    }

    public CategoricalTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CategoricalTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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

    @Override
    public void init() {
        gridMultiCat = findViewById(R.id.catGrid);
    }

    @Override
    public void loadLayout() {
        final String trait = getCurrentTrait().getTrait();
        getEtCurVal().setHint("");
        getEtCurVal().setVisibility(EditText.VISIBLE);

        //read the preferences, default to displaying values instead of labels
        String labelValPref = getPrefs().getString(GeneralKeys.LABELVAL_CUSTOMIZE,"value");

        //read the json object stored in additional info of the trait object (only in BrAPI imported traits)
        String[] cat = new String[0];

        String categoryString = getCurrentTrait().getCategories();
        try {

            ArrayList<BrAPIScaleValidValuesCategories> json = CategoryJsonUtil.Companion.decode(categoryString);

            if (!json.isEmpty()) {

                cat = new String[json.size()];
                int i = 0;
                for (BrAPIScaleValidValuesCategories scale : json) {

                    if (labelValPref.equals("value")) {
                        cat[i++] = scale.getLabel();
                    } else cat[i++] = scale.getValue();
                }

            }

        } catch (Exception e) {

            cat = categoryString.split("/");
        }

        if (!getNewTraits().containsKey(trait)) {
            getEtCurVal().setText("");
            getEtCurVal().setTextColor(Color.BLACK);
        } else {
            String label = getNewTraits().get(trait);
            if (label != null) {
                if (CategoryJsonUtil.Companion.contains(cat, label)) {
                    getEtCurVal().setText(getNewTraits().get(trait));
                    getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
                }
            }
        }

        FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(getContext());
        layoutManager.setFlexWrap(FlexWrap.WRAP);
        layoutManager.setFlexDirection(FlexDirection.ROW);
        layoutManager.setAlignItems(AlignItems.STRETCH);
        gridMultiCat.setLayoutManager(layoutManager);

        if (!((CollectActivity) getContext()).isDataLocked()) {

            String[] finalCat = cat;
            gridMultiCat.setAdapter(new CategoryTraitAdapter(getContext()) {

                @Override
                public void onBindViewHolder(CategoryTraitViewHolder holder, int position) {
                    holder.bindTo();
                    holder.mButton.setText(finalCat[position]);
                    holder.mButton.setOnClickListener(createClickListener(holder.mButton,position));
                    if (getEtCurVal().getText().toString().equals(finalCat[position]))
                        pressOnButton(holder.mButton);
                }

                @Override
                public int getItemCount() {
                    return finalCat.length;
                }
            });
        }

        gridMultiCat.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                gridMultiCat.getViewTreeObserver().removeOnGlobalLayoutListener(this);
//                gridMultiCat.getViewTreeObserver().removeGlobalOnLayoutListener(this);
//                View lastChild = gridMultiCat.getChildAt(gridMultiCat.getChildCount() - 1);
//                gridMultiCat.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, lastChild.getBottom()));
                gridMultiCat.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
        });
    }

    private OnClickListener createClickListener(final Button button, int position) {
        return new OnClickListener() {
            @Override
            public void onClick(View v) {
                final String category = button.getText().toString();
                String currentCat = getEtCurVal().getText().toString();

                if (currentCat.equals(category)) {
                    pressOffButton(button);
                    currentCat = "";
                } else {
                    pressOnButton(button);
                    currentCat = category;
                }

                getEtCurVal().setText(currentCat);

                updateTrait(getCurrentTrait().getTrait(),
                        getCurrentTrait().getFormat(),
                        currentCat);

                loadLayout(); //todo this is not the best way to do this
            }
        };
    }

    private void pressOnButton(Button button) {
        button.setTextColor(Color.parseColor(getDisplayColor()));
        button.getBackground().setColorFilter(button.getContext().getResources().getColor(R.color.button_pressed), PorterDuff.Mode.MULTIPLY);
    }

    private void pressOffButton(Button button) {
        button.setTextColor(Color.BLACK);
        button.getBackground().setColorFilter(button.getContext().getResources().getColor(R.color.button_normal), PorterDuff.Mode.MULTIPLY);
    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
        loadLayout();
    }
}
