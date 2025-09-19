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
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;

import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;

import java.util.ArrayList;

public class CategoricalTraitLayout extends BaseTraitLayout {

    //todo this can eventually be merged with multicattraitlayout when we can support a switch in traits on how many categories to allow user to select

    public static String[] POSSIBLE_VALUES = new String[]{ "qualitative", "categorical" };

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

    static public boolean isTraitCategorical(String traitFormat) {
        for (String name : POSSIBLE_VALUES) {
            if (name.equals(traitFormat)) return true;
        }
        return false;
    }

    private boolean shouldDisplayValues() {
        return getCurrentTrait().getCategoryDisplayValue();
    }

    @Override
    public void setNaTraitsText() {
        getCollectInputView().setText("NA");
        setAdapter(getCategories());
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

        gridMultiCat.requestFocus();
    }

    @Override
    public void afterLoadNotExists(CollectActivity act) {
        super.afterLoadNotExists(act);
        setAdapter(getCategories());
    }

    @Override
    public void afterLoadDefault(CollectActivity act) {
        super.afterLoadDefault(act);
        setAdapter(getCategories());
    }

    @Override
    public void afterLoadExists(CollectActivity act, @Nullable String value) {
        super.afterLoadExists(act, value);

        //read the json object stored in additional info of the trait object (only in BrAPI imported traits)
        ArrayList<BrAPIScaleValidValuesCategories> cats = getCategories();

        if (value != null && !value.isEmpty()) {

            //check if its the new json
            try {

                ArrayList<BrAPIScaleValidValuesCategories> c = CategoryJsonUtil.Companion.decode(value);

                if (!c.isEmpty()) {

                    //get the value from the single-sized array
                    BrAPIScaleValidValuesCategories labelVal = c.get(0);

                    //check that this pair is a valid label/val pair in the category,
                    //if it is then set the text based on the preference
                    if (CategoryJsonUtil.Companion.contains(cats, labelVal)) {

                        //display the category based on preferences
                        if (shouldDisplayValues()) {

                            getCollectInputView().setText(labelVal.getValue());

                        } else {

                            getCollectInputView().setText(labelVal.getLabel());

                        }

                    }
                }

            } catch (Exception e) {

                e.printStackTrace(); //if it fails to decode, assume its an old string

                if (CategoryJsonUtil.Companion.contains(cats, value)) {

                    getCollectInputView().setText(value);

                    getCollectInputView().setTextColor(Color.parseColor(getDisplayColor()));
                }
            }
        }

        setAdapter(cats);
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

    private void setAdapter(ArrayList<BrAPIScaleValidValuesCategories> cats) {

        FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(getContext());
        layoutManager.setFlexWrap(FlexWrap.WRAP);
        layoutManager.setFlexDirection(FlexDirection.ROW);
        layoutManager.setAlignItems(AlignItems.STRETCH);
        gridMultiCat.setLayoutManager(layoutManager);

        gridMultiCat.setAdapter(new CategoryTraitAdapter(getContext()) {

            @Override
            public void onBindViewHolder(CategoryTraitViewHolder holder, int position) {
                holder.bindTo();

                //get the label for this position
                BrAPIScaleValidValuesCategories pair = cats.get(position);

                //update button with the preference based text
                if (shouldDisplayValues()) {

                    holder.mButton.setText(pair.getValue());

                } else {

                    holder.mButton.setText(pair.getLabel());

                }

                //set the buttons tag to the json, when clicked this is updated in db
                holder.mButton.setTag(pair);
                holder.mButton.setOnClickListener(createClickListener(holder.mButton, position));

                //update the button's state if this category is selected
                String currentText = getCollectInputView().getText();

                if (shouldDisplayValues()) {

                    if (currentText.equals(pair.getValue())) {

                        pressOnButton(holder.mButton);

                    } else pressOffButton(holder.mButton);

                } else {

                    if (currentText.equals(pair.getLabel())) {

                        pressOnButton(holder.mButton);

                    } else pressOffButton(holder.mButton);
                }
            }

            @Override
            public int getItemCount() {
                return cats.size();
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
        RecyclerView.Adapter<?> adapter = gridMultiCat.getAdapter();
        if (adapter != null) {
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    private OnClickListener createClickListener(final Button button, int position) {
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

                updateObservation(getCurrentTrait(),
                        CategoryJsonUtil.Companion.encode(scale));

                triggerTts(category);

                refreshLayout(false);
            }
        };
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
        loadLayout();
    }

    @Override
    public String decodeValue(String value) {
        ArrayList<BrAPIScaleValidValuesCategories> scale = CategoryJsonUtil.Companion.decode(value);
        if (!scale.isEmpty()) {
            if (shouldDisplayValues()) {
                return scale.get(0).getValue();
            } else return scale.get(0).getLabel();
        } else return "";
    }

    @NonNull
    @Override
    public Boolean validate(String data) {

        try {
            ArrayList<BrAPIScaleValidValuesCategories> userChosenCats = CategoryJsonUtil.Companion.decode(data);
            if (userChosenCats.isEmpty()) {
                return true;
            } else {
                ArrayList<BrAPIScaleValidValuesCategories> cats = getCategories();
                return CategoryJsonUtil.Companion.contains(cats, userChosenCats.get(0));
            }
        } catch (Exception e) {
            return false;
        }
    }
}
