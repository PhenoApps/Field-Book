package com.fieldbook.tracker.brapi;

import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.DateTimeParseException;

import java.util.Objects;

public class BrapiObservation {

    private String dbId;
    private OffsetDateTime lastSyncedTime;
    private OffsetDateTime timestamp;
    private String fieldbookDbId;
    private String unitDbId;
    private String variableDbId;
    private String variableName;

    public enum Status {
        NEW, SYNCED, EDITED, INVALID
    }

    public String getFieldbookDbId() {
        return fieldbookDbId;
    }

    public void setFieldbookDbId(String fieldbookDbId) {
        this.fieldbookDbId = fieldbookDbId;
    }

    public String getDbId() {
        return dbId;
    }

    public void setDbId(String id) {
        this.dbId = id;
    }

    public void setLastSyncedTime(String timestamp) {
        this.lastSyncedTime = convertTime(timestamp);
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

    public BrapiObservation.Status getStatus() {

        BrapiObservation.Status status = BrapiObservation.Status.INVALID;

        if (dbId == null) {
            status = BrapiObservation.Status.NEW;
        }
        else if (dbId != null && lastSyncedTime != null && timestamp.compareTo(lastSyncedTime) < 0) {
            status = BrapiObservation.Status.SYNCED;
        }
        else if (dbId != null && lastSyncedTime != null && timestamp.compareTo(lastSyncedTime) > 0) {
            status = BrapiObservation.Status.EDITED;
        }

        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Observation that = (Observation) o;
        return  Objects.equals(unitDbId, that.getUnitDbId()) &&
                Objects.equals(variableDbId, that.getVariableDbId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(unitDbId, variableDbId);
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

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

}
