package com.fieldbook.tracker.objects;

import android.util.Log;

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
    private String realPosition;
    private String id;
    private Boolean visible;
    private String external_db_id;
    private String trait_data_source;

    public String getTrait() {
        return trait;
    }

    public void setTrait(String trait) {
        this.trait = trait;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
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

    public String getRealPosition() {
        return realPosition;
    }

    public void setRealPosition(String realPosition) {
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
        return external_db_id;
    }

    public void setExternalDbId(String externalDbId) {
        this.external_db_id = externalDbId;
    }

    public String getTraitDataSource() {
        return trait_data_source;
    }

    public void setTraitDataSource(String traitDataSource) {
        this.trait_data_source = traitDataSource;
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