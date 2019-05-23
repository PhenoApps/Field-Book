package com.fieldbook.tracker.fields;

/**
 * Simple wrapper class for field data
 */
public class FieldObject {
    private int exp_id;
    private String exp_name;
    private String exp_alias;
    private String unique_id;
    private String primary_id;
    private String secondary_id;
    private String exp_layout;
    private String exp_species;
    private String exp_sort;
    private String date_import;
    private String date_edit;
    private String date_export;
    private String count;

    public FieldObject() {}

    public int getExp_id() {
        return exp_id;
    }

    public void setExp_id(int exp_id) {
        this.exp_id = exp_id;
    }

    public String getExp_name() {
        return exp_name;
    }

    public void setExp_name(String exp_name) {
        this.exp_name = exp_name;
    }

    public String getExp_alias() {
        return exp_alias;
    }

    public void setExp_alias(String exp_alias) {
        this.exp_alias = exp_alias;
    }

    public String getUnique_id() {
        return unique_id;
    }

    public void setUnique_id(String unique_id) {
        this.unique_id = unique_id;
    }

    public String getPrimary_id() {
        return primary_id;
    }

    public void setPrimary_id(String primary_id) {
        this.primary_id = primary_id;
    }

    public String getSecondary_id() {
        return secondary_id;
    }

    public void setSecondary_id(String secondary_id) {
        this.secondary_id = secondary_id;
    }

    public String getExp_layout() {
        return exp_layout;
    }

    public void setExp_layout(String exp_layout) {
        this.exp_layout = exp_layout;
    }

    public String getExp_species() {
        return exp_species;
    }

    public void setExp_species(String exp_species) {
        this.exp_species = exp_species;
    }

    public String getExp_sort() {
        return exp_sort;
    }

    public void setExp_sort(String exp_sort) {
        this.exp_sort = exp_sort;
    }

    public String getDate_import() {
        return date_import;
    }

    public void setDate_import(String date_import) {
        this.date_import = date_import;
    }

    public String getDate_edit() {
        return date_edit;
    }

    public void setDate_edit(String date_edit) {
        this.date_edit = date_edit;
    }

    public String getDate_export() {
        return date_export;
    }

    public void setDate_export(String date_export) {
        this.date_export = date_export;
    }

    public String getCount() {
        return count;
    }

    public void setCount(String count) {
        this.count = count;
    }
}
