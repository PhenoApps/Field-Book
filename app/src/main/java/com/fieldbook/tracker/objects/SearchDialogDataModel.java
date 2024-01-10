package com.fieldbook.tracker.objects;

public class SearchDialogDataModel {

    private String attribute;
    private int imageResourceId;
    private String text;

    public SearchDialogDataModel(String attribute, int imageResourceId, String text) {
        this.attribute = attribute;
        this.imageResourceId = imageResourceId;
        this.text = text;
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

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
