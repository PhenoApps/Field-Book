package com.fieldbook.tracker.traits;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;

class MultiCatTraitAdapter extends RecyclerView.Adapter<MultiCatTraitViewHolder> {

    private Context mContext;

    MultiCatTraitAdapter(Context context) {
        mContext = context;
    }

    @NonNull
    @Override
    public MultiCatTraitViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.trait_multicat_button, parent, false);
        return new MultiCatTraitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MultiCatTraitViewHolder holder, int position) {

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