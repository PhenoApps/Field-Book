package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.utilities.CategoryJsonUtil;
import com.fieldbook.tracker.utilities.JsonUtil;
import com.fieldbook.tracker.utilities.Utils;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;

import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringJoiner;

public class MultiCatTraitLayout extends BaseTraitLayout {
    //todo this can eventually be merged with multicattraitlayout when we can support a switch in traits on how many categories to allow user to select

    private ArrayList<BrAPIScaleValidValuesCategories> categoryList;

    //track when we go to new data
    private boolean isFrozen = false;

    //private StaggeredGridView gridMultiCat;
    private RecyclerView gridMultiCat;

    private final BrAPIScaleValidValuesCategories defaultNaCategory = new BrAPIScaleValidValuesCategories().label("NA").value("NA");

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
        getCollectInputView().setText("NA");
        categoryList = new ArrayList<>();
        categoryList.add(defaultNaCategory);
        setAdapter();
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
    public int layoutId() {
        return R.layout.trait_multicat;
    }

    @Override
    public void init(Activity act) {

        gridMultiCat = act.findViewById(R.id.catGrid);

        categoryList = new ArrayList<>();

        gridMultiCat.requestFocus();
    }

    @Override
    public void afterLoadNotExists(CollectActivity act) {
        super.afterLoadNotExists(act);
        setAdapter();
    }

    @Override
    public void afterLoadDefault(CollectActivity act) {
        super.afterLoadDefault(act);
        setAdapter();
    }

    @Override
    public void afterLoadExists(CollectActivity act, @Nullable String value) {
        super.afterLoadExists(act, value);

        categoryList = new ArrayList<>();

        loadScale();

        if (value != null && value.equals("NA")) controller.getInputView().setText("NA");

        setAdapter();

        refreshLock();
    }

    private boolean shouldDisplayValues() {
        return getCurrentTrait().getCategoryDisplayValue();
    }

