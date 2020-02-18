package com.fieldbook.tracker.brapi;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fieldbook.tracker.R;

public class BrapiInfoDialog extends Dialog implements android.view.View.OnClickListener {

    private String bodyMessage;

    public BrapiInfoDialog(@NonNull Context context, String bodyMessage) {
        super(context);
        this.bodyMessage = bodyMessage;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.dialog_info);
        Button closeBtn = findViewById(R.id.closeBtn);
        TextView bodyTextView = findViewById(R.id.body_message);

        // Set our body text
        bodyTextView.setText(bodyMessage);
        closeBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.closeBtn) {
            dismiss();
        }
    }
}