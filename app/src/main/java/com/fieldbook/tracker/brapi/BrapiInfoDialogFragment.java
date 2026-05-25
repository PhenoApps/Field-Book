package com.fieldbook.tracker.brapi;

import com.fieldbook.tracker.utilities.ThemedAlertDialog;
import com.fieldbook.tracker.utilities.AppThemeResolver;
import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.fieldbook.tracker.R;

public class BrapiInfoDialogFragment extends DialogFragment {
    private String bodyMessage;

    public BrapiInfoDialogFragment newInstance(String bodyMessage) {
        this.bodyMessage = bodyMessage;
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AlertDialog.Builder builder = ThemedAlertDialog.builder(requireContext());
        builder.setTitle(requireContext().getString(R.string.brapi_info_title));
        builder.setMessage(bodyMessage);
        builder.setNegativeButton(requireContext().getString(R.string.dialog_close), (dialog, which) -> dialog.dismiss());

        return builder.create();
    }
}