    private void setAdapter() {

        FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(getContext());
        layoutManager.setFlexWrap(FlexWrap.WRAP);
        layoutManager.setFlexDirection(FlexDirection.ROW);
        layoutManager.setAlignItems(AlignItems.STRETCH);
        gridMultiCat.setLayoutManager(layoutManager);

        loadScale();

        //grabs all categories for this given trait
        BrAPIScaleValidValuesCategories[] cat = getCategories();

        //creates buttons for each category and sets state based on if its been selected
        gridMultiCat.setAdapter(new MultiCatTraitAdapter(getContext()) {

            @Override
            public void onBindViewHolder(MultiCatTraitViewHolder holder, int position) {
                holder.bindTo(cat[position]);

                holder.mButton.setOnClickListener(createClickListener(holder.mButton,position));

                if (shouldDisplayValues()) {
                    holder.mButton.setText(cat[position].getValue());

                } else {
                    holder.mButton.setText(cat[position].getLabel());
                }

                //has category checks the loaded categoryList to see if this button has been selected
                if (hasCategory(cat[position])) {

                    pressOnButton(holder.mButton);

                } else {

                    pressOffButton(holder.mButton);
                }
            }

            @Override
            public int getItemCount() {
                return cat.length;
            }
        });

        gridMultiCat.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                gridMultiCat.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                gridMultiCat.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
        });
    }

    private void refreshList() {

        categoryList.clear();

        String value = getCollectInputView().getText();

        ArrayList<BrAPIScaleValidValuesCategories> scale = new ArrayList<>();

        try {

            scale = CategoryJsonUtil.Companion.decodeCategories(value);

        } catch (Exception e) {

            String[] tokens = value.split("\\:");
            for (String token : tokens) {
                BrAPIScaleValidValuesCategories c = new BrAPIScaleValidValuesCategories();
                c.setLabel(token);
                c.setValue(token);
                scale.add(c);
            }
        }

        //only filter if it's not NA
        if (!value.equals("NA")) {

            scale = CategoryJsonUtil.Companion.filterExists(getCategories(), scale);

        }

        categoryList.addAll(scale);
    }

    private void loadScale() {

        refreshList();
        refreshCategoryText();

    }

    private OnClickListener createClickListener(final Button button, int position) {
        return v -> {

            if (!isFrozen) {

                removeCategory(new BrAPIScaleValidValuesCategories().label("NA").value("NA"));

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
                    if (shouldDisplayValues()) {
                        joiner.add(c.getValue());
                    } else joiner.add(c.getLabel());
                }

                getCollectInputView().setText(joiner.toString());

                String json = CategoryJsonUtil.Companion.encode(categoryList);

                updateObservation(getCurrentTrait(), json);

                if (shouldDisplayValues()) {
                    triggerTts(cat.getValue());
                } else triggerTts(cat.getLabel());
            }
        };
    }

    private BrAPIScaleValidValuesCategories[] getCategories() {

        //read the json object stored in additional info of the trait object (only in BrAPI imported traits)
        ArrayList<BrAPIScaleValidValuesCategories> cat = new ArrayList<>();

        String categoryString = getCurrentTrait().getCategories();
        try {

            ArrayList<BrAPIScaleValidValuesCategories> json = CategoryJsonUtil.Companion.decodeCategories(categoryString);

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

    private boolean hasCategory(final BrAPIScaleValidValuesCategories category) {
        for (final BrAPIScaleValidValuesCategories cat : categoryList) {
            if (cat.getLabel().equals(category.getLabel())
                && cat.getValue().equals(category.getValue())) return true;
        }
        return false;
    }

    private void pressOnButton(Button button) {
        button.setBackgroundResource(R.drawable.button_selected);
    }

    private void pressOffButton(Button button) {
        button.setBackgroundResource(R.drawable.button_unselected);
    }

    private void addCategory(final BrAPIScaleValidValuesCategories category) {

        categoryList.add(category);

        refreshCategoryText();
    }

    private void refreshCategoryText() {

        StringJoiner joiner = new StringJoiner(":");

        for (BrAPIScaleValidValuesCategories c : categoryList) {
            String value;
            if (shouldDisplayValues()) value = c.getValue();
            else value = c.getLabel();
            joiner.add(value);
        }

        getCollectInputView().setText(joiner.toString());
    }

    private void removeCategory(final BrAPIScaleValidValuesCategories category) {

        categoryList.remove(category);

        refreshCategoryText();

    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
        super.deleteTraitListener();
        refreshLayout(false);
    }

    /**
     * Notifies grid adapter to refresh which buttons are selected.
     * This is used when the repeat value view navigates across repeated values.
     */
    @Override
    public void refreshLayout(Boolean onNew) {
        super.refreshLayout(onNew);
        refreshList();
        loadScale();
        setAdapter();
    }

    @Override
    public String decodeValue(String value) {
        StringJoiner joiner = new StringJoiner(":");
        ArrayList<BrAPIScaleValidValuesCategories> scale = CategoryJsonUtil.Companion.decode(value);
        for (BrAPIScaleValidValuesCategories s : scale) {
            if (shouldDisplayValues()) {
                joiner.add(s.getValue());
            } else joiner.add(s.getLabel());
        }
        return joiner.toString();
    }

    @NonNull
    @Override
    public Boolean validate(String data) {

        ArrayList<BrAPIScaleValidValuesCategories> cats = new ArrayList<>(Arrays.asList(getCategories()));

        ArrayList<BrAPIScaleValidValuesCategories> userChosenCats = new ArrayList<>();

        try {

            if (JsonUtil.Companion.isJsonValid(data)) {

                userChosenCats.addAll(CategoryJsonUtil.Companion.decode(data));

            } else throw new RuntimeException();

        } catch (Exception e) {

            String[] classTokens = data.split(":");

            for (String token : classTokens) {

                userChosenCats.add(new BrAPIScaleValidValuesCategories()
                        .label(token)
                        .value(token));
            }
        }

        boolean valid = true;
        for (BrAPIScaleValidValuesCategories cat : userChosenCats) {
            valid = CategoryJsonUtil.Companion.contains(cats, cat);
            if (!valid) break;
        }

        //check if the data is in the list of categories
        if (!valid) {
            getCollectActivity().runOnUiThread(() ->
                    Utils.makeToast(controller.getContext(),
                            controller.getContext().getString(R.string.trait_error_invalid_multicat_value)));
        }

        return valid;
    }
}
