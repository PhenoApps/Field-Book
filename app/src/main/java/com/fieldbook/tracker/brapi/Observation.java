package com.fieldbook.tracker.brapi;

import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.DateTimeParseException;

import java.util.Objects;

import io.swagger.client.model.NewObservationDbIdsObservations;

public class Observation extends BrapiObservation {

    private String collector;

    private String season;
    private String studyId;
    private String value;

    public Observation() {
    }

    public Observation(NewObservationDbIdsObservations response) {
        this.setDbId(response.getObservationDbId());
        this.setUnitDbId(response.getObservationUnitDbId());
        this.setVariableDbId(response.getObservationVariableDbId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Observation that = (Observation) o;
        return super.objectsEquals(unitDbId, that.getUnitDbId()) &&
                objectsEquals(variableDbId, that.getVariableDbId());
    }

    @Override
    public int hashCode() {
        return super.objectsHash(unitDbId, variableDbId);
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


}
