package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
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
import java.util.StringJoiner;

public class CategoricalTraitLayout extends BaseTraitLayout {

    //todo this can eventually be merged with multicattraitlayout when we can support a switch in traits on how many categories to allow user to select

    public static String[] POSSIBLE_VALUES = new String[]{ "qualitative", "categorical" };
    private static final String CATEGORY_SEPARATOR = ":";

    //private StaggeredGridView gridMultiCat;
    private RecyclerView gridMultiCat;
    private ArrayList<BrAPIScaleValidValuesCategories> categoryList;
    private final BrAPIScaleValidValuesCategories defaultNaCategory = new BrAPIScaleValidValuesCategories().label("NA").value("NA");

    public CategoricalTraitLayout(Context context) {
        super(context);
    }

    public CategoricalTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CategoricalTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    static public boolean isTraitCategorical(String traitFormat) {
        for (String name : POSSIBLE_VALUES) {
            if (name.equals(traitFormat)) return true;
        }
        return false;
    }

    private boolean shouldDisplayValues() {
        return getCurrentTrait().getCategoryDisplayValue();
    }

    private boolean isMulticatEnabled() {
        return getCurrentTrait().getAllowMulticat();
    }

    private String getDisplayText(BrAPIScaleValidValuesCategories category) {
        return shouldDisplayValues() ? category.getValue() : category.getLabel();
    }

    @Override
    public void setNaTraitsText() {
        getCollectInputView().setText("NA");
        if (isMulticatEnabled()) {
            categoryList = new ArrayList<>();
            categoryList.add(defaultNaCategory);
        }
        setAdapter();
    }

    @Override
    public String type() {
        return "categorical";
    }

    @Override
    public int layoutId() {
        return R.layout.trait_categorical;
    }

    public boolean isTraitType(String trait) {
        return trait.equals("categorical") || trait.equals("qualitative");
    }

    @Override
    public void init(Activity act) {

        gridMultiCat = act.findViewById(R.id.catGrid);

        if (isMulticatEnabled()) {
            categoryList = new ArrayList<>();
        }

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

        //read the json object stored in additional info of the trait object (only in BrAPI imported traits)
        if (isMulticatEnabled()) {
            categoryList = new ArrayList<>();
            loadMulticatScale();
            if (value != null && value.equals("NA")) getCollectInputView().setText("NA");
        } else {
            loadCategoryValue(value);
        }

        refreshLock();
        setAdapter();
    }

    private void loadCategoryValue(@Nullable String value) {
        ArrayList<BrAPIScaleValidValuesCategories> categories = getCategories();

        if (value == null || value.isEmpty()) return;

        //check if its the new json
        try {

            ArrayList<BrAPIScaleValidValuesCategories> cat = CategoryJsonUtil.Companion.decode(value);

            if (!cat.isEmpty()) {

                // if the trait was set to allowMulticat previously
                // make sure to validate if the observation had multiple categories recorded
                if (cat.size() > 1) {
                    StringJoiner joiner = new StringJoiner(CATEGORY_SEPARATOR);
                    for (BrAPIScaleValidValuesCategories c : cat) {
                        joiner.add(getDisplayText(c));
                    }
                    getCollectInputView().setText(joiner.toString());
                    return;
                }

                //get the value from the single-sized array
                BrAPIScaleValidValuesCategories category = cat.get(0);

                //check that this pair is a valid label/val pair in the category,
                //if it is then set the text based on the preference
                if (CategoryJsonUtil.Companion.contains(categories, category)) {

                    //display the category based on preferences
                    getCollectInputView().setText(getDisplayText(category));

                }
            }

        } catch (Exception e) {

            e.printStackTrace(); //if it fails to decode, assume its an old string

            if (CategoryJsonUtil.Companion.contains(categories, value)) {

                getCollectInputView().setText(value);

                getCollectInputView().setTextColor(Color.parseColor(getDisplayColor()));
            }
        }
    }

    private void loadMulticatScale() {
        refreshMultiCatList();
        refreshMultiCatText();
    }

