package com.fieldbook.tracker.fields;

/**
 * Simple wrapper class for field data
 */
public class FieldObject {
    public int exp_id;
    public String exp_name;
    public String exp_alias;
    public String unique_id = "observationUnitDbId";
    public String primary_id = "X";
    public String secondary_id = "Y";
    public String exp_layout;
    public String exp_species;
    public String exp_sort = "PlotNumber";
    public String date_import;
    public String date_edit;
    public String date_export;
    public String count;

    public FieldObject() {}

    public FieldObject(String exp_name) {
        this.exp_name = exp_name;
    }
}
