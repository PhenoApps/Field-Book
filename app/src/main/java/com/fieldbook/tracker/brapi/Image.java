package com.fieldbook.tracker.brapi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.exifinterface.media.ExifInterface;

import android.graphics.Matrix;
import android.os.Build;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import io.swagger.client.model.GeoJSON;

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
    private GeoJSON location;
    private ExifInterface exif;

    private List<String> descriptiveOntologyTerms;
    private String description;

    public Image(String filePath, Bitmap missingPhoto) {

        this.file = new File(filePath);
        this.fileSize = file.length();
        this.fileName = file.getName();
        this.imageName = this.fileName;
        this.missing = missingPhoto;
        this.location = new GeoJSON();
        try {
            exif = new ExifInterface(filePath);
            double latlon[] = exif.getLatLong();
            if (latlon != null) {
                double lat = latlon[0];
                double lon = latlon[1];
                location.setType(GeoJSON.TypeEnum.FEATURE);
                JsonObject o = new JsonObject();
                o.addProperty("type", "Point");
                JsonArray a = new JsonArray();
                a.add(lon);
                a.add(lat);
                o.add("coordinates", a);
                location.setGeometry(o);
            }
        } catch (IOException e) { }

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

    public GeoJSON getLocation() {
        return location;
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
        bitmap.compress(Bitmap.CompressFormat.JPEG, 99, stream);
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

        rotateImageIfNeeded();
    }

    private void rotateImageIfNeeded() {

        if (exif != null) {
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int angle = 0;

            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                angle = 90;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                angle = 180;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                angle = 270;
            }

            Matrix mat = new Matrix();
            mat.postRotate(angle);

            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mat, true);
        }
    }
}
