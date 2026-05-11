package com.fieldbook.tracker.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
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
        LayoutInflater inflater = activity.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list_buttonless, null);

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

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AppAlertDialog);
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

        alertDialog.setOnShowListener(dialogInterface -> {
            android.view.WindowManager.LayoutParams params = alertDialog.getWindow().getAttributes();
            params.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            alertDialog.getWindow().setAttributes(params);
        });

        alertDialog.show();
    }
}
