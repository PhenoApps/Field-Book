package com.fieldbook.tracker;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

/**
 * The difference between this and the basic Adapter, is that it throws key presses
 * back up to the calling Activity. This allows items to be clickable when help is activated
 */
public class GenericCheckedArrayAdapter extends ArrayAdapter<String> {

    Context context;
    int layoutResourceId;
    String data[] = null;
    OnItemClickListener listener;

    public GenericCheckedArrayAdapter(Context context, int layoutResourceId, String[] data, OnItemClickListener listener) {
        super(context, layoutResourceId, data);
        this.layoutResourceId = layoutResourceId;
        this.context = context;
        this.data = data;

        this.listener = listener;
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        View row = convertView;
        Holder holder = null;

        if(row == null)
        {
            LayoutInflater inflater = ((Activity)context).getLayoutInflater();
            row = inflater.inflate(layoutResourceId, parent, false);

            holder = new Holder();
            holder.txt = (CheckedTextView)row.findViewById(R.id.spinnerTarget);

            holder.txt.setOnClickListener(new OnClickListener(){

                public void onClick(View v) {

                    CheckedTextView tv = (CheckedTextView) v;
                    tv.setChecked(!tv.isChecked());

                    ListView rateList = (ListView) parent;

                    rateList.setItemChecked(position, tv.isChecked());

                    if (listener != null)
                    {
                        // This is necessary for controls such as the listview or spinner to work
                        // with the tips / hints when it is visible
                        listener.onItemClick((AdapterView) parent, v, position, v.getId());
                    }
                }
            });

            row.setTag(holder);
        }
        else
        {
            holder = (Holder)row.getTag();
        }

        holder.txt.setText(data[position]);

        return row;
    }

    static class Holder
    {
        CheckedTextView txt;
    }
}