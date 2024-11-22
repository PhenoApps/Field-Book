package com.fieldbook.tracker.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.fieldbook.tracker.R;

import java.util.List;

public class StatisticsListAdapter extends BaseAdapter {

    private List<String> data;
    private LayoutInflater inflater;

    public StatisticsListAdapter(Context context, List<String> data) {
        this.data = data;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public String getItem(int i) {
        return data.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder holder;

        if (view == null) {
            holder = new ViewHolder();
            view = inflater.inflate(R.layout.list_item_individual_statistics, null);
            holder.listItem = view.findViewById(R.id.list_item_individual_stats);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        holder.listItem.setText(getItem(i));

        return view;
    }

    private class ViewHolder {
        TextView listItem;
    }
}
