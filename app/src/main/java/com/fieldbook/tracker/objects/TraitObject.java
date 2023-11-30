package com.fieldbook.tracker.objects;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Simple wrapper class for trait data
 */
public class TraitObject {
    private String name;
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

    /**
     * This is a BMS specific field. This will be populated when traits are imported from
     * the BMS implementation of Brapi 2.0 GET /variables.
     */
    private List<String> observationLevelNames;

    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
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

    public List<String> getObservationLevelNames() {
        return observationLevelNames;
    }

    public void setObservationLevelNames(List<String> observationLevelNames) {
        this.observationLevelNames = observationLevelNames;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraitObject that = (TraitObject) o;
        return realPosition == that.realPosition && Objects.equals(name, that.name) && Objects.equals(format, that.format) && Objects.equals(defaultValue, that.defaultValue) && Objects.equals(minimum, that.minimum) && Objects.equals(maximum, that.maximum) && Objects.equals(details, that.details) && Objects.equals(categories, that.categories) && Objects.equals(id, that.id) && Objects.equals(visible, that.visible) && Objects.equals(externalDbId, that.externalDbId) && Objects.equals(traitDataSource, that.traitDataSource) && Objects.equals(additionalInfo, that.additionalInfo) && Objects.equals(observationLevelNames, that.observationLevelNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, format, defaultValue, minimum, maximum, details, categories, realPosition, id, visible, externalDbId, traitDataSource, additionalInfo, observationLevelNames);
    }
}