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

    private Context context;
    private String bodyMessage;
    private Button closeBtn;
    private TextView bodyTextView;

    public BrapiInfoDialog(@NonNull Context context, String bodyMessage) {

        super(context);
        this.context = context;
        this.bodyMessage = bodyMessage;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.dialog_info);
        closeBtn = findViewById(R.id.closeBtn);
        bodyTextView = findViewById(R.id.body_message);

        // Set our body text
        bodyTextView.setText(bodyMessage);

        closeBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {

        switch (view.getId()) {
            case R.id.closeBtn:
                dismiss();
        }
    }
}
