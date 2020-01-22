package com.fieldbook.tracker.traitLayouts;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.utilities.ExpandableHeightGridView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MultiCatTraitLayout extends TraitLayout {

    private ExpandableHeightGridView gridMultiCat;

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
    public void setNaTraitsText() { }
    @Override
    public String type() { return "multicat"; }
    
    @Override
    public void init(){
        gridMultiCat = findViewById(R.id.catGrid);
        gridMultiCat.setExpanded(true);
    }
    
    @Override
    public void loadLayout(){
		final String trait = getCurrentTrait().getTrait();
        getEtCurVal().setHint("");
        getEtCurVal().setVisibility(EditText.VISIBLE);

        if (!getNewTraits().containsKey(trait)) {
            getEtCurVal().removeTextChangedListener(getCvNum());
            getEtCurVal().setText("");
            getEtCurVal().setTextColor(Color.BLACK);
            getEtCurVal().addTextChangedListener(getCvNum());
        } else {
            getEtCurVal().removeTextChangedListener(getCvNum());
            getEtCurVal().setText(getNewTraits().get(trait).toString());
            getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
            getEtCurVal().addTextChangedListener(getCvNum());
        }

        final String[] cat = getCurrentTrait().getCategories().split("/");

        if(!((MainActivity) getContext()).isDataLocked()) {
            gridMultiCat.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return cat.length;
                }

                @Override
                public Object getItem(int position) {
                    return null;
                }

                @Override
                public long getItemId(int position) {
                    return 0;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    final Button newButton = (Button) LayoutInflater.from(getContext()).inflate(R.layout.custom_button_multicat, null);
                    newButton.setText(cat[position]);
                    newButton.setOnClickListener(createClickListener(newButton, position));
                    if (hasCategory(cat[position], getEtCurVal().getText().toString()))
                    	pressOnButton(newButton);
                    return newButton;
                }
            });
        }

        gridMultiCat.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                gridMultiCat.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                View lastChild = gridMultiCat.getChildAt(gridMultiCat.getChildCount() - 1);
                gridMultiCat.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, lastChild.getBottom()));
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
				}
				else {
					pressOnButton(button);
					addCategory(category);
				}
                updateTrait(getCurrentTrait().getTrait(),
                			getCurrentTrait().getFormat(),
                			getEtCurVal().getText().toString());
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
    	button.setBackgroundColor(getResources().getColor(R.color.button_pressed));
	}
	
	private void pressOffButton(Button button) {
    	button.setTextColor(Color.BLACK);
    	button.setBackgroundColor(getResources().getColor(R.color.button_normal));
	}
	
	private void addCategory(final String category) {
		final String currentValue = getEtCurVal().getText().toString();
		if (currentValue.length() > 0) {
			getEtCurVal().setText(currentValue + ":" + category);
		}
		else {
			getEtCurVal().setText(category);
		}
	}
    
    private void removeCategory(final String category) {
		final String[] categories = getCategoryList();
		ArrayList<String>	newCategories = new ArrayList<>();
		for (final String cat : categories) {
			if (!cat.equals(category))
				newCategories.add(cat);
		}
		
		if (newCategories.isEmpty()) {
			getEtCurVal().setText("");
		}
		else {
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
		((MainActivity) getContext()).removeTrait();
    }
}
