package com.fieldbook.tracker.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.adapters.OperatorAdapter;


public class OperatorDialog extends AlertDialog {
    Context context;
    private OnOperatorClickedListener listener;
    private int adapter_position;

    public OperatorDialog(Context context, OnOperatorClickedListener listener, int adapter_position) {
        super(context, R.style.AppAlertDialog);
        this.context = context;
        this.listener = listener;
        this.adapter_position = adapter_position;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View customView = getLayoutInflater().inflate(R.layout.dialog_operator, null);
        setContentView(customView);
        setTitle("Select an operator");

        String[] operatorText = new String[6];

        operatorText[0] = context.getString(R.string.search_dialog_query_is_equal_to);
        operatorText[1] = context.getString(R.string.search_dialog_query_is_not_equal_to);
        operatorText[2] = context.getString(R.string.search_dialog_query_contains);
        operatorText[3] = context.getString(R.string.search_dialog_query_does_not_contain);
        operatorText[4] = context.getString(R.string.search_dialog_query_is_more_than);
        operatorText[5] = context.getString(R.string.search_dialog_query_is_less_than);

        int[] operatorImage = new int[6];

        operatorImage[0] = R.drawable.ic_tb_equal;
        operatorImage[1] = R.drawable.ic_tb_not_equal;
        operatorImage[2] = R.drawable.ic_tb_contains;
        operatorImage[3] = R.drawable.ic_tb_does_not_contain;
        operatorImage[4] = R.drawable.ic_tb_greater_than;
        operatorImage[5] = R.drawable.ic_tb_less_than;

        OperatorAdapter adapter = new OperatorAdapter(context, operatorText, operatorImage);

        ListView lv = customView.findViewById(R.id.dialog_operator_rv);
        lv.setAdapter(adapter);

        lv.setOnItemClickListener((parent, view, position, id) -> {
            listener.onOperatorSelected(adapter_position, operatorImage[position]);
            dismiss();
        });

        Button cancelButton = customView.findViewById(R.id.dialog_operator_cancel_btn);
        cancelButton.setOnClickListener(arg0 -> dismiss());

    }

    public interface OnOperatorClickedListener {
        void onOperatorSelected(int adapter_position, int imageId);
    }
}
