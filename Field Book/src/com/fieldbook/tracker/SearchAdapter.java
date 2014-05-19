package com.fieldbook.tracker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

/**
 * Loads data on search screen
 */
public class SearchAdapter extends BaseAdapter {

    LayoutInflater mLayoutInflater;
    SearchData[] data;

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
        }
        else {
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
            convertView = mLayoutInflater.inflate(R.layout.twocolumn, null);
            holder.range = (TextView) convertView.findViewById(R.id.range);
            holder.plot = (TextView) convertView.findViewById(R.id.plot);
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.range.setText(getItem(position).range);
        holder.plot.setText(getItem(position).plot);

        return convertView;
    }

}