    private void refreshMultiCatList() {
        if (categoryList == null) categoryList = new ArrayList<>();
        categoryList.clear();

        String value = (getCurrentObservation() != null) ? getCurrentObservation().getValue() : getCollectInputView().getText();

        ArrayList<BrAPIScaleValidValuesCategories> scale = new ArrayList<>();

        try {
            scale = CategoryJsonUtil.Companion.decodeCategories(value);
        } catch (Exception e) {
            String[] tokens = value.split(CATEGORY_SEPARATOR);
            for (String token : tokens) {
                BrAPIScaleValidValuesCategories c = new BrAPIScaleValidValuesCategories();
                c.setLabel(token);
                c.setValue(token);
                scale.add(c);
            }
        }

        //only filter if it's not NA
        if (!value.equals("NA")) {
            BrAPIScaleValidValuesCategories[] categoriesArray = getCategories().toArray(new BrAPIScaleValidValuesCategories[0]);
            scale = CategoryJsonUtil.Companion.filterExists(categoriesArray, scale);
        }

        categoryList.addAll(scale);
    }

    private ArrayList<BrAPIScaleValidValuesCategories> getCategories() {

        //read the json object stored in additional info of the trait object (only in BrAPI imported traits)
        ArrayList<BrAPIScaleValidValuesCategories> cats = new ArrayList<>();

        String categoryString = getCurrentTrait().getCategories();
        try {

            cats = CategoryJsonUtil.Companion.decodeCategories(categoryString);

        } catch (Exception e) {

            String[] cat = categoryString.split("/");
            for (String label : cat) {
                BrAPIScaleValidValuesCategories c = new BrAPIScaleValidValuesCategories();
                c.setLabel(label);
                c.setValue(label);
                cats.add(c);
            }
        }

        return cats;
    }

