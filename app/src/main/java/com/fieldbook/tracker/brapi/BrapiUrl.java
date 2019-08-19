package com.fieldbook.tracker.brapi;

/**
 * Created by jessica on 7/19/18.
 */

public class BrapiUrl {

    private final String basePath = "https://test-server.brapi.org/brapi/v1";
    private final  String header = "/studies/";
    private final String plotsTail = "/observationunits";
    private final String trailTail = "/observationVariables";
    private final String studiesURL = basePath + "/studies-search";

    //private final String experimentURL = basePath + "/studies/1001";
    //private final String plotsURL = basePath + "/studies/1001/observationunits";
    //private final String traitURL = basePath + "/studies/1001/observationVariables";
    //final static String traitFormatURL = basePath + "/variables/datatypes";

    //final String test = "https://test-server.brapi.org/brapi/v1/studies-search";
    //trait = "https://test-server.brapi.org/brapi/v1/studies/1001/observationVariables";
    private String studies;

    public void setStudies(String studies) {
        this.studies = studies;
    }

    public String getExperimentURL() {
        return basePath + header + studies;
    }

    public String getPlotsURL() {
        return basePath + header + studies + plotsTail;
    }

    public String getTraitURL() {
        return basePath + header + studies + trailTail;
    }

    public String getStudiesURL() {
        return studiesURL;
    }
}
