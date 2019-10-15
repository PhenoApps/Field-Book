package com.fieldbook.tracker.brapi;

import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.DateTimeParseException;

import java.util.Objects;
import io.swagger.client.model.NewObservationDbIdsObservations;

public class Observation {

    private String collector;
    private String brapiDbId;
    private String fieldbookDbId;
    private String unitDbId;
    private String variableDbId;
    private OffsetDateTime timestamp;
    private OffsetDateTime lastSyncedTime;
    private String variableName;
    private String season;
    private String studyId;
    private String dbId;
    private String value;

    public enum Status {
        NEW, SYNCED, EDITED, INVALID
    }

    public Observation() {
    }

    public Observation(NewObservationDbIdsObservations response) {
        this.dbId = response.getObservationDbId();
        this.unitDbId = response.getObservationUnitDbId();
        this.variableDbId = response.getObservationVariableDbId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Observation that = (Observation) o;
        return objectsEquals(unitDbId, that.unitDbId) &&
               objectsEquals(variableDbId, that.variableDbId);
    }

    @Override
    public int hashCode() {
        return objectsHash(unitDbId, variableDbId);
    }

    // The objects methods used were added in API 19 so they'll just
    // be duplicated here to work around that
    
    // Objects.equals Jdk7
    private boolean objectsEquals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    // Objects.hash Jdk7
    private int objectsHash(Object... values) {
        return arraysHashCode(values);
    }

    // Objects.hashCode Jdk7
    private int arraysHashCode(Object a[]) {
        if (a == null)
            return 0;

        int result = 1;

        for (Object element : a)
            result = 31 * result + (element == null ? 0 : element.hashCode());

        return result;
    }

    public Status getStatus() {

        Status status = Status.INVALID;

        if (dbId == null) {
            status = Status.NEW;
        }
        else if (dbId != null && lastSyncedTime != null && timestamp.compareTo(lastSyncedTime) < 0) {
            status = Status.SYNCED;
        }
        else if (dbId != null && lastSyncedTime != null && timestamp.compareTo(lastSyncedTime) > 0) {
            status = Status.EDITED;
        }

        return status;
    }

    public String getFieldbookDbId() {
        return fieldbookDbId;
    }

    public void setFieldbookDbId(String fieldbookDbId) {
        this.fieldbookDbId = fieldbookDbId;
    }

    public String getUnitDbId() {
        return unitDbId;
    }

    public void setUnitDbId(String unitDbId) {
        this.unitDbId = unitDbId;
    }

    public String getVariableDbId() {
        return variableDbId;
    }

    public void setVariableDbId(String variableDbId) {
        this.variableDbId = variableDbId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = convertTime(timestamp);
    }

    public void setLastSyncedTime(String timestamp) {
        this.lastSyncedTime = convertTime(timestamp);
    }

    private OffsetDateTime convertTime(String time) {
        OffsetDateTime converted = null;
        try {
            //TODO: locale
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ");
            converted = OffsetDateTime.parse(time, formatter);
        } catch (DateTimeParseException e) {
            e.printStackTrace();
        }
        finally {
            return converted;
        }

    }

    public OffsetDateTime getLastSyncedTime() {
        return lastSyncedTime;
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }


    public String getCollector() {
        return collector;
    }

    public void setCollector(String collector) {
        this.collector = collector;
    }

    public String getBrapiDbId() {
        return brapiDbId;
    }

    public void setBrapiDbId(String brapiDbId) {
        this.brapiDbId = brapiDbId;
    }

    public String getDbId() {
        return dbId;
    }

    public void setDbId(String dbId) {
        this.dbId = dbId;
    }

}
