package com.fieldbook.tracker.brapi;

import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.DateTimeParseException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Date;

import io.swagger.client.model.NewObservationDbIdsObservations;

public class Observation {

    private String collector;
    private String brapiDbId;
    private String fieldbookDbId;
    private String unitDbId;
    private String variableDbId;
    private OffsetDateTime timestamp;
    private String variableName;
    private String season;
    private String studyId;
    private String dbId;
    private String value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Observation that = (Observation) o;
        return Objects.equals(collector, that.collector) &&
                Objects.equals(brapiDbId, that.brapiDbId) &&
                Objects.equals(fieldbookDbId, that.fieldbookDbId) &&
                Objects.equals(unitDbId, that.unitDbId) &&
                Objects.equals(variableDbId, that.variableDbId) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(variableName, that.variableName) &&
                Objects.equals(season, that.season) &&
                Objects.equals(studyId, that.studyId) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collector, brapiDbId, fieldbookDbId, unitDbId, variableDbId, timestamp, variableName, season, studyId, value);
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
        try {
            //TODO: locale
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ");
            this.timestamp = OffsetDateTime.parse(timestamp, formatter);
        } catch (DateTimeParseException e) {
            e.printStackTrace();
        }
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
