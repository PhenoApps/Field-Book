package com.fieldbook.tracker.objects;

import java.util.List;

public class StatisticObject {
    private String statTitle;
    private String statValue;
    private int statIconId;
    private int isToast;
    private String dialogTitle;
    private List<String> dialogData;
    private String toastMessage;

    /**
     * Container for individual stats within the statistics card
     * @param statTitle Title of the statistic
     * @param statValue Value of the statistic
     * @param statIconId Resource ID for the icon of the statistic
     * @param isToast 1: if a toast message is required when clicked, 0: if a dialog is required when clicked
     * @param dialogTitle Title of the dialog; empty string if isToast = 1
     * @param dialogData List to be displayed within the dialog; null string if isToast = 1
     * @param toastMessage Message to be displayed in the toast; empty string if isToast = 0
     */
    public StatisticObject(String statTitle, String statValue, int statIconId, int isToast, String dialogTitle, List<String> dialogData, String toastMessage) {
        this.statTitle = statTitle;
        this.statValue = statValue;
        this.statIconId = statIconId;
        this.isToast = isToast;
        this.dialogTitle = dialogTitle;
        this.dialogData = dialogData;
        this.toastMessage = toastMessage;
    }

    public String getStatTitle() {
        return statTitle;
    }

    public String getStatValue() {
        return statValue;
    }

    public int getStatIconId() {
        return statIconId;
    }

    public int getIsToast() {
        return isToast;
    }

    public String getDialogTitle() {
        return dialogTitle;
    }

    public List<String> getDialogData() {
        return dialogData;
    }

    public String getToastMessage() {
        return toastMessage;
    }
}
