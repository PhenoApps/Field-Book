package com.fieldbook.tracker.brapi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.util.List;

public class Image extends BrapiObservation {

    private File file;
    private int width;
    private int height;
    private long fileSize;
    private String fileName;
    private String imageName;
    private String mimeType;
    private Object data;
    private Bitmap bitmap;
    private List<String> descriptiveOntologyTerms;
    private String description;

    public Image(String filePath) {
        this.file = new File(filePath);
        this.fileSize = file.length();
        this.fileName = file.getName();
        this.imageName = this.fileName;

        loadImage();
    }

    public Image(io.swagger.client.model.Image response) {
        this.setDbId(response.getImageDbId());
        this.setUnitDbId(response.getObservationUnitDbId());
        this.fileName = response.getImageFileName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Image that = (Image) o;
        return super.objectsEquals(unitDbId, that.getUnitDbId()) &&
                objectsEquals(fileName, that.getFileName());
    }

    @Override
    public int hashCode() {
        return super.objectsHash(unitDbId, fileName);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFileName() {
        return fileName;
    }

    public String getImageName() {
        return imageName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public File getFile() {
        return file;
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
        //bmOptions.inJustDecodeBounds = true;

        bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        width = bitmap.getWidth();
        height = bitmap.getHeight();
        //mimeType = bmOptions.outMimeType;
    }

}
