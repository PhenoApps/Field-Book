package com.fieldbook.tracker;

import android.app.Activity;
import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

/**
 * The difference between this and the basic Adapter, is that it throws key presses
 * back up to the calling Activity. This allows items to be clickable when help is activated
 */
public class GenericSpinnerAdapter extends ArrayAdapter<String> implements SpinnerAdapter {

    Context context; 
    int layoutResourceId;
    int dropdownResourceId;
    String data[] = null;
    OnItemSelectedListener listener;
    
    public GenericSpinnerAdapter(Context context, int layoutResourceId, int dropdownResourceId, String[] data, OnItemSelectedListener listener) {
        super(context, layoutResourceId, data);
        this.layoutResourceId = layoutResourceId;
        this.dropdownResourceId = dropdownResourceId;
        this.context = context;
        this.data = data;
        this.listener = listener;
    }
    
    @Override
    public View getDropDownView(final int position, View convertView, final ViewGroup parent) {
        View row = convertView;
        Holder holder = null;
        
        if(row == null)
        {
            LayoutInflater inflater = ((Activity)context).getLayoutInflater();
            row = inflater.inflate(dropdownResourceId, parent, false);
            
            holder = new Holder();
            holder.txt = (TextView)row.findViewById(R.id.spinnerTarget);
                        
            row.setTag(holder);
        }
        else
        {
            holder = (Holder)row.getTag();
        }
        
        holder.txt.setText(data[position]);
    
        // The spinner will no longer operate the way it originally does
        // Which is why we need to add our own listener to mimic key presses
        // to close it
        row.setOnClickListener(new ItemOnClickListener(parent, position));
        
        return row;    
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
            holder.txt = (TextView)row.findViewById(R.id.spinnerTarget);
            
            holder.txt.setOnClickListener(new OnClickListener(){

				public void onClick(View v) {
					// This is necessary for controls such as the listview or spinner to work 
					// with the tips / hints when it is visible					
					((Spinner) parent).performClick();				
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
        TextView txt;
    }
    
    class ItemOnClickListener implements View.OnClickListener {
        private View _parent;
        private int position;
        
        public ItemOnClickListener(ViewGroup parent, int position) {
            _parent = parent;
            this.position = position;
        }

        public void onClick(View view) {
            // close the dropdown
            View root = _parent.getRootView();
            root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
            root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
            
            listener.onItemSelected((AdapterView) _parent, view, position, view.getId());
        }
    }    
}