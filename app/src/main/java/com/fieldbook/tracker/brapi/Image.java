package com.fieldbook.tracker.brapi;

import android.graphics.BitmapFactory;

public class Image {

    private String filePath;
    private int width;
    private int height;
    private int fileSize;
    private String fileName;
    private String mimeType;

    public Image(String filePath) {
        this.filePath = filePath;
        loadImage();
    }

    private void loadImage() {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(filePath, bmOptions);
        width = bmOptions.outWidth;
        height = bmOptions.outHeight;
        mimeType = bmOptions.outMimeType;
    }

}
