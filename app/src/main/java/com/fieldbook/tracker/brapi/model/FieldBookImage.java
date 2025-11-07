package com.fieldbook.tracker.brapi.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;
import androidx.exifinterface.media.ExifInterface;

import com.fieldbook.tracker.utilities.DocumentTreeUtil;
import com.fieldbook.tracker.utilities.FileUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.client.model.GeoJSON;

public class FieldBookImage extends BrapiObservation {

    private static final String TAG = FieldBookImage.class.getSimpleName();
    private DocumentFile file;
    private int width;
    private int height;
    private long fileSize;
    private String fileName;
    private String imageName;
    private String mimeType;
    private String rep;
    private Bitmap missing;
    private GeoJSON location;
    private Map<String, String> additionalInfo;
    private byte[] bytes;

    private List<String> descriptiveOntologyTerms;
    private String description;

    public FieldBookImage() {

    }

    public FieldBookImage(Context ctx, String filePath, String traitName, Bitmap missingPhoto) {

        String sanitizedTraitName = FileUtil.sanitizeFileName(traitName);
        DocumentFile photosDir = DocumentTreeUtil.Companion.getFieldMediaDirectory(ctx, sanitizedTraitName);

        this.mimeType = "image/jpeg";
        this.missing = missingPhoto;
        this.location = new GeoJSON();
        this.additionalInfo = new HashMap<>();

        if (photosDir != null) {

            Uri filePathUri = Uri.parse(filePath);
            this.file = DocumentFile.fromSingleUri(ctx, filePathUri);

            if (this.file != null) {

                this.fileSize = file.length();
                this.fileName = file.getName();
                this.imageName = this.fileName;

                Log.d(TAG, "Instantiated fb image: " + imageName + " " + fileSize);

            } else {

                Log.d(TAG, "Failed to find file: " + filePath);

            }

        } else {

            Log.d(TAG, "Failed to find photos dir");

        }
    }

    public FieldBookImage(io.swagger.client.model.Image response) {
        this.setDbId(response.getImageDbId());
        this.setUnitDbId(response.getObservationUnitDbId());
        this.fileName = response.getImageFileName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldBookImage that = (FieldBookImage) o;
        return objectsEquals(getUnitDbId(), that.getUnitDbId()) &&
                objectsEquals(fileName, that.getFileName());
    }

    @Override
    public int hashCode() {
        return objectsHash(getUnitDbId(), fileName);//, timestamp);
    }

    public DocumentFile getFile() { return file; }

    public void setFile(DocumentFile file) {
        this.file = file;
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

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setMissing(Bitmap missing) {
        this.missing = missing;
    }

    public void setLocation(GeoJSON location) {
        this.location = location;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public void setRep(String rep) {
        this.rep = rep;
    }

    public String getRep() {
        return this.rep;
    }

    public String getImageName() {
        return imageName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getCopyright() {
        if(getTimestamp() != null) {
            return String.valueOf(getTimestamp().getYear());
        }
        return null;
    }

    public Map<String, String> getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(Map<String, String> additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    public byte[] getImageData() {
        return bytes;
    }

    public List<String> getDescriptiveOntologyTerms() {
        return this.descriptiveOntologyTerms;
    }

    public void setDescriptiveOntologyTerms(List<String> descriptiveOntologyTerms) {
        this.descriptiveOntologyTerms = descriptiveOntologyTerms;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    private void loadMissingImage() {

        Log.d(TAG, "Loading missing image for: " + imageName);

        width = missing.getWidth();
        height = missing.getHeight();
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            missing.compress(Bitmap.CompressFormat.JPEG, 95, stream);
            bytes = stream.toByteArray();
            fileSize = bytes.length;
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    public void loadImage(Context ctx) {

        if (file == null || file.length() == 0) {

            loadMissingImage();

        } else {

            try (InputStream is = ctx.getContentResolver().openInputStream(this.file.getUri())) {

                int byteLength = (int) file.length();

                Log.d(TAG, "Stream opened for: " + imageName + " bytes: " + byteLength);

                Bitmap bitmap = BitmapFactory.decodeStream(is);

                width = bitmap.getWidth();
                height = bitmap.getHeight();
                bytes = new byte[(int) file.length()];

                ExifInterface exif = new ExifInterface(is);
                double[] latlon = exif.getLatLong();
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

                //stream must be reopened after BitmapFactory.decodeStream
                //could also use mark/reset but it doesn't always work
                is.close();

                try (InputStream s = ctx.getContentResolver().openInputStream(this.file.getUri())) {

                    int r = s.read(bytes);

                    Log.d(TAG, "Read: " + r + " bytes from stream.");

                } catch (Exception e) {

                    e.printStackTrace();

                }

            } catch (Exception e) {

                e.printStackTrace();

                loadMissingImage();
            }
        }

        mimeType = "image/jpeg";
    }
}