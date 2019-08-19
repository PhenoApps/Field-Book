package com.fieldbook.tracker.brapi;

import com.fieldbook.tracker.traits.TraitObject;

import java.util.List;

public class StudyDetails {
    private String studyDbId;
    private String studyName;
    private String studyDescription;
    private String studyLocation;
    private String commonCropName;
    private Integer numberOfPlots;
    private List<String> attributes;
    private List<List<String>> values;
    private List<TraitObject> traits;

    public static void merge(StudyDetails sd1, StudyDetails sd2){
        if(sd2.getStudyDbId() != null)
            sd1.setStudyDbId(sd2.getStudyDbId());
        if(sd2.getStudyName() != null)
            sd1.setStudyName(sd2.getStudyName());
        if(sd2.getStudyDescription() != null)
            sd1.setStudyDescription(sd2.getStudyDescription());
        if(sd2.getStudyLocation() != null)
            sd1.setStudyLocation(sd2.getStudyLocation());
        if(sd2.getCommonCropName() != null)
            sd1.setCommonCropName(sd2.getCommonCropName());
        if(sd2.getNumberOfPlots() != null)
            sd1.setNumberOfPlots(sd2.getNumberOfPlots());
        if(sd2.getAttributes() != null)
            sd1.setAttributes(sd2.getAttributes());
        if(sd2.getValues() != null)
            sd1.setValues(sd2.getValues());
        if(sd2.getTraits() != null)
            sd1.setTraits(sd2.getTraits());
    }

    public String getCommonCropName() {
        return commonCropName;
    }

    public void setCommonCropName(String commonCropName) {
        this.commonCropName = commonCropName;
    }

    public List<TraitObject> getTraits() {
        return traits;
    }

    public void setTraits(List<TraitObject> traits) {
        this.traits = traits;
    }

    public String getStudyDbId() {
        return studyDbId;
    }

    public void setStudyDbId(String studyDbId) {
        this.studyDbId = studyDbId;
    }

    public String getStudyName() {
        return studyName;
    }

    public void setStudyName(String studyName) {
        this.studyName = studyName;
    }

    public String getStudyDescription() {
        return studyDescription;
    }

    public void setStudyDescription(String studyDescription) {
        this.studyDescription = studyDescription;
    }

    public String getStudyLocation() {
        return studyLocation;
    }

    public void setStudyLocation(String studyLocation) {
        this.studyLocation = studyLocation;
    }

    public Integer getNumberOfPlots() {
        return numberOfPlots;
    }

    public void setNumberOfPlots(Integer numberOfPlots) {
        this.numberOfPlots = numberOfPlots;
    }

    public List<String> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<String> attributes) {
        this.attributes = attributes;
    }

    public List<List<String>> getValues() {
        return values;
    }

    public void setValues(List<List<String>> values) {
        this.values = values;
    }
}
