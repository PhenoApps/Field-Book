package com.fieldbook.tracker.brapi;

import android.graphics.BitmapFactory;

public class Image {

    private String filePath;
    private int width;
    private int height;
    private int fileSize;
    private String fileName;
    private String mimeType;
    private Object data;
    private String dbId;

    public Image(String filePath) {
        this.filePath = filePath;
        loadImage();
    }

    public String getFilePath() {
        return filePath;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFileSize() {
        return fileSize;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Object getData() {
        return data;
    }

    public String getDbId() {
        return dbId;
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
