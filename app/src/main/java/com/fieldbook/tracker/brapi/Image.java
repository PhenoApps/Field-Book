package com.fieldbook.tracker.brapi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
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
    private Bitmap missing;
    private List<String> descriptiveOntologyTerms;
    private String description;

    public Image(String filePath, Bitmap missingPhoto) {

        this.file = new File(filePath);
        this.fileSize = file.length();
        this.fileName = file.getName();
        this.imageName = this.fileName;
        this.missing = missingPhoto;
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
        return  objectsEquals(unitDbId, that.getUnitDbId()) &&
                objectsEquals(fileName, that.getFileName()); //&&
                //objectsEquals(timestamp, that.getTimestamp());
    }

    @Override
    public int hashCode() {
        return objectsHash(unitDbId, fileName);//, timestamp);
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

    public byte[] getBytes() {
        int size = byteSizeOf(bitmap);
        ByteBuffer buffer = ByteBuffer.allocate(size);
        bitmap.copyPixelsToBuffer(buffer);
        return buffer.array();
    }

    private int byteSizeOf(Bitmap data) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
            return data.getRowBytes() * data.getHeight();
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return data.getByteCount();
        } else {
            return data.getAllocationByteCount();
        }
    }

    public byte[] getImageData() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream);
        return stream.toByteArray();
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

    public void loadImage() {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        //bmOptions.inJustDecodeBounds = true;

        bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

        if (bitmap == null) {
            bitmap = missing;
        }

        if (bitmap != null) {
            width = bitmap.getWidth();
            height = bitmap.getHeight();
            mimeType = "image/jpeg";
        }
    }

}
