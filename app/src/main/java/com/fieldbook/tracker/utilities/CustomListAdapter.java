package com.fieldbook.tracker.utilities;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.fieldbook.tracker.R;

public class CustomListAdapter extends ArrayAdapter<String> {
    String[] color_names;
    Integer[] image_id;
    Context context;

    public CustomListAdapter(Activity context, Integer[] image_id, String[] text) {
        super(context, R.layout.listitem_language, text);
        this.color_names = text;
        this.image_id = image_id;
        this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View single_row = inflater.inflate(R.layout.listitem_language, null, true);
        TextView textView = single_row.findViewById(R.id.txt);
        ImageView imageView = single_row.findViewById(R.id.img);
        textView.setText(color_names[position]);
        imageView.setImageResource(image_id[position]);
        return single_row;
    }
}