package com.fieldbook.tracker.traits;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.google.android.flexbox.FlexboxLayoutManager;

import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;

class CategoryTraitViewHolder extends RecyclerView.ViewHolder {

    Button mButton;

    CategoryTraitViewHolder(View itemView) {
        super(itemView);
        mButton = (Button) itemView.findViewById(R.id.multicatButton);
    }

    void bindTo(BrAPIScaleValidValuesCategories category) {
        mButton.setTag(category);
        ViewGroup.LayoutParams lp = mButton.getLayoutParams();
        if (lp instanceof FlexboxLayoutManager.LayoutParams) {
            FlexboxLayoutManager.LayoutParams flexboxLp = (FlexboxLayoutManager.LayoutParams) lp;
            flexboxLp.setFlexGrow(1.0f);
        }
    }
}