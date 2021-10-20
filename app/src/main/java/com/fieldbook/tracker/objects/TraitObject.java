package com.fieldbook.tracker.objects;

import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Simple wrapper class for trait data
 */
public class TraitObject {
    private String trait;
    private String format;
    private String defaultValue;
    private String minimum;
    private String maximum;
    private String details;
    private String categories;
    private int realPosition;
    private String id;
    private Boolean visible;
    private String externalDbId;
    private String traitDataSource;
    private String additionalInfo;

    public String getTrait() {
        return trait;
    }

    public void setTrait(@NonNull String trait) {
        this.trait = trait;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(@NonNull String format) {
        this.format = format;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getMinimum() {
        return minimum;
    }

    public void setMinimum(String minimum) {
        this.minimum = minimum;
    }

    public String getMaximum() {
        return maximum;
    }

    public void setMaximum(String maximum) {
        this.maximum = maximum;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public int getRealPosition() {
        return realPosition;
    }

    public void setRealPosition(int realPosition) {
        this.realPosition = realPosition;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    public String getExternalDbId() {
        return externalDbId;
    }

    public void setExternalDbId(String externalDbId) {
        this.externalDbId = externalDbId;
    }

    public String getTraitDataSource() {
        return traitDataSource;
    }

    public void setTraitDataSource(String traitDataSource) {
        this.traitDataSource = traitDataSource;
    }

    public String getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    public boolean isValidValue(final String s) {
        // this code is not perfect.
        // I think that it is necessary to check
        // the minimum and the maximum values
        return !isUnder(s) && !isOver(s);
    }

    public boolean isUnder(final String s) {
        if (!(format.equals("numeric")))
            return false;

        Log.d("FB",s);
        if (minimum.length() > 0) {     // minimum exists
            try {
                final double v = Double.parseDouble(s);
                final double lowerValue = Double.parseDouble(minimum);
                return v < lowerValue;
            } catch (NumberFormatException e) {
                return true;
            }
        } else {
            return false;
        }
    }

    public boolean isOver(final String s) {
        if (!(format.equals("numeric")))
            return false;

        Log.d("FB",s);
        if (maximum.length() > 0) {     // maximum exists
            try {
                final double v = Double.parseDouble(s);
                final double upperValue = Double.parseDouble(maximum);
                return v > upperValue;
            } catch (NumberFormatException e) {
                return true;
            }
        } else {
            return false;
        }
    }
}