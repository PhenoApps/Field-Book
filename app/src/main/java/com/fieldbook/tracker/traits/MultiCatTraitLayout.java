package com.fieldbook.tracker.traits;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
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

import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringJoiner;

public class MultiCatTraitLayout extends BaseTraitLayout {
    //todo this can eventually be merged with multicattraitlayout when we can support a switch in traits on how many categories to allow user to select

    //on load layout, check preferences and save to variable
    //this will choose whether to display the label or value in subsequent functions
    private boolean showLabel = true;

    private ArrayList<BrAPIScaleValidValuesCategories> categoryList;

    //track when we go to new data
    private boolean isFrozen = false;

    //private StaggeredGridView gridMultiCat;
    private RecyclerView gridMultiCat;

    public MultiCatTraitLayout(Context context) {
        super(context);
    }

    public MultiCatTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MultiCatTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public void refreshLock() {
        isFrozen = ((CollectActivity) getContext()).isDataLocked();
    }

    @Override
    public String type() {
        return "multicat";
    }

    @Override
    public void init() {

        gridMultiCat = findViewById(R.id.catGrid);

        categoryList = new ArrayList<>();
    }

    @Override
    public void loadLayout() {
        super.loadLayout();

        final String trait = getCurrentTrait().getTrait();

        getEtCurVal().setHint("");
        getEtCurVal().setVisibility(EditText.VISIBLE);

        String labelValPref = getPrefs().getString(GeneralKeys.LABELVAL_CUSTOMIZE,"value");
        showLabel = !labelValPref.equals("value");

        categoryList = new ArrayList<>();

        if (!getNewTraits().containsKey(trait)) {
            getEtCurVal().setText("");
            getEtCurVal().setTextColor(Color.BLACK);
        } else {

            String value = getNewTraits().get(trait);
            if (value != null) {

                ArrayList<BrAPIScaleValidValuesCategories> scale = new ArrayList<>();

                try {

                     scale = CategoryJsonUtil.Companion.decode(value);

                } catch (Exception e) {

                    String[] tokens = value.split("\\:");
                    for (String token : tokens) {
                        BrAPIScaleValidValuesCategories c = new BrAPIScaleValidValuesCategories();
                        c.setLabel(token);
                        c.setValue(token);
                        scale.add(c);
                    }
                }

                scale = CategoryJsonUtil.Companion.filterExists(getCategories(), scale);

                categoryList.addAll(scale);
                refreshCategoryText();
                getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
            }
        }

        BrAPIScaleValidValuesCategories[] cat = getCategories();

        FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(getContext());
        layoutManager.setFlexWrap(FlexWrap.WRAP);
        layoutManager.setFlexDirection(FlexDirection.ROW);
        layoutManager.setAlignItems(AlignItems.STRETCH);
        gridMultiCat.setLayoutManager(layoutManager);

        if (!((CollectActivity) getContext()).isDataLocked()) {

            BrAPIScaleValidValuesCategories[] finalCat = cat;
            gridMultiCat.setAdapter(new MultiCatTraitAdapter(getContext()) {

                @Override
                public void onBindViewHolder(MultiCatTraitViewHolder holder, int position) {
                    holder.bindTo(finalCat[position]);

                    holder.mButton.setOnClickListener(createClickListener(holder.mButton,position));

                    if (showLabel) {
                        holder.mButton.setText(finalCat[position].getLabel());

                    } else {
                        holder.mButton.setText(finalCat[position].getValue());
                    }

                    if (hasCategory(finalCat[position]))
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

        refreshLock();
    }

    private OnClickListener createClickListener(final Button button, int position) {
        return v -> {

            if (!isFrozen) {
                BrAPIScaleValidValuesCategories cat = (BrAPIScaleValidValuesCategories) button.getTag();

                if (hasCategory(cat)) {
                    pressOffButton(button);
                    removeCategory(cat);
                } else {
                    pressOnButton(button);
                    addCategory((BrAPIScaleValidValuesCategories) button.getTag());
                }

                StringJoiner joiner = new StringJoiner(":");
                for (BrAPIScaleValidValuesCategories c : categoryList) {
                    if (showLabel) {
                        joiner.add(c.getLabel());
                    } else joiner.add(c.getValue());
                }

                getEtCurVal().setText(joiner.toString());

                String json = CategoryJsonUtil.Companion.encode(categoryList);

                updateTrait(getCurrentTrait().getTrait(),
                        getCurrentTrait().getFormat(),
                        json);

                if (showLabel) {
                    triggerTts(cat.getLabel());
                } else triggerTts(cat.getValue());
            }
        };
    }

    private BrAPIScaleValidValuesCategories[] getCategories() {

        //read the json object stored in additional info of the trait object (only in BrAPI imported traits)
        ArrayList<BrAPIScaleValidValuesCategories> cat = new ArrayList<>();

        String categoryString = getCurrentTrait().getCategories();
        try {

            ArrayList<BrAPIScaleValidValuesCategories> json = CategoryJsonUtil.Companion.decode(categoryString);

            if (!json.isEmpty()) {

                cat.addAll(json);
            }

        } catch (Exception e) {

            String[] rawStrings = categoryString.split("/");

            for (String label : rawStrings) {
                BrAPIScaleValidValuesCategories s = new BrAPIScaleValidValuesCategories();
                s.setValue(label);
                s.setLabel(label);
                cat.add(s);
            }
        }

        return cat.toArray(new BrAPIScaleValidValuesCategories[0]);
    }

    private boolean existsCategory(BrAPIScaleValidValuesCategories category) {
        final BrAPIScaleValidValuesCategories[] cats = getCategories();
        for (BrAPIScaleValidValuesCategories cat : cats) {
            if (cat.getValue().equals(category.getValue())
                    && cat.getLabel().equals(category.getLabel()))
                return true;
        }
        return false;
    }

    // if there are wrong categories, remove them
    // I want to remove them when moveing the page,
    // but it is not so easy
    private String normalizeCategory() {
        ArrayList<String> normalizedCategoryList = new ArrayList<>();
        HashSet<String> appeared = new HashSet<>();
        for (BrAPIScaleValidValuesCategories category : categoryList) {
            String value = "";
            if (showLabel) value = category.getLabel();
            else value = category.getValue();
            if (!appeared.contains(value) && existsCategory(category)) {
                normalizedCategoryList.add(value);
                appeared.add(value);
            }
        }

        if (normalizedCategoryList.isEmpty())
            return "";
        String normalizedCategory = normalizedCategoryList.get(0);
        for (int i = 1; i < normalizedCategoryList.size(); ++i) {
            normalizedCategory += ":";
            normalizedCategory += normalizedCategoryList.get(i);
        }
        return normalizedCategory;
    }

    private boolean hasCategory(final BrAPIScaleValidValuesCategories category) {
        for (final BrAPIScaleValidValuesCategories cat : categoryList) {
            if (cat.getLabel().equals(category.getLabel())
                && cat.getValue().equals(category.getValue())) return true;
        }
        return false;
    }

    private void pressOnButton(Button button) {
        button.setTextColor(Color.parseColor(getDisplayColor()));
        button.getBackground().setColorFilter(button.getContext().getResources().getColor(R.color.button_pressed), PorterDuff.Mode.MULTIPLY);
    }

    private void pressOffButton(Button button) {
        button.setTextColor(Color.BLACK);
        button.getBackground().setColorFilter(button.getContext().getResources().getColor(R.color.button_normal), PorterDuff.Mode.MULTIPLY);
    }

    private void addCategory(final BrAPIScaleValidValuesCategories category) {

        categoryList.add(category);

        refreshCategoryText();
    }

    private void refreshCategoryText() {

        StringJoiner joiner = new StringJoiner(":");

        for (BrAPIScaleValidValuesCategories c : categoryList) {
            String value;
            if (showLabel) value = c.getLabel();
            else value = c.getValue();
            joiner.add(value);
        }

        getEtCurVal().setText(joiner.toString());
    }

    private void removeCategory(final BrAPIScaleValidValuesCategories category) {

        categoryList.remove(category);

        refreshCategoryText();

    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
        loadLayout();
    }
}
