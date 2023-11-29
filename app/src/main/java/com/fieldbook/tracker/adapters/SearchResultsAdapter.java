package com.fieldbook.tracker.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.objects.SearchData;

import java.util.ArrayList;

/**
 * Loads data on search screen
 */
public class SearchResultsAdapter extends BaseAdapter {

    private LayoutInflater mLayoutInflater;
    private SearchData[] data;
    private ArrayList<ArrayList<String>> traitData;
    private int number_of_traits;

    public SearchResultsAdapter(Context context, SearchData[] data, ArrayList<ArrayList<String>> traitData) {
        this.data = data;
        mLayoutInflater = LayoutInflater.from(context);
        this.traitData = traitData;
        this.number_of_traits = traitData.get(0).size();
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
            holder.itemContainer = convertView.findViewById(R.id.search_results_list_parent);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
            holder.itemContainer.removeAllViews();
        }

//        Log.d("MyApp", position + ": " + getItem(position).range +  ", " +getItem(position).plot);
        holder.range.setText(getItem(position).range);
        holder.plot.setText(getItem(position).plot);
        ArrayList<String> itemData = traitData.get(position);
        for (int i = 0; i < number_of_traits; i++) {
            View v = mLayoutInflater.inflate(R.layout.listitem_search_results_traits, null);
            TextView textView = v.findViewById(R.id.trait_value);
            textView.setText(itemData.get(i));
//            Log.d("MyApp", itemData.get(i));
            holder.itemContainer.addView(textView);
        }

        return convertView;
    }

    private class ViewHolder {
        TextView range;
        TextView plot;
        LinearLayout itemContainer;
    }
}