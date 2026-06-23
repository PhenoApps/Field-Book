package com.fieldbook.tracker.dialogs;

import com.fieldbook.tracker.utilities.ThemedAlertDialog;
import com.fieldbook.tracker.utilities.AppThemeResolver;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.fieldbook.tracker.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListSortDialog {

    private Activity activity;
    private Map<String, String> sortOptions;
    private String currentSortOrder;
    private String defaultSortOrder;
    private OnSortOptionSelectedListener listener;

    public interface OnSortOptionSelectedListener {
        void onSortOptionSelected(String criteria);
    }

    public ListSortDialog(Activity activity, Map<String, String> sortOptions, String currentSortOrder, String defaultSortOrder, OnSortOptionSelectedListener listener) {
        this.activity = activity;
        this.sortOptions = sortOptions;
        this.currentSortOrder = currentSortOrder;
        this.defaultSortOrder = defaultSortOrder;
        this.listener = listener;
    }

    public void show() {
        View layout = ThemedAlertDialog.inflate(activity, R.layout.dialog_list_buttonless);

        ListView myList = layout.findViewById(R.id.myList);
        List<String> displayOptions = new ArrayList<>(sortOptions.keySet());
        TypedValue fbTraitButtonBackgroundTintValue = new TypedValue();
        activity.getTheme().resolveAttribute(R.attr.fb_trait_button_background_tint, fbTraitButtonBackgroundTintValue, true);
        int backgroundColor = fbTraitButtonBackgroundTintValue.data;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, R.layout.list_item_dialog_list, R.id.spinnerTarget, displayOptions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(R.id.spinnerTarget);

                // Highlight the current sort order
                String columnName = sortOptions.get(displayOptions.get(position));
                if (columnName != null && columnName.equals(currentSortOrder)) {
                    textView.setBackgroundColor(backgroundColor);
                } else {
                    textView.setBackgroundColor(activity.getResources().getColor(android.R.color.transparent));
                }

                return view;
            }
        };
        myList.setAdapter(adapter);

        AlertDialog.Builder builder = ThemedAlertDialog.builder(activity);
        builder.setTitle(R.string.dialog_sort_by)
                .setCancelable(true)
                .setView(layout)
                .setNegativeButton(activity.getString(R.string.dialog_cancel), (dialog, which) -> dialog.dismiss())
                .setNeutralButton(activity.getString(R.string.fields_sort_reset), (dialog, which) -> {
                    listener.onSortOptionSelected(defaultSortOrder);
                });

        final AlertDialog alertDialog = builder.create();

        myList.setOnItemClickListener((parent, view, position, id) -> {
            String displayOption = displayOptions.get(position);
            String columnName = sortOptions.get(displayOption);
            if (columnName != null) {
                listener.onSortOptionSelected(columnName);
            } else {
                Log.e("ListSortDialog", "Unknown sorting option selected: " + displayOption);
            }
            alertDialog.dismiss();
        });

        ThemedAlertDialog.chainOnShow(alertDialog, activity, dialogInterface -> {
            android.view.WindowManager.LayoutParams params = alertDialog.getWindow().getAttributes();
            params.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            alertDialog.getWindow().setAttributes(params);
        });

        alertDialog.show();
    }
}
