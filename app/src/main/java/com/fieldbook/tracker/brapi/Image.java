package com.fieldbook.tracker.brapi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.List;

public class Image extends BrapiObservation {

    private String fieldBookDbId;
    private String filePath;
    private int width;
    private int height;
    private int fileSize;
    private String fileName;
    private String imageName;
    private String mimeType;
    private Object data;
    private Bitmap bitmap;
    private String observationUnitDbId;
    private List<String> descriptiveOntologyTerms;
    private String description;

    public Image(String filePath) {
        this.filePath = filePath;
        this.fileName = filePath.substring(filePath.lastIndexOf("/")+1);
        this.imageName = this.fileName;
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

    public String getObservationUnitDbId() {
        return observationUnitDbId;
    }

    public void setObservationUnitDbId(String observationUnitDbId) {
        this.observationUnitDbId = observationUnitDbId;
    }

    public List<String> getDescriptiveOntologyTerms() {
        return this.descriptiveOntologyTerms;
    }

    public void setDescriptiveOntologyTerms(List<String> descriptiveOntologyTerms) {
        this.descriptiveOntologyTerms = descriptiveOntologyTerms;
    }

    public String getDescription(){ return this.description; }

    public void setDescription(String description) {
        this.description = description;
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
