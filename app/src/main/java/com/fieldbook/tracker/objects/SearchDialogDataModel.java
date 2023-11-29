package com.fieldbook.tracker.objects;

public class SearchDialogDataModel {

    private String attribute;
    private int imageResourceId;
    private String editText;

    public SearchDialogDataModel(String attribute, int imageResourceId, String editText) {
        this.attribute = attribute;
        this.imageResourceId = imageResourceId;
        this.editText = editText;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public int getImageResourceId() {
        return imageResourceId;
    }

    public void setImageResourceId(int imageResourceId) {
        this.imageResourceId = imageResourceId;
    }

    public String getEditText() {
        return editText;
    }

    public void setEditText(String editText) {
        this.editText = editText;
    }
}
