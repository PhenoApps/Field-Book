package com.fieldbook.tracker.brapi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.exifinterface.media.ExifInterface;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import io.swagger.client.model.GeoJSON;

public class Image extends BrapiObservation {

    private File file;
    private int width;
    private int height;
    private long fileSize;
    private String fileName;
    private String imageName;
    private String mimeType;
    private Bitmap missing;
    private GeoJSON location;
    private Map<String, String> additionalInfo;
    private byte[] bytes;

    private List<String> descriptiveOntologyTerms;
    private String description;

    public Image(String filePath, Bitmap missingPhoto) {

        this.file = new File(filePath);
        this.fileSize = file.length();
        this.fileName = file.getName();
        this.imageName = this.fileName;
        this.missing = missingPhoto;
        this.location = new GeoJSON();
        this.additionalInfo = new HashMap<>();
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
        return objectsEquals(unitDbId, that.getUnitDbId()) &&
                objectsEquals(fileName, that.getFileName());
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

    long getFileSize() {
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

    String getCopyright() {
        return String.valueOf(timestamp.getYear());
    }

    Map<String, String> getAdditionalInfo() {
        return additionalInfo;
    }

    byte[] getImageData() {
        return bytes;
    }

    List<String> getDescriptiveOntologyTerms() {
        return this.descriptiveOntologyTerms;
    }

    public void setDescriptiveOntologyTerms(List<String> descriptiveOntologyTerms) {
        this.descriptiveOntologyTerms = descriptiveOntologyTerms;
    }

    String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void loadImage() {
        Bitmap bitmap;
        if (!file.exists()) {
            bitmap = missing;
            width = missing.getWidth();
            height = missing.getHeight();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream);
            bytes = stream.toByteArray();
            fileSize = bytes.length;
        } else {
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), bmOptions);
            width = bmOptions.outWidth;
            height = bmOptions.outHeight;
            bytes = new byte[(int) file.length()];

            try {
                ExifInterface exif = new ExifInterface(file.getAbsolutePath());
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

                FileInputStream fin = new FileInputStream(file);
                fin.read(bytes);
            } catch (IOException e) {
            }
        }

        mimeType = "image/jpeg";
    }
}