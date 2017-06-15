package com.fieldbook.tracker.search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.fieldbook.tracker.R;

/**
 * Loads data on search screen
 */
class SearchAdapter extends BaseAdapter {

    private LayoutInflater mLayoutInflater;
    private SearchData[] data;

    SearchAdapter(Context context, SearchData[] data) {
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

    private class ViewHolder {
        TextView range;
        TextView plot;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mLayoutInflater.inflate(R.layout.listitem_search_results, null);
            holder.range = (TextView) convertView.findViewById(R.id.range);
            holder.plot = (TextView) convertView.findViewById(R.id.plot);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.range.setText(getItem(position).range);
        holder.plot.setText(getItem(position).plot);

        return convertView;
    }

}
