package com.fieldbook.tracker.brapi.model;

import io.swagger.client.model.NewObservationDbIdsObservations;

public class Observation extends BrapiObservation {

    private String collector;
    private String season;
    private String studyId;
    private String value;
    private String rep;

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
        return super.objectsEquals(getUnitDbId(), that.getUnitDbId()) &&
                objectsEquals(getVariableDbId(), that.getVariableDbId());
    }

    @Override
    public int hashCode() {
        return super.objectsHash(getUnitDbId(), getVariableDbId());
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

    public void setRep(String rep) { this.rep = rep; }

    public String getRep() { return this.rep; }

}