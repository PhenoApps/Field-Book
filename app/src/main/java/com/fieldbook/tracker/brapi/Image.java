package com.fieldbook.tracker.brapi;

import android.graphics.Bitmap;
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
    private Bitmap bitmap;
    private String observationUnitDbId;

    public Image(String filePath) {
        this.filePath = filePath;
        this.fileName = filePath.substring(filePath.lastIndexOf("/")+1);
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

    public void setDbId(String id) {
        this.dbId = id;
    }

    public String getObservationUnitDbId() {
        return observationUnitDbId;
    }

    public void setObservationUnitDbId(String observationUnitDbId) {
        this.observationUnitDbId = observationUnitDbId;
    }

    private void loadImage() {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        bitmap = BitmapFactory.decodeFile(filePath, bmOptions);
        width = bmOptions.outWidth;
        height = bmOptions.outHeight;
        mimeType = bmOptions.outMimeType;
    }

}
