package com.fieldbook.tracker.brapi;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity;


public class BrapiAuthDialogFragment extends DialogFragment {

    public BrapiAuthDialogFragment newInstance() {
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.AppAlertDialog);

        builder.setTitle(R.string.brapi_auth_needed_title);
        builder.setMessage(R.string.brapi_auth_needed);

        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss());

        builder.setPositiveButton(R.string.brapi_auth_btn, (dialog, which) -> {
            Intent intent = new Intent();
            intent.setClassName(requireContext(), BrapiAuthActivity.class.getName());
            requireContext().startActivity(intent);
            dismiss();
        });

        return builder.create();
    }
}