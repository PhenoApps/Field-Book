package com.fieldbook.tracker.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.fieldbook.tracker.R;

import java.util.Map;

public class ListSortDialog {

    private Activity activity;
    private Map<String, String> sortOptions;
    private OnSortOptionSelectedListener listener;

    public interface OnSortOptionSelectedListener {
        void onSortOptionSelected(String criteria);
    }

    public ListSortDialog(Activity activity, Map<String, String> sortOptions, OnSortOptionSelectedListener listener) {
        this.activity = activity;
        this.sortOptions = sortOptions;
        this.listener = listener;
    }

    public void show() {
        LayoutInflater inflater = activity.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_list_buttonless, null);

        ListView myList = layout.findViewById(R.id.myList);
        String[] displayOptions = sortOptions.keySet().toArray(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, R.layout.list_item_dialog_list, displayOptions);
        myList.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AppAlertDialog);
        builder.setTitle(R.string.dialog_sort_by)
                .setCancelable(true)
                .setView(layout)
                .setNegativeButton(activity.getString(R.string.dialog_cancel), (dialog, which) -> dialog.dismiss());

        final AlertDialog alertDialog = builder.create();

        myList.setOnItemClickListener((parent, view, position, id) -> {
            String displayOption = displayOptions[position];
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
