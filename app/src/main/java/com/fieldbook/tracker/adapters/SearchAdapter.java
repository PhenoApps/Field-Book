package com.fieldbook.tracker.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.objects.SearchData;

/**
 * Loads data on search screen
 */
public class SearchAdapter extends BaseAdapter {

    private LayoutInflater mLayoutInflater;
    private SearchData[] data;

    public SearchAdapter(Context context, SearchData[] data) {
        this.data = data;
        mLayoutInflater = LayoutInflater.from(context);
    }

    public int getCount() {
        return data.length;
    }

    public SearchData getItem(int position) {
        if (position >= 0 && position < data.length) {
            return data[position];
        } else {
            return null;
        }
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mLayoutInflater.inflate(R.layout.listitem_search_results, null);
            holder.range = convertView.findViewById(R.id.range);
            holder.plot = convertView.findViewById(R.id.plot);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.range.setText(getItem(position).range);
        holder.plot.setText(getItem(position).plot);

        return convertView;
    }

    private class ViewHolder {
        TextView range;
        TextView plot;
    }
}