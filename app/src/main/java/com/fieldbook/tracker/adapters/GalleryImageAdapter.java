package com.fieldbook.tracker.adapters;

import java.util.List;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class GalleryImageAdapter extends BaseAdapter {

    private static ImageView imageView;
    private Activity context;
    private List<Drawable> plotsImages;

    public GalleryImageAdapter(Activity context, List<Drawable> plotsImages) {
        this.context = context;
        this.plotsImages = plotsImages;
    }

    @Override
    public int getCount() {
        return plotsImages.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            imageView = new ImageView(this.context);
            imageView.setPadding(3, 3, 3, 3);
            convertView = imageView;
            holder.imageView = imageView;
            convertView.setTag(holder);

        } else {

            holder = (ViewHolder) convertView.getTag();
        }

        holder.imageView.setImageDrawable(plotsImages.get(position));
        holder.imageView.setScaleType(ScaleType.FIT_CENTER);
        holder.imageView.setLayoutParams(new Gallery.LayoutParams(270, 450));

        return imageView;
    }

    private static class ViewHolder {
        ImageView imageView;
    }
}