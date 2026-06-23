package com.fieldbook.tracker.dialogs;

import com.fieldbook.tracker.utilities.ThemedAlertDialog;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
import com.google.android.material.color.MaterialColors;

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
        android.content.Context dialogContext = ThemedAlertDialog.contentContext(activity);
        int surfaceColor = MaterialColors.getColor(dialogContext, R.attr.fb_color_background, 0);

        LinearLayout layout = new LinearLayout(dialogContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);
        if (surfaceColor != 0) {
            layout.setBackgroundColor(surfaceColor);
        }

        ListView listView = new ListView(dialogContext);
        if (surfaceColor != 0) {
            listView.setBackgroundColor(surfaceColor);
        }
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(listView);

        ListAddAdapter adapter = new ListAddAdapter(dialogContext, items, icons);
        listView.setAdapter(adapter);

        AlertDialog.Builder builder = ThemedAlertDialog.builder(activity);
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

        ThemedAlertDialog.chainOnShow(dialog, activity, dialogInterface -> {
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(params);
        });

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
        private final android.content.Context dialogContext;
        private final String[] values;
        private final int[] icons;

        public ListAddAdapter(android.content.Context dialogContext, String[] values, int[] icons) {
            super(dialogContext, R.layout.list_item_dialog_with_icon, values);
            this.dialogContext = dialogContext;
            this.values = values;
            this.icons = icons;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = android.view.LayoutInflater.from(dialogContext)
                        .inflate(R.layout.list_item_dialog_with_icon, parent, false);
            }

            ImageView imageView = convertView.findViewById(R.id.icon);
            TextView textView = convertView.findViewById(R.id.spinnerTarget);

            textView.setText(values[position]);
            imageView.setImageResource(icons[position]);
            tintListItem(dialogContext, imageView, textView);

            return convertView;
        }
    }

    private static void tintListItem(android.content.Context dialogContext, ImageView imageView, TextView textView) {
        int iconTint = MaterialColors.getColor(dialogContext, R.attr.fb_icon_tint, 0);
        Drawable drawable = imageView.getDrawable();
        if (drawable != null && iconTint != 0) {
            drawable = DrawableCompat.wrap(drawable.mutate());
            DrawableCompat.setTint(drawable, iconTint);
            imageView.setImageDrawable(drawable);
        }
        int textColor = MaterialColors.getColor(dialogContext, android.R.attr.textColorPrimary, 0);
        if (textColor != 0) {
            textView.setTextColor(textColor);
        }
    }
}
