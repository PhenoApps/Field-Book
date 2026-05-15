package com.fieldbook.tracker.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.DialogFragment;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.utilities.AppThemeResolver;

public class ListAddDialog extends DialogFragment {

    private Activity activity;
    private String title;
    private String[] items;
    private int[] icons;
    private AdapterView.OnItemClickListener onItemClickListener;

    public ListAddDialog(Activity activity, String title, String[] items, int[] icons, AdapterView.OnItemClickListener onItemClickListener) {
        this.activity = activity;
        this.title = title;
        this.items = items;
        this.icons = icons;
        this.onItemClickListener = onItemClickListener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);
        TypedValue bgValue = new TypedValue();
        if (activity.getTheme().resolveAttribute(R.attr.fb_color_background, bgValue, true)) {
            layout.setBackgroundColor(bgValue.data);
        }

        ListView listView = new ListView(activity);
        if (activity.getTheme().resolveAttribute(R.attr.fb_color_background, bgValue, true)) {
            listView.setBackgroundColor(bgValue.data);
        }
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(listView);

        ListAddAdapter adapter = new ListAddAdapter(activity, items, icons);
        listView.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(
                activity, AppThemeResolver.alertDialogStyle(activity));
        builder.setTitle(title)
                .setCancelable(true)
                .setView(layout)
                .setPositiveButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                });

        final AlertDialog dialog = builder.create();
        dialog.show();

        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(params);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onItemClickListener.onItemClick(parent, view, position, id);
                dialog.dismiss();
            }
        });

        return dialog;
    }

    private class ListAddAdapter extends ArrayAdapter<String> {
        private final Activity context;
        private final String[] values;
        private final int[] icons;

        public ListAddAdapter(Activity context, String[] values, int[] icons) {
            super(context, R.layout.list_item_dialog_with_icon, values);
            this.context = context;
            this.values = values;
            this.icons = icons;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = context.getLayoutInflater().inflate(R.layout.list_item_dialog_with_icon, parent, false);
            }

            ImageView imageView = convertView.findViewById(R.id.icon);
            TextView textView = convertView.findViewById(R.id.spinnerTarget);

            textView.setText(values[position]);
            imageView.setImageResource(icons[position]);
            tintListItem(context, imageView, textView);

            return convertView;
        }
    }

    private static void tintListItem(Activity context, ImageView imageView, TextView textView) {
        TypedValue typedValue = new TypedValue();
        Drawable drawable = imageView.getDrawable();
        if (drawable != null
                && context.getTheme().resolveAttribute(R.attr.fb_icon_tint, typedValue, true)) {
            drawable = DrawableCompat.wrap(drawable.mutate());
            DrawableCompat.setTint(drawable, typedValue.data);
            imageView.setImageDrawable(drawable);
        }
        if (context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
            textView.setTextColor(typedValue.data);
        }
    }
}

