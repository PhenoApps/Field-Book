package com.fieldbook.tracker.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fieldbook.tracker.R;


public class OperatorAdapter extends ArrayAdapter<String> {
    Context context;
    String[] operatorText;
    int[] operatorImage;

    public OperatorAdapter(@NonNull Context context, String[] operatorText, int[] operatorImage) {
        super(context, R.layout.list_item_operator, operatorText);
        this.context = context;
        this.operatorText = operatorText;
        this.operatorImage = operatorImage;
    }

    @NonNull
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.list_item_operator, parent, false);

            holder = new ViewHolder();
            holder.imageView = convertView.findViewById(R.id.list_item_operator_image);
            holder.textView = convertView.findViewById(R.id.list_item_operator_text);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String currentItem = getItem(position);

        if (currentItem != null) {
            holder.imageView.setImageResource(operatorImage[position]);
            holder.textView.setText(operatorText[position]);
        }

        return convertView;
    }

    static class ViewHolder {
        ImageView imageView;
        TextView textView;
    }
}
