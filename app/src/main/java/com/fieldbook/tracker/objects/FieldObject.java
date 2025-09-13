package com.fieldbook.tracker.objects;

import java.util.List;


/**
 * Simple wrapper class for field data
 */
public class FieldObject {
    private int studyId;
    private String studyDbId;
    private String name;
    private String alias;
    private String uniqueId;
    private String primaryId;
    private String secondaryId;
    private String layout;
    private String species;
    private String sortColumnsStringArray;
    private String dateImport;
    private String dateEdit;
    private String dateExport;
    private String dateSync;
    private String entryCount;
    private String dataSource;
    private ImportFormat dataSourceFormat;
    private String observationLevel;
    private String attributeCount;
    private String traitCount;
    private String observationCount;
    private String trialName;
    private String searchAttribute;
    private Integer groupId;
    private boolean isArchived;
    private String startCorner;
    private String walkingDirection;
    private String walkingPattern;

    public String getTrialName() {
        return trialName;
    }

    public void setTrialName(String trialName) {
        this.trialName = trialName;
    }

    public static class TraitDetail {
        private final String traitName;
        private final String format;
        private String categories;
        private final int count;
        private final List<String> observations;
        private final float completeness;  // Add this line

        public TraitDetail(String traitName, String format, String categories, int count, List<String> observations, float completeness) {
            this.traitName = traitName;
            this.format = format;
            this.categories = categories;
            this.count = count;
            this.observations = observations;
            this.completeness = completeness;  // Add this line
        }

        // Getters
        public String getTraitName() { return traitName; }
        public String getFormat() { return format; }
        public String getCategories() {
            return categories;
        }
        public int getCount() { return count; }
        public List<String> getObservations() { return observations; }
        public float getCompleteness() { return completeness; }  // Add this line
    }

    private List<TraitDetail> traitDetails;
    public List<TraitDetail> getTraitDetails() {
        return traitDetails;
    }

    public void setTraitDetails(List<TraitDetail> traitDetails) {
        this.traitDetails = traitDetails;
    }

    public FieldObject() {
    }

    public int getStudyId() {
        return studyId;
    }

    public void setStudyId(int studyId) {
        this.studyId = studyId;
    }

    public String getStudyDbId() {
        return studyDbId;
    }

    public void setStudyDbId(String studyDbId) {
        this.studyDbId = studyDbId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getPrimaryId() {
        return primaryId;
    }

    public void setPrimaryId(String primaryId) {
        this.primaryId = primaryId;
    }

    public String getSecondaryId() {
        return secondaryId;
    }

    public void setSecondaryId(String secondaryId) {
        this.secondaryId = secondaryId;
    }

    public String getLayout() {
        return layout;
    }

    public void setLayout(String layout) {
        this.layout = layout;
    }

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public String getSortColumnsStringArray() {
        return sortColumnsStringArray;
    }

    public void setSortColumnsStringArray(String sortColumnsStringArray) {
        this.sortColumnsStringArray = sortColumnsStringArray;
    }

    public String getDateImport() {
        return dateImport;
    }

    public void setDateImport(String dateImport) {
        this.dateImport = dateImport;
    }

    public String getDateEdit() {
        return dateEdit;
    }

    public void setDateEdit(String dateEdit) {
        this.dateEdit = dateEdit;
    }

    public String getDateExport() {
        return dateExport;
    }

    public void setDateExport(String dateExport) {
        this.dateExport = dateExport;
    }

    public String getDateSync() {
        return dateSync;
    }

    public void setDateSync(String dateSync) {
        this.dateSync = dateSync;
    }

    public String getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(String entryCount) {
        this.entryCount = entryCount;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public ImportFormat getDataSourceFormat() {
        return dataSourceFormat;
    }

    public void setDataSourceFormat(ImportFormat dataSourceFormat) {
        this.dataSourceFormat = dataSourceFormat;
    }

    public String getObservationLevel() {
        return observationLevel;
    }

    public void setObservationLevel(String observationLevel) {
        this.observationLevel = observationLevel;
    }
    public String getAttributeCount() {
        return attributeCount;
    }

    public void setAttributeCount(String attributeCount) {
        this.attributeCount = attributeCount;
    }
    public String getTraitCount() {
        return traitCount;
    }

    public void setTraitCount(String traitCount) {
        this.traitCount = traitCount;
    }
    public String getObservationCount() {
        return observationCount;
    }

    public void setObservationCount(String observationCount) {
        this.observationCount = observationCount;
    }
    public String getSearchAttribute() {
        return searchAttribute;
    }

    public void setSearchAttribute(String searchAttribute) {
        this.searchAttribute = searchAttribute;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public boolean getArchived() {
        return isArchived;
    }

    public void setArchived(boolean isArchived) {
        this.isArchived = isArchived;
    }

    public String getStartCorner() {
        return startCorner;
    }

    public void setStartCorner(String startCorner) {
        this.startCorner = startCorner;
    }

    public String getWalkingDirection() {
        return walkingDirection;
    }

    public void setWalkingDirection(String walkingDirection) {
        this.walkingDirection = walkingDirection;
    }

    public String getWalkingPattern() {
        return walkingPattern;
    }

    public void setWalkingPattern(String walkingPattern) {
        this.walkingPattern = walkingPattern;
    }
}