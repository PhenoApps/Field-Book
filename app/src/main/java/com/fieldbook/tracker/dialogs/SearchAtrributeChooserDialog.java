package com.fieldbook.tracker.dialogs;


import com.fieldbook.tracker.activities.CollectActivity;

public class SearchAtrributeChooserDialog extends CollectAttributeChooserDialog {
    private final OnAttributeClickedListener onAttributeClickedListener;


    public SearchAtrributeChooserDialog(CollectActivity activity, OnAttributeClickedListener listener) {
        super(activity);
        this.onAttributeClickedListener = listener;
    }

    @Override
    public void onAttributeClicked(String label, int position) {

        if (onAttributeClickedListener != null) {
            onAttributeClickedListener.onAttributeSelected(label);
        }
        dismiss();
    }

    public interface OnAttributeClickedListener {
        void onAttributeSelected(String selectedAttribute);
    }
}
