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
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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

        if (!getNewTraits().containsKey(trait)) {
            getEtCurVal().setText("");
            getEtCurVal().setTextColor(Color.BLACK);
        } else {
            getEtCurVal().setText(getNewTraits().get(trait).toString());
            getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
        }

        //read the preferences, default to displaying values instead of labels
        String labelValPref = getPrefs().getString(GeneralKeys.LABELVAL_CUSTOMIZE,"value");

        //read the json object stored in additional info of the trait object (only in BrAPI imported traits)
        Gson g = new Gson();
        String[] buttonCat;
        String additionalInfo = getCurrentTrait().getAdditionalInfo();

        if (additionalInfo.equals("")){
            //If imported before additional info was added, use original retrieval from categories
            buttonCat = getCurrentTrait().getCategories().split("/");
        } else { //otherwise decode the json object and populate the buttons with the value or label
            JsonObject catObject = g.fromJson(additionalInfo,JsonObject.class);
            JsonArray catValueLabel = catObject.getAsJsonArray("catValueLabel");
            buttonCat = new String[catValueLabel.size()];
            JsonObject valueLabelRow;
            for (int i=0; i<catValueLabel.size();i++){
                valueLabelRow = g.fromJson(catValueLabel.get(i), JsonObject.class);
                buttonCat[i] = valueLabelRow.get(labelValPref).getAsString();
            }
        }

        final String[] cat = buttonCat;

        FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(getContext());
        layoutManager.setFlexWrap(FlexWrap.WRAP);
        layoutManager.setFlexDirection(FlexDirection.ROW);
        layoutManager.setAlignItems(AlignItems.STRETCH);
        gridMultiCat.setLayoutManager(layoutManager);

        if (!((CollectActivity) getContext()).isDataLocked()) {

            gridMultiCat.setAdapter(new CategoricalTraitAdapter(getContext()) {

                @Override
                public void onBindViewHolder(CategoricalTraitViewHolder holder, int position) {
                    holder.bindTo();
                    holder.mButton.setText(cat[position]);
                    holder.mButton.setOnClickListener(createClickListener(holder.mButton,position));
                    if (hasCategory(cat[position], getEtCurVal().getText().toString()))
                        pressOnButton(holder.mButton);
                }

                @Override
                public int getItemCount() {
                    return cat.length;
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
                final String normalizedCategory = normalizeCategory();
                getEtCurVal().setText(normalizedCategory);
                final String category = button.getText().toString();

                if (hasCategory(category, normalizedCategory)) {
                    pressOffButton(button);
                    removeCategory(category);
                } else {
                    pressOnButton(button);
                    addCategory(category);
                }

                String[] catList = getCategoryList();

                List<String> list = new ArrayList<String>(Arrays.asList(catList));
                list.remove(category);
                catList = list.toArray(new String[0]);

                for(String cat : catList) {
                    removeCategory(cat);
                }

                updateTrait(getCurrentTrait().getTrait(),
                        getCurrentTrait().getFormat(),
                        getEtCurVal().getText().toString());

                loadLayout(); //todo this is not the best way to do this
            }
        };
    }

    private boolean existsCategory(final String category) {
        final String[] cats = getCurrentTrait().getCategories().split("/");
        for (String cat : cats) {
            if (cat.equals(category))
                return true;
        }
        return false;
    }

    // if there are wrong categories, remove them
    // I want to remove them when moveing the page,
    // but it is not so easy
    private String normalizeCategory() {
        final String[] categories = getCategoryList();
        ArrayList<String> normalizedCategoryList = new ArrayList<>();
        HashSet<String> appeared = new HashSet<>();
        for (String category : categories) {
            if (!appeared.contains(category) && existsCategory(category)) {
                normalizedCategoryList.add(category);
                appeared.add(category);
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

    private boolean hasCategory(final String category, final String categories) {
        final String[] categoryArray = categories.split(":");
        for (final String cat : categoryArray) {
            if (cat.equals(category))
                return true;
        }
        return false;
    }

    public String[] getCategoryList() {
        return getEtCurVal().getText().toString().split(":");
    }

    private void pressOnButton(Button button) {
        button.setTextColor(Color.parseColor(getDisplayColor()));
        button.getBackground().setColorFilter(button.getContext().getResources().getColor(R.color.button_pressed), PorterDuff.Mode.MULTIPLY);
    }

    private void pressOffButton(Button button) {
        button.setTextColor(Color.BLACK);
        button.getBackground().setColorFilter(button.getContext().getResources().getColor(R.color.button_normal), PorterDuff.Mode.MULTIPLY);
    }

    private void addCategory(final String category) {
        final String currentValue = getEtCurVal().getText().toString();
        getEtCurVal().setText(category);
    }

    private void removeCategory(final String category) {
        final String[] categories = getCategoryList();
        ArrayList<String> newCategories = new ArrayList<>();
        for (final String cat : categories) {
            if (!cat.equals(category))
                newCategories.add(cat);
        }

        if (newCategories.isEmpty()) {
            getEtCurVal().setText("");
        } else {
            // String#join does not work
            String newValue = newCategories.get(0);
            for (int i = 1; i < newCategories.size(); ++i) {
                newValue += ":";
                newValue += newCategories.get(i);
            }
            getEtCurVal().setText(newValue);
        }
    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
        loadLayout();
    }
}

class CategoricalTraitAdapter extends RecyclerView.Adapter<CategoricalTraitViewHolder> {

    private Context mContext;

    CategoricalTraitAdapter(Context context) {
        mContext = context;
    }

    @Override
    public CategoricalTraitViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.trait_multicat_button, parent, false);
        return new CategoricalTraitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(CategoricalTraitViewHolder holder, int position) {

    }

    @Override
    public int getItemCount() {
        return 0;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }
}

class CategoricalTraitViewHolder extends RecyclerView.ViewHolder {

    Button mButton;

    CategoricalTraitViewHolder(View itemView) {
        super(itemView);
        mButton = (Button) itemView.findViewById(R.id.multicatButton);
    }

    void bindTo() {
        ViewGroup.LayoutParams lp = mButton.getLayoutParams();
        if (lp instanceof FlexboxLayoutManager.LayoutParams) {
            FlexboxLayoutManager.LayoutParams flexboxLp = (FlexboxLayoutManager.LayoutParams) lp;
            flexboxLp.setFlexGrow(1.0f);
        }
    }
}
