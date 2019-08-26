package com.fieldbook.tracker.traitLayouts;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.text.TextWatcher;
import android.util.AttributeSet;
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
    public void init(){
        gridMultiCat = findViewById(R.id.catGrid);
        gridMultiCat.setExpanded(true);
    }
    
    @Override
    public void loadLayout(){

        getEtCurVal().setVisibility(EditText.VISIBLE);

        if (!getNewTraits().containsKey(getCurrentTrait().getTrait())) {
            getEtCurVal().removeTextChangedListener(getCvNum());
            getEtCurVal().setText("");
            getEtCurVal().setTextColor(Color.BLACK);
            getEtCurVal().addTextChangedListener(getCvNum());
        } else {
            getEtCurVal().removeTextChangedListener(getCvNum());
            getEtCurVal().setText(getNewTraits().get(getCurrentTrait().getTrait()).toString());
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
                    newButton.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (getEtCurVal().length() > 0) {
                                getEtCurVal().setText(getEtCurVal().getText().toString() + ":" + newButton.getText().toString());
                            } else {
                                getEtCurVal().setText(newButton.getText().toString());
                            }
                        }
                    });

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
    @Override
    public void deleteTraitListener() {

    }
}
