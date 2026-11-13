package com.fieldbook.tracker.brapi;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity;


public class BrapiAuthDialogFragment extends DialogFragment {

    public static final String TAG = "BrapiAuthDialogFragment";

    /**
     * Shows the auth dialog unless one is already in the fragment manager.
     * Several failed BrAPI calls can request the dialog back to back; show() commits
     * asynchronously, so showNow() is used to make the tag check see the previous request.
     */
    public static void showIfNotShowing(@NonNull FragmentManager fragmentManager) {
        if (fragmentManager.isStateSaved() || fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }
        new BrapiAuthDialogFragment().showNow(fragmentManager, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

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