    private void setAdapter() {
        FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(getContext());
        layoutManager.setFlexWrap(FlexWrap.WRAP);
        layoutManager.setFlexDirection(FlexDirection.ROW);
        layoutManager.setAlignItems(AlignItems.STRETCH);
        gridMultiCat.setLayoutManager(layoutManager);

        ArrayList<BrAPIScaleValidValuesCategories> categories = getCategories();

        gridMultiCat.setAdapter(new CategoryTraitAdapter(getContext()) {
            @Override
            public void onBindViewHolder(CategoryTraitViewHolder holder, int position) {

                BrAPIScaleValidValuesCategories category = categories.get(position);

                holder.bindTo(category);

                holder.mButton.setText(getDisplayText(category));

                if (isMulticatEnabled()) {
                    holder.mButton.setOnClickListener(createMultiCatClickListener(holder.mButton));

                    if (hasCategory(category)) {
                        pressOnButton(holder.mButton);
                    } else {
                        pressOffButton(holder.mButton);
                    }
                } else {
                    holder.mButton.setOnClickListener(createCategoryClickListener(holder.mButton));
                    String currentText = getCollectInputView().getText();

                    boolean isSelected = currentText.equals(getDisplayText(category));

                    if (isSelected) {
                        pressOnButton(holder.mButton);
                    } else {
                        pressOffButton(holder.mButton);
                    }
                }
            }

            @Override
            public int getItemCount() {
                return categories.size();
            }
        });

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

    /**
     * Notifies grid adapter to refresh which buttons are selected.
     * This is used when the repeat value view navigates across repeated values.
     */
    @Override
    public void refreshLayout(Boolean onNew) {
        super.refreshLayout(onNew);

        if (isMulticatEnabled()) {
            refreshMultiCatList();
        }

        RecyclerView.Adapter<?> adapter = gridMultiCat.getAdapter();
        if (adapter != null) {
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    private OnClickListener createCategoryClickListener(final Button button) {
        return v -> {

            if (!((CollectActivity) getContext()).isDataLocked()) {
                //cast tag to the buttons label/val pair
                final BrAPIScaleValidValuesCategories pair = (BrAPIScaleValidValuesCategories) button.getTag();
                final ArrayList<BrAPIScaleValidValuesCategories> scale = new ArrayList<>(); //this is saved in the db

                final String category = button.getText().toString();
                String currentCat = getCollectInputView().getText(); //displayed in the edit text

                if (currentCat.equals(category)) {
                    pressOffButton(button);
                    currentCat = "";
                } else {
                    pressOnButton(button);
                    currentCat = category;
                    scale.add(pair);
                }

                getCollectInputView().setText(currentCat);

                updateObservation(getCurrentTrait(), CategoryJsonUtil.Companion.encode(scale));

                triggerTts(category);

                refreshLayout(false);
            }
        };
    }

    private OnClickListener createMultiCatClickListener(final Button button) {
        return v -> {
            if (!((CollectActivity) getContext()).isDataLocked()) {
                removeCategory(defaultNaCategory);
                BrAPIScaleValidValuesCategories cat = (BrAPIScaleValidValuesCategories) button.getTag();

                if (hasCategory(cat)) {
                    pressOffButton(button);
                    removeCategory(cat);
                } else {
                    pressOnButton(button);
                    addCategory(cat);
                }

                StringJoiner joiner = new StringJoiner(CATEGORY_SEPARATOR);
                for (BrAPIScaleValidValuesCategories c : categoryList) {
                    joiner.add(getDisplayText(c));
                }

                getCollectInputView().setText(joiner.toString());
                String json = CategoryJsonUtil.Companion.encode(categoryList);
                updateObservation(getCurrentTrait(), json);

                triggerTts(getDisplayText(cat));
            }
        };
    }

    private boolean hasCategory(final BrAPIScaleValidValuesCategories category) {
        if (categoryList == null) return false;
        for (final BrAPIScaleValidValuesCategories cat : categoryList) {
            if (cat.getLabel().equals(category.getLabel())
                    && cat.getValue().equals(category.getValue())) return true;
        }
        return false;
    }

    private void addCategory(final BrAPIScaleValidValuesCategories category) {
        if (categoryList == null) categoryList = new ArrayList<>();
        categoryList.add(category);
        refreshMultiCatText();
    }

    private void refreshMultiCatText() {
        StringJoiner joiner = new StringJoiner(CATEGORY_SEPARATOR);
        for (BrAPIScaleValidValuesCategories c : categoryList) {
            joiner.add(getDisplayText(c));
        }
        getCollectInputView().setText(joiner.toString());
    }

    private void removeCategory(final BrAPIScaleValidValuesCategories category) {
        if (categoryList != null) {
            categoryList.remove(category);
            refreshMultiCatText();
        }
    }

    private void pressOnButton(Button button) {
        button.setBackgroundResource(R.drawable.button_selected);
    }

    private void pressOffButton(Button button) {
        button.setBackgroundResource(R.drawable.button_unselected);
    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
        super.deleteTraitListener();
        if (isMulticatEnabled()) {
            refreshLayout(false);
        } else {
            loadLayout();
        }
    }

    @Override
    public String decodeValue(String value) {
        if (isMulticatEnabled()) {
            StringJoiner joiner = new StringJoiner(CATEGORY_SEPARATOR);
            ArrayList<BrAPIScaleValidValuesCategories> scale = CategoryJsonUtil.Companion.decode(value);
            for (BrAPIScaleValidValuesCategories s : scale) {
                joiner.add(getDisplayText(s));
            }
            return joiner.toString();
        } else {
            ArrayList<BrAPIScaleValidValuesCategories> scale = CategoryJsonUtil.Companion.decode(value);
            if (!scale.isEmpty()) {
                return getDisplayText(scale.get(0));
            } else return "";
        }
    }

    @NonNull
    @Override
    public Boolean validate(String data) {
        if (isMulticatEnabled()) {
            return validateMulticat(data);
        } else {
            return validateCategory(data);
        }
    }

    public Boolean validateMulticat(String data) {
        ArrayList<BrAPIScaleValidValuesCategories> cats = getCategories();
        ArrayList<BrAPIScaleValidValuesCategories> userChosenCats = new ArrayList<>();

        String value = (getCurrentObservation() != null) ? getCurrentObservation().getValue() : data;
        try {
            if (JsonUtil.Companion.isJsonValid(value)) {
                userChosenCats.addAll(CategoryJsonUtil.Companion.decode(value));
            } else throw new RuntimeException();

        } catch (Exception e) {

            String[] classTokens = value.split(CATEGORY_SEPARATOR);

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

        // check if the data is in the list of categories
        if (!valid) {
            getCollectActivity().runOnUiThread(() ->
                    Utils.makeToast(controller.getContext(),
                            controller.getContext().getString(R.string.trait_error_invalid_multicat_value)));
        }

        return valid;
    }

    public Boolean validateCategory(String data) {
        try {
            ArrayList<BrAPIScaleValidValuesCategories> userChosenCats = CategoryJsonUtil.Companion.decode(data);
            if (userChosenCats.isEmpty()) {
                return true;
            }

            if (userChosenCats.size() > 1) { // if multicat, invalid
                getCollectActivity().runOnUiThread(() ->
                        Utils.makeToast(controller.getContext(),
                                controller.getContext().getString(R.string.trait_error_multicat_disabled)));
                return false;
            }

            ArrayList<BrAPIScaleValidValuesCategories> cats = getCategories();
            return CategoryJsonUtil.Companion.contains(cats, userChosenCats.get(0));
        } catch (Exception e) {
            return false;
        }
    }
}