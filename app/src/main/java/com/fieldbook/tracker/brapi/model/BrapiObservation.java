package com.fieldbook.tracker.brapi.model;

import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.DateTimeParseException;

public class BrapiObservation {

    private OffsetDateTime timestamp;
    private String unitDbId;
    private String variableDbId;
    private String dbId;
    private OffsetDateTime lastSyncedTime;
    private String fieldbookDbId;
    private String variableName;

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
            if (time != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZZZZZ");
                converted = OffsetDateTime.parse(time, formatter);
            }
        } catch (DateTimeParseException e) {
            e.printStackTrace();
        } finally {
            return converted;
        }

    }

    public OffsetDateTime getLastSyncedTime() {
        return lastSyncedTime;
    }

    public void setLastSyncedTime(String timestamp) {
        this.lastSyncedTime = convertTime(timestamp);
    }

    public void setLastSyncedTime(OffsetDateTime timestamp) {
        this.lastSyncedTime = timestamp;
    }

    public BrapiObservation.Status getStatus() {

        BrapiObservation.Status status = BrapiObservation.Status.INVALID;

        if (dbId == null) {
            status = BrapiObservation.Status.NEW;
        } else if (lastSyncedTime == null) {
            status = BrapiObservation.Status.INCOMPLETE;
        } else if (timestamp == null || timestamp.compareTo(lastSyncedTime) <= 0) {
            status = BrapiObservation.Status.SYNCED;
        } else if (timestamp != null && timestamp.compareTo(lastSyncedTime) > 0) {
            status = BrapiObservation.Status.EDITED;
        }

        return status;
    }

    // Objects.equals Jdk7
    boolean objectsEquals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    // The objects methods used were added in API 19 so they'll just
    // be duplicated here to work around that

    // Objects.hash Jdk7
    int objectsHash(Object... values) {
        return arraysHashCode(values);
    }

    // Objects.hashCode Jdk7
    private int arraysHashCode(Object[] a) {
        if (a == null)
            return 0;

        int result = 1;

        for (Object element : a)
            result = 31 * result + (element == null ? 0 : element.hashCode());

        return result;
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

    public String getFieldBookDbId() {
        return fieldbookDbId;
    }

    public void setFieldBookDbId(String fieldbookDbId) {
        this.fieldbookDbId = fieldbookDbId;
    }

    public enum Status {
        NEW, SYNCED, EDITED, INCOMPLETE, INVALID
    }
}
