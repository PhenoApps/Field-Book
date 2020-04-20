package com.fieldbook.tracker.utilities;

import android.widget.Button;

import androidx.appcompat.app.AlertDialog;

public class DialogUtils {

    public static void styleDialogs(AlertDialog dialog) {
        setButtonSize(dialog);
    }

    public static void setButtonSize(AlertDialog dialog) {
        int buttonSize = 19;

        Button buttonPos =  dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        buttonPos.setTextSize(buttonSize);
        Button buttonNeg =  dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        buttonNeg.setTextSize(buttonSize);
        Button buttonNeu =  dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        buttonNeu.setTextSize(buttonSize);
    }
}
