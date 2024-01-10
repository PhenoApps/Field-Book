package com.fieldbook.tracker.adapters;

import android.content.Context;
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

    private final LayoutInflater mLayoutInflater;
    private final SearchData[] data;
    private final ArrayList<ArrayList<String>> traitData;
    private final int numberOfTraits;

    public SearchResultsAdapter(Context context, SearchData[] data, ArrayList<ArrayList<String>> traitData) {
        mLayoutInflater = LayoutInflater.from(context);
        this.data = data;
        this.traitData = traitData;
        this.numberOfTraits = traitData.get(0).size();
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
            holder.itemContainer = convertView.findViewById(R.id.search_results_list_parent);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
            holder.itemContainer.removeAllViews();
        }

        createAndAddTextView(holder, getItem(position).range);
        createAndAddTextView(holder, getItem(position).plot);

        ArrayList<String> itemData = traitData.get(position);
        for (int i = 0; i < numberOfTraits; i++) {
            createAndAddTextView(holder, itemData.get(i));
        }

        return convertView;
    }

    private class ViewHolder {
        LinearLayout itemContainer;
    }

    /**
     * Creates a text view with the respective plot, row or trait information and adds it to the parent container
     */
    private void createAndAddTextView(ViewHolder holder, String text) {

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, // width
                LinearLayout.LayoutParams.WRAP_CONTENT, // height
                1f // weight
        );

        View v = mLayoutInflater.inflate(R.layout.listitem_search_results_traits, null);
        TextView textView = v.findViewById(R.id.trait_value);
        textView.setText(text);
        textView.setLayoutParams(layoutParams);
        holder.itemContainer.addView(textView);
    }
}