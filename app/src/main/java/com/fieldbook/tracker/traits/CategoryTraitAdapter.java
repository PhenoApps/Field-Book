package com.fieldbook.tracker.traits;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;

class CategoryTraitAdapter extends RecyclerView.Adapter<CategoryTraitViewHolder> {

    private Context mContext;

    CategoryTraitAdapter(Context context) {
        mContext = context;
    }

    @NonNull
    @Override
    public CategoryTraitViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.trait_category_button, parent, false);
        return new CategoryTraitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(CategoryTraitViewHolder holder, int position) {

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