package com.fieldbook.tracker.objects;

import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fieldbook.tracker.utilities.CategoryJsonUtil;

import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;

import java.util.ArrayList;
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
    private Boolean closeKeyboardOnOpen = false;
    private Boolean cropImage = false;

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



    public boolean isValidCategoricalValue(final String inputCategory) {

        //check if its the new json
        try {

            ArrayList<BrAPIScaleValidValuesCategories> c = CategoryJsonUtil.Companion.decode(inputCategory);

            if (!c.isEmpty()) {

                //get the value from the single-sized array
                BrAPIScaleValidValuesCategories labelVal = c.get(0);

                //check that this pair is a valid label/val pair in the category,
                //if it is then set the text based on the preference
                return CategoryJsonUtil.Companion.contains(c, labelVal);
            }

        } catch (Exception e) {

            e.printStackTrace(); //if it fails to decode, assume its an old string

//            if (CategoryJsonUtil.Companion.contains(cats, value)) {
//
//                getCollectInputView().setText(value);
//
//                getCollectInputView().setTextColor(Color.parseColor(getDisplayColor()));
//            }
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraitObject that = (TraitObject) o;
        return realPosition == that.realPosition && Objects.equals(name, that.name) && Objects.equals(format, that.format) && Objects.equals(defaultValue, that.defaultValue) && Objects.equals(minimum, that.minimum) && Objects.equals(maximum, that.maximum) && Objects.equals(details, that.details) && Objects.equals(categories, that.categories) && Objects.equals(id, that.id) && Objects.equals(visible, that.visible) && Objects.equals(externalDbId, that.externalDbId) && Objects.equals(traitDataSource, that.traitDataSource) && Objects.equals(additionalInfo, that.additionalInfo) && Objects.equals(observationLevelNames, that.observationLevelNames) && Objects.equals(closeKeyboardOnOpen, that.closeKeyboardOnOpen) && Objects.equals(cropImage, that.cropImage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, format, defaultValue, minimum, maximum, details, categories, realPosition, id, visible, externalDbId, traitDataSource, additionalInfo, observationLevelNames, closeKeyboardOnOpen, cropImage);
    }

    public TraitObject clone() {

        TraitObject t = new TraitObject();
        t.setName(this.name);
        t.setFormat(this.format);
        t.setDefaultValue(this.defaultValue);
        t.setMinimum(this.minimum);
        t.setMaximum(this.maximum);
        t.setDetails(this.details);
        t.setCategories(this.categories);
        t.setRealPosition(this.realPosition);
        t.setId(this.id);
        t.setVisible(this.visible);
        t.setExternalDbId(this.externalDbId);
        t.setTraitDataSource(this.traitDataSource);
        t.setAdditionalInfo(this.additionalInfo);
        t.setObservationLevelNames(this.observationLevelNames);
        t.setCloseKeyboardOnOpen(this.closeKeyboardOnOpen);
        t.setCropImage(this.cropImage);

        return t;
    }

    public Boolean getCloseKeyboardOnOpen() {
        return closeKeyboardOnOpen;
    }

    public void setCloseKeyboardOnOpen(Boolean closeKeyboardOnOpen) {
        this.closeKeyboardOnOpen = closeKeyboardOnOpen;
    }

    public Boolean getCropImage() {
        return cropImage;
    }

    public void setCropImage(Boolean cropImage) {
        this.cropImage = cropImage;
    }